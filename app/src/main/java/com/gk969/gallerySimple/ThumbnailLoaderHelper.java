package com.gk969.gallerySimple;
import android.graphics.Bitmap;

import com.gk969.gallery.gallery3d.glrenderer.StringTexture;

public interface ThumbnailLoaderHelper
{
    public String getLabelString(int index);
    public boolean needLabel();
    public Bitmap getThumbnailByIndex(int index, Bitmap container);
    public void setLoader(ThumbnailLoader loader);
}
