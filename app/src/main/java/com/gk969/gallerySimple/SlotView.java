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

public class SlotView extends GLView
{
    private static final String TAG = "AlbumView";
    private final float mMatrix[] = new float[16];
    private final static float BACKGROUND_COLOR[]=new float[]{0, 0.25f, 0.25f, 0.25f};
    
    private static final int SLOT_GAP_MIN_IN_DP=5;
    private static final int SLOT_BACKGROUND_COLOR=0xFF808080;
    private static final int SLOT_PER_ROW_PORTRAIT=3;
    private static final int SLOT_PER_ROW_LANDSCAPE=5;

    private int slotsPerRow;

    private int viewHeight;

    private int slotSize;
    private int slotGap;

    private int slotRowsInView;
    private int slotHeightWithGap;

    private int overScrollGap=0;

    private int scrollDistance=0;

    private ThumbnailLoader mThumbnailLoader;
    private GLRootView mGLrootView;

    private MyGestureListener mGestureListener;
    private GestureRecognizer mGestureRecognizer;


    private static final int FLY_ACCURACY_ABS=5000;
    private int flyAccuracy;
    private int flyVelocity;
    private long animationTime;

    private class MyGestureListener implements GestureRecognizer.Listener {

        @Override
        public boolean onSingleTapUp(float x, float y) {
            Log.i(TAG, "onSingleTapUp "+x+" "+y);
            return true;
        }

        @Override
        public boolean onDoubleTap(float x, float y) {
            Log.i(TAG, "onDoubleTap "+x+" "+y);

            return true;
        }

        @Override
        public boolean onScroll(float dx, float dy, float totalX, float totalY) {
            //Log.i(TAG, "onScroll "+dx+" "+dy+" "+totalX+" "+totalY);

            mGLrootView.lockRenderThread();
            scrollDistance+=dy;
            if(scrollDistance<0)
            {
                scrollDistance=0;
            }
            mGLrootView.unlockRenderThread();
            
            invalidate();
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            mGLrootView.lockRenderThread();
            mGLrootView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
            flyVelocity=(int)velocityY;
            flyAccuracy=(velocityY>0)?(0-FLY_ACCURACY_ABS):FLY_ACCURACY_ABS;
            animationTime= System.currentTimeMillis();
            mGLrootView.unlockRenderThread();

            Log.i(TAG, "onFling " + velocityX + " " + velocityY + " "+animationTime);
            return true;
        }

        @Override
        public boolean onScaleBegin(float focusX, float focusY) {
            //Log.i(TAG, "onScaleBegin "+focusX+" "+focusY);
            return true;
        }

        @Override
        public boolean onScale(float focusX, float focusY, float scale) {
            //Log.i(TAG, "onScaleBegin "+focusX+" "+focusY+" "+scale);
            return true;
        }

        @Override
        public void onScaleEnd() {
            //Log.i(TAG, "onScaleEnd");
        }

        @Override
        public void onDown(float x, float y) {
            Log.i(TAG, "onDown "+x+" "+y);

            mGLrootView.lockRenderThread();

            stopAnimation();

            mGLrootView.unlockRenderThread();
        }

        @Override
        public void onUp() {
            Log.i(TAG, "onUp");
        }

    }


    public SlotView(Context context, ThumbnailLoader loader, GLRootView glRootView)
    {
        mGestureListener = new MyGestureListener();
        mGestureRecognizer = new GestureRecognizer(context, mGestureListener);
        
        slotGap= Utils.DisplayUtil.dipToPx(context, SLOT_GAP_MIN_IN_DP);
        mThumbnailLoader=loader;
        mGLrootView=glRootView;
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom)
    {
        // Set the mSlotView as a reference point to the open animation
        GalleryUtils.setViewPointMatrix(mMatrix,
                (right - left) / 2, (bottom - top) / 2, 0 - GalleryUtils.meterToPixel(0.3f));

        setViewSize(getWidth(), getHeight());
    }
    
    public void setViewSize(int width, int height)
    {
        Log.i(TAG, "setViewSize "+width+" "+height);

        slotsPerRow=(width>height)?SLOT_PER_ROW_LANDSCAPE:SLOT_PER_ROW_PORTRAIT;

        slotSize=(width-(slotsPerRow-1)*slotGap)/slotsPerRow;
        viewHeight=height;

        slotHeightWithGap=slotSize+slotGap;
        slotRowsInView=viewHeight/slotHeightWithGap+2;
        mThumbnailLoader.init(slotSize, slotRowsInView * slotsPerRow);
        mThumbnailLoader.scrollToIndex(0);
    }

    private void stopAnimation()
    {
        mGLrootView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        flyVelocity=0;
    }

    @Override
    protected boolean onTouch(MotionEvent event) {
        mGestureRecognizer.onTouchEvent(event);
        return true;
    }

    @Override
    protected void render(GLCanvas canvas)
    {
        canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
        canvas.clearBuffer(BACKGROUND_COLOR);

        if(flyVelocity!=0)
        {
            long curTime= System.currentTimeMillis();
            int animationTimeInterval = (int)(curTime-animationTime);
            animationTime=curTime;

            int curFlyVelocity=flyVelocity+(flyAccuracy/1000)*animationTimeInterval;

            if(curFlyVelocity*flyVelocity<0)
            {
                stopAnimation();
            }
            else
            {
                scrollDistance -= (curFlyVelocity + flyVelocity) * animationTimeInterval / 2 / 1000;
                flyVelocity = curFlyVelocity;
                if(scrollDistance<0)
                {
                    scrollDistance=0;
                }
            }
        }

        int overScrollHeight=slotHeightWithGap+overScrollGap;
        int slotOffsetTop=overScrollGap-scrollDistance%overScrollHeight;
        int slotIndex=scrollDistance/overScrollHeight*slotsPerRow;

        for(int topIndex=0; topIndex<slotRowsInView; topIndex++)
        {
            for(int leftIndex=0; leftIndex<slotsPerRow; leftIndex++)
            {
                //Log.i(TAG, topIndex+" "+leftIndex+" "+(slotIndex + (topIndex * slotsPerRow) + leftIndex));

                ThumbnailLoader.SlotTexture slotTexture = mThumbnailLoader.
                        getTexture(slotIndex + (topIndex * slotsPerRow) + leftIndex);

                if(slotTexture!=null)
                {
                    int slotLeft = leftIndex * overScrollHeight;
                    int slotTop = slotOffsetTop + topIndex * overScrollHeight;

                    if (slotTexture.isLoaded.get() && slotTexture.texture.isReady())
                    {
                        slotTexture.texture.draw(canvas, slotLeft, slotTop, slotSize, slotSize);
                    }
                    else
                    {
                        canvas.fillRect(slotLeft, slotTop, slotSize, slotSize, SLOT_BACKGROUND_COLOR);
                    }
                }
            }
        }
    }
}