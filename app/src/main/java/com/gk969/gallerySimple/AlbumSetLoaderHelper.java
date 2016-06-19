package com.gk969.gallerySimple;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;

import com.gk969.UltimateImgSpider.SpiderProject;
import com.gk969.UltimateImgSpider.StaticValue;

public class AlbumSetLoaderHelper extends ThumbnailLoaderHelper
{
    private final static String TAG = "AlbumSetLoaderHelper";

    private String appPath;

    private ThumbnailLoader mThumbnailLoader;
    private SpiderProject mSpiderProject;

    public AlbumSetLoaderHelper(String path, SpiderProject project)
    {
        appPath = path;
        mSpiderProject=project;
    }

    @Override
    public void setLoader(ThumbnailLoader loader)
    {
        mThumbnailLoader=loader;
    }

    @Override
    public String getLabelString(int index)
    {
        if(index < mSpiderProject.projectList.size())
        {
            Log.i(TAG, "getLabelString " + index + " " + mSpiderProject.projectList.get(index));
            return mSpiderProject.projectList.get(index).site + " " +
                    mSpiderProject.projectList.get(index).imgInfo[StaticValue.PARA_DOWNLOAD];
        }

        return "";
    }

    @Override
    public boolean needLabel()
    {
        return true;
    }



    @Override
    public Bitmap getThumbnailByIndex(int index, Bitmap container)
    {
        //Log.i(TAG, "try to load index:" + index);

        if(index < mSpiderProject.projectList.size())
        {
            SpiderProject.ProjectInfo project=mSpiderProject.projectList.get(index);
            if(project.thumbnail==null)
            {
                String fileName = String.format("%s/%s/%s/%d/%03d.%s", appPath, mSpiderProject.projectList.get(index).site,
                        StaticValue.THUMBNAIL_DIR_NAME, 0, 0, StaticValue.THUMBNAIL_FILE_EXT);

                BitmapFactory.Options bmpOpts = new BitmapFactory.Options();
                bmpOpts.inPreferredConfig = Bitmap.Config.RGB_565;

                project.thumbnail = BitmapFactory.decodeFile(fileName, bmpOpts);
            }

            return project.thumbnail;
        }

        return null;
    }

}