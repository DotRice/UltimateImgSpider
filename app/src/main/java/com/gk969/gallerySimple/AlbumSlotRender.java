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

public class AlbumSlotRender implements GLRender
{
    private static final String TAG = "AlbumSlotRender";

    TextureLoader mTextureLoader;

    /*
    StringTexture strTexture;
    BitmapTexture bmpTexture0;
    BitmapTexture bmpTexture1;
    BitmapTexture bmpTexture2;

    private void loadTestTexture()
    {
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inPreferredConfig=Bitmap.Config.RGB_565;
        bmpTexture0=new BitmapTexture(BitmapFactory.decodeFile(projectPath+"/0/000.jpg", opts));
        bmpTexture1=new BitmapTexture(BitmapFactory.decodeFile(projectPath+"/0/001.jpg"));
        bmpTexture2=new BitmapTexture(BitmapFactory.decodeFile(projectPath+"/0/002.jpg"));
        strTexture = StringTexture.newInstance("string texture", 64, 0xFFFF0000);
    }

    private void drawTestTexture(GLCanvas canvas)
    {
        canvas.translate(400, 10);
        canvas.scale(1.5f, 1.5f, 1);
        canvas.rotate(45, 0, 0, 1);
        bmpTexture0.draw(canvas, 0, 0);
        canvas.restore();

        canvas.fillRect(0, 0, 100, 100, 0xFF00F040);

        bmpTexture1.draw(canvas, 0, 0);

        canvas.drawTexture(bmpTexture2, -50, 600, 300, 400);

        strTexture.draw(canvas, 50, 800);
    }
    */

    public AlbumSlotRender(TextureLoader textureLoader)
    {
        mTextureLoader=textureLoader;
    }

    @Override
    public void setViewSize(int width, int height)
    {

    }

    @Override
    public void render(GLCanvas canvas)
    {

    }


}