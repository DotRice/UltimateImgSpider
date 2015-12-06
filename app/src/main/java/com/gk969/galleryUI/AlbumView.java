package com.gk969.galleryUI;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.os.Handler;

import com.gk969.gallery.gallery3d.glrenderer.GLCanvas;
import com.gk969.gallery.gallery3d.glrenderer.TiledTexture;
import com.gk969.gallery.gallery3d.ui.GLView;
import com.gk969.gallery.gallery3d.util.GalleryUtils;

public class AlbumView extends GLView
{
    private static final String TAG = "AlbumView";

    private final float mMatrix[] = new float[16];
    TiledTexture slotImgTexture;

    public AlbumView(Handler handler, String projectPath)
    {
        Log.i(TAG, "projectPath:" + projectPath);
        Bitmap bitmap= BitmapFactory.decodeFile(projectPath+"/0/000.jpg");
        if(bitmap!=null)
        {
            Log.i(TAG, "bitmap decode");
            slotImgTexture = new TiledTexture(bitmap);
            //bitmap.recycle();
        }

        handler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                Log.i(TAG, "slotImgTexture " + slotImgTexture.isReady());
            }
        }, 1000);
    }

    @Override
    protected void onLayout(
            boolean changed, int left, int top, int right, int bottom)
    {

        // Set the mSlotView as a reference point to the open animation

        GalleryUtils.setViewPointMatrix(mMatrix,
                (right - left) / 2, (bottom - top) / 2, 0 - GalleryUtils.meterToPixel(0.3f));
    }

    @Override
    protected void render(GLCanvas canvas)
    {
        canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
        canvas.multiplyMatrix(mMatrix, 0);
        super.render(canvas);

        canvas.clearBuffer(new float[]{0f, 0.5f, 0.5f, 0.5f});
        canvas.restore();

        canvas.fillRect(100, 100, 500, 500, 0xFF00F040);

        if(slotImgTexture!=null)
        {
            if (slotImgTexture.isReady())
            {
                slotImgTexture.draw(canvas, 100, 600);
            }
        }
    }
}