package com.gk969.gallerySimple;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.Log;
import android.os.Handler;

import com.gk969.UltimateImgSpider.StaticValue;
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

    private final static int CACHE_SIZE=128;
    //A circle cache
    private SlotTexture[] textureCache=new SlotTexture[CACHE_SIZE];
    private AtomicInteger scrollStep=new AtomicInteger(1);

    private final static String[] IMG_FILE_EXT={"jpg", "png", "gif"};

    private int bestOffsetOfDispInCache;
    private int cacheOffset=0;
    private AtomicInteger dispAreaOffset=new AtomicInteger(0);

    private AtomicBoolean isLoaderRunning=new AtomicBoolean(false);
    public AtomicInteger albumTotalImgNum=new AtomicInteger(0);

    private AtomicInteger imgsInDispArea=new AtomicInteger(0);

    private GLRootView mGLrootView;

    private TextureLoaderThread mTextureLoaderThread;

    private TiledTexture.Uploader mTextureUploader;

    private SlotView slotView;

    private ThumbnailLoaderHelper loaderHelper;

    protected int labelHeight;
    private final static int INFO_TEXT_COLOR=0xFF00FF00;

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
        mGLrootView=glRoot;
        loaderHelper=helper;
        loaderHelper.setLoader(this);
    }

    public void setView(SlotView view)
    {
        slotView=view;
    }

    private void clearCache()
    {
        for (SlotTexture slot : textureCache)
        {
            slot.recycle();
        }

        mTextureUploader.clear();
        TiledTexture.freeResources();
    }

    public void setAlbumTotalImgNum(int totalImgNum)
    {
        //Log.i(TAG, "setAlbumTotalImgNum " + totalImgNum);

        int prevTotalImgNum=albumTotalImgNum.get();
        if(prevTotalImgNum==totalImgNum)
        {
            return;
        }

        albumTotalImgNum.set(totalImgNum);

        if(totalImgNum==0)
        {
            if(prevTotalImgNum!=0)
            {
                mGLrootView.lockRenderThread();
                clearCache();
                slotView.scrollAbs(0);
                mGLrootView.unlockRenderThread();
            }
        }
        else if(totalImgNum>prevTotalImgNum)
        {
            if(dispAreaOffset.get()+CACHE_SIZE>prevTotalImgNum)
            {
                mGLrootView.lockRenderThread();
                refreshCacheOffset(dispAreaOffset.get(), true);
                mGLrootView.unlockRenderThread();

                for (SlotTexture slot:textureCache)
                {
                    slot.hasTried.set(false);
                }
            }

        }

        mGLrootView.requestRender();
    }

    private void startLoader()
    {
        TiledTexture.prepareResources();
        isLoaderRunning.set(true);
        mTextureLoaderThread = new TextureLoaderThread();
        mTextureLoaderThread.setDaemon(true);
        mTextureLoaderThread.start();

    }

    public void stopLoader()
    {
        isLoaderRunning.set(false);
    }

    public void initAboutView(int slotNum, int paraLabelHeight)
    {
        imgsInDispArea.set(slotNum);
        bestOffsetOfDispInCache=(CACHE_SIZE-slotNum)/2;
        Log.i(TAG, "slotNum " + slotNum);

        labelHeight=paraLabelHeight;

        if(!isLoaderRunning.get())
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
        if((index>=0)&&(index<albumTotalImgNum.get()))
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
        StringTexture label;
        AtomicInteger imgIndex;
        AtomicBoolean isReady;
        AtomicBoolean hasTried;

        public void recycle()
        {
            if(texture!=null)
            {
                texture.recycle();
                texture = null;

                if(loaderHelper.needLabel())
                {
                    label.recycle();
                    label = null;
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
        int cacheOffsetMax=albumTotalImgNum.get()-CACHE_SIZE;
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
                slot.recycle();

                slot.imgIndex.set(imgIndex);
                imgIndex += step;
            }

            scrollStep.set(step);
            cacheOffset=newCacheOffset;
        }

        dispAreaOffset.set(index);

        //Log.i(TAG, "scrollToIndex "+index+" cacheOffset "+cacheOffset);
    }


    private class TextureLoaderThread extends Thread
    {
        private final static int CHECK_INTERVAL=500;

        private boolean isOffsetChangedInLoading()
        {
            int step=scrollStep.get();
            int dispOffset=dispAreaOffset.get();
            int imgIndexForLoader=(step==1)?dispOffset:(dispOffset+imgsInDispArea.get()-1);
            for(int i=0; i<CACHE_SIZE; i++)
            {
                int cacheIndex=imgIndexForLoader%CACHE_SIZE;

                SlotTexture slot=textureCache[cacheIndex];
                if(!slot.hasTried.get())
                {
                    Log.i(TAG, "Try "+slot.imgIndex.get());
                    boolean hasTried=true;
                    if(!slot.isReady.get())
                    {
                        mGLrootView.lockRenderThread();
                        int imgIndex = slot.imgIndex.get();
                        Bitmap bmpContainer = slot.mainBmp;
                        mGLrootView.unlockRenderThread();

                        Bitmap bmp = loaderHelper.getThumbnailByIndex(imgIndex, bmpContainer);

                        if (bmp != null)
                        {
                            mGLrootView.lockRenderThread();
                            if (imgIndex == slot.imgIndex.get())
                            {
                                slot.texture = new TiledTexture(bmp);

                                if(loaderHelper.needLabel())
                                {
                                    slot.label = StringTexture.newInstance(
                                            loaderHelper.getLabelString(imgIndex),
                                            labelHeight, INFO_TEXT_COLOR);
                                }

                                slot.isReady.set(true);
                                Log.i(TAG, "load success  cacheIndex:" + cacheIndex + " imgIndex:" +
                                        textureCache[cacheIndex].imgIndex);
                                mTextureUploader.addTexture(slot.texture);

                            }

                            mGLrootView.unlockRenderThread();
                        }
                        hasTried=imgIndex == slot.imgIndex.get();
                    }

                    slot.hasTried.set(hasTried);
                }

                if(dispAreaOffset.get()!=dispOffset)
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
            while(isLoaderRunning.get())
            {
                if(!isOffsetChangedInLoading())
                {
                    try
                    {
                        sleep(CHECK_INTERVAL);
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}