package com.gk969.gallerySimple;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.Log;
import android.os.Handler;

import com.gk969.gallery.gallery3d.glrenderer.BitmapTexture;
import com.gk969.UltimateImgSpider.SpiderService;
import com.gk969.gallery.gallery3d.glrenderer.StringTexture;
import com.gk969.gallery.gallery3d.glrenderer.Texture;
import com.gk969.gallery.gallery3d.glrenderer.TiledTexture;
import com.gk969.gallery.gallery3d.ui.GLRootView;

import java.io.File;
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

    private int thumbnailDispSize;
    private float screenDensity;

    private int bestOffsetOfDispInCache;
    private int cacheOffset=0;
    private AtomicInteger dispAreaOffset=new AtomicInteger(0);

    private AtomicBoolean isLoaderRunning=new AtomicBoolean(false);
    public AtomicInteger albumTotalImgNum=new AtomicInteger(0);

    private int imgsInDispArea;

    private GLRootView mGLrootView;

    TextureLoaderThread mTextureLoaderThread;

    private TiledTexture.Uploader mTextureUploader;

    private int infoTextFontSize=48;
    private final static int INFO_TEXT_COLOR=0xFF00FF00;

    public ThumbnailLoader(String Path, GLRootView glRoot, float density)
    {
        projectPath=Path+"/";
        mTextureUploader=new TiledTexture.Uploader(glRoot);
        mGLrootView=glRoot;
        for(int i=0; i<CACHE_SIZE; i++)
        {
            textureCache[i]=new SlotTexture();
            textureCache[i].imgIndex=i;
            textureCache[i].info=StringTexture.newInstance(String.valueOf(i), infoTextFontSize,
                                                            INFO_TEXT_COLOR);
            textureCache[i].isLoaded=new AtomicBoolean(false);
        }

        screenDensity=density;
    }

    public void setAlbumTotalImgNum(int totalImgNum)
    {
        if(totalImgNum==0&&albumTotalImgNum.get()!=0)
        {
            mGLrootView.lockRenderThread();
            for(SlotTexture slot:textureCache)
            {
                if(slot.thumbnail!=null)
                {
                    slot.thumbnail.recycle();
                    slot.thumbnail = null;
                    slot.info.recycle();
                    slot.info = null;
                }
                slot.isLoaded.set(false);
            }
            mGLrootView.unlockRenderThread();
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

    public void init(int slotSize, int slotNum)
    {
        thumbnailDispSize=slotSize;
        imgsInDispArea=slotNum;
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
        TiledTexture thumbnail;
        StringTexture info;
        int imgIndex;
        AtomicBoolean isLoaded;
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
                int cacheIndex = imgIndex % CACHE_SIZE;

                //Log.i(TAG, "recycle cacheIndex:" + cacheIndex + " imgIndex:" + imgIndex);
                if(textureCache[cacheIndex].thumbnail!=null)
                {
                    textureCache[cacheIndex].thumbnail.recycle();
                    textureCache[cacheIndex].thumbnail = null;
                    textureCache[cacheIndex].info.recycle();
                    textureCache[cacheIndex].info = null;
                }

                textureCache[cacheIndex].imgIndex = imgIndex;
                textureCache[cacheIndex].isLoaded.set(false);

                imgIndex += step;
            }

            scrollStep.set(step);
            cacheOffset=newCacheOffset;
        }

        dispAreaOffset.set(index);

        //Log.i(TAG, "scrollToIndex "+index+" cacheOffset "+cacheOffset);
    }

    private Bitmap getThumbnailByIndex(int index)
    {
        String fileName=String.format("%s%d/%03d.", projectPath,
                index/SpiderService.MAX_IMG_FILE_PER_DIR,
                index%SpiderService.MAX_IMG_FILE_PER_DIR);

        BitmapFactory.Options bmpOpts=new BitmapFactory.Options();
        bmpOpts.inPreferredConfig=Bitmap.Config.RGB_565;

        Bitmap rawBmp=null;
        for(String ext:IMG_FILE_EXT)
        {
            String fullName=fileName + ext;
            if(new File(fullName).exists())
            {
                rawBmp = BitmapFactory.decodeFile(fullName, bmpOpts);
                if (rawBmp != null)
                {
                    break;
                }
            }
        }

        if(rawBmp==null)
        {
            return null;
        }

        int rawWidth = rawBmp.getWidth();
        int rawHeight = rawBmp.getHeight();
        int x,y,w,h;
        float matrixScale=0;


        if(rawHeight > rawWidth)
        {
            matrixScale = (float)thumbnailDispSize / (float)rawWidth;
            x=0;
            w=rawWidth;
            y=(rawHeight-rawWidth)/2;
            h=rawWidth;
        }
        else
        {
            matrixScale=(float)thumbnailDispSize / (float)rawHeight;
            y=0;
            h=rawHeight;
            x=(rawWidth-rawHeight)/2;
            w=rawHeight;
        }

        matrixScale/=screenDensity;

        //Log.i(TAG, "matrixScale "+matrixScale);



        Matrix matrix = new Matrix();
        matrix.postScale(matrixScale, matrixScale);

        /*
        Log.i(TAG, "slot " + thumbnailDispSize);
        Log.i(TAG, "rawBitmap " + rawWidth + " " + rawHeight);
        Log.i(TAG, "thumbnail " + x + " " + y + " " + w + " " + h);
        Log.i(TAG, "matrixScale "+matrixScale);
        */

        Bitmap thumbnail = Bitmap.createBitmap(rawBmp, x, y, w, h, matrix, true);
        rawBmp.recycle();

        //Log.i(TAG, "thumbnail bmp " + thumbnail.getWidth() + " " + thumbnail.getHeight());
        return thumbnail;
    }

    private class TextureLoaderThread extends Thread
    {
        private final static int CHECK_INTERVAL=500;

        private boolean isOffsetChangedInLoading()
        {
            int step=scrollStep.get();
            int dispOffset=dispAreaOffset.get();
            int imgIndexForLoader=(step==1)?dispOffset:(dispOffset+imgsInDispArea-1);
            for(int i=0; i<CACHE_SIZE; i++)
            {
                int cacheIndex=imgIndexForLoader%CACHE_SIZE;
                if(!textureCache[cacheIndex].isLoaded.get())
                {
                    mGLrootView.lockRenderThread();
                    int imgIndex=textureCache[cacheIndex].imgIndex;
                    mGLrootView.unlockRenderThread();

                    Bitmap bmp = getThumbnailByIndex(imgIndex);
                    if(bmp!=null)
                    {
                        mGLrootView.lockRenderThread();
                        if(imgIndex==textureCache[cacheIndex].imgIndex)
                        {
                            textureCache[cacheIndex].thumbnail = new TiledTexture(bmp);
                            textureCache[cacheIndex].info=StringTexture.newInstance(
                                    String.valueOf(imgIndex), infoTextFontSize, INFO_TEXT_COLOR);
                            textureCache[cacheIndex].isLoaded.set(true);
                            //Log.i(TAG, "load  cacheIndex:" + cacheIndex + " imgIndex:" +
                            //        textureCache[cacheIndex].imgIndex);
                            mTextureUploader.addTexture(textureCache[cacheIndex].thumbnail);
                        }
                        else
                        {
                            Log.i(TAG, "offset changed");
                        }

                        mGLrootView.unlockRenderThread();
                    }
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