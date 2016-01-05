package com.gk969.gallerySimple;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.Log;
import android.os.Handler;

import com.gk969.gallery.gallery3d.glrenderer.BitmapTexture;
import com.gk969.UltimateImgSpider.SpiderService;
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

    private final static int CACHE_SIZE=96;
    //A circle cache
    private SlotTexture[] textureCache=new SlotTexture[CACHE_SIZE];

    private String projectPath;

    private final static String[] IMG_FILE_EXT={"jpg", "png", "gif"};

    private int mSlotSize;
    private float screenDensity;
    private int mWindowSize;

    private int mScrollIndex;

    private AtomicBoolean isLoaderRunning=new AtomicBoolean(false);
    public AtomicInteger albumTotalImgNum=new AtomicInteger(96);

    TextureLoaderThread mTextureLoaderThread;

    private GLRootView mGLRootView;
    private TiledTexture.Uploader mTextureUploader;

    public ThumbnailLoader(String Path, GLRootView glRoot, float density)
    {
        projectPath=Path+"/";
        mGLRootView=glRoot;
        mTextureUploader=new TiledTexture.Uploader(glRoot);

        for(int i=0; i<CACHE_SIZE; i++)
        {
            textureCache[i]=new SlotTexture();
            textureCache[i].isLoaded=new AtomicBoolean(false);
        }

        screenDensity=density;
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
        mSlotSize=slotSize;
        mWindowSize=slotNum;
        TiledTexture.prepareResources();

        startLoader();
    }

    public SlotTexture getTexture(int index)
    {
        if(index>=0)
        {
            //Log.i(TAG, "getTexture "+index);
            return textureCache[index % CACHE_SIZE];
        }
        return null;
    }

    public class SlotTexture
    {
        TiledTexture texture;
        AtomicBoolean isLoaded;
    }

    public void scrollToIndex(int index)
    {
        mScrollIndex=index;
        Log.i(TAG, "scrollToIndex "+index);
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
            rawBmp = BitmapFactory.decodeFile(fileName + ext, bmpOpts);
            if(rawBmp!=null)
            {
                break;
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
            matrixScale = (float)mSlotSize / (float)rawWidth;
            x=0;
            w=rawWidth;
            y=(rawHeight-rawWidth)/2;
            h=rawWidth;
        }
        else
        {
            matrixScale=(float)mSlotSize / (float)rawHeight;
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
        Log.i(TAG, "slot " + mSlotSize);
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



        public void run()
        {
            while(isLoaderRunning.get())
            {
                int i=0;
                for(int index=0; index<CACHE_SIZE; index++)
                {
                    if(!textureCache[i].isLoaded.get())
                    {
                        Bitmap bmp = getThumbnailByIndex(index);
                        if(bmp!=null)
                        {
                            textureCache[i].texture = new TiledTexture(bmp);
                            textureCache[i].isLoaded.set(true);

                            //Log.i(TAG, "mTextureUploader.addTexture "+i);
                            mTextureUploader.addTexture(textureCache[i].texture);

                            i++;
                        }
                    }
                }

                try
                {
                    sleep(CHECK_INTERVAL);
                }catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }
}