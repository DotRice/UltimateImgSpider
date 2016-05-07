package com.gk969.gallerySimple;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;

import com.gk969.UltimateImgSpider.SpiderProject;
import com.gk969.UltimateImgSpider.StaticValue;
import com.gk969.UltimateImgSpider.WatchdogService;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

public class AlbumSetLoaderHelper extends ThumbnailLoaderHelper
{
    private final static String TAG = "AlbumSetLoaderHelper";

    private boolean needRefreshList = true;

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
        Log.i(TAG, "try to load index:" + index);

        if(needRefreshList)
        {
            mSpiderProject.refreshProjectList();
            mThumbnailLoader.setAlbumTotalImgNum(mSpiderProject.projectList.size());
            needRefreshList = false;
        }

        if(index < mSpiderProject.projectList.size())
        {
            String fileName = String.format("%s/%s/%s/%d/%03d%s", appPath, mSpiderProject.projectList.get(index).site,
                    StaticValue.THUMBNAIL_DIR_NAME, 0, 0, StaticValue.THUMBNAIL_FILE_EXT);

            BitmapFactory.Options bmpOpts = new BitmapFactory.Options();
            bmpOpts.inPreferredConfig = Bitmap.Config.RGB_565;
            bmpOpts.inBitmap = container;
            bmpOpts.inSampleSize = 1;

            Bitmap bmp = BitmapFactory.decodeFile(fileName, bmpOpts);
            if(bmp==null)
            {
                container.eraseColor(Color.GRAY);
                bmp = container;
            }

            return bmp;
        }

        return null;
    }

}