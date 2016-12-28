package com.gk969.gallerySimple;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;

import com.gk969.UltimateImgSpider.StaticValue;
import com.gk969.Utils.Utils;
import com.gk969.gallery.gallery3d.glrenderer.BitmapTexture;
import com.gk969.gallery.gallery3d.glrenderer.GLCanvas;
import com.gk969.gallery.gallery3d.glrenderer.StringTexture;
import com.gk969.gallery.gallery3d.glrenderer.UploadedTexture;
import com.gk969.gallery.gallery3d.ui.GLRoot;
import com.gk969.gallery.gallery3d.ui.GLRootView;

import java.util.ArrayDeque;

/*
Cache Mode:
***********************
*                     *
*      img file       *
*                     *
* ******************* *
* *                 * *
* *  texture cache  * *
* *                 * *
* * *************** * *
* * *             * * *
* * *             * * *
* * *   visible   * * *
* * *   window    * * *
* * *             * * *
* * *             * * *
* * *************** * *
* *                 * *
* *                 * *
* ******************* *
*                     *
*                     *
***********************
 */


public class ThumbnailLoader {
    private static final String TAG = "ThumbnailLoader";

    private int cacheSize;
    //A circle cache
    private SlotTexture[] textureCache;
    private volatile int scrollStep = 1;

    private int bestOffsetOfVisibleInCache;
    private int cacheOffset = 0;

    public volatile int visibleAreaOffset;
    public volatile int albumTotalImgNum;
    private volatile int imgNumInView;

    private GLRootView mGLRootView;

    private Uploader uploader = new Uploader();

    private ThumbnailLoaderThreadPool mThumbnailLoaderThreadPool;
    private SlotView slotView;

    private ThumbnailLoaderHelper loaderHelper;

    private final static int LABEL_TEXT_HEIGHT = (int) (StaticValue.THUMBNAIL_SIZE * SlotView.LABEL_HEIGHT_RATIO *
            SlotView.LABEL_TEXT_HEIGHT_RATIO);
    private final static int LABEL_NAME_WIDTH = (int) (StaticValue.THUMBNAIL_SIZE * SlotView.LABEL_NAME_WIDTH_RATIO);

    private final static int LABEL_NAME_COLOR = 0xFF00F000;
    private final static int LABEL_INFO_ACTIVE_COLOR = 0xFF00F000;
    private final static int LABEL_INFO_INACTIVE_COLOR = 0xFFFFFFFF;

    private TextPaint labelNamePaint;
    Paint.FontMetricsInt labelMetrics;

    private int activeSlotIndex = StaticValue.INDEX_INVALID;

    private volatile boolean needLabel;

    private boolean isViewStop;


    public ThumbnailLoader(GLRootView glRoot, ThumbnailLoaderHelper helper) {
        cacheSize = StaticValue.getThumbnailCacheSize();
        textureCache = new SlotTexture[cacheSize];

        labelNamePaint = new TextPaint();
        labelNamePaint.setTextSize(LABEL_TEXT_HEIGHT);
        labelNamePaint.setAntiAlias(true);
        labelNamePaint.setColor(LABEL_NAME_COLOR);
        labelNamePaint.setShadowLayer(2f, 0f, 0f, Color.BLACK);
        labelMetrics = labelNamePaint.getFontMetricsInt();

        for(int i = 0; i < cacheSize; i++) {
            textureCache[i] = new SlotTexture();
            textureCache[i].mainBmp = Bitmap.createBitmap(StaticValue.THUMBNAIL_SIZE,
                    StaticValue.THUMBNAIL_SIZE, StaticValue.BITMAP_TYPE);
            textureCache[i].labelName = new LabelNameTexture();
            textureCache[i].imgIndex = i;
        }

        mGLRootView = glRoot;
        loaderHelper = helper;
        helper.setLoader(this);
        needLabel = helper.needLabel();
        Log.i(TAG, "ThumbnailLoader cache size " + cacheSize);
    }

    public void refreshSlotInfo(int slotIndex, String infoStr, boolean isActive) {
        mGLRootView.lockRenderThread();
        activeSlotIndex = isActive ? slotIndex : StaticValue.INDEX_INVALID;

        if(infoStr != null) {
            if((slotIndex >= cacheOffset) && (slotIndex < (cacheOffset + cacheSize))) {
                textureCache[slotIndex % cacheSize].labelInfo = StringTexture.newInstance(infoStr,
                        LABEL_TEXT_HEIGHT, isActive ? LABEL_INFO_ACTIVE_COLOR : LABEL_INFO_INACTIVE_COLOR);
            }
        }

        mGLRootView.unlockRenderThread();
    }

    public void setView(SlotView view) {
        slotView = view;
    }

    public void setHelper(ThumbnailLoaderHelper helper, int totalImgNum, int scrollDistance) {
        Log.i(TAG, "setHelper "+helper+" "+totalImgNum);

        mGLRootView.lockRenderThread();

        loaderHelper = helper;
        needLabel = helper.needLabel();
        slotView.onChangeView();

        albumTotalImgNum=totalImgNum;
        for(SlotTexture slot : textureCache) {
            slot.recycle();
        }
        slotView.scrollAbs(scrollDistance);
        scrollStep=1;

        mThumbnailLoaderThreadPool.wakeup(true);

        mGLRootView.unlockRenderThread();
        mGLRootView.requestRender();
    }

    public void setAlbumTotalImgNum(int totalImgNum) {
        Log.i(TAG, "setAlbumTotalImgNum " + totalImgNum);

        int prevTotalImgNum = albumTotalImgNum;
        if(prevTotalImgNum == totalImgNum) {
            return;
        }

        mGLRootView.lockRenderThread();
        albumTotalImgNum = totalImgNum;
        if(totalImgNum > prevTotalImgNum) {
            if(visibleAreaOffset + cacheSize > prevTotalImgNum) {
                for(SlotTexture slot : textureCache) {
                    slot.hasTried = false;
                }
                refreshCacheOffset(visibleAreaOffset, true);
            }

            if(prevTotalImgNum != 0) {
                slotView.onNewImgReceived(prevTotalImgNum);
            }
        }
        mGLRootView.unlockRenderThread();

        mGLRootView.requestRender();
    }

    public void stopLoader() {
        mThumbnailLoaderThreadPool.shutdown();
    }

    void initAboutView(int slotNumInView) {
        imgNumInView = slotNumInView;
        bestOffsetOfVisibleInCache = (cacheSize - slotNumInView) / 2;
        Log.i(TAG, "initAboutView slotNumInView " + slotNumInView);


        int indexStart = visibleAreaOffset % cacheSize;
        for(int i = 0; i < cacheSize; i++) {
            SlotTexture slot = textureCache[indexStart];
            if(slot.isReady) {
                //Log.i(TAG, "reload " + slot.labelName.text);
                uploader.add(slot);
                uploader.add(slot.labelName);
            }
            indexStart = (indexStart + 1) % cacheSize;
        }

        if(mThumbnailLoaderThreadPool == null) {
            mThumbnailLoaderThreadPool = new ThumbnailLoaderThreadPool();
        } else {
            mThumbnailLoaderThreadPool.wakeup(false);
        }

        isViewStop=false;
        mGLRootView.addOnGLIdleListener(uploader);
    }

    SlotTexture getTexture(int index) {
        if((index >= 0) && (index < albumTotalImgNum)) {
            //Log.i(TAG, "getTexture "+index);
            return textureCache[index % cacheSize];
        }
        return null;
    }

    abstract class ReusableUploadedTexture extends UploadedTexture{
        boolean contentLoaded;

        void onRefresh(){
            contentLoaded=true;
            uploader.add(this);
        }

        @Override
        public void recycle() {
            super.recycle();
            contentLoaded=false;
        }
    }

    class SlotTexture extends ReusableUploadedTexture {
        Bitmap mainBmp;
        StringTexture labelInfo;
        LabelNameTexture labelName;
        int imgIndex;
        boolean hasTried;
        boolean isReady;
        boolean isLoading;

        @Override
        protected Bitmap onGetBitmap() {
            //Log.i(TAG, "onGetBitmap SlotTexture "+labelName.text+" "+imgIndex);
            return mainBmp;
        }

        @Override
        protected void onFreeBitmap(Bitmap bitmap) {

        }

        @Override
        public void recycle() {
            super.recycle();

            if(labelInfo != null) {
                labelInfo.recycle();
                labelInfo = null;
            }

            labelName.recycle();

            hasTried = false;
            isReady = false;
        }
    }

    class LabelNameTexture extends ReusableUploadedTexture {
        private Bitmap bitmap;
        private Canvas canvas;
        private String text;

        LabelNameTexture() {
            int labelTextHeight = labelMetrics.bottom - labelMetrics.top;
            bitmap = Bitmap.createBitmap(LABEL_NAME_WIDTH, labelTextHeight, Bitmap.Config.ARGB_8888);
            setSize(LABEL_NAME_WIDTH, labelTextHeight);
            setOpaque(false);
            canvas = new Canvas(bitmap);
        }

        void setText(String text) {
            //Log.i(TAG, "LabelNameTexture setText " + text);
            this.text = TextUtils.ellipsize(
                    text, labelNamePaint, LABEL_NAME_WIDTH, TextUtils.TruncateAt.END).toString();
        }

        @Override
        protected Bitmap onGetBitmap() {
            //Log.i(TAG, "onGetBitmap LabelNameTexture "+text);
            bitmap.eraseColor(Color.TRANSPARENT);
            canvas.translate(0, -labelMetrics.ascent);
            canvas.drawText(text, 0, 0, labelNamePaint);
            canvas.translate(0, labelMetrics.ascent);
            return bitmap;
        }

        @Override
        protected void onFreeBitmap(Bitmap bitmap) {

        }

        @Override
        public void recycle() {
            super.recycle();
        }
    }

    public void onViewStart(){
        mGLRootView.requestLayoutContentPane();
    }

    public void onViewStop(){
        mGLRootView.lockRenderThread();
        isViewStop = true;
        mGLRootView.unlockRenderThread();
    }

    public void onViewScrollOverLine(int index) {
        refreshCacheOffset(index, false);
    }

    private void refreshCacheOffset(int firstSlotInView, boolean forceRefresh) {
        //Log.i(TAG, "refreshCacheOffset "+firstSlotInView+" "+forceRefresh);

        int newCacheOffset = firstSlotInView - bestOffsetOfVisibleInCache;
        int cacheOffsetMax = albumTotalImgNum - cacheSize;
        if(cacheOffsetMax < 0) {
            cacheOffsetMax = 0;
        }

        if(newCacheOffset < 0) {
            newCacheOffset = 0;
        } else if(newCacheOffset > cacheOffsetMax) {
            newCacheOffset = cacheOffsetMax;
        }

        if((newCacheOffset != cacheOffset) || forceRefresh) {
            int interval = Math.abs(newCacheOffset - cacheOffset);
            int step;
            int imgIndex;
            if(newCacheOffset >= cacheOffset) {
                step = 1;
                imgIndex = cacheOffset + cacheSize;
            } else {
                step = -1;
                imgIndex = cacheOffset - 1;
            }

            for(int i = 0; i < interval; i++) {
                //Log.i(TAG, "recycle cacheIndex:" + cacheIndex + " imgIndex:" + imgIndex);
                SlotTexture slot = textureCache[imgIndex % cacheSize];
                slot.recycle();

                slot.imgIndex = imgIndex;
                imgIndex += step;
            }

            scrollStep = step;
            cacheOffset = newCacheOffset;

            if(!isViewStop) {
                mThumbnailLoaderThreadPool.wakeup(false);
            }
        }

        visibleAreaOffset = firstSlotInView;

        //Log.i(TAG, "scrollToIndex "+index+" cacheOffset "+cacheOffset);
    }

    private class Uploader implements GLRoot.OnGLIdleListener {
        // We are targeting at 60fps, so we have 16ms for each frame.
        // In this 16ms, we use about 4~8 ms to upload tiles.
        private static final long UPLOAD_TILE_LIMIT = 4; // ms
        private boolean isQueued;
        private ArrayDeque<ReusableUploadedTexture> textureDeque = new ArrayDeque<>(3);

        /**
         * Must be synchronized with GL-thread
         *
         * @param texture
         */
        void add(ReusableUploadedTexture texture) {
            /*
            if(textureDeque.size()>cacheSize){
                textureDeque.removeFirst();
            }
            */
            textureDeque.addLast(texture);

            if(!isQueued) {
                isQueued = true;
                mGLRootView.addOnGLIdleListener(this);
            }
        }

        @Override
        public boolean onGLIdle(GLCanvas canvas, boolean renderRequested) {
            //Log.i(TAG, "onGLIdle");
            if(isViewStop){
                isQueued=false;
            }else {
                long now = SystemClock.uptimeMillis();
                long dueTime = now + UPLOAD_TILE_LIMIT;
                //Log.i(TAG, "onGLIdle");
                while(now < dueTime && !textureDeque.isEmpty()) {
                    ReusableUploadedTexture texture = textureDeque.removeFirst();
                    if(texture.contentLoaded) {
                        /*
                        if(texture instanceof SlotTexture) {
                            Log.i(TAG, "deque " + textureDeque.size() + " updateContent SlotTexture " + ((SlotTexture) texture).imgIndex);
                        } else if(texture instanceof LabelNameTexture) {
                            Log.i(TAG, "deque " + textureDeque.size() + " updateContent LabelNameTexture " + ((LabelNameTexture) texture).text);
                        }
                        */
                        texture.updateContent(canvas);
                        mGLRootView.requestRender();
                    }
                    now = SystemClock.uptimeMillis();
                }
                isQueued = !textureDeque.isEmpty();
            }
            return isQueued;
        }
    }

    private int getNextIndexInCache(int curIndex, int step) {
        curIndex += step;
        if(curIndex < 0) {
            curIndex = cacheSize - 1;
        } else if(curIndex >= cacheSize) {
            curIndex = 0;
        }
        return curIndex;
    }

    private class ThumbnailLoaderThreadPool {
        private volatile boolean isRunning;
        private static final int THREAD_POOL_SIZE_MAX = 4;
        
        private ThumbnailLoaderThread[] threads;
        
        ThumbnailLoaderThreadPool() {
            int poolSize = Utils.getCpuCoresNum();
            if(poolSize > THREAD_POOL_SIZE_MAX) {
                poolSize = 4;
            }

            Log.i(TAG, "loader thread pool size " + poolSize);
            isRunning = true;
            threads = new ThumbnailLoaderThread[poolSize];
            for(int i = 0; i < poolSize; i++) {
                threads[i] = new ThumbnailLoaderThread(i);
            }
        }

        
        void wakeup(boolean needReStart) {
            //Log.i(TAG, "pool wakeup");
            for(ThumbnailLoaderThread thread : threads) {
                thread.wakeup(needReStart);
            }
        }
        
        void shutdown() {
            isRunning = false;
            wakeup(false);
        }
        
        private class ThumbnailLoaderThread extends Thread {
            private final static long SLEEP_INTERVAL = 365 * 24 * 3600 * 1000;
            private int index;
            volatile boolean isWorking;
            volatile boolean needRestart;

            private byte[] bitmapInTempStorage = new byte[16 * 1024];
            private BitmapFactory.Options bmpOpts;

            ThumbnailLoaderThread(int i) {
                super("ThumbnailLoaderThread");
                index = i;

                bmpOpts = new BitmapFactory.Options();
                bmpOpts.inPreferredConfig = StaticValue.BITMAP_TYPE;
                bmpOpts.inTempStorage = bitmapInTempStorage;

                setDaemon(true);
                start();
            }

            private boolean isOffsetChangedInLoading() {
                int step = scrollStep;
                int visibleAreaOffset = ThumbnailLoader.this.visibleAreaOffset;
                int loaderStart = (step == 1) ? visibleAreaOffset : (visibleAreaOffset + imgNumInView - 1);
                int imgIndexForLoader = loaderStart % cacheSize;
                for(int i = 0; i < cacheSize; i++) {
                    if(!isRunning) {
                        break;
                    }

                    SlotTexture slot = textureCache[imgIndexForLoader];
                    boolean bmpValid = true;

                    mGLRootView.lockRenderThread();
                    int imgIndex = slot.imgIndex;
                    if(!slot.hasTried) {
                        //Log.i(TAG, "not Tried " + imgIndex);
                        if(!slot.isLoading) {
                            slot.isLoading = true;
                            //Log.i(TAG, "not Loading " + imgIndex);

                            boolean hasTried = true;
                            if(!slot.isReady) {
                                //Log.i(TAG, "not ready " + imgIndex);
                                bmpOpts.inBitmap = slot.mainBmp;
                                mGLRootView.unlockRenderThread();
                                Bitmap bmp = loaderHelper.getThumbnailByIndex(imgIndex, bmpOpts);
                                mGLRootView.lockRenderThread();

                                bmpValid = imgIndex == slot.imgIndex && !needRestart && !isViewStop;
                                //Log.i(TAG, "loaded imgIndex " + imgIndex+" "+slot.imgIndex+" i "+i);

                                if(bmpValid) {
                                    if(bmp != null) {
                                        //Log.i(TAG, "add slot texture" +imgIndex);
                                        slot.onRefresh();
                                        slot.isReady = true;
                                    }

                                    if(needLabel) {

                                        String[] labelStr = loaderHelper.getLabelString(imgIndex).split(" ");
                                        //Log.i(TAG, "Load Label " + imgIndex + " " + labelStr[0]);

                                        if(!labelStr[0].isEmpty()) {
                                            //Log.i(TAG, "add label name texture "+imgIndex);
                                            slot.labelName.setText(labelStr[0]);
                                            slot.labelName.onRefresh();
                                        }

                                        if(labelStr.length > 1) {
                                            slot.labelInfo = StringTexture.newInstance(labelStr[1],
                                                    LABEL_TEXT_HEIGHT, (imgIndex == activeSlotIndex) ?
                                                            LABEL_INFO_ACTIVE_COLOR : LABEL_INFO_INACTIVE_COLOR);
                                        }
                                    }
                                }

                                mGLRootView.requestRender();

                                hasTried = bmpValid;
                            }

                            slot.hasTried = hasTried;
                            slot.isLoading = false;
                        }
                    }
                    mGLRootView.unlockRenderThread();

                    if(!bmpValid) {
                        if(needRestart) {
                            needRestart = false;
                        }
                        return true;
                    }

                    imgIndexForLoader = getNextIndexInCache(imgIndexForLoader, step);
                }

                return visibleAreaOffset != ThumbnailLoader.this.visibleAreaOffset;
            }

            void wakeup(boolean needRestart) {
                if(!isWorking) {
                    //Log.i(TAG, "thread "+index+" wakeup");
                    interrupt();
                } else if(needRestart){
                    this.needRestart = true;
                }
            }

            public void run() {
                while(isRunning) {
                    isWorking = true;
                    //Log.i(TAG, "thread "+index+" work");
                    while(isOffsetChangedInLoading()) ;
                    isWorking = false;

                    if(isRunning) {
                        try {
                            sleep(SLEEP_INTERVAL);
                        } catch(InterruptedException e) {
                            //Log.i(TAG, "Loader Interrupted");
                        }
                    }
                }
            }
        }
    }
}