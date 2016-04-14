package com.gk969.gallerySimple;
import android.graphics.Bitmap;

import com.gk969.gallery.gallery3d.glrenderer.StringTexture;

public abstract class ThumbnailLoaderHelper
{
    public abstract String getLabelString(int index);
    public abstract boolean needLabel();
    public abstract Bitmap getThumbnailByIndex(int index, Bitmap container);
    public void setLoader(ThumbnailLoader loader)
    {

    }
}
