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

public class TextureLoader
{
    private static final String TAG = "TextureLoader";

    private String mProjectPath;

    public TextureLoader(String projectPath)
    {
        mProjectPath=projectPath;
    }


}