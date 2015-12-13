package com.gk969.gallerySimple;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.Log;
import android.os.Handler;

import com.gk969.gallery.gallery3d.glrenderer.BitmapTexture;


public class AlbumSlidingWindow
{
    private static final String TAG = "AlbumSlidingWindow";

    private final static int CACHE_SIZE=96;
    //A circle cache for sliding window
    private ThumbnailTextureEntry textureCache[]=new ThumbnailTextureEntry[CACHE_SIZE];

    private String mProjectPath;
    private ThumbnailTextureLoader mTextureLoader;

    public AlbumSlidingWindow(String projectPath)
    {
        mProjectPath=projectPath;
        mTextureLoader=new ThumbnailTextureLoader(projectPath);
    }

    class ThumbnailTextureEntry
    {
        BitmapTexture texture;
        String pathInProject;
    }
}