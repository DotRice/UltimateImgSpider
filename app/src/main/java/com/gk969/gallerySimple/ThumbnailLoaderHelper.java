package com.gk969.gallerySimple;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.gk969.UltimateImgSpider.StaticValue;
import com.gk969.gallery.gallery3d.glrenderer.StringTexture;

public abstract class ThumbnailLoaderHelper {
    protected BitmapFactory.Options bmpOpts;

    public ThumbnailLoaderHelper(){
        bmpOpts=new BitmapFactory.Options();
        bmpOpts.inPreferredConfig = StaticValue.BITMAP_TYPE;
        bmpOpts.inSampleSize = 1;
    }

    public abstract String getLabelString(int index);

    public abstract boolean needLabel();

    public abstract Bitmap getThumbnailByIndex(int index, Bitmap container);

    public void setLoader(ThumbnailLoader loader) {

    }
}
