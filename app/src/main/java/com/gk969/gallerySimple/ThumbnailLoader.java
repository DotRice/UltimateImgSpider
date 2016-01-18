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


public class ThumbnailLoader
{
    private static final String TAG = "ThumbnailLoader";

    private final static int CACHE_SIZE=64;
    //A circle cache
    private SlotTexture[] textureCache=new SlotTexture[CACHE_SIZE];
    private AtomicInteger scrollStep=new AtomicInteger(1);

    private String projectPath;

    private final static String[] IMG_FILE_EXT={"jpg", "png", "gif"};

    private int bestOffsetOfDispInCache;
    private int cacheOffset=0;
    private AtomicInteger dispAreaOffset=new AtomicInteger(0);

    private AtomicBoolean isLoaderRunning=new AtomicBoolean(false);
    public AtomicInteger albumTotalImgNum=new AtomicInteger(0);

    private AtomicInteger imgsInDispArea=new AtomicInteger(0);

    private GLRootView mGLrootView;

    TextureLoaderThread mTextureLoaderThread;

    private TiledTexture.Uploader mTextureUploader;

    private int infoTextFontSize=48;
    private final static int INFO_TEXT_COLOR=0xFF00FF00;

    public ThumbnailLoader(String Path, GLRootView glRoot)
    {
        projectPath=Path+"/";
        mTextureUploader=new TiledTexture.Uploader(glRoot);
        mGLrootView=glRoot;

        for(int i=0; i<CACHE_SIZE; i++)
        {
            textureCache[i]=new SlotTexture();
            textureCache[i].bitmap=Bitmap.createBitmap(StaticValue.THUMBNAIL_SIZE,
                    StaticValue.THUMBNAIL_SIZE, Bitmap.Config.RGB_565);
            textureCache[i].imgIndex=i;
            textureCache[i].info=StringTexture.newInstance(String.valueOf(i), infoTextFontSize,
                                                            INFO_TEXT_COLOR);
            textureCache[i].isReady=new AtomicBoolean(false);
            textureCache[i].hasTried=new AtomicBoolean(false);
        }
    }

    private void clearCache()
    {
        mGLrootView.lockRenderThread();
        for (SlotTexture slot : textureCache)
        {
            if (slot.texture != null)
            {
                slot.texture.recycle();
                slot.texture = null;
                slot.info.recycle();
                slot.info = null;
            }
            slot.isReady.set(false);
            slot.hasTried.set(false);
        }
        mGLrootView.unlockRenderThread();
    }

    public void setAlbumTotalImgNum(int totalImgNum)
    {
        if(totalImgNum==0)
        {
            if(albumTotalImgNum.get()!=0)
            {
                clearCache();
                mGLrootView.requestRender();
            }
        }

        if(dispAreaOffset.get()+CACHE_SIZE>albumTotalImgNum.get())
        {
            for(SlotTexture slot : textureCache)
            {
                slot.hasTried.set(false);
            }
        }

        if(dispAreaOffset.get()+imgsInDispArea.get()>albumTotalImgNum.get())
        {
            mGLrootView.requestRender();
        }
        albumTotalImgNum.set(totalImgNum);
    }

    public void startLoader()
    {
        if(!isLoaderRunning.get())
        {
            isLoaderRunning.set(true);
            mTextureLoaderThread = new TextureLoaderThread();
            mTextureLoaderThread.start();
        }
    }

    public void stopLoader()
    {
        isLoaderRunning.set(false);
    }

    public void init(int slotNum)
    {
        imgsInDispArea.set(slotNum);
        bestOffsetOfDispInCache=(CACHE_SIZE-slotNum)/2;
        TiledTexture.prepareResources();
        Log.i(TAG, "slotNum "+slotNum);
        startLoader();
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
        TiledTexture texture;
        Bitmap bitmap;
        StringTexture info;
        int imgIndex;
        AtomicBoolean isReady;
        AtomicBoolean hasTried;
    }

    public void dispAreaScrollToIndex(int index)
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

        if(newCacheOffset != cacheOffset)
        {
            int interval=Math.abs(newCacheOffset-cacheOffset);
            int step;
            int imgIndex;
            if (newCacheOffset > cacheOffset)
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
                if(slot.texture!=null)
                {
                    slot.texture.recycle();
                    slot.texture = null;
                    slot.info.recycle();
                    slot.info = null;
                }

                slot.imgIndex = imgIndex;
                slot.isReady.set(false);
                slot.hasTried.set(false);

                imgIndex += step;
            }

            scrollStep.set(step);
            cacheOffset=newCacheOffset;
        }

        dispAreaOffset.set(index);

        //Log.i(TAG, "scrollToIndex "+index+" cacheOffset "+cacheOffset);
    }

    private Bitmap getThumbnailByIndex(int index, Bitmap container)
    {
        int group=index/StaticValue.MAX_IMG_FILE_PER_DIR;
        int offset=index%StaticValue.MAX_IMG_FILE_PER_DIR;

        String fileName=String.format("%s%s/%d/%03d.jpg", projectPath,StaticValue.THUMBNAIL_DIR_NAME,
                                        group, offset);

        BitmapFactory.Options bmpOpts=new BitmapFactory.Options();
        bmpOpts.inPreferredConfig=Bitmap.Config.RGB_565;
        bmpOpts.inBitmap=container;

        return BitmapFactory.decodeFile(fileName, bmpOpts);
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
                if(!(slot.hasTried.get()||slot.isReady.get()))
                {
                    mGLrootView.lockRenderThread();
                    int imgIndex=slot.imgIndex;
                    Bitmap bmpContainer=slot.bitmap;
                    mGLrootView.unlockRenderThread();

                    Bitmap bmp = getThumbnailByIndex(imgIndex, bmpContainer);

                    if(bmp!=null)
                    {
                        mGLrootView.lockRenderThread();
                        if(imgIndex==slot.imgIndex)
                        {
                            slot.texture = new TiledTexture(bmp);
                            slot.info=StringTexture.newInstance(
                                    String.valueOf(imgIndex), infoTextFontSize, INFO_TEXT_COLOR);
                            slot.isReady.set(true);
                            //Log.i(TAG, "load  cacheIndex:" + cacheIndex + " imgIndex:" +
                            //        textureCache[cacheIndex].imgIndex);
                            mTextureUploader.addTexture(slot.texture);
                        }

                        mGLrootView.unlockRenderThread();
                    }
                    slot.hasTried.set(imgIndex == slot.imgIndex);
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