package com.gk969.gallerySimple;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.Log;
import android.os.Handler;

import com.gk969.gallery.gallery3d.glrenderer.BitmapTexture;


public class ThumbnailTextureLoader
{
    private static final String TAG = "ThumbnailTextureLoader";

    private String mProjectPath;

    public ThumbnailTextureLoader(String projectPath)
    {
        mProjectPath=projectPath;
    }
}