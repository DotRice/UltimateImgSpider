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

public class PhotoView extends GLView
{
    private static final String TAG = "PhotoView";
    private final float mMatrix[] = new float[16];
    private final static float BACKGROUND_COLOR[] = new float[]{0, 0, 0, 0};

    private int viewWidth;
    private int viewHeight;

    private long renderTime;
    private GLRootView mGLRootView;
    private GestureRecognizer mGestureRecognizer;

    private boolean isTouching;

    private volatile int curPhotoIndex= SpiderProject.INVALID_INDEX;
    private volatile boolean isLoaderRunning=true;

    private PhotoLoader loader;

    private volatile String curProjectPath;
    private TiledTexture photoTexture;

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


    public PhotoView(Context context, GLRootView glRootView)
    {
        mGestureRecognizer = new GestureRecognizer(context, new MyGestureListener());

        mGLRootView = glRootView;
        loader=new PhotoLoader();
        loader.start();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom)
    {
        // Set the mSlotView as a reference point to the open animation
        GalleryUtils.setViewPointMatrix(mMatrix,
                (right - left) / 2, (bottom - top) / 2, 0 - GalleryUtils.meterToPixel(0.3f));

        stopAnimation();
        setViewSize(getWidth(), getHeight());
    }


    private void setViewSize(int width, int height)
    {
        Log.i(TAG, "setViewSize " + width + " " + height);

        viewWidth = width;
        viewHeight = height;
    }

    public void openPhoto(String projectPath, int photoIndex)
    {
        curProjectPath=projectPath;
        curPhotoIndex=photoIndex;
        loader.interrupt();
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

        //Log.i(TAG, "render");

        long curTime = System.currentTimeMillis();
        int renderTimeInterval = (int) (curTime - renderTime);
        renderTime = curTime;

    }

    public void stopLoader()
    {
        isLoaderRunning=false;
        loader.interrupt();
    }

    private class PhotoLoader extends Thread
    {
        private final static long SLEEP_TIME=3600000*24*365;

        public PhotoLoader()
        {
            super("PhotoLoader");
            setDaemon(true);
        }

        private boolean photoIndexNotChangedInLoading()
        {
            Log.i(TAG, "load "+curPhotoIndex);

            Bitmap bmp=getCurBitmap();


            return true;
        }

        public Bitmap getCurBitmap()
        {
            //Log.i(TAG, "try to load index:" + index);
            int group=curPhotoIndex/ StaticValue.MAX_IMG_FILE_PER_DIR;
            int offset=curPhotoIndex%StaticValue.MAX_IMG_FILE_PER_DIR;

            Bitmap bmp=null;
            for(int i=0; i< StaticValue.IMG_FILE_EXT.length; i++)
            {
                String fileName=String.format("%s/%d/%03d.%s", curProjectPath,
                        group, offset, StaticValue.IMG_FILE_EXT[i]);

                BitmapFactory.Options bmpOpts=new BitmapFactory.Options();
                bmpOpts.inPreferredConfig=Bitmap.Config.RGB_565;

                bmp = BitmapFactory.decodeFile(fileName, bmpOpts);
                if(bmp!=null)
                {
                    break;
                }
            }

            return bmp;
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
                        sleep(SLEEP_TIME);
                    } catch(InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}