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
    private static final String TAG = "SlotView";
    private final float mMatrix[] = new float[16];
    private final static float BACKGROUND_COLOR[]=new float[]{0, 0, 0, 0};
    
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

    private int scrollDistance;
    private int scrollDistanceOverRow;

    private ThumbnailLoader mThumbnailLoader;
    private GLRootView mGLrootView;

    private MyGestureListener mGestureListener;
    private GestureRecognizer mGestureRecognizer;


    private static final int DECELERATE_MULT_MIN=1;
    private static final int DECELERATE_MULT_MAX=5;
    private static final int FLY_ACCURACY_ABS=10000;
    private float flyAccuracy;
    private float flyVelocity;
    private float flyVelocityRaw;

    private static final float REBOUND_VELOCITY_PARAM=2000;
    private float overScrollGapY=0;
    private float reboundVelocity=0;
    private boolean rebound;
    private float overScrollGapYRaw;
    
    private long renderTime;

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
            scroll(dy);
            mGLrootView.unlockRenderThread();
            
            invalidate();
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            mGLrootView.lockRenderThread();
            startFly(velocityY);
            renderTime= System.currentTimeMillis();
            mGLrootView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
            mGLrootView.unlockRenderThread();

            Log.i(TAG, "onFling " + velocityX + " " + velocityY);
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
            mGLrootView.lockRenderThread();
            if((overScrollGapY!=0)&&(flyVelocity==0))
            {
                startRebound();
                renderTime = System.currentTimeMillis();
                mGLrootView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
            }
            mGLrootView.unlockRenderThread();
        }

    }


    public SlotView(Context context, ThumbnailLoader loader, GLRootView glRootView)
    {
        mGestureListener = new MyGestureListener();
        mGestureRecognizer = new GestureRecognizer(context, mGestureListener);
        
        slotGap= Utils.DisplayUtil.dipToPx(context, SLOT_GAP_MIN_IN_DP);
        mThumbnailLoader=loader;
        mThumbnailLoader.dispAreaScrollToIndex(0);
        mGLrootView=glRootView;
        loader.setView(this);
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

        int prevSlotHeightWithGap=slotHeightWithGap;
        int prevSlotsPerRow=slotsPerRow;

        slotsPerRow=(width>height)?SLOT_PER_ROW_LANDSCAPE:SLOT_PER_ROW_PORTRAIT;

        slotSize=(width-(slotsPerRow-1)*slotGap)/slotsPerRow;
        viewHeight=height;

        slotHeightWithGap=slotSize+slotGap;
        slotRowsInView=viewHeight/slotHeightWithGap + 2;
        mThumbnailLoader.init(slotRowsInView * slotsPerRow);

        if(scrollDistance!=0)
        {
            int scrollMax=getScrollDistanceMax();
            int newScroll = scrollDistance / prevSlotHeightWithGap * prevSlotsPerRow / slotsPerRow
                    * slotHeightWithGap + scrollDistance % prevSlotHeightWithGap;
            if (newScroll > scrollMax)
            {
                newScroll=scrollMax;
            }
            scrollAbs(newScroll);
        }
    }

    private void stopAnimation()
    {
        mGLrootView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        flyVelocity=0;
        rebound=false;
    }

    private float calculateDecelerate(float curValue, float rawValue)
    {
        return curValue*(DECELERATE_MULT_MIN-DECELERATE_MULT_MAX)/rawValue+DECELERATE_MULT_MAX;
    }

    private void startRebound()
    {
        rebound=true;
        overScrollGapYRaw=overScrollGapY;
        reboundVelocity=Math.abs(overScrollGapYRaw*REBOUND_VELOCITY_PARAM/slotSize);
        Log.i(TAG, "startRebound "+reboundVelocity);
    }

    private void startFly(float velocity)
    {
        flyVelocity=velocity;
        flyVelocityRaw=flyVelocity;
        flyAccuracy=(velocity>0)?(0-FLY_ACCURACY_ABS):FLY_ACCURACY_ABS;
    }

    private int getScrollDistanceMax()
    {
        int scrollMax=(mThumbnailLoader.albumTotalImgNum.get()+(slotsPerRow-1))/slotsPerRow
                        *slotHeightWithGap-viewHeight;
        if(scrollMax<0)
        {
            scrollMax=0;
        }

        return scrollMax;
    }

    private void scroll(float dy)
    {
        scroll(dy, getScrollDistanceMax());
    }

    public void scrollAbs(int distance)
    {
        scroll(distance-scrollDistance);
    }

    private void scroll(float dy, int scrollMax)
    {
        scrollDistance+=dy;
        if((scrollDistance<0)||(scrollDistance>scrollMax))
        {
            scrollDistance=(scrollDistance<0)?0:scrollMax;
            overScrollGapY-=dy/(Math.abs(overScrollGapY)+2)/4;
            Log.i(TAG, "overScrollGapY " + overScrollGapY);
        }
        else if(overScrollGapY!=0)
        {
            scrollDistance=(overScrollGapY>0)?0:scrollMax;
            float preGap=overScrollGapY;
            overScrollGapY-=2*dy/(Math.abs(overScrollGapY)+1);

            if(overScrollGapY*preGap<0)
            {
                overScrollGapY=0;
            }
        }

        //Log.i(TAG, scrollDistance+" "+scrollDistanceOverRow+" "+slotHeightWithGap);
        if(scrollDistance/slotHeightWithGap != scrollDistanceOverRow/slotHeightWithGap)
        {
            scrollDistanceOverRow=scrollDistance;
            mThumbnailLoader.dispAreaScrollToIndex(scrollDistance / slotHeightWithGap * slotsPerRow);
        }
    }

    @Override
    protected boolean onTouch(MotionEvent event) {
        mGestureRecognizer.onTouchEvent(event);
        return true;
    }

    private void renderFly(int interval)
    {
        if(flyVelocity!=0)
        {
            int scrollMax=getScrollDistanceMax();
            float curFlyVelocity;
            if(scrollDistance==0||scrollDistance==scrollMax)
            {
                curFlyVelocity = flyVelocity + 5 * flyAccuracy / 1000 * interval;
            }
            else
            {
                curFlyVelocity = flyVelocity + flyAccuracy / 1000 * interval /
                        calculateDecelerate(flyVelocity, flyVelocityRaw);
            }

            if(curFlyVelocity*flyVelocity<=0)
            {
                if(overScrollGapY==0)
                {
                    stopAnimation();
                }
                else
                {
                    flyVelocity=0;
                    startRebound();
                }
            }
            else
            {
                scroll(0 -(curFlyVelocity + flyVelocity) * interval / 2 / 1000, scrollMax);
                flyVelocity = curFlyVelocity;
            }
        }
    }

    private void renderRebound(int interval)
    {
        if(rebound)
        {
            if(overScrollGapY!=0)
            {
                float preGap=overScrollGapY;
                overScrollGapY-=((overScrollGapY>0)?1:-1)*interval*reboundVelocity/1000/
                        calculateDecelerate(overScrollGapY, overScrollGapYRaw);
                if(overScrollGapY*preGap<=0)
                {
                    overScrollGapY=0;
                    stopAnimation();
                }
            }
        }
    }


    @Override
    protected void render(GLCanvas canvas)
    {
        canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
        canvas.clearBuffer(BACKGROUND_COLOR);

        //Log.i(TAG, "render");

        long curTime= System.currentTimeMillis();
        int renderTimeInterval = (int)(curTime-renderTime);
        renderTime=curTime;

        renderFly(renderTimeInterval);

        renderRebound(renderTimeInterval);
        

        int overScrollGapAbs=(int)Math.abs(overScrollGapY);
        int offsetNormal=scrollDistance%slotHeightWithGap;
        int slotOffsetTop=((overScrollGapY>0)?overScrollGapAbs:(0-overScrollGapAbs*slotRowsInView))-
                offsetNormal;

        int slotIndexOffset=scrollDistance/slotHeightWithGap*slotsPerRow;
        int overScrollHeight=slotHeightWithGap+overScrollGapAbs;

        int albumTotalImg=mThumbnailLoader.albumTotalImgNum.get();

        for(int topIndex=0; topIndex<slotRowsInView; topIndex++)
        {
            for(int leftIndex=0; leftIndex<slotsPerRow; leftIndex++)
            {
                int slotIndex=slotIndexOffset + (topIndex * slotsPerRow) + leftIndex;
                if(slotIndex>=albumTotalImg)
                {
                    break;
                }

                ThumbnailLoader.SlotTexture slotTexture = mThumbnailLoader.getTexture(slotIndex);

                if(slotTexture!=null)
                {
                    int slotLeft = leftIndex * slotHeightWithGap;
                    int slotTop = slotOffsetTop + topIndex * overScrollHeight;

                    if (slotTexture.isReady.get() && slotTexture.texture.isReady())
                    {
                        slotTexture.texture.draw(canvas, slotLeft, slotTop, slotSize, slotSize);
                        slotTexture.info.draw(canvas, slotLeft, slotTop);
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