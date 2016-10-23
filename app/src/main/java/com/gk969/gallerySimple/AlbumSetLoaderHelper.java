package com.gk969.gallerySimple;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.gk969.UltimateImgSpider.SpiderProject;
import com.gk969.UltimateImgSpider.StaticValue;
import com.gk969.Utils.Utils;

import java.io.File;

public class AlbumSetLoaderHelper extends ThumbnailLoaderHelper {
    private final static String TAG = "AlbumSetLoaderHelper";
    private final static int PROJECT_THUMBNAIL_NUM = 4;
    private final static int PROJECT_THUMBNAIL_SLOTS = 2;
    private ThumbnailLoader mThumbnailLoader;
    private SpiderProject mSpiderProject;

    public AlbumSetLoaderHelper(SpiderProject project) {
        super();
        mSpiderProject = project;
    }

    @Override
    public void setLoader(ThumbnailLoader loader) {
        mThumbnailLoader = loader;
    }

    @Override
    public String getLabelString(int index) {
        if(index < mSpiderProject.projectList.size()) {
            Log.i(TAG, "getLabelString " + index + " " + mSpiderProject.projectList.get(index));
            return mSpiderProject.projectList.get(index).host + " " +
                    mSpiderProject.projectList.get(index).imgDownloadNum;
        }

        return "";
    }

    @Override
    public boolean needLabel() {
        return true;
    }


    @Override
    public Bitmap getThumbnailByIndex(int index, BitmapFactory.Options bmpOptions) {
        //Log.i(TAG, "try to load index:" + index);

        if(index < mSpiderProject.projectList.size()) {
            SpiderProject.ProjectInfo project = mSpiderProject.projectList.get(index);
            Bitmap thumbnailBmp;
            if(project.imgDownloadNum < PROJECT_THUMBNAIL_NUM) {
                String fileName = String.format("%s/%s/%d/%03d.%s", project.dir.getPath(),
                        StaticValue.SLOT_THUMBNAIL_DIR_NAME, 0, 0, StaticValue.THUMBNAIL_FILE_EXT);
                thumbnailBmp = BitmapFactory.decodeFile(fileName, bmpOptions);
            } else {
                File thumbnailFile = new File(String.format("%s/%s/%s", project.dir.getPath(),
                        StaticValue.SLOT_THUMBNAIL_DIR_NAME, StaticValue.PROJECT_THUMBNAIL));

                if(thumbnailFile.exists()) {
                    thumbnailBmp = BitmapFactory.decodeFile(thumbnailFile.getPath(), bmpOptions);
                } else {
                    BitmapFactory.Options opts=new BitmapFactory.Options();
                    opts.inSampleSize = 2;
                    Canvas canvas = new Canvas(bmpOptions.inBitmap);
                    int slotSize = StaticValue.THUMBNAIL_SIZE / PROJECT_THUMBNAIL_SLOTS;
                    Log.i(TAG, "create project thumbnail " + canvas.getWidth() + " " + canvas.getHeight());
                    for(int i = 0; i < PROJECT_THUMBNAIL_NUM; i++) {
                        Bitmap bmp = BitmapFactory.decodeFile(String.format("%s/%s/%d/%03d.%s",
                                project.dir.getPath(), StaticValue.SLOT_THUMBNAIL_DIR_NAME,
                                0, i, StaticValue.THUMBNAIL_FILE_EXT), opts);
                        if(bmp != null) {
                            canvas.drawBitmap(bmp, (i % PROJECT_THUMBNAIL_SLOTS) * slotSize,
                                    (i / PROJECT_THUMBNAIL_SLOTS) * slotSize, null);
                        }
                    }

                    Utils.saveBitmapToFile(bmpOptions.inBitmap, thumbnailFile);
                    thumbnailBmp=bmpOptions.inBitmap;
                }
            }

            return thumbnailBmp;
        }

        return null;
    }

}