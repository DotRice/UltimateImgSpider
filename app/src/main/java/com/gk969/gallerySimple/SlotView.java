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

public class SlotView extends GLView {
    private static final String TAG = "SlotView";
    private final float mMatrix[] = new float[16];
    private final static float BACKGROUND_COLOR[] = new float[]{0, 0, 0, 0};

    private static final int SLOT_GAP_MIN_IN_DP = 5;
    private static final int SLOT_BACKGROUND_COLOR = 0xFF808080;
    private static final int SLOT_PER_ROW_PORTRAIT = 3;
    private static final int SLOT_PER_ROW_LANDSCAPE = 5;

    private static final float LABEL_HEIGHT_RATIO = 0.12f;
    private static final int LABEL_BACKGROUND_COLOR = 0x80000000;

    private static final float LABEL_TEXT_HEIGHT_RATIO = 0.7f;
    private static final float LABEL_NAME_LIMIT_RATIO = 0.7f;
    private static final float LABEL_PADDING_RATIO = 0.02f;

    private static final int SCROLL_BAR_TAP_WIDTH_IN_DP = 30;
    private static final int SCROLL_BAR_WIDTH_IN_DP = 20;
    private static final int SCROLL_BAR_HEIGHT_MAX_IN_DP = 100;
    private static final int SCROLL_BAR_HEIGHT_MIN_IN_DP = 40;

    private static final int NEED_SMOOTH_SCROLL_BAR = 25;
    private static final int NEED_SCROLL_BAR = 5;
    private static final int SCROLL_BAR_BACKGROUND_COLOR = 0xA020C020;


    private static final int BAR_SCROLL_VALID_IN_DP = 5;
    private int barScrollValid;

    private int scrollBarHeightMax;
    private int scrollBarHeightMin;

    private int scrollBarTapWidth;
    private int scrollBarWidth;
    private int scrollBarHeight;
    private float scrollBarTop;
    private boolean touchOnScrollBar;


    private int slotsPerRow;

    private int viewWidth;
    private int viewHeight;

    private int slotSize;
    private int slotGap;

    private int labelHeight;
    private int labelTextSize;
    private int labelTextTop;
    private int labelPadding;

    private int maxSlotRowsInView;
    private int maxSlotRowsInOverScrollView;
    private int slotHeightWithGap;

    private int scrollDistance;
    private int scrollDistanceOverRow;

    private ThumbnailLoader mThumbnailLoader;
    private GLRootView mGLRootView;

    private MyGestureListener mGestureListener;
    private GestureRecognizer mGestureRecognizer;


    private static final int DECELERATE_MULTI_MIN = 1;
    private static final int DECELERATE_MULTI_MAX = 5;
    private static final int FLY_ACCURACY_ABS = 10000;
    private float flyAccuracy;
    private float flyVelocity;
    private float flyVelocityRaw;

    private static final float REBOUND_VELOCITY_PARAM = 6;
    private float overScrollGapY = 0;
    private float reboundVelocity = 0;
    private boolean isRebounding;
    private float overScrollGapYRaw;

    private long renderTime;

    private final static int BAR_SCROLL_DURATION = 300;
    private final static int NEW_LINE_SCROLL_DURATION = 500;


    class BezierScroll {
        private Utils.CubicBezier scrollBezier;
        private int scrollStartPoint;
        private int scrollTotalDistance;
        private long scrollStartTime;
        private int scrollDuration;
        private boolean isScrolling = false;
        
        public BezierScroll(Utils.CubicBezier cubicBezier, int duration) {
            scrollBezier = cubicBezier;
            scrollDuration = duration;
        }

        public void start(int startPoint, int endPoint) {
            scrollStartTime = SystemClock.uptimeMillis();
            scrollStartPoint = startPoint;
            scrollTotalDistance = endPoint - startPoint;
            mGLRootView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
            isScrolling = true;
        }

        public void render(long curTime) {
            if(isScrolling) {
                float progress = scrollBezier.calculateYByX(
                        ((float) (curTime - scrollStartTime)) / scrollDuration);

                if(progress == 1) {
                    scrollAbs(scrollStartPoint + scrollTotalDistance);

                    if(!(isRebounding || (flyVelocity != 0))) {
                        stopAnimation();
                    }
                } else {
                    scrollAbs((int) (scrollStartPoint + progress * scrollTotalDistance));
                }
            }
        }

        public void stop() {
            isScrolling = false;
        }
    }

    private BezierScroll barScroll = new BezierScroll(
            new Utils.CubicBezier(0.25f, 0.1f, 0.1f, 1), BAR_SCROLL_DURATION);
    private BezierScroll newLineScroll = new BezierScroll(
            new Utils.CubicBezier(0.4f, 0.1f, 0.1f, 1f), NEW_LINE_SCROLL_DURATION);

    public interface OnClickListener {
        public void onClick(int slotIndex);
    }

    OnClickListener runOnClick;
    private boolean validClick;

    public interface OnScrollEndListener {
        public void onScrollEnd(int curScrollDistance);
    }

    OnScrollEndListener runOnScrollEnd;

    public interface OnManuallyScrollListener {
        public void onManuallyScroll();
    }

    OnManuallyScrollListener runOnManuallyScroll;

    public interface OnStartListener{
        public void onStart();
    }
    OnStartListener runOnStart;
    private boolean hasStarted=false;


    private boolean isTouching;

    private class MyGestureListener implements GestureRecognizer.Listener {

        @Override
        public boolean onSingleTapUp(float x, float y) {
            Log.i(TAG, "onSingleTapUp " + x + " " + y);
            if(validClick && runOnClick != null) {
                int intX = (int) x;
                int intY = (int) y;
                runOnClick.onClick((scrollDistance + intY) / slotHeightWithGap * slotsPerRow + intX / slotHeightWithGap);
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(float x, float y) {
            Log.i(TAG, "onDoubleTap " + x + " " + y);

            return true;
        }

        @Override
        public void onLongPress(float x, float y) {
            Log.i(TAG, "onLongPress " + x + " " + y);
        }

        @Override
        public boolean onScroll(float dx, float dy, float totalX, float totalY) {
            //Log.i(TAG, "onScroll "+dx+" "+dy+" "+totalX+" "+totalY);
            runOnManuallyScroll.onManuallyScroll();

            mGLRootView.lockRenderThread();

            if(touchOnScrollBar) {
                int scrollBarTopMax = viewHeight - scrollBarHeight;
                int ScrollDistanceMax = getScrollDistanceMax();
                scrollBarTop -= dy;
                if(scrollBarTop < 0) {
                    scrollBarTop = 0;
                } else if(scrollBarTop > scrollBarTopMax) {
                    scrollBarTop = scrollBarTopMax;
                }

                int finalScrollDistance = (int) ((double) scrollBarTop * ScrollDistanceMax / scrollBarTopMax);

                if(ScrollDistanceMax > (NEED_SMOOTH_SCROLL_BAR * viewHeight)) {
                    int validScroll = barScrollValid * ScrollDistanceMax / scrollBarTopMax;
                    if((Math.abs(finalScrollDistance - scrollDistance) > validScroll) ||
                            (finalScrollDistance < validScroll) ||
                            ((ScrollDistanceMax - finalScrollDistance) < validScroll)) {
                        barScroll.start(scrollDistance, finalScrollDistance);
                    }
                } else {
                    scrollAbs(finalScrollDistance);
                }
            } else {
                scroll(dy);
            }

            mGLRootView.unlockRenderThread();

            mGLRootView.requestRender();
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            mGLRootView.lockRenderThread();

            if(!touchOnScrollBar) {
                startFly(velocityY);
            }

            mGLRootView.unlockRenderThread();

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
            Log.i(TAG, "onDown " + x + " " + y);

            mGLRootView.lockRenderThread();

            touchOnScrollBar = (x > (viewWidth - scrollBarTapWidth)) &&
                    (y > scrollBarTop) && (y < (scrollBarTop + scrollBarHeight));
            validClick = (!isRebounding) && (flyVelocity == 0);
            isTouching = true;

            stopAnimation();

            mGLRootView.unlockRenderThread();
        }

        @Override
        public void onUp() {
            Log.i(TAG, "onUp");
            mGLRootView.lockRenderThread();
            if((overScrollGapY != 0) && (flyVelocity == 0)) {
                startRebound();
                renderTime = SystemClock.uptimeMillis();
                mGLRootView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
            }

            isTouching = false;
            runOnScrollEnd.onScrollEnd(scrollDistance);
            mGLRootView.unlockRenderThread();
        }

    }


    public SlotView(Context context, ThumbnailLoader loader, GLRootView glRootView) {
        mGestureListener = new MyGestureListener();
        mGestureRecognizer = new GestureRecognizer(context, mGestureListener);

        slotGap = Utils.DisplayUtil.dipToPx(context, SLOT_GAP_MIN_IN_DP);
        scrollBarTapWidth = Utils.DisplayUtil.dipToPx(context, SCROLL_BAR_TAP_WIDTH_IN_DP);
        scrollBarWidth = Utils.DisplayUtil.dipToPx(context, SCROLL_BAR_WIDTH_IN_DP);
        scrollBarHeightMax = Utils.DisplayUtil.dipToPx(context, SCROLL_BAR_HEIGHT_MAX_IN_DP);
        scrollBarHeightMin = Utils.DisplayUtil.dipToPx(context, SCROLL_BAR_HEIGHT_MIN_IN_DP);
        barScrollValid = Utils.DisplayUtil.dipToPx(context, BAR_SCROLL_VALID_IN_DP);

        mThumbnailLoader = loader;
        mThumbnailLoader.onViewScrollOverLine(0);
        mGLRootView = glRootView;
        loader.setView(this);
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom) {
        Log.i(TAG, "onLayout");
        // Set the mSlotView as a reference point to the open animation
        GalleryUtils.setViewPointMatrix(mMatrix,
                (right - left) / 2, (bottom - top) / 2, 0 - GalleryUtils.meterToPixel(0.3f));

        stopAnimation();
        setViewSize(getWidth(), getHeight());

        if(runOnStart!=null && hasStarted==false){
            runOnStart.onStart();
            hasStarted=true;
        }
    }

    public void setOnClick(OnClickListener listener) {
        runOnClick = listener;
    }

    public void setOnScrollEnd(OnScrollEndListener listener) {
        runOnScrollEnd = listener;
    }

    public void setOnManuallyScroll(OnManuallyScrollListener listener) {
        runOnManuallyScroll = listener;
    }

    public void setOnStart(OnStartListener listener){
        runOnStart = listener;
    }

    private void setViewSize(int width, int height) {
        Log.i(TAG, "setViewSize " + width + " " + height);

        int prevSlotHeightWithGap = slotHeightWithGap;
        int prevSlotsPerRow = slotsPerRow;
        int prevViewHeight = viewHeight;

        slotsPerRow = (width > height) ? SLOT_PER_ROW_LANDSCAPE : SLOT_PER_ROW_PORTRAIT;

        slotSize = (width - (slotsPerRow - 1) * slotGap) / slotsPerRow;

        labelHeight = (int) (slotSize * LABEL_HEIGHT_RATIO);
        labelTextSize = (int) (labelHeight * LABEL_TEXT_HEIGHT_RATIO);
        labelTextTop = (labelHeight - labelTextSize) / 2;
        labelPadding = (int) (slotSize * LABEL_PADDING_RATIO);

        viewWidth = width;
        viewHeight = height;

        slotHeightWithGap = slotSize + slotGap;
        maxSlotRowsInView = viewHeight / slotHeightWithGap + 2;
        maxSlotRowsInOverScrollView = viewHeight / slotHeightWithGap + 1;
        mThumbnailLoader.initAboutView(maxSlotRowsInView * slotsPerRow, labelTextSize, (int) (slotSize * LABEL_NAME_LIMIT_RATIO));

        if((scrollDistance != 0) && (prevViewHeight != height)) {
            scrollAbs(scrollDistance / prevSlotHeightWithGap * prevSlotsPerRow / slotsPerRow
                    * slotHeightWithGap + scrollDistance % prevSlotHeightWithGap);
        }
    }

    public void stopAnimation() {
        mGLRootView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        flyVelocity = 0;
        isRebounding = false;
        barScroll.stop();
        newLineScroll.stop();
        runOnScrollEnd.onScrollEnd(scrollDistance);
    }

    private float calculateDecelerate(float curValue, float rawValue) {
        return curValue * (DECELERATE_MULTI_MIN - DECELERATE_MULTI_MAX) / rawValue + DECELERATE_MULTI_MAX;
    }

    private void startRebound() {
        isRebounding = true;
        overScrollGapYRaw = overScrollGapY;
        reboundVelocity = Math.abs(overScrollGapYRaw * REBOUND_VELOCITY_PARAM);
        Log.i(TAG, "startRebound " + reboundVelocity);
    }

    private void startFly(float velocity) {
        flyVelocity = velocity;
        flyVelocityRaw = flyVelocity;
        flyAccuracy = (velocity > 0) ? (0 - FLY_ACCURACY_ABS) : FLY_ACCURACY_ABS;

        renderTime = SystemClock.uptimeMillis();
        mGLRootView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    public void onNewImgReceived(int prevImgNum) {
        if(!isTouching) {
            if(newLineScroll.isScrolling || scrollDistance == getSlotDistance(prevImgNum)) {
                int albumBottom = getScrollDistanceMax();
                Log.i(TAG, "albumBottom " + albumBottom);
                if((albumBottom - scrollDistance) >= slotHeightWithGap) {
                    Log.i(TAG, "newLineScroll.start");
                    newLineScroll.start(scrollDistance, albumBottom);
                }
            }
        }
    }

    private int getSlotDistance(int slotNum) {
        int distance = (slotNum + (slotsPerRow - 1)) / slotsPerRow
                * slotHeightWithGap - viewHeight;
        if(distance < 0) {
            distance = 0;
        }

        return distance;
    }

    private int getScrollDistanceMax() {
        return getSlotDistance(mThumbnailLoader.albumTotalImgNum);
    }

    private void scroll(float dy) {
        scroll(dy, getScrollDistanceMax());
    }

    public void scrollAbs(int distance) {
        int scrollMax = getScrollDistanceMax();
        if(distance < 0) {
            distance = 0;
        } else if(distance > scrollMax) {
            distance = scrollMax;
        }

        scrollDistance = distance;
        scrollSlotRow();
    }

    private void scroll(float dy, int scrollMax) {
        scrollDistance += dy;
        if((scrollDistance < 0) || (scrollDistance > scrollMax)) {
            scrollDistance = (scrollDistance < 0) ? 0 : scrollMax;
            overScrollGapY -= dy / (Math.abs(overScrollGapY) + 2) / 1.5f;
            //Log.i(TAG, "overScrollGapY " + overScrollGapY);
        } else if(overScrollGapY != 0) {
            scrollDistance = (overScrollGapY > 0) ? 0 : scrollMax;
            float preGap = overScrollGapY;
            overScrollGapY -= dy / (Math.abs(overScrollGapY) + 1);

            if(overScrollGapY * preGap < 0) {
                overScrollGapY = 0;
            }
        }

        scrollSlotRow();
    }

    private void scrollSlotRow() {
        //Log.i(TAG, scrollDistance+" "+scrollDistanceOverRow+" "+slotHeightWithGap);
        if(scrollDistance / slotHeightWithGap != scrollDistanceOverRow / slotHeightWithGap) {
            scrollDistanceOverRow = scrollDistance;
            mThumbnailLoader.onViewScrollOverLine(scrollDistance / slotHeightWithGap * slotsPerRow);
        }
    }

    @Override
    protected boolean onTouch(MotionEvent event) {
        mGestureRecognizer.onTouchEvent(event);
        return true;
    }

    private void renderFly(int interval, int scrollMax) {
        if(flyVelocity != 0) {
            float curFlyVelocity;
            if(scrollDistance == 0 || scrollDistance == scrollMax) {
                curFlyVelocity = flyVelocity + 5 * flyAccuracy / 1000 * interval;
            } else {
                curFlyVelocity = flyVelocity + flyAccuracy / 1000 * interval /
                        calculateDecelerate(flyVelocity, flyVelocityRaw);
            }

            if(curFlyVelocity * flyVelocity <= 0) {
                if(overScrollGapY == 0) {
                    stopAnimation();
                } else {
                    flyVelocity = 0;
                    startRebound();
                }
            } else {
                scroll(0 - (curFlyVelocity + flyVelocity) * interval / 2 / 1000, scrollMax);
                flyVelocity = curFlyVelocity;
            }
        }
    }

    private void renderRebound(int interval) {
        if(isRebounding) {
            if(overScrollGapY != 0) {
                float preGap = overScrollGapY;
                overScrollGapY -= ((overScrollGapY > 0) ? 1 : -1) * interval * reboundVelocity / 1000 /
                        calculateDecelerate(overScrollGapY, overScrollGapYRaw);
                if(overScrollGapY * preGap <= 0) {
                    overScrollGapY = 0;
                    if(!newLineScroll.isScrolling) {
                        stopAnimation();
                    }
                }
            }
        }
    }


    @Override
    protected void render(GLCanvas canvas) {
        canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
        canvas.clearBuffer(BACKGROUND_COLOR);

        //Log.i(TAG, "render");

        long curTime = SystemClock.uptimeMillis();
        int renderTimeInterval = (int) (curTime - renderTime);
        renderTime = curTime;

        int scrollMax = getScrollDistanceMax();
        renderFly(renderTimeInterval, scrollMax);

        renderRebound(renderTimeInterval);

        barScroll.render(curTime);
        newLineScroll.render(curTime);


        int overScrollGapAbs = (int) Math.abs(overScrollGapY);
        int offsetNormal = scrollDistance % slotHeightWithGap;

        int albumTotalImg = mThumbnailLoader.albumTotalImgNum;

        int rowsInView = (scrollMax > 0) ? maxSlotRowsInOverScrollView :
                (mThumbnailLoader.albumTotalImgNum + (slotsPerRow - 1)) / slotsPerRow;
        int slotOffsetTop = ((overScrollGapY > 0) ? overScrollGapAbs :
                (0 - overScrollGapAbs * rowsInView)) - offsetNormal;

        int slotIndexOffset = scrollDistance / slotHeightWithGap * slotsPerRow;
        int overScrollHeight = slotHeightWithGap + overScrollGapAbs;


        for(int topIndex = 0; topIndex < maxSlotRowsInView; topIndex++) {
            for(int leftIndex = 0; leftIndex < slotsPerRow; leftIndex++) {
                int slotIndex = slotIndexOffset + (topIndex * slotsPerRow) + leftIndex;
                if(slotIndex >= albumTotalImg) {
                    break;
                }

                ThumbnailLoader.SlotTexture slotTexture = mThumbnailLoader.getTexture(slotIndex);

                if(slotTexture != null) {
                    int slotLeft = leftIndex * slotHeightWithGap;
                    int slotTop = slotOffsetTop + topIndex * overScrollHeight;


                    if(slotTexture.isReady) {
                        slotTexture.texture.draw(canvas, slotLeft, slotTop, slotSize, slotSize);
                    } else {
                        canvas.fillRect(slotLeft, slotTop, slotSize, slotSize, SLOT_BACKGROUND_COLOR);
                    }

                    int labelY = slotTop + slotSize - labelHeight;
                    if(slotTexture.labelName != null) {
                        canvas.fillRect(slotLeft, labelY, slotSize, labelHeight, LABEL_BACKGROUND_COLOR);
                        slotTexture.labelName.draw(canvas, slotLeft + labelPadding, labelY + labelTextTop);
                    }

                    if(slotTexture.labelInfo != null) {
                        slotTexture.labelInfo.draw(canvas, slotLeft + slotSize - slotTexture.labelInfo.getWidth() - labelPadding,
                                labelY + labelTextTop);
                    }
                }
            }
        }

        int needScrollBar = viewHeight * NEED_SCROLL_BAR;
        if(scrollMax > needScrollBar) {
            scrollBarHeight = scrollBarHeightMin + (scrollBarHeightMax - scrollBarHeightMin) * needScrollBar / scrollMax;
            if(!touchOnScrollBar) {
                scrollBarTop = (int) ((viewHeight - scrollBarHeight) * (long) scrollDistance / scrollMax);
                scrollBarTop -= overScrollGapY;
            }

            canvas.fillRect(viewWidth - scrollBarWidth, scrollBarTop, scrollBarWidth, scrollBarHeight, SCROLL_BAR_BACKGROUND_COLOR);
        }
    }
}