package com.gk969.gallerySimple;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.Log;
import android.os.Handler;

import com.gk969.gallery.gallery3d.glrenderer.BitmapTexture;
import com.gk969.gallery.gallery3d.glrenderer.GLCanvas;
import com.gk969.gallery.gallery3d.glrenderer.StringTexture;
import com.gk969.gallery.gallery3d.glrenderer.TiledTexture;
import com.gk969.gallery.gallery3d.ui.GLView;
import com.gk969.gallery.gallery3d.ui.Paper;
import com.gk969.gallery.gallery3d.util.GalleryUtils;

public class SlotView extends GLView
{
    private static final String TAG = "AlbumView";
    private final float mMatrix[] = new float[16];
    private final static float BACKGROUND_COLOR[]=new float[]{0f, 0.5f, 0.5f, 0.5f};

    GLRender mRender;

    public SlotView(GLRender render)
    {
        mRender=render;
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom)
    {
        // Set the mSlotView as a reference point to the open animation
        GalleryUtils.setViewPointMatrix(mMatrix,
                (right - left) / 2, (bottom - top) / 2, 0 - GalleryUtils.meterToPixel(0.3f));

        mRender.setViewSize(getWidth(), getHeight());
    }

    @Override
    protected void render(GLCanvas canvas)
    {
        canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
        canvas.clearBuffer(BACKGROUND_COLOR);

        mRender.render(canvas);

    }
}