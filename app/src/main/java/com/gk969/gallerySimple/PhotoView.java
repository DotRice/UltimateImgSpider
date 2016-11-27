package com.gk969.gallerySimple;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.os.Handler;
import android.view.KeyEvent;
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
import com.gk969.gallery.gallery3d.glrenderer.UploadedTexture;
import com.gk969.gallery.gallery3d.ui.GLRootView;
import com.gk969.gallery.gallery3d.ui.GLView;
import com.gk969.gallery.gallery3d.ui.GestureRecognizer;
import com.gk969.gallery.gallery3d.ui.Paper;
import com.gk969.gallery.gallery3d.ui.SynchronizedHandler;
import com.gk969.gallery.gallery3d.util.GalleryUtils;
import com.gk969.gallery.gallery3d.util.UsageStatistics;
import com.gk969.gallery.photos.BitmapRegionTileSource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

public class PhotoView extends GLView {
    private static final String TAG = "PhotoView";
    private final float mMatrix[] = new float[16];
    private final static float BACKGROUND_COLOR[] = new float[]{0, 0, 0, 0};

    private final static int NEXT_TEXTURE_MIXED_COLOR = 0xFF000000;
    private final static int EMPTY_IMG_COLOR = 0xFF808080;

    private final static int BORDER_SIZE=1;
    private final static int TILE_SIZE = StaticValue.TILED_BMP_SLOT_SIZE+BORDER_SIZE*2;
    private Bitmap sUploadBitmap;
    private Canvas sCanvas;
    private Paint sBitmapPaint;
    private Paint sPaint;
    private RectF srcRect;
    private RectF destRect;

    private float scaleForImg;

    private int viewWidth;
    private int viewHeight;
    private int scaledViewWidthForImg;
    private int scaledViewHeightForImg;

    private int photoViewMinSize;
    private int minSizeForImg;


    private long renderTime;
    private final static float VELOCITY_END_MIN = 0.24f;
    private final static float VELOCITY_START_MIN = 7.3f;
    private final static float VELOCITY_KEY_SCROLL = 8;
    private float velocityMinAtEnd;
    private float velocityMinAtStart;
    private float velocityKeyScroll;
    private float flyVelocity;
    private float flyStartVelocity;
    private float flyStartLeft;

    private ArrayDeque<Integer> keyQueue=new ArrayDeque<>();
    private static final int KEY_QUEUE_SIZE=3;

    private GLRootView mGLRootView;

    private GestureRecognizer mGestureRecognizer;

    private boolean isTouching;

    private volatile boolean isLoaderRunning;

    private boolean inDisplay;
    private PhotoLoader loader;
    private int curPhotoIndexInCache;
    private static final int PHOTO_CACHE_SIZE = 5;
    private static final int PHOTO_CACHE_CENTER = PHOTO_CACHE_SIZE / 2;
    private Photo[] photoCache;
    private volatile String curProjectPath;

    private final static int SCROLL_NEXT = 1;
    private final static int SCROLL_PREV = -1;

    private SpiderProject.ProjectInfo curProjectInfo;

    private DisplayPosition renderPos = new DisplayPosition();

    private class DisplayPosition {
        float top;
        float left;
        float width;
        float height;

        void set(DisplayPosition pos) {
            top = pos.top;
            left = pos.left;
            width = pos.width;
            height = pos.height;
        }

        void set(float pTop, float pLeft, float pWidth, float pHeight) {
            top = pTop;
            left = pLeft;
            width = pWidth;
            height = pHeight;
        }
    }

    DisplayPosition fullScreen = new DisplayPosition();


    public void prepareResources() {
        sUploadBitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, StaticValue.BITMAP_TYPE);
        sCanvas = new Canvas(sUploadBitmap);
        sBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        sBitmapPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        sPaint = new Paint();
        sPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        sPaint.setColor(Color.TRANSPARENT);

        srcRect=new RectF(BORDER_SIZE, BORDER_SIZE,
                BORDER_SIZE+StaticValue.TILED_BMP_SLOT_SIZE, BORDER_SIZE+StaticValue.TILED_BMP_SLOT_SIZE);
        destRect=new RectF();
    }

    private class Photo {
        private static final int FULL_THUMBNAIL_SLOT_NUM_MAX=(StaticValue.SIZE_TO_CREATE_FULL_THUMBNAIL
                +(StaticValue.TILED_BMP_SLOT_SIZE-1))/StaticValue.TILED_BMP_SLOT_SIZE;
        static final int FILL_CENTER_TILE_CACHE_SIZE=FULL_THUMBNAIL_SLOT_NUM_MAX
                *FULL_THUMBNAIL_SLOT_NUM_MAX;

        int indexInCache;
        volatile int indexInProject = SpiderProject.INVALID_INDEX;

        DisplayPosition boxPos = new DisplayPosition();
        float widthInBox;
        float heightInBox;
        int fillCenterBmpWidth;
        int fillCenterBmpHeight;

        StringTexture indexTexture;

        BitmapFactory.Options bmpOptions = new BitmapFactory.Options();
        BitmapRegionDecoder regionDecoder;
        Tile[] fillCenterTiles=new Tile[FILL_CENTER_TILE_CACHE_SIZE];
        boolean fillCenterTilesLoaded;

        class Tile extends UploadedTexture{
            private final static int CONTENT_SIZE=StaticValue.THUMBNAIL_SIZE;
            int id;
            Bitmap bmp;
            volatile boolean bmpLoaded;
            boolean textureReady;

            Tile(int id){
                super();
                this.id=id;
                bmp=Bitmap.createBitmap(CONTENT_SIZE, CONTENT_SIZE, StaticValue.BITMAP_TYPE);
            }

            @Override
            protected Bitmap onGetBitmap() {
                sCanvas.drawBitmap(bmp, BORDER_SIZE, BORDER_SIZE, sBitmapPaint);

                //sCanvas.drawLine(0, 0, 0, TILE_SIZE, sPaint);
                //sCanvas.drawLine(0, 0, TILE_SIZE, 0, sPaint);
                //sCanvas.drawLine(TILE_SIZE, TILE_SIZE, 0, TILE_SIZE, sPaint);
                //sCanvas.drawLine(TILE_SIZE, TILE_SIZE, TILE_SIZE, 0, sPaint);

                return sUploadBitmap;
            }

            @Override
            protected void onFreeBitmap(Bitmap bitmap) {

            }
        }

        Photo(int index) {
            bmpOptions.inPreferredConfig = StaticValue.BITMAP_TYPE;
            indexInCache = index;
            for(int i=0; i<fillCenterTiles.length; i++){
                fillCenterTiles[i]=new Tile(i);
            }
        }


        public void recycle() {
            Log.i(TAG, "recycle " + indexInProject + " @ " + indexInCache);
            for(Tile tile:fillCenterTiles){
                tile.recycle();
                tile.bmpLoaded=false;
            }

            fillCenterTilesLoaded=false;
            fillCenterBmpHeight=0;
            fillCenterBmpWidth=0;
        }

        void calcRenderPos() {
            //Log.i(TAG, String.format("calcRenderPos %d boxPos %f %f %f %f inbox %.3f %.3f",
            //                indexInProject, boxPos.width, boxPos.height, boxPos.top, boxPos.left, widthInBox, heightInBox));
            renderPos.width = boxPos.width*widthInBox;
            renderPos.height = boxPos.height*heightInBox;
            renderPos.top = boxPos.top + (boxPos.height - renderPos.height) / 2;
            renderPos.left = boxPos.left + (boxPos.width - renderPos.width) / 2;
        }

        void scaleByScreenCenter(float scaleRatio) {
            scale(viewHeight / 2, viewWidth / 2, scaleRatio, fullScreen);
        }

        void scale(int focusTop, int focusLeft, float scaleRatio, DisplayPosition basePos) {
            //Log.i(TAG, "scale " + indexInCache + " " + scaleRatio);

            boxPos.top = focusTop + (basePos.top - focusTop) * scaleRatio;
            boxPos.left = focusLeft + (basePos.left - focusLeft) * scaleRatio;
            boxPos.width = basePos.width * scaleRatio;
            boxPos.height = basePos.height * scaleRatio;
        }

        void drawFillCenterTexture(GLCanvas canvas){
            if(fillCenterBmpWidth*fillCenterBmpHeight==0){
                return;
            }

            int tilesHorizontal=(fillCenterBmpWidth+StaticValue.TILED_BMP_SLOT_SIZE-1) / StaticValue.TILED_BMP_SLOT_SIZE;
            int tilesVertical=(fillCenterBmpHeight+StaticValue.TILED_BMP_SLOT_SIZE-1) / StaticValue.TILED_BMP_SLOT_SIZE;
            int width=(int)(boxPos.width*widthInBox+0.5f);
            int height=(int)(boxPos.height*heightInBox+0.5f);
            int contentWidth=(int)(StaticValue.TILED_BMP_SLOT_SIZE*boxPos.width*widthInBox/fillCenterBmpWidth+0.5f);
            int contentHeight=(int)(StaticValue.TILED_BMP_SLOT_SIZE*boxPos.height*heightInBox/fillCenterBmpHeight+0.5f);

            calcRenderPos();
            for(int x = 0; x < tilesHorizontal; x++) {
                int left=x*contentWidth;
                if((left+contentWidth)>width){
                    left=width-contentWidth;
                }
                left+=renderPos.left-BORDER_SIZE;
                for(int y = 0; y < tilesVertical; y++) {
                    int top=y*contentHeight;
                    if((top+contentHeight)>height){
                        top=height-contentHeight;
                    }
                    top+=renderPos.top-BORDER_SIZE;

                    Tile tile = fillCenterTiles[x * tilesVertical + y];
                    if(tile.bmpLoaded){
                        Log.i(TAG, String.format("draw %d tile %d l:%d t:%d", indexInProject,
                                tile.id, left, top));
                        destRect.set(left, top, left+contentWidth, top+contentHeight);
                        canvas.drawTexture(tile, srcRect, destRect);
                    }
                }
            }
        }

        void loadFillCenterBmp() {
            int group = indexInProject / StaticValue.MAX_IMG_FILE_PER_DIR;
            int offset = indexInProject % StaticValue.MAX_IMG_FILE_PER_DIR;
            String imgFilePath = String.format("%s/%s/%d/%03d.%s", curProjectPath,
                    StaticValue.FULL_THUMBNAIL_DIR_NAME, group, offset, StaticValue.THUMBNAIL_FILE_EXT);
            if(!new File(imgFilePath).exists()) {
                int i;
                imgFilePath = String.format("%s/%d/%03d.", curProjectPath, group, offset);
                for(i = 0; i < StaticValue.IMG_FILE_EXT.length; i++) {
                    File file = new File(imgFilePath + StaticValue.IMG_FILE_EXT[i]);
                    if(file.exists()) {
                        imgFilePath = file.getPath();
                        break;
                    }
                }

                if(i == StaticValue.IMG_FILE_EXT.length) {
                    return;
                }
            }

            Log.i(TAG, "loadFillCenterBmp " + indexInProject + " @ " + imgFilePath);

            try {
                regionDecoder=BitmapRegionDecoder.newInstance(imgFilePath, false);
                int rawWidth=regionDecoder.getWidth();
                int rawHeight=regionDecoder.getHeight();

                int tilesHorizontal=(rawWidth+StaticValue.TILED_BMP_SLOT_SIZE-1) / StaticValue.TILED_BMP_SLOT_SIZE;
                int tilesVertical=(rawHeight+StaticValue.TILED_BMP_SLOT_SIZE-1) / StaticValue.TILED_BMP_SLOT_SIZE;
                if(tilesHorizontal*tilesVertical<fillCenterTiles.length){
                    if(rawHeight>=StaticValue.TILED_BMP_SLOT_SIZE && rawWidth>=StaticValue.TILED_BMP_SLOT_SIZE) {
                        float width;
                        float height;

                        mGLRootView.lockRenderThread();
                        if(rawWidth <= minSizeForImg && rawHeight <= minSizeForImg) {
                            if(rawHeight > rawWidth) {
                                width = rawWidth * photoViewMinSize / rawHeight;
                                height = photoViewMinSize;
                            } else {
                                width = photoViewMinSize;
                                height = rawHeight * photoViewMinSize / rawWidth;
                            }
                        } else if(rawWidth < scaledViewWidthForImg && rawHeight < scaledViewHeightForImg) {
                            width = scaleForImg * rawWidth;
                            height = scaleForImg * rawHeight;
                        } else if(rawHeight * scaledViewWidthForImg > rawWidth * scaledViewHeightForImg) {
                            width = rawWidth * viewHeight / rawHeight;
                            height = viewHeight;
                        } else {
                            width = viewWidth;
                            height = rawHeight * viewWidth / rawWidth;
                        }

                        widthInBox = width / viewWidth;
                        heightInBox = height / viewHeight;

                        fillCenterBmpWidth=rawWidth;
                        fillCenterBmpHeight=rawHeight;
                        mGLRootView.unlockRenderThread();

                        Rect tileRect = new Rect();
                        for(int x = 0; x < tilesHorizontal; x++) {
                            tileRect.left = x * StaticValue.TILED_BMP_SLOT_SIZE;
                            tileRect.right = tileRect.left + StaticValue.TILED_BMP_SLOT_SIZE;
                            if(tileRect.right >= rawWidth) {
                                tileRect.right = rawWidth;
                                tileRect.left=rawWidth-StaticValue.TILED_BMP_SLOT_SIZE;
                            }

                            for(int y = 0; y < tilesVertical; y++) {
                                tileRect.top = y * StaticValue.TILED_BMP_SLOT_SIZE;
                                tileRect.bottom = tileRect.top + StaticValue.TILED_BMP_SLOT_SIZE;
                                if(tileRect.bottom >= rawHeight) {
                                    tileRect.bottom = rawHeight;
                                    tileRect.top=rawHeight-StaticValue.TILED_BMP_SLOT_SIZE;
                                }

                                Tile tile = fillCenterTiles[x * tilesVertical + y];

                                bmpOptions.inBitmap = tile.bmp;
                                Bitmap bmp = regionDecoder.decodeRegion(tileRect, bmpOptions);

                                Log.i(TAG, String.format("load %d %d raw size w:%d h:%d rect t:%d l:%d b:%d r:%d",
                                        indexInProject, tile.id, rawWidth, rawHeight, tileRect.top, tileRect.left, tileRect.bottom, tileRect.right));
                                if(bmp != null) {
                                    //Log.i(TAG, "success");
                                    tile.bmpLoaded = true;
                                }
                            }
                        }
                    }
                }

            } catch(IOException e) {
                e.printStackTrace();
            }
        }

        private void load() {
            int cacheIndexBeforeLoad = curPhotoIndexInCache;
            if(!fillCenterTilesLoaded) {
                Log.i(TAG, "load bmp " + indexInProject + " for cache " + indexInCache);

                int projectIndexBeforeLoad = indexInProject;

                mGLRootView.unlockRenderThread();
                long loadTime = SystemClock.uptimeMillis();
                loadFillCenterBmp();
                loadTime = SystemClock.uptimeMillis() - loadTime;
                Log.i(TAG, "loadTime " + loadTime + "ms");
                /*
                try
                {
                    Thread.sleep(1000);
                } catch(InterruptedException e)
                {
                    e.printStackTrace();
                }
                */

                mGLRootView.lockRenderThread();

                if(projectIndexBeforeLoad != indexInProject) {
                    loader.needReload = true;
                    return;
                }

                indexTexture = StringTexture.newInstance(String.valueOf(indexInProject), 40, 0xFFFF0000);
                for(Tile tile:fillCenterTiles){
                    if(tile.bmpLoaded){
                        Log.i(TAG, String.format("create texture %d %d", indexInProject, tile.id));
                    }
                }
                fillCenterTilesLoaded=true;
            }

            if(cacheIndexBeforeLoad != curPhotoIndexInCache) {
                loader.needReload = true;
            }
        }
    }


    public PhotoView(Context context, GLRootView glRootView) {
        mGestureRecognizer = new GestureRecognizer(context, new MyGestureListener());

        float density = context.getResources().getDisplayMetrics().density;
        Log.i(TAG, "density "+density);
        scaleForImg = density;

        mGLRootView = glRootView;
        prepareResources();
        photoCache = new Photo[PHOTO_CACHE_SIZE];
        for(int i = 0; i < PHOTO_CACHE_SIZE; i++) {
            photoCache[i] = new Photo(i);
        }
        loader = new PhotoLoader();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // Set the mSlotView as a reference point to the open animation
        GalleryUtils.setViewPointMatrix(mMatrix,
                (right - left) / 2, (bottom - top) / 2, 0 - GalleryUtils.meterToPixel(0.3f));

        stopAnimation();
        setViewSize(getWidth(), getHeight());
    }

    private void startLoader() {
        if(!isLoaderRunning) {
            isLoaderRunning = true;
            loader = new PhotoLoader();
            loader.start();
        } else {
            loader.wakeUp();
        }
    }

    private void setViewSize(int width, int height) {
        if(viewWidth != width || viewHeight != height) {
            viewWidth = width;
            viewHeight = height;
            photoViewMinSize = Math.min(viewWidth, viewHeight) / 3;

            if(scaleForImg < 1) {
                scaleForImg = 1;
            }


            velocityMinAtEnd = VELOCITY_END_MIN * width;
            velocityMinAtStart = VELOCITY_START_MIN * width;
            velocityKeyScroll = VELOCITY_KEY_SCROLL * width;

            scaledViewHeightForImg = (int) (viewHeight / scaleForImg);
            scaledViewWidthForImg = (int) (viewWidth / scaleForImg);
            minSizeForImg = (int) (photoViewMinSize / scaleForImg);

            Log.i(TAG, "setViewSize " + width + "*" + height + " scaleForImg:" + scaleForImg);

            fullScreen.width = width;
            fullScreen.height = height;

            for(Photo photo : photoCache) {
                photo.boxPos.set(fullScreen);
                photo.recycle();
            }

            startLoader();
        }
    }

    private int getNextPhotoIndexInCache(int index) {
        return (index == (PHOTO_CACHE_SIZE - 1)) ? 0 : index + 1;
    }

    private int getPrevPhotoIndexInCache(int index) {
        return (index == 0) ? (PHOTO_CACHE_SIZE - 1) : index - 1;
    }

    private void photoCacheScroll(int scrollDir) {
        Log.i(TAG, "photoCacheScroll " + ((scrollDir == SCROLL_NEXT) ? "next" : "prev") + " curPhotoIndexInCache:" + curPhotoIndexInCache);
        int recycleCacheIndex = (scrollDir == SCROLL_NEXT) ? ((curPhotoIndexInCache + PHOTO_CACHE_CENTER) % PHOTO_CACHE_SIZE) :
                ((curPhotoIndexInCache + PHOTO_CACHE_SIZE - PHOTO_CACHE_CENTER) % PHOTO_CACHE_SIZE);

        Log.i(TAG, "photoCacheScroll recycleCacheIndex:" + recycleCacheIndex);

        Photo photo = photoCache[recycleCacheIndex];
        photo.recycle();
        photo.indexInProject += scrollDir * PHOTO_CACHE_SIZE;
        loader.wakeUp();
    }

    public void openPhoto(String projectPath, int indexInProject, SpiderProject.ProjectInfo projectInfo) {
        Log.i(TAG, "openPhoto " + projectPath + " " + indexInProject);
        mGLRootView.lockRenderThread();
        if(!projectPath.equals(curProjectPath)) {
            curProjectPath = projectPath;
            curProjectInfo = projectInfo;
            indexInProject -= PHOTO_CACHE_CENTER;
            for(int i = 0; i < PHOTO_CACHE_SIZE; i++) {
                photoCache[i].recycle();
                photoCache[i].indexInProject = indexInProject + i;
            }
            curPhotoIndexInCache = PHOTO_CACHE_CENTER;
        } else {
            int i;
            for(i = 0; i < PHOTO_CACHE_SIZE; i++) {
                if(photoCache[i].indexInProject == indexInProject) {
                    curPhotoIndexInCache = i;
                    break;
                }
            }

            if(i == PHOTO_CACHE_SIZE) {
                curPhotoIndexInCache = 0;
                photoCache[0].indexInProject = indexInProject;
                photoCache[0].recycle();
            }

            int prevIndex = getPrevPhotoIndexInCache(curPhotoIndexInCache);
            int nextIndex = getNextPhotoIndexInCache(curPhotoIndexInCache);
            for(i = 0; i < PHOTO_CACHE_CENTER; i++) {
                int newIndex;

                newIndex = indexInProject - 1 - i;
                if(photoCache[prevIndex].indexInProject != newIndex) {
                    photoCache[prevIndex].indexInProject = newIndex;
                    photoCache[prevIndex].recycle();
                }
                prevIndex = getPrevPhotoIndexInCache(prevIndex);

                newIndex = indexInProject + 1 + i;
                if(photoCache[nextIndex].indexInProject != newIndex) {
                    photoCache[nextIndex].indexInProject = newIndex;
                    photoCache[nextIndex].recycle();
                }
                nextIndex = getNextPhotoIndexInCache(nextIndex);
            }
        }

        inDisplay = true;
        loader.needReload = true;

        startLoader();
        mGLRootView.unlockRenderThread();

    }

    public void stopAnimation() {
        mGLRootView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        flyVelocity = 0;
    }

    @Override
    protected boolean onTouch(MotionEvent event) {
        mGestureRecognizer.onTouchEvent(event);
        return true;
    }

    private void startFly(float velocity) {
        Log.i(TAG, "startFly "+velocity);
        if(velocity!=0) {
            flyVelocity = velocity;
            flyStartVelocity = velocity;
            flyStartLeft = photoCache[curPhotoIndexInCache].boxPos.left;


            renderTime = SystemClock.uptimeMillis();
            mGLRootView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        }
    }

    public int getCurPhotoIndexInProject(){
        return photoCache[curPhotoIndexInCache].indexInProject;
    }

    private void onKeyDown(int keyCode){
        int indexInProject = photoCache[curPhotoIndexInCache].indexInProject;
        switch(keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if(indexInProject > 0) {
                    onScroll(-1);
                    startFly(velocityKeyScroll);
                }
                break;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if(indexInProject < (curProjectInfo.imgDownloadNum - 1)) {
                    startFly(0 - velocityKeyScroll);
                }
                break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, boolean canGiveUpFocus) {
        mGLRootView.lockRenderThread();
        if(flyVelocity==0) {
            onKeyDown(keyCode);
        }else{
            if(keyQueue.size()>=KEY_QUEUE_SIZE){
                keyQueue.removeFirst();
            }
            keyQueue.addLast(keyCode);
        }
        mGLRootView.unlockRenderThread();
        return true;
    }

    @Override
    protected void render(GLCanvas canvas) {
        canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
        canvas.clearBuffer(BACKGROUND_COLOR);

        long curTime = SystemClock.uptimeMillis();
        int renderTimeInterval = (int) (curTime - renderTime);
        renderTime = curTime;

        Photo photo = photoCache[curPhotoIndexInCache];
        //Log.i(TAG, "render " + photo.indexInProject + " " + curPhotoIndexInCache + " time " + renderTimeInterval);

        if(flyVelocity != 0) {
            float newLeft = photo.boxPos.left + renderTimeInterval * flyVelocity / 1000;

            if(flyVelocity > 0) {
                if(newLeft >= 0) {
                    stopAnimation();
                    photo.boxPos.left = 0;
                } else {
                    flyVelocity = Math.max(photo.boxPos.left / flyStartLeft * flyStartVelocity, velocityMinAtEnd);
                    photo.boxPos.left = newLeft;
                }
            } else {
                if(newLeft <= (0 - viewWidth)) {
                    stopAnimation();
                    curPhotoIndexInCache = getNextPhotoIndexInCache(curPhotoIndexInCache);
                    photo = photoCache[curPhotoIndexInCache];
                    photo.boxPos.set(fullScreen);
                    photoCacheScroll(SCROLL_NEXT);
                } else {
                    flyVelocity = Math.min((viewWidth + photo.boxPos.left) / (flyStartLeft + viewWidth)
                            * flyStartVelocity, (0 - velocityMinAtEnd));
                    photo.boxPos.left = newLeft;
                }
            }
        }

        if(photo.boxPos.left != 0) {
            float mainTextureMoveLeftRatio = (0 - photo.boxPos.left) / viewWidth;
            Photo nextPhoto = photoCache[getNextPhotoIndexInCache(curPhotoIndexInCache)];

            nextPhoto.scaleByScreenCenter(0.5f + mainTextureMoveLeftRatio * 0.5f);

            if(nextPhoto.fillCenterTilesLoaded){
                nextPhoto.drawFillCenterTexture(canvas);
                canvas.setAlpha(1-mainTextureMoveLeftRatio);
                canvas.fillRect(0, 0, viewWidth, viewHeight, NEXT_TEXTURE_MIXED_COLOR);
                canvas.setAlpha(mainTextureMoveLeftRatio);
                nextPhoto.indexTexture.draw(canvas, (int) renderPos.left, (int) renderPos.top);
                canvas.setAlpha(1);
            } else {
                canvas.fillRect(nextPhoto.boxPos.left, nextPhoto.boxPos.top, nextPhoto.boxPos.width,
                        nextPhoto.boxPos.height, EMPTY_IMG_COLOR);
            }
        }

        if(photo.fillCenterTilesLoaded){
            photo.drawFillCenterTexture(canvas);
            photo.indexTexture.draw(canvas, (int) renderPos.left, (int) renderPos.top);
        } else {
            canvas.fillRect(photo.boxPos.left, photo.boxPos.top, photo.boxPos.width, photo.boxPos.height,
                    EMPTY_IMG_COLOR);
        }

        if(flyVelocity==0){
            if(!keyQueue.isEmpty()){
                onKeyDown(keyQueue.removeFirst());
            }
        }
    }

    public void onDestroy() {
        mGLRootView.lockRenderThread();
        //TiledTexture.freeResources();
        for(Photo photo : photoCache) {
            photo.boxPos.set(fullScreen);
        }

        inDisplay = false;
        mGLRootView.unlockRenderThread();
    }

    public void stopLoader() {
        isLoaderRunning = false;
        loader.wakeUp();
    }


    private class PhotoLoader extends Thread {
        private final static long SLEEP_TIME = 1000*3600 * 24 * 365;
        private volatile boolean inSleep;

        public volatile boolean needReload;

        public PhotoLoader() {
            super("PhotoLoader");
            setDaemon(true);
        }

        public void wakeUp() {
            if(inSleep) {
                inSleep = false;
                interrupt();
            }
        }

        private void load() {
            Log.i(TAG, "start load " + photoCache[curPhotoIndexInCache].indexInProject + " @ " +
                    curPhotoIndexInCache + " left:" + photoCache[curPhotoIndexInCache].boxPos.left);
            if(viewHeight != 0) {
                mGLRootView.lockRenderThread();

                photoCache[curPhotoIndexInCache].load();

                mGLRootView.unlockRenderThread();
                if(needReload) {
                    return;
                }
                mGLRootView.requestRender();

                mGLRootView.lockRenderThread();

                int nextIndex = getNextPhotoIndexInCache(curPhotoIndexInCache);
                int prevIndex = getPrevPhotoIndexInCache(curPhotoIndexInCache);
                for(int i = 0; i < PHOTO_CACHE_CENTER; i++) {
                    photoCache[nextIndex].load();
                    if(needReload) {
                        break;
                    }
                    nextIndex = getNextPhotoIndexInCache(nextIndex);

                    photoCache[prevIndex].load();
                    if(needReload) {
                        break;
                    }
                    prevIndex = getPrevPhotoIndexInCache(prevIndex);
                }

                mGLRootView.unlockRenderThread();

                mGLRootView.requestRender();
            } else {
                Log.i(TAG, "viewHeight == 0");
            }
        }

        public void run() {
            while(isLoaderRunning) {
                needReload = false;
                load();

                if(!isLoaderRunning) {
                    break;
                }

                if(!needReload) {
                    try {
                        inSleep = true;
                        sleep(SLEEP_TIME);
                    } catch(InterruptedException e) {
                        Log.i(TAG, "loader wake up");
                    }
                }
                inSleep = false;
            }
        }
    }

    private boolean onScroll(float dx){
        //Log.i(TAG, "onScroll "+dx);

        Photo photo = photoCache[curPhotoIndexInCache];
        float newLeft = photo.boxPos.left - dx;

        float leftMin = 0 - viewWidth;

        boolean isEdge=false;
        if(newLeft > 0 && photo.boxPos.left <= 0) {
            //Log.i(TAG, "cur "+curPhotoIndexInCache+" left "+photo.boxPos.left+" width "+photo.boxPos.width);
            if(photo.indexInProject > 0) {
                curPhotoIndexInCache = getPrevPhotoIndexInCache(curPhotoIndexInCache);
                photo = photoCache[curPhotoIndexInCache];
                photo.boxPos.set(0, leftMin, viewWidth, viewHeight);
                photoCacheScroll(SCROLL_PREV);
            } else {
                isEdge=true;
            }
            //Log.i(TAG, "prev "+curPhotoIndexInCache+" left "+photo.boxPos.left+" width "+photo.boxPos.width);
        } else if(newLeft <= leftMin && photo.boxPos.left > leftMin) {
            //Log.i(TAG, "cur "+curPhotoIndexInCache+" left "+photo.boxPos.left+" width "+photo.boxPos.width);
            curPhotoIndexInCache = getNextPhotoIndexInCache(curPhotoIndexInCache);
            photo = photoCache[curPhotoIndexInCache];
            photo.boxPos.set(fullScreen);
            photoCacheScroll(SCROLL_NEXT);
            //Log.i(TAG, "next "+curPhotoIndexInCache+" left "+photo.boxPos.left+" width "+photo.boxPos.width);
        }

        if(dx > 0 && photo.indexInProject == (curProjectInfo.imgDownloadNum - 1)) {
            isEdge=true;
        }

        if(!isEdge) {
            photo.boxPos.left -= dx;
        }

        return isEdge;
    }

    private class MyGestureListener implements GestureRecognizer.Listener {

        @Override
        public boolean onSingleTapUp(float x, float y) {
            Log.i(TAG, "onSingleTapUp " + x + " " + y);

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
            mGLRootView.lockRenderThread();
            boolean isEdge=PhotoView.this.onScroll(dx);
            mGLRootView.unlockRenderThread();
            mGLRootView.requestRender();
            return isEdge;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if(velocityX != 0) {
                mGLRootView.lockRenderThread();
                int indexInProject=photoCache[curPhotoIndexInCache].indexInProject;
                if(velocityX > 0) {
                    velocityX = (indexInProject==0 && photoCache[curPhotoIndexInCache].boxPos.left==0)?0:Math.max(velocityX, velocityMinAtStart);
                } else {
                    velocityX = (indexInProject==(curProjectInfo.imgDownloadNum - 1))?
                            0:Math.min(velocityX, (0 - velocityMinAtStart));
                }

                startFly(velocityX);
                mGLRootView.unlockRenderThread();
            }
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

            isTouching = true;

            stopAnimation();

            mGLRootView.unlockRenderThread();
        }

        @Override
        public void onUp() {
            Log.i(TAG, "onUp");
            mGLRootView.lockRenderThread();
            if(flyVelocity == 0) {
                Photo photo = photoCache[curPhotoIndexInCache];
                float leftMinToBack = 0 - viewWidth / 2;
                if(photo.boxPos.left <= leftMinToBack) {
                    startFly(0 - (viewWidth + photo.boxPos.left) / viewWidth * velocityMinAtStart);
                } else {
                    startFly((0 - photo.boxPos.left) / (viewWidth + photo.boxPos.left) * velocityMinAtStart);
                }
            }

            isTouching = false;
            mGLRootView.unlockRenderThread();
        }

    }
}