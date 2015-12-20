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
import com.gk969.gallery.gallery3d.ui.GLRootView;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

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

/*
    StringTexture strTexture;
    BitmapTexture bmpTexture0;
    BitmapTexture bmpTexture1;
    BitmapTexture bmpTexture2;

    private void loadTestTexture()
    {
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inPreferredConfig=Bitmap.Config.RGB_565;
        bmpTexture0=new BitmapTexture(BitmapFactory.decodeFile(projectPath+"/0/000.jpg", opts));
        bmpTexture1=new BitmapTexture(BitmapFactory.decodeFile(projectPath+"/0/001.jpg"));
        bmpTexture2=new BitmapTexture(BitmapFactory.decodeFile(projectPath+"/0/002.jpg"));
        strTexture = StringTexture.newInstance("string texture", 64, 0xFFFF0000);
    }
    */

public class ThumbnailLoader
{
    private static final String TAG = "ThumbnailLoader";

    private final static int CACHE_SIZE=96;
    //A circle cache
    private SlotTexture[] textureCache=new SlotTexture[CACHE_SIZE];

    private String projectPath;

    private final static String[] IMG_FILE_EXT={"jpg", "png", "gif"};

    private int slotWidth;
    private int slotHeight;
    private float slotDivide;

    private AtomicBoolean isLoaderValid=new AtomicBoolean(false);

    TextureLoader mTextureLoader;

    private GLRootView mGLRootView;

    public ThumbnailLoader(String Path, GLRootView glRoot)
    {
        projectPath=Path+"/";
        mGLRootView=glRoot;

        for(int i=0; i<CACHE_SIZE; i++)
        {
            textureCache[i]=new SlotTexture();
            textureCache[i].isReady=new AtomicBoolean(false);
        }

    }

    public void startLoader()
    {
        if(!isLoaderValid.get())
        {
            isLoaderValid.set(true);
            mTextureLoader = new TextureLoader();
            mTextureLoader.start();
        }
    }

    public void stopLoader()
    {
        isLoaderValid.set(false);
    }

    public void setSlotTextureSize(int width, int height)
    {
        slotWidth=width;
        slotHeight=height;
        slotDivide=width/height;

        startLoader();
    }

    public SlotTexture getTexture(int index)
    {
        return textureCache[index%CACHE_SIZE];
    }

    public class SlotTexture
    {
        BitmapTexture texture;
        AtomicBoolean isReady;
        int xInSlot;
        int yInSlot;
    }

    Bitmap getBitmapByIndex(int index)
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

        return rawBmp;
    }

    private class TextureLoader extends Thread
    {
        private final static int CHECK_INTERVAL=500;



        public void run()
        {
            while(isLoaderValid.get())
            {
                int i=0;
                for(int index=0; index<CACHE_SIZE; index++)
                {
                    if(!textureCache[i].isReady.get())
                    {
                        Bitmap rawBitmap = getBitmapByIndex(index);
                        if(rawBitmap!=null)
                        {
                            int width = rawBitmap.getWidth();
                            int height = rawBitmap.getHeight();
                            float matrixScale = (slotDivide > (float)width / (float)height) ?
                                    (float)slotHeight / (float)height : (float)slotWidth / (float)width;
                            Matrix matrix = new Matrix();
                            matrix.postScale(matrixScale, matrixScale);
                            Log.i(TAG, "slot " + slotWidth + " " + slotHeight);
                            Log.i(TAG, "rawBitmap " + width + " " + height);
                            Log.i(TAG, "matrix "+matrixScale);
                            Bitmap bmp = Bitmap.createBitmap(rawBitmap, 0, 0, width, height, matrix, true);
                            rawBitmap.recycle();

                            textureCache[i].texture = new BitmapTexture(bmp);
                            textureCache[i].xInSlot = (slotWidth - bmp.getWidth()) / 2;
                            textureCache[i].yInSlot = (slotHeight - bmp.getHeight()) / 2;
                            textureCache[i].isReady.set(true);

                            mGLRootView.requestRender();

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