package com.gk969.gallerySimple;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.Log;
import android.os.Handler;

import com.gk969.UltimateImgSpider.StaticValue;
import com.gk969.Utils.Utils;
import com.gk969.gallery.gallery3d.glrenderer.BitmapTexture;
import com.gk969.gallery.gallery3d.glrenderer.StringTexture;
import com.gk969.gallery.gallery3d.glrenderer.Texture;
import com.gk969.gallery.gallery3d.glrenderer.TiledTexture;
import com.gk969.gallery.gallery3d.ui.GLRootView;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/*
Cache Mode:
*************************
*                       *
*      img file         *
*                       *
* ********************  *
* *                  *  *
* *  texture cache   *  *
* *                  *  *
* * ***************  *  *
* * *             *  *  *
* * *  disp area  *  *  *
* * *             *  *  *
* * ***************  *  *
* *                  *  *
* *                  *  *
* ********************  *
*                       *
*                       *
*************************
 */


public  class ThumbnailLoader
{
    private static final String TAG = "ThumbnailLoader";

    private final static int CACHE_SIZE=64;
    //A circle cache
    private SlotTexture[] textureCache=new SlotTexture[CACHE_SIZE];
    private volatile int scrollStep=1;

    private int bestOffsetOfDispInCache;
    private int cacheOffset=0;

    private volatile int dispAreaOffset;
    private volatile boolean isLoaderRunning;
    public volatile int albumTotalImgNum;
    private volatile int imgsInDispArea;

    private GLRootView mGLRootView;

    private TextureLoaderThread mTextureLoaderThread;

    private TiledTexture.Uploader mTextureUploader;

    private SlotView slotView;

    private ThumbnailLoaderHelper loaderHelper;

    private int labelTextSize;
    private final static int LABEL_NAME_COLOR=0xFF00F000;
    private final static int LABEL_INFO_ACTIVE_COLOR=0xFF00F000;
    private final static int LABEL_INFO_INACTIVE_COLOR=0xFFFFFFFF;

    private int activeSlotIndex=StaticValue.INDEX_INVALID;

    private volatile boolean needLabel;

    private int labelNameLimit;

    public volatile boolean isPaused;
    private Utils.ReadWaitLock loaderPauseLock=new Utils.ReadWaitLock();

    public ThumbnailLoader(GLRootView glRoot, ThumbnailLoaderHelper helper)
    {

        for(int i=0; i<CACHE_SIZE; i++)
        {
            textureCache[i]=new SlotTexture();
            textureCache[i].mainBmp=Bitmap.createBitmap(StaticValue.THUMBNAIL_SIZE,
                    StaticValue.THUMBNAIL_SIZE, Bitmap.Config.RGB_565);

            textureCache[i].imgIndex=new AtomicInteger(i);
            textureCache[i].isReady=new AtomicBoolean(false);
            textureCache[i].hasTried=new AtomicBoolean(false);
        }

        mTextureUploader=new TiledTexture.Uploader(glRoot);
        mGLRootView=glRoot;
        loaderHelper=helper;
        helper.setLoader(this);
        needLabel=helper.needLabel();
    }

    public void refreshSlotInfo(int slotIndex, String infoStr, boolean isActive)
    {
        mGLRootView.lockRenderThread();
        activeSlotIndex=isActive?slotIndex:StaticValue.INDEX_INVALID;

        if(infoStr!=null)
        {
            if((slotIndex >= cacheOffset) && (slotIndex < (cacheOffset + CACHE_SIZE)))
            {
                textureCache[slotIndex % CACHE_SIZE].labelInfo = StringTexture.newInstance(infoStr,
                        labelTextSize, isActive?LABEL_INFO_ACTIVE_COLOR:LABEL_INFO_INACTIVE_COLOR);
            }
        }

        mGLRootView.unlockRenderThread();
    }

    public void setView(SlotView view)
    {
        slotView=view;
    }

    public void onPause()
    {
        isPaused=true;
        loaderPauseLock.lock();
    }

    public void onResume()
    {
        isPaused=false;
        loaderPauseLock.unlock();
    }

    public void setHelper(ThumbnailLoaderHelper helper, int totalImgNum)
    {
        loaderHelper=helper;
        needLabel=helper.needLabel();

        mGLRootView.lockRenderThread();
        slotView.stopAnimation();
        mGLRootView.unlockRenderThread();

        setAlbumTotalImgNum(0);
        setAlbumTotalImgNum(totalImgNum);
        mTextureLoaderThread.interrupt();
    }

    public void setHelper(ThumbnailLoaderHelper helper, int totalImgNum, int scrollDistance)
    {
        setHelper(helper, totalImgNum);

        mGLRootView.lockRenderThread();
        slotView.scrollAbs(scrollDistance);
        mGLRootView.unlockRenderThread();
    }

    private void clearCache()
    {
        for (SlotTexture slot : textureCache)
        {
            slot.prepareToRecycle();
        }

        mTextureUploader.clear();
    }

    public void setAlbumTotalImgNum(int totalImgNum)
    {
        //Log.i(TAG, "setAlbumTotalImgNum " + totalImgNum);

        int prevTotalImgNum=albumTotalImgNum;
        if(prevTotalImgNum==totalImgNum)
        {
            return;
        }

        albumTotalImgNum=totalImgNum;

        if(totalImgNum==0)
        {
            if(prevTotalImgNum!=0)
            {
                mGLRootView.lockRenderThread();
                clearCache();
                slotView.scrollAbs(0);
                mGLRootView.unlockRenderThread();
            }
        }
        else if(totalImgNum>prevTotalImgNum)
        {
            if(dispAreaOffset+CACHE_SIZE>prevTotalImgNum)
            {
                mGLRootView.lockRenderThread();
                refreshCacheOffset(dispAreaOffset, true);
                mGLRootView.unlockRenderThread();

                for (SlotTexture slot:textureCache)
                {
                    slot.hasTried.set(false);
                }
            }

            if(prevTotalImgNum!=0)
            {
                slotView.onNewImgReceived(prevTotalImgNum);
            }
        }

        mGLRootView.requestRender();
    }

    private void startLoader()
    {
        TiledTexture.prepareResources();
        isLoaderRunning=true;
        mTextureLoaderThread = new TextureLoaderThread();
        mTextureLoaderThread.setDaemon(true);
        mTextureLoaderThread.start();
    }

    public void stopLoader()
    {
        isLoaderRunning=false;
    }

    public void initAboutView(int slotNum, int paraLabelTextSize, int paraLabelNameLimit)
    {
        imgsInDispArea=slotNum;
        bestOffsetOfDispInCache=(CACHE_SIZE-slotNum)/2;
        Log.i(TAG, "slotNum " + slotNum);

        labelTextSize=paraLabelTextSize;
        labelNameLimit=paraLabelNameLimit;

        if(!isLoaderRunning)
        {
            startLoader();
        }
        else
        {
            clearCache();
        }
    }

    public SlotTexture getTexture(int index)
    {
        if((index>=0)&&(index<albumTotalImgNum))
        {
            //Log.i(TAG, "getTexture "+index);
            return textureCache[index % CACHE_SIZE];
        }
        return null;
    }

    public class SlotTexture
    {
        Bitmap mainBmp;
        TiledTexture texture;
        StringTexture labelName;
        StringTexture labelInfo;
        AtomicInteger imgIndex;
        AtomicBoolean isReady;
        AtomicBoolean hasTried;

        public void recycleTiledTexture()
        {
            if(texture!=null)
            {
                texture.recycle();
                texture = null;
            }

        }

        public void prepareToRecycle()
        {
            if(needLabel)
            {
                if(labelInfo!=null)
                {
                    labelInfo.recycle();
                    labelInfo=null;
                }

                if(labelName!=null)
                {
                    labelName.recycle();
                    labelName = null;
                }
            }
            isReady.set(false);
            hasTried.set(false);
        }
    }



    public void dispAreaScrollToIndex(int index)
    {
        refreshCacheOffset(index, false);
    }

    private void refreshCacheOffset(int index, boolean forceRefresh)
    {
        int newCacheOffset=index-bestOffsetOfDispInCache;
        int cacheOffsetMax=albumTotalImgNum-CACHE_SIZE;
        if(cacheOffsetMax<0)
        {
            cacheOffsetMax=0;
        }

        if(newCacheOffset<0)
        {
            newCacheOffset=0;
        }
        else if(newCacheOffset>cacheOffsetMax)
        {
            newCacheOffset=cacheOffsetMax;
        }

        if((newCacheOffset != cacheOffset)||forceRefresh)
        {
            int interval=Math.abs(newCacheOffset-cacheOffset);
            int step;
            int imgIndex;
            if (newCacheOffset >= cacheOffset)
            {
                step = 1;
                imgIndex=cacheOffset+CACHE_SIZE;
            }
            else
            {
                step=-1;
                imgIndex=cacheOffset-1;
            }

            for(int i=0; i<interval; i++)
            {
                //Log.i(TAG, "recycle cacheIndex:" + cacheIndex + " imgIndex:" + imgIndex);
                SlotTexture slot=textureCache[imgIndex % CACHE_SIZE];
                slot.prepareToRecycle();

                slot.imgIndex.set(imgIndex);
                imgIndex += step;
            }

            scrollStep=step;
            cacheOffset=newCacheOffset;
        }

        dispAreaOffset=index;

        //Log.i(TAG, "scrollToIndex "+index+" cacheOffset "+cacheOffset);
    }


    private class TextureLoaderThread extends Thread
    {
        private final static int CHECK_INTERVAL=500;

        public TextureLoaderThread()
        {
            super("TextureLoaderThread");
        }

        private boolean isOffsetChangedInLoading()
        {
            int step=scrollStep;
            int dispOffset=dispAreaOffset;
            int imgIndexForLoader=(step==1)?dispOffset:(dispOffset+imgsInDispArea-1);
            for(int i=0; i<CACHE_SIZE; i++)
            {
                if(isPaused)
                {
                    break;
                }

                SlotTexture slot=textureCache[imgIndexForLoader%CACHE_SIZE];
                if(!slot.hasTried.get())
                {
                    //Log.i(TAG, "Try "+slot.imgIndex.get());
                    boolean hasTried=true;
                    if(!slot.isReady.get())
                    {
                        int imgIndex = slot.imgIndex.get();
                        slot.recycleTiledTexture();

                        Bitmap bmp = loaderHelper.getThumbnailByIndex(imgIndex, slot.mainBmp);
                        if (bmp != null)
                        {
                            if (imgIndex == slot.imgIndex.get())
                            {
                                TiledTexture texture = new TiledTexture(bmp);

                                loaderPauseLock.waitIfLocked();

                                mGLRootView.lockRenderThread();

                                slot.texture=texture;
                                slot.isReady.set(true);
                                mTextureUploader.addTexture(slot.texture);

                                if(needLabel)
                                {
                                    //Log.i(TAG, "Load Label " + imgIndex + " " + loaderHelper.getLabelString(imgIndex));

                                    String[] labelStr=loaderHelper.getLabelString(imgIndex).split(" ");

                                    slot.labelName = StringTexture.newInstance(labelStr[0], labelTextSize,
                                            LABEL_NAME_COLOR, labelNameLimit, false);
                                    if(labelStr.length>1)
                                    {
                                        slot.labelInfo = StringTexture.newInstance(labelStr[1],
                                                labelTextSize, (imgIndex==activeSlotIndex)?
                                                LABEL_INFO_ACTIVE_COLOR:LABEL_INFO_INACTIVE_COLOR);
                                    }
                                }

                                mGLRootView.unlockRenderThread();
                            }
                        }


                        hasTried=imgIndex == slot.imgIndex.get();
                    }

                    slot.hasTried.set(hasTried);
                }

                if(dispAreaOffset!=dispOffset)
                {
                    return true;
                }

                imgIndexForLoader+=step;
                if(imgIndexForLoader<0)
                {
                    imgIndexForLoader=CACHE_SIZE-1;
                }

            }

            return false;
        }

        public void run()
        {
            while(isLoaderRunning)
            {
                while(isOffsetChangedInLoading());

                try
                {
                    sleep(CHECK_INTERVAL);
                } catch (InterruptedException e)
                {
                    Log.i(TAG, "Loader Interrupted");
                }
            }
        }
    }
}