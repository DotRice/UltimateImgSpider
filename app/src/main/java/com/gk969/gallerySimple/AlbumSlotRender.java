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

import java.util.concurrent.atomic.AtomicInteger;



public class AlbumSlotRender implements GLRender
{
    private static final String TAG = "AlbumSlotRender";
    private static final int SLOT_GAP_MIN_IN_DP=5;
    private static final int SLOT_BACKGROUND_COLOR=0xFF808080;

    private int slotsPerRow=3;

    private int viewWidth;
    private int viewHeight;

    private int slotWidth;
    private int slotHeight;
    private int slotGapX;
    private int slotGapY;

    private Context mContext;

    private ThumbnailLoader mThumbnailLoader;

    private AtomicInteger scrollDistance=new AtomicInteger(0);




    public AlbumSlotRender(Context context, ThumbnailLoader loader)
    {
        mContext=context;
        slotGapX= Utils.DisplayUtil.dipToPx(mContext, SLOT_GAP_MIN_IN_DP);
        slotGapY= slotGapX;
        mThumbnailLoader=loader;
    }

    // Same thread with render thread
    @Override
    public void setViewSize(int width, int height)
    {
        Log.i(TAG, "setViewSize "+width+" "+height);

        slotWidth=(width-(slotsPerRow-1)*slotGapX)/slotsPerRow;
        slotHeight=slotWidth;
        viewWidth=width;
        viewHeight=height;

        mThumbnailLoader.setSlotTextureSize(slotWidth, slotHeight);
    }

    @Override
    public void render(GLCanvas canvas)
    {
        int scrollTotal=scrollDistance.get();
        int slotHeightWithGap=slotHeight+slotGapY;
        int slotWidthWithGap=slotWidth+slotGapX;
        int slotOffsetTop=0-scrollTotal%slotHeightWithGap;
        int slotIndex=scrollTotal/slotHeightWithGap*slotsPerRow;
        int slotRowsInView=viewHeight/slotHeightWithGap+1;

        Log.i(TAG, "slotRowsInView "+slotRowsInView);
        for(int topIndex=0; topIndex<slotRowsInView; topIndex++)
        {
            for(int leftIndex=0; leftIndex<slotsPerRow; leftIndex++)
            {
                ThumbnailLoader.SlotTexture slotTexture = mThumbnailLoader.
                        getTexture(slotIndex + topIndex * slotsPerRow + leftIndex);

                int slotLeft=leftIndex * slotWidthWithGap;
                int slotTop=slotOffsetTop + topIndex * slotHeightWithGap;

                canvas.fillRect(slotLeft, slotTop, slotWidth, slotHeight, SLOT_BACKGROUND_COLOR);
                if(slotTexture.isReady.get())
                {
                    slotTexture.texture.draw(canvas, slotLeft + slotTexture.xInSlot,
                            slotTop + slotTexture.yInSlot);
                }
                slotIndex++;
            }
        }
    }
}