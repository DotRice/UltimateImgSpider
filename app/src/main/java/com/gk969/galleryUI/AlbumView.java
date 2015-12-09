package com.gk969.galleryUI;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.os.Handler;

import com.gk969.gallery.gallery3d.glrenderer.BitmapTexture;
import com.gk969.gallery.gallery3d.glrenderer.GLCanvas;
import com.gk969.gallery.gallery3d.glrenderer.StringTexture;
import com.gk969.gallery.gallery3d.glrenderer.TiledTexture;
import com.gk969.gallery.gallery3d.ui.GLView;
import com.gk969.gallery.gallery3d.util.GalleryUtils;

public class AlbumView extends GLView
{
    private static final String TAG = "AlbumView";

    private final float mMatrix[] = new float[16];
    StringTexture strTexture;
    BitmapTexture bmpTexture0;
    BitmapTexture bmpTexture1;
    BitmapTexture bmpTexture2;

    public AlbumView(Handler handler, String projectPath)
    {
        Log.i(TAG, "projectPath:" + projectPath);
        /**/
        bmpTexture0=new BitmapTexture(BitmapFactory.decodeFile(projectPath+"/0/000.jpg"));
        bmpTexture1=new BitmapTexture(BitmapFactory.decodeFile(projectPath+"/0/001.jpg"));
        bmpTexture2=new BitmapTexture(BitmapFactory.decodeFile(projectPath+"/0/002.jpg"));

        handler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                //Log.i(TAG, "strTexture " + strTexture.isReady());
            }
        }, 1000);


        strTexture = StringTexture.newInstance("string texture", 64, 0xFFFF0000);
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
        //canvas.multiplyMatrix(mMatrix, 0);
        //super.render(canvas);

        canvas.clearBuffer(new float[]{0f, 0.5f, 0.5f, 0.5f});


        canvas.translate(400, 10);
        canvas.scale(1.5f, 1.5f, 1);
        canvas.rotate(45, 0, 0, 1);
        bmpTexture0.draw(canvas, 0, 0);
        canvas.restore();

        canvas.fillRect(100, 100, 100, 100, 0xFF00F040);


        //bmpTexture0.draw(canvas, 100, 50);
        bmpTexture1.draw(canvas, 400, 500);
        canvas.drawTexture(bmpTexture2, -50, 600, 300, 400);

        strTexture.draw(canvas, 50, 800);

        Log.i(TAG, "render");
    }
}