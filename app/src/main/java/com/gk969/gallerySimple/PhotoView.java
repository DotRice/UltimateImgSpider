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
    private final static int EMPTY_IMG_COLOR=0xFF808080;

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

    DisplayPosition fullScreen=new DisplayPosition();

    private class Photo
    {
        int indexInProject=SpiderProject.INVALID_INDEX;
        volatile boolean bmpLoaded;
        volatile boolean textureCreate;

        DisplayPosition renderPos=new DisplayPosition();
        DisplayPosition boxPos=new DisplayPosition();
        float widthInBox;
        float heightInBox;

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

        void calcRenderPos()
        {
            renderPos.width=boxPos.width*widthInBox;
            renderPos.height=boxPos.height*heightInBox;
            renderPos.top=boxPos.top+(boxPos.height-renderPos.height)/2;
            renderPos.left=boxPos.left+(boxPos.width-renderPos.width)/2;
        }

        void scale(float scaleRatio)
        {
            scale(viewHeight / 2, viewWidth / 2, scaleRatio, fullScreen);
        }

        void scale(int focusTop, int focusLeft, float scaleRatio, DisplayPosition basePos)
        {
            boxPos.top=focusTop+(basePos.top-focusTop)*scaleRatio;
            boxPos.left=focusLeft+(basePos.left-focusLeft)*scaleRatio;
            boxPos.width=basePos.width*scaleRatio;
            boxPos.height=basePos.height*scaleRatio;
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

            float width;
            float height;

            if(bmpOptions.outWidth<=minSizeForImg && bmpOptions.outHeight<=minSizeForImg)
            {
                if(bmpOptions.outHeight>bmpOptions.outWidth)
                {
                    width=bmpOptions.outWidth*minSize/bmpOptions.outHeight;
                    height=minSize;
                }
                else
                {
                    width=minSize;
                    height=bmpOptions.outHeight*minSize/bmpOptions.outWidth;
                }
            }
            else if(bmpOptions.outWidth<viewWidthForImg && bmpOptions.outHeight<viewHeightForImg)
            {
                width=scaleForImg*bmpOptions.outWidth;
                height=scaleForImg*bmpOptions.outHeight;
            }
            else if(bmpOptions.outHeight*viewWidthForImg > bmpOptions.outWidth*viewHeight)
            {
                width=bmpOptions.outWidth*viewHeight/bmpOptions.outHeight;
                height=viewHeight;
            }
            else
            {
                width=viewWidth;
                height=bmpOptions.outHeight*viewWidth/bmpOptions.outWidth;
            }

            widthInBox=width/viewWidth;
            heightInBox=height/viewHeight;

            bmpOptions.inSampleSize=(int)(bmpOptions.outHeight/(height/scaleForImg));
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
            int cacheIndexBeforeLoad=curPhotoIndexInCache;
            if(!bmpLoaded)
            {
                Log.i(TAG, "load bmp " + indexInProject);

                String photoFilePath=getTargetFilePath();
                int projectIndexBeforeLoad=indexInProject;

                mGLRootView.unlockRenderThread();
                loadFillCenterBmp(photoFilePath);

                try
                {
                    Thread.sleep(1000);
                } catch(InterruptedException e)
                {
                    e.printStackTrace();
                }

                mGLRootView.lockRenderThread();

                if(projectIndexBeforeLoad!=indexInProject)
                {
                    loader.needReload=true;
                    return;
                }
                bmpLoaded=true;
            }

            if((!textureCreate) && inDisplay)
            {
                Log.i(TAG, "create texture " + indexInProject);

                if(fillCenterBmp!=null)
                {
                    fillCenterTexture=new TiledTexture(fillCenterBmp);
                    mTextureUploader.addTexture(fillCenterTexture);
                }
                textureCreate=true;
            }

            if(cacheIndexBeforeLoad!=curPhotoIndexInCache)
            {
                loader.needReload=true;
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

        fullScreen.width=width;
        fullScreen.height=height;

        for(Photo photo:photoCache)
        {
            photo.boxPos.set(fullScreen);
        }

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
        flyStartLeft=photoCache[curPhotoIndexInCache].boxPos.left;


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

        if(flyVelocity!=0)
        {
            float newLeft=photo.boxPos.left+renderTimeInterval*flyVelocity/1000;

            if(flyVelocity>0)
            {
                if(newLeft>=0)
                {
                    stopAnimation();
                    photo.boxPos.left=0;
                }
                else
                {
                    flyVelocity=Math.max(photo.boxPos.left/flyStartLeft*flyStartVelocity, velocityMinAtEnd);
                    photo.boxPos.left=newLeft;
                }
            }
            else
            {
                if(newLeft<=(0-viewWidth))
                {
                    stopAnimation();
                    curPhotoIndexInCache=getNextPhotoIndexInCache(curPhotoIndexInCache);
                    photo=photoCache[curPhotoIndexInCache];
                    photo.boxPos.set(fullScreen);
                }
                else
                {
                    flyVelocity = Math.min((viewWidth + photo.boxPos.left) / (flyStartLeft + viewWidth)
                            * flyStartVelocity, (0 - velocityMinAtEnd));
                    photo.boxPos.left=newLeft;
                }
            }
        }

        if(photo.boxPos.left!=0)
        {
            float mainTextureMoveLeftRatio=(0-photo.boxPos.left)/viewWidth;
            Photo nextPhoto=photoCache[getNextPhotoIndexInCache(curPhotoIndexInCache)];

            nextPhoto.scale(0.25f+mainTextureMoveLeftRatio*0.75f);

            while(true)
            {
                if(nextPhoto.fillCenterTexture != null)
                {
                    if(nextPhoto.fillCenterTexture.isReady())
                    {
                        nextPhoto.calcRenderPos();
                        nextPhoto.fillCenterTexture.drawMixed(canvas, NEXT_TEXTURE_MIXED_COLOR, 1 - mainTextureMoveLeftRatio,
                                (int) nextPhoto.renderPos.left, (int) nextPhoto.renderPos.top,
                                (int) nextPhoto.renderPos.width, (int) nextPhoto.renderPos.height);
                        break;
                    }
                }

                canvas.fillRect(nextPhoto.boxPos.left, nextPhoto.boxPos.top, nextPhoto.boxPos.width,
                        nextPhoto.boxPos.height, EMPTY_IMG_COLOR);
                break;
            }
        }

        while(true)
        {
            if(photo.fillCenterTexture != null)
            {
                //Log.i(TAG, "render not null velocity "+flyVelocity);
                if(photo.fillCenterTexture.isReady())
                {
                    Log.i(TAG, "render ready");
                    photo.calcRenderPos();
                    photo.fillCenterTexture.draw(canvas, (int) photo.renderPos.left, (int) photo.renderPos.top,
                            (int) photo.renderPos.width, (int) photo.renderPos.height);
                    break;
                }
            }

            canvas.fillRect(photo.boxPos.left, photo.boxPos.top, photo.boxPos.width, photo.boxPos.height,
                    EMPTY_IMG_COLOR);
            break;
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
            float newLeft=photo.boxPos.left-dx;

            float leftMin=0-viewWidth;
            if(newLeft>0 && photo.boxPos.left<=0)
            {
                //Log.i(TAG, "cur "+curPhotoIndexInCache+" left "+photo.boxPos.left+" width "+photo.boxPos.width);
                curPhotoIndexInCache=getPrevPhotoIndexInCache(curPhotoIndexInCache);
                photo=photoCache[curPhotoIndexInCache];
                photo.boxPos.set(0, leftMin, viewWidth, viewHeight);
                //Log.i(TAG, "prev "+curPhotoIndexInCache+" left "+photo.boxPos.left+" width "+photo.boxPos.width);
            }
            else if(newLeft<=leftMin && photo.boxPos.left>leftMin)
            {
                Log.i(TAG, "cur "+curPhotoIndexInCache+" left "+photo.boxPos.left+" width "+photo.boxPos.width);
                curPhotoIndexInCache=getNextPhotoIndexInCache(curPhotoIndexInCache);
                photo=photoCache[curPhotoIndexInCache];
                photo.boxPos.set(fullScreen);
                Log.i(TAG, "next "+curPhotoIndexInCache+" left "+photo.boxPos.left+" width "+photo.boxPos.width);
            }

            photo.boxPos.left-=dx;

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
                float leftMinToBack=0-viewWidth/2;
                if(photo.boxPos.left<=leftMinToBack)
                {
                    startFly(0-(viewWidth+photo.boxPos.left)/viewWidth*velocityMinAtStart);
                }
                else
                {
                    startFly((0-photo.boxPos.left)/(viewWidth+photo.boxPos.left)*velocityMinAtStart);
                }
            }

            isTouching=false;
            mGLRootView.unlockRenderThread();
        }

    }
}