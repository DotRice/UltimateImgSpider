package com.gk969.gallerySimple;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.gk969.UltimateImgSpider.StaticValue;

import java.io.File;

public class AlbumLoaderHelper extends ThumbnailLoaderHelper {
    private final static String TAG = "AlbumLoaderHelper";

    private String projectPath;

    public void setProjectPath(String path) {
        projectPath = path;
    }

    @Override
    public String getLabelString(int index) {
        return String.valueOf(index);
    }

    @Override
    public boolean needLabel() {
        return true;
    }

    @Override
    public Bitmap getThumbnailByIndex(int index, Bitmap container) {
        //Log.i(TAG, "try to load index:" + index);
        int group = index / StaticValue.MAX_IMG_FILE_PER_DIR;
        int offset = index % StaticValue.MAX_IMG_FILE_PER_DIR;

        File file=new File(String.format("%s/%s/%d/%03d.%s", projectPath, StaticValue.SLOT_THUMBNAIL_DIR_NAME,
                group, offset, StaticValue.THUMBNAIL_FILE_EXT));

        if(file.exists()) {
            bmpOpts.inBitmap = container;
            return BitmapFactory.decodeFile(file.getPath(), bmpOpts);
        }

        return null;
    }
}