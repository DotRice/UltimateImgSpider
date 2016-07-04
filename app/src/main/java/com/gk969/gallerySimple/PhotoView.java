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
import java.util.concurrent.locks.ReentrantLock;

public class PhotoView extends GLView
{
    private static final String TAG = "PhotoView";
    private final float mMatrix[] = new float[16];
    private final static float BACKGROUND_COLOR[] = new float[]{0, 0, 0, 0};

    private final static int NEXT_TEXTURE_MIXED_COLOR=0xFF000000;

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
    private final static int VELOCITY_END_MIN_IN_DP=100;
    private final static int VELOCITY_START_MIN_IN_DP=3000;
    private float velocityMinAtEnd;
    private float velocityMinAtStart;
    private float flyVelocity;
    private float flyStartVelocity;
    private float flyStartLeft;

    private GLRootView mGLRootView;
    private TiledTexture.Uploader mTextureUploader;

    private GestureRecognizer mGestureRecognizer;

    private boolean isTouching;

    private volatile boolean isLoaderRunning;

    private boolean inDisplay;
    private PhotoLoader loader;
    private int curPhotoIndexInCache;
    private static final int PHOTO_CACHE_SIZE=5;
    private Photo[] photoCache;
    private volatile String curProjectPath;


    private class DisplayPosition
    {
        float top;
        float left;
        float width;
        float height;

        void set(DisplayPosition pos)
        {
            top=pos.top;
            left=pos.left;
            width=pos.width;
            height=pos.height;
        }

        void set(float pTop, float pLeft, float pWidth, float pHeight)
        {
            top=pTop;
            left=pLeft;
            width=pWidth;
            height=pHeight;
        }
    }

    private class Photo
    {
        int indexInProject=SpiderProject.INVALID_INDEX;
        volatile boolean bmpLoaded;
        volatile boolean textureCreate;

        DisplayPosition position=new DisplayPosition();
        DisplayPosition fillCenter=new DisplayPosition();

        float scaleRatio;

        TiledTexture fillCenterTexture;
        Bitmap fillCenterBmp;
        BitmapFactory.Options bmpOptions = new BitmapFactory.Options();

        public Photo()
        {
            bmpOptions.inPreferredConfig=Bitmap.Config.RGB_565;
        }

        String getTargetFilePath()
        {
            int group=indexInProject/ StaticValue.MAX_IMG_FILE_PER_DIR;
            int offset=indexInProject%StaticValue.MAX_IMG_FILE_PER_DIR;
            return String.format("%s/%d/%03d.", curProjectPath, group, offset);
        }

        void scale(float scaleRatio)
        {
            scale(viewHeight / 2, viewWidth / 2, scaleRatio, fillCenter);
        }

        void scale(int focusTop, int focusLeft, float scaleRatio, DisplayPosition basePos)
        {
            position.top=focusTop+(basePos.top-focusTop)*scaleRatio;
            position.left=focusLeft+(basePos.left-focusLeft)*scaleRatio;
            position.width=basePos.width*scaleRatio;
            position.height=basePos.height*scaleRatio;
        }

        void loadFillCenterBmp(String targetFilePath)
        {
            Log.i(TAG, "loadFillCenterBmp "+targetFilePath);

            String imgFilePath=null;
            for(int i=0; i< StaticValue.IMG_FILE_EXT.length; i++)
            {
                File file=new File(targetFilePath+StaticValue.IMG_FILE_EXT[i]);
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
                    fillCenter.width=bmpOptions.outWidth*minSize/bmpOptions.outHeight;
                    fillCenter.left=minSqureLeft+(minSize-fillCenter.width)/2;
                    fillCenter.height=minSize;
                    fillCenter.top=minSqureTop;
                }
                else
                {
                    fillCenter.width=minSize;
                    fillCenter.left=minSqureLeft;
                    fillCenter.height=bmpOptions.outHeight*minSize/bmpOptions.outWidth;
                    fillCenter.top=minSqureTop+(minSize-fillCenter.height)/2;
                }
            }
            else if(bmpOptions.outWidth<viewWidthForImg && bmpOptions.outHeight<viewHeightForImg)
            {
                fillCenter.width=scaleForImg*bmpOptions.outWidth;
                fillCenter.left=(viewWidth-fillCenter.width)/2;
                fillCenter.height=scaleForImg*bmpOptions.outHeight;
                fillCenter.top=(viewHeight-fillCenter.height)/2;
            }
            else if(bmpOptions.outHeight*viewWidthForImg > bmpOptions.outWidth*viewHeight)
            {
                fillCenter.width=bmpOptions.outWidth*viewHeight/bmpOptions.outHeight;
                fillCenter.left=(viewWidth-fillCenter.width)/2;
                fillCenter.height=viewHeight;
                fillCenter.top=0;
            }
            else
            {
                fillCenter.width=viewWidth;
                fillCenter.left=0;
                fillCenter.height=bmpOptions.outHeight*viewWidth/bmpOptions.outWidth;
                fillCenter.top=(viewHeight-fillCenter.height)/2;
            }

            bmpOptions.inSampleSize=(int)(bmpOptions.outHeight/(fillCenter.height/scaleForImg));
            bmpOptions.inJustDecodeBounds=false;

            Log.i(TAG, String.format("%s raw size:%d*%d inSampleSize:%d", imgFilePath,
                    bmpOptions.outWidth, bmpOptions.outHeight, bmpOptions.inSampleSize));

            fillCenterBmp = BitmapFactory.decodeFile(imgFilePath, bmpOptions);
            if(fillCenterBmp != null)
            {
                Log.i(TAG, String.format("fill center bmp size:%d*%d",
                        fillCenterBmp.getWidth(), fillCenterBmp.getHeight()));
            }

        }

        private void load()
        {
            if(!bmpLoaded)
            {
                Log.i(TAG, "load bmp" + indexInProject);

                String photoFilePath=getTargetFilePath();
                int indexBeforeLoad=indexInProject;

                mGLRootView.unlockRenderThread();
                loadFillCenterBmp(photoFilePath);
                mGLRootView.lockRenderThread();

                if(indexBeforeLoad!=indexInProject)
                {
                    return;
                }
                bmpLoaded=true;
            }

            if((!textureCreate) && inDisplay)
            {
                Log.i(TAG, "create texture" + indexInProject);

                position.set(fillCenter);
                if(fillCenterBmp!=null)
                {
                    fillCenterTexture=new TiledTexture(fillCenterBmp);
                    mTextureUploader.addTexture(fillCenterTexture);
                }
                textureCreate=true;
            }
        }
    }


    public PhotoView(Context context, GLRootView glRootView)
    {
        mGestureRecognizer = new GestureRecognizer(context, new MyGestureListener());

        float density=context.getResources().getDisplayMetrics().density;
        velocityMinAtEnd=VELOCITY_END_MIN_IN_DP*density;
        velocityMinAtStart=VELOCITY_START_MIN_IN_DP*density;
        scaleForImg=density;

        mGLRootView = glRootView;

        TiledTexture.prepareResources();
        mTextureUploader=new TiledTexture.Uploader(glRootView);

        photoCache=new Photo[PHOTO_CACHE_SIZE];
        for(int i=0; i<PHOTO_CACHE_SIZE; i++)
        {
            photoCache[i]=new Photo();
        }
        loader=new PhotoLoader();
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
        viewWidth = width;
        viewHeight = height;
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

        Log.i(TAG, "setViewSize " + width + "*" + height + " scaleForImg:" + scaleForImg);

        if(!isLoaderRunning)
        {
            isLoaderRunning=true;
            loader=new PhotoLoader();
            loader.start();
        }
        else
        {
            loader.wakeUp();
        }
    }

    private int getNextPhotoIndexInCache(int index)
    {
        return (index==(PHOTO_CACHE_SIZE-1))?0:index+1;
    }

    private int getPrevPhotoIndexInCache(int index)
    {
        return (index==0)?(PHOTO_CACHE_SIZE-1):index-1;
    }

    public void openPhoto(String projectPath, int indexInProject)
    {
        Log.i(TAG, "openPhoto " + projectPath + " " + indexInProject);
        mGLRootView.lockRenderThread();
        curProjectPath=projectPath;
        int i;
        for(i=0; i<PHOTO_CACHE_SIZE; i++)
        {
            if(photoCache[i].indexInProject==indexInProject)
            {
                curPhotoIndexInCache=i;
                break;
            }
        }

        if(i==PHOTO_CACHE_SIZE)
        {
            curPhotoIndexInCache=0;
            photoCache[0].indexInProject=indexInProject;
            photoCache[0].bmpLoaded=false;
        }

        int prevIndex=getPrevPhotoIndexInCache(curPhotoIndexInCache);
        int nextIndex=getNextPhotoIndexInCache(curPhotoIndexInCache);
        for(i=0; i<(PHOTO_CACHE_SIZE/2); i++)
        {
            int newIndex;

            newIndex=indexInProject-1-i;
            if(photoCache[prevIndex].indexInProject!=newIndex)
            {
                photoCache[prevIndex].indexInProject=newIndex;
                photoCache[prevIndex].bmpLoaded=false;
            }
            prevIndex=getPrevPhotoIndexInCache(prevIndex);

            newIndex=indexInProject+1+i;
            if(photoCache[nextIndex].indexInProject!=newIndex)
            {
                photoCache[nextIndex].indexInProject=newIndex;
                photoCache[nextIndex].bmpLoaded=false;
            }
            nextIndex=getNextPhotoIndexInCache(nextIndex);
        }


        for(Photo photo:photoCache)
        {
            if(!photo.bmpLoaded)
            {
                if(photo.fillCenterBmp!=null)
                {
                    photo.fillCenterBmp.recycle();
                    photo.fillCenterBmp=null;
                }
            }

            photo.textureCreate=false;
        }

        inDisplay=true;
        loader.needReload=true;

        //TiledTexture.prepareResources();
        mGLRootView.unlockRenderThread();

        loader.wakeUp();
    }

    public void stopAnimation()
    {
        mGLRootView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        flyVelocity = 0;
    }

    @Override
    protected boolean onTouch(MotionEvent event)
    {
        mGestureRecognizer.onTouchEvent(event);
        return true;
    }

    private void startFly(float velocity)
    {
        flyVelocity = velocity;
        flyStartVelocity=velocity;
        flyStartLeft=photoCache[curPhotoIndexInCache].position.left;


        renderTime = SystemClock.uptimeMillis();
        mGLRootView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    @Override
    protected void render(GLCanvas canvas)
    {
        canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
        canvas.clearBuffer(BACKGROUND_COLOR);


        long curTime = SystemClock.uptimeMillis();
        int renderTimeInterval = (int) (curTime - renderTime);
        renderTime = curTime;

        Photo photo=photoCache[curPhotoIndexInCache];
        Log.i(TAG, "render "+photo.indexInProject+" "+curPhotoIndexInCache);
        if(photo.fillCenterTexture!=null)
        {
            Log.i(TAG, "render not null velocity "+flyVelocity);

            if(flyVelocity!=0)
            {
                float newLeft=photo.position.left+renderTimeInterval*flyVelocity/1000;

                if(flyVelocity>0)
                {
                    if(newLeft>=photo.fillCenter.left)
                    {
                        stopAnimation();
                        photo.position.left=photo.fillCenter.left;
                    }
                    else
                    {
                        flyVelocity=Math.max((photo.fillCenter.left-photo.position.left)/
                                (photo.fillCenter.left-flyStartLeft)*flyStartVelocity, velocityMinAtEnd);
                        photo.position.left=newLeft;
                    }
                }
                else
                {
                    if(newLeft<=(0-photo.fillCenter.width))
                    {
                        stopAnimation();
                        curPhotoIndexInCache=getNextPhotoIndexInCache(curPhotoIndexInCache);
                        photo=photoCache[curPhotoIndexInCache];
                        photo.position.set(photo.fillCenter);
                    }
                    else
                    {
                        flyVelocity = Math.min((photo.fillCenter.width + photo.position.left) /
                                (flyStartLeft + photo.fillCenter.width) * flyStartVelocity, (0 - velocityMinAtEnd));
                        photo.position.left=newLeft;
                    }
                }
            }


            if(photo.position.left!=photo.fillCenter.left)
            {
                float mainTextureMoveLeftRatio=(photo.fillCenter.left-photo.position.left)/
                        (photo.fillCenter.width+photo.fillCenter.left);
                Photo nextPhoto=photoCache[getNextPhotoIndexInCache(curPhotoIndexInCache)];
                if(nextPhoto.fillCenterTexture!=null)
                {
                    if(nextPhoto.fillCenterTexture.isReady())
                    {
                        nextPhoto.scale(mainTextureMoveLeftRatio);
                        nextPhoto.fillCenterTexture.drawMixed(canvas, NEXT_TEXTURE_MIXED_COLOR, 1-mainTextureMoveLeftRatio,
                                (int)nextPhoto.position.left, (int)nextPhoto.position.top,
                                (int)nextPhoto.position.width, (int)nextPhoto.position.height);
                    }
                }
            }

            if(photo.fillCenterTexture.isReady())
            {
                //Log.i(TAG, "render ready");
                photo.fillCenterTexture.draw(canvas, (int)photo.position.left, (int)photo.position.top,
                        (int)photo.position.width, (int)photo.position.height);
            }
        }

    }

    public void onDestroy()
    {
        mGLRootView.lockRenderThread();
        mTextureUploader.clear();
        //TiledTexture.freeResources();
        for(Photo photo:photoCache)
        {
            if(photo.fillCenterTexture!=null)
            {
                photo.fillCenterTexture.recycle();
                photo.fillCenterTexture=null;
            }
        }

        inDisplay=false;
        mGLRootView.unlockRenderThread();
    }

    public void stopLoader()
    {
        isLoaderRunning=false;
        loader.wakeUp();
    }


    private class PhotoLoader extends Thread
    {
        private final static long SLEEP_TIME=3600000*24*365;
        private volatile boolean inSleep;

        public volatile boolean needReload;

        public PhotoLoader()
        {
            super("PhotoLoader");
            setDaemon(true);
        }

        public void wakeUp()
        {
            if(inSleep)
            {
                inSleep=false;
                interrupt();
            }
        }

        private void load()
        {
            Log.i(TAG, "start load " + curPhotoIndexInCache);
            mGLRootView.lockRenderThread();

            photoCache[curPhotoIndexInCache].load();

            mGLRootView.unlockRenderThread();
            if(needReload)
            {
                return;
            }
            mGLRootView.requestRender();

            mGLRootView.lockRenderThread();

            int prevIndex=getPrevPhotoIndexInCache(curPhotoIndexInCache);
            int nextIndex=getNextPhotoIndexInCache(curPhotoIndexInCache);
            for(int i=0; i<(PHOTO_CACHE_SIZE/2); i++)
            {
                photoCache[prevIndex].load();
                if(needReload)
                {
                    break;
                }
                prevIndex=getPrevPhotoIndexInCache(prevIndex);

                photoCache[nextIndex].load();
                if(needReload)
                {
                    break;
                }
                nextIndex=getNextPhotoIndexInCache(nextIndex);
            }

            mGLRootView.unlockRenderThread();

            mGLRootView.requestRender();
        }

        public void run()
        {
            while(isLoaderRunning)
            {
                needReload=false;
                load();

                if(!isLoaderRunning)
                {
                    break;
                }

                if(!needReload)
                {
                    try
                    {
                        inSleep = true;
                        sleep(SLEEP_TIME);
                    } catch(InterruptedException e)
                    {
                        Log.i(TAG, "loader wake up");
                    }
                }
                inSleep = false;
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
            mGLRootView.lockRenderThread();
            Photo photo=photoCache[curPhotoIndexInCache];
            float newLeft=photo.position.left-dx;

            float leftMin=0-photo.fillCenter.width;
            if(newLeft>photo.fillCenter.left && photo.position.left<=photo.fillCenter.left)
            {
                //Log.i(TAG, "cur "+curPhotoIndexInCache+" left "+photo.position.left+" width "+photo.position.width);
                curPhotoIndexInCache=getPrevPhotoIndexInCache(curPhotoIndexInCache);
                photo=photoCache[curPhotoIndexInCache];
                DisplayPosition fillCenter=photo.fillCenter;
                photo.position.set(fillCenter.top, 0 - fillCenter.width, fillCenter.width, fillCenter.height);
                //Log.i(TAG, "prev "+curPhotoIndexInCache+" left "+photo.position.left+" width "+photo.position.width);
            }
            else if(newLeft<=leftMin && photo.position.left>leftMin)
            {
                //Log.i(TAG, "cur "+curPhotoIndexInCache+" left "+photo.position.left+" width "+photo.position.width);
                curPhotoIndexInCache=getNextPhotoIndexInCache(curPhotoIndexInCache);
                photo=photoCache[curPhotoIndexInCache];
                //Log.i(TAG, "next "+curPhotoIndexInCache+" left "+photo.position.left+" width "+photo.position.width);
            }

            photo.position.left-=dx;

            mGLRootView.unlockRenderThread();
            mGLRootView.requestRender();
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
        {
            if(velocityX!=0)
            {
                mGLRootView.lockRenderThread();
                if(velocityX>0)
                {
                    velocityX=Math.max(velocityX, velocityMinAtStart);
                }
                else
                {
                    velocityX=Math.min(velocityX, (0 - velocityMinAtStart));
                }
                startFly(velocityX);
                mGLRootView.unlockRenderThread();
            }
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
            if(flyVelocity==0)
            {
                Photo photo=photoCache[curPhotoIndexInCache];
                float leftMinToBack=photo.fillCenter.left-photo.fillCenter.width/2;
                if(photo.position.left<=leftMinToBack)
                {
                    startFly(0-(photo.fillCenter.width+photo.position.left)/
                            (photo.fillCenter.width+photo.fillCenter.left)*velocityMinAtStart);
                }
                else
                {
                    startFly((photo.fillCenter.left-photo.position.left)/
                            (photo.fillCenter.width+photo.fillCenter.left)*velocityMinAtStart);
                }
            }

            isTouching=false;
            mGLRootView.unlockRenderThread();
        }

    }
}