package com.gk969.gallerySimple;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.Log;
import android.os.Handler;

import com.gk969.Utils.Utils;
import com.gk969.gallery.gallery3d.glrenderer.BitmapTexture;
import com.gk969.gallery.gallery3d.glrenderer.GLCanvas;
import com.gk969.gallery.gallery3d.glrenderer.StringTexture;
import com.gk969.gallery.gallery3d.glrenderer.TiledTexture;
import com.gk969.gallery.gallery3d.ui.GLView;
import com.gk969.gallery.gallery3d.ui.Paper;
import com.gk969.gallery.gallery3d.util.GalleryUtils;

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

public class AlbumSlotRender implements GLRender
{
    private static final String TAG = "AlbumSlotRender";
    private static final int SLOT_GAP_MIN_IN_DP=5;

    private int slotsPerRow=3;

    private int slotWidth;
    private int slotHeight;

    private int slotGap;

    private Context mContext;

    AlbumSlidingWindow mAlbumSlidingWindow;



    public AlbumSlotRender(Context context, AlbumSlidingWindow window)
    {
        mContext=context;
        slotGap= Utils.DisplayUtil.dipToPx(mContext, SLOT_GAP_MIN_IN_DP);
        mAlbumSlidingWindow=window;
    }

    @Override
    public void setViewSize(int width, int height)
    {
        slotWidth=(width-(slotsPerRow-1)*slotGap)/slotsPerRow;
        slotHeight=slotWidth;
    }

    @Override
    public void render(GLCanvas canvas)
    {
        canvas.fillRect(0, 0, 100, 100, 0xFF00F040);
    }


}