package com.gk969.gallerySimple;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Message;
import android.os.SystemClock;
import android.util.FloatMath;
import android.util.Log;
import android.os.Handler;
import android.view.MotionEvent;

import com.gk969.UltimateImgSpider.SpiderProject;
import com.gk969.UltimateImgSpider.StaticValue;
import com.gk969.Utils.Utils;
import com.gk969.gallery.gallery3d.common.ApiHelper;
import com.gk969.gallery.gallery3d.data.MediaItem;
import com.gk969.gallery.gallery3d.glrenderer.BitmapTexture;
import com.gk969.gallery.gallery3d.glrenderer.GLCanvas;
import com.gk969.gallery.gallery3d.glrenderer.StringTexture;
import com.gk969.gallery.gallery3d.glrenderer.TiledTexture;
import com.gk969.gallery.gallery3d.ui.GLRootView;
import com.gk969.gallery.gallery3d.ui.GLView;
import com.gk969.gallery.gallery3d.ui.GestureRecognizer;
import com.gk969.gallery.gallery3d.ui.Paper;
import com.gk969.gallery.gallery3d.ui.SynchronizedHandler;
import com.gk969.gallery.gallery3d.util.GalleryUtils;
import com.gk969.gallery.gallery3d.util.UsageStatistics;

import java.io.File;

public class PhotoView extends GLView
{
    private static final String TAG = "PhotoView";
    private final float mMatrix[] = new float[16];
    private final static float BACKGROUND_COLOR[] = new float[]{0, 0, 0, 0};

    private float scaleForImg;

    private int viewWidth;
    private int viewHeight;
    private int viewWidthForImg;
    private int viewHeightForImg;

    private int minSize;
    private int minSizeForImg;

    private int minSqureTop;
    private int minSqureLeft;

    private long renderTime;
    private GLRootView mGLRootView;
    private TiledTexture.Uploader mTextureUploader;

    private GestureRecognizer mGestureRecognizer;

    private boolean isTouching;

    private volatile int curPhotoIndex= SpiderProject.INVALID_INDEX;
    private volatile boolean isLoaderRunning=true;

    private PhotoLoader loader;
    private Photo photo=new Photo();
    private volatile String curProjectPath;

    private class Photo
    {
        int dispTop;
        int dispLeft;
        int dispWidth;
        int dispHeight;

        TiledTexture texture;
        Bitmap wholeBmp;
        BitmapFactory.Options bmpOptions = new BitmapFactory.Options();
        String imgFilePath;

        public void paramInit(int index)
        {
            Log.i(TAG, "paramInit "+index);

            if(viewHeight==0 || viewWidth==0)
            {
                return;
            }

            int group=index/ StaticValue.MAX_IMG_FILE_PER_DIR;
            int offset=index%StaticValue.MAX_IMG_FILE_PER_DIR;

            for(int i=0; i< StaticValue.IMG_FILE_EXT.length; i++)
            {
                File file=new File(String.format("%s/%d/%03d.%s", curProjectPath,
                        group, offset, StaticValue.IMG_FILE_EXT[i]));
                if(file.exists())
                {
                    imgFilePath=file.getPath();
                    break;
                }
            }

            if(imgFilePath==null)
            {
                return;
            }


            bmpOptions.inJustDecodeBounds = true;
            bmpOptions.outHeight=0;
            bmpOptions.outWidth=0;
            bmpOptions.inSampleSize=1;
            BitmapFactory.decodeFile(imgFilePath, bmpOptions);

            if(bmpOptions.outWidth==0 || bmpOptions.outHeight==0)
            {
                return;
            }


            if(bmpOptions.outWidth<=minSizeForImg && bmpOptions.outHeight<=minSizeForImg)
            {
                if(bmpOptions.outHeight>bmpOptions.outWidth)
                {
                    dispWidth=bmpOptions.outWidth*minSize/bmpOptions.outHeight;
                    dispLeft=minSqureLeft+(minSize-dispWidth)/2;
                    dispHeight=minSize;
                    dispTop=minSqureTop;
                }
                else
                {
                    dispWidth=minSize;
                    dispLeft=minSqureLeft;
                    dispHeight=bmpOptions.outHeight*minSize/bmpOptions.outWidth;
                    dispTop=minSqureTop+(minSize-dispHeight)/2;
                }
            }
            else if(bmpOptions.outWidth<viewWidthForImg && bmpOptions.outHeight<viewHeightForImg)
            {
                dispWidth=(int)(scaleForImg*bmpOptions.outWidth);
                dispLeft=(viewWidth-dispWidth)/2;
                dispHeight=(int)(scaleForImg*bmpOptions.outHeight);
                dispTop=(viewHeight-dispHeight)/2;
            }
            else if(bmpOptions.outHeight*viewWidthForImg > bmpOptions.outWidth*viewHeight)
            {
                dispWidth=bmpOptions.outWidth*viewHeight/bmpOptions.outHeight;
                dispLeft=(viewWidth-dispWidth)/2;
                dispHeight=viewHeight;
                dispTop=0;
            }
            else
            {
                dispWidth=viewWidth;
                dispLeft=0;
                dispHeight=bmpOptions.outHeight*viewWidth/bmpOptions.outWidth;
                dispTop=(viewHeight-dispHeight)/2;
            }

            bmpOptions.inSampleSize=(int)(bmpOptions.outHeight/(dispHeight/scaleForImg));
            bmpOptions.inJustDecodeBounds=false;

            Log.i(TAG, String.format("%s raw size:%d*%d inSampleSize:%d", imgFilePath,
                    bmpOptions.outWidth, bmpOptions.outHeight, bmpOptions.inSampleSize));
        }

        void loadWholeBitmap()
        {
            if(bmpOptions.outHeight!=0 && bmpOptions.outWidth!=0)
            {
                wholeBmp = BitmapFactory.decodeFile(imgFilePath, bmpOptions);
                if(wholeBmp != null)
                {
                    Log.i(TAG, String.format("whole bmp size:%d*%d",
                            wholeBmp.getWidth(), wholeBmp.getHeight()));
                }
            }
        }

        void createTexture()
        {
            if(wholeBmp!=null)
            {
                texture=new TiledTexture(wholeBmp);
                mTextureUploader.addTexture(texture);
            }
        }

        private void recycle()
        {
            if(texture!=null)
            {
                texture.recycle();
                texture=null;
            }

            if(wholeBmp!=null)
            {
                wholeBmp.recycle();
                wholeBmp=null;
            }


            imgFilePath=null;
        }
    }


    public PhotoView(Context context, GLRootView glRootView)
    {
        mGestureRecognizer = new GestureRecognizer(context, new MyGestureListener());

        scaleForImg=context.getResources().getDisplayMetrics().density;
        mGLRootView = glRootView;

        mTextureUploader=new TiledTexture.Uploader(glRootView);
        TiledTexture.prepareResources();
        loader=new PhotoLoader();
        loader.start();
    }

    @Override
    protected void onLayout(boolean changed, int dispLeft, int dispTop, int right, int bottom)
    {
        // Set the mSlotView as a reference point to the open animation
        GalleryUtils.setViewPointMatrix(mMatrix,
                (right - dispLeft) / 2, (bottom - dispTop) / 2, 0 - GalleryUtils.meterToPixel(0.3f));

        stopAnimation();
        setViewSize(getWidth(), getHeight());
    }


    private void setViewSize(int dispWidth, int dispHeight)
    {

        viewWidth = dispWidth;
        viewHeight = dispHeight;
        minSize=Math.min(viewWidth,viewHeight)/3;

        if(scaleForImg<1)
        {
            scaleForImg=1;
        }

        viewHeightForImg=(int)(viewHeight/scaleForImg);
        viewWidthForImg=(int)(viewWidth/scaleForImg);
        minSizeForImg=(int)(minSize/scaleForImg);

        minSqureLeft=(viewWidth-minSize)/2;
        minSqureTop=(viewHeight-minSize)/2;

        Log.i(TAG, "setViewSize " + dispWidth + "*" + dispHeight + " scaleForImg:"+scaleForImg);
        loader.wakeUpFormSleep();
    }

    public void openPhoto(String projectPath, int photoIndex)
    {
        curProjectPath=projectPath;
        curPhotoIndex=photoIndex;
        loader.wakeUpFormSleep();
    }

    public void stopAnimation()
    {
        mGLRootView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    protected boolean onTouch(MotionEvent event)
    {
        mGestureRecognizer.onTouchEvent(event);
        return true;
    }

    @Override
    protected void render(GLCanvas canvas)
    {
        canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
        canvas.clearBuffer(BACKGROUND_COLOR);

        Log.i(TAG, "render");

        long curTime = System.currentTimeMillis();
        int renderTimeInterval = (int) (curTime - renderTime);
        renderTime = curTime;

        if(photo.texture!=null && photo.texture.isReady())
        {
            photo.texture.draw(canvas, photo.dispLeft, photo.dispTop, photo.dispWidth, photo.dispHeight);
        }
    }

    public void onDestroy()
    {
        mGLRootView.lockRenderThread();
        photo.recycle();
        mGLRootView.unlockRenderThread();
    }

    public void stopLoader()
    {
        isLoaderRunning=false;
        loader.wakeUpFormSleep();
    }


    private class PhotoLoader extends Thread
    {
        private final static long SLEEP_TIME=3600000*24*365;
        private volatile boolean inSleep;

        public PhotoLoader()
        {
            super("PhotoLoader");
            setDaemon(true);
        }

        public void wakeUpFormSleep()
        {
            if(inSleep)
            {
                inSleep=false;
                interrupt();
            }
        }

        private boolean photoIndexNotChangedInLoading()
        {
            Log.i(TAG, "load "+curPhotoIndex);

            Log.i(TAG, "get wholeBmp success");
            mGLRootView.lockRenderThread();
            photo.recycle();
            photo.paramInit(curPhotoIndex);
            mGLRootView.unlockRenderThread();

            photo.loadWholeBitmap();

            mGLRootView.lockRenderThread();
            photo.createTexture();
            mGLRootView.unlockRenderThread();

            mGLRootView.requestRender();

            return true;
        }

        public void run()
        {
            while(isLoaderRunning)
            {
                if(photoIndexNotChangedInLoading())
                {
                    if(!isLoaderRunning)
                    {
                        break;
                    }

                    try
                    {
                        inSleep=true;
                        sleep(SLEEP_TIME);
                    } catch(InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                    inSleep=false;
                }
            }
        }
    }




    private class MyGestureListener implements GestureRecognizer.Listener
    {

        @Override
        public boolean onSingleTapUp(float x, float y)
        {
            Log.i(TAG, "onSingleTapUp " + x + " " + y);

            return true;
        }

        @Override
        public boolean onDoubleTap(float x, float y)
        {
            Log.i(TAG, "onDoubleTap " + x + " " + y);

            return true;
        }

        @Override
        public void onLongPress(float x, float y)
        {
            Log.i(TAG, "onLongPress " + x + " " + y);
        }

        @Override
        public boolean onScroll(float dx, float dy, float totalX, float totalY)
        {
            //Log.i(TAG, "onScroll "+dx+" "+dy+" "+totalX+" "+totalY);

            mGLRootView.requestRender();
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
        {
            mGLRootView.lockRenderThread();

            mGLRootView.unlockRenderThread();

            Log.i(TAG, "onFling " + velocityX + " " + velocityY);
            return true;
        }

        @Override
        public boolean onScaleBegin(float focusX, float focusY)
        {
            //Log.i(TAG, "onScaleBegin "+focusX+" "+focusY);
            return true;
        }

        @Override
        public boolean onScale(float focusX, float focusY, float scale)
        {
            //Log.i(TAG, "onScaleBegin "+focusX+" "+focusY+" "+scale);
            return true;
        }

        @Override
        public void onScaleEnd()
        {
            //Log.i(TAG, "onScaleEnd");
        }

        @Override
        public void onDown(float x, float y)
        {
            Log.i(TAG, "onDown " + x + " " + y);

            mGLRootView.lockRenderThread();

            isTouching=true;

            stopAnimation();

            mGLRootView.unlockRenderThread();
        }

        @Override
        public void onUp()
        {
            Log.i(TAG, "onUp");
            mGLRootView.lockRenderThread();


            isTouching=false;
            mGLRootView.unlockRenderThread();
        }

    }
}