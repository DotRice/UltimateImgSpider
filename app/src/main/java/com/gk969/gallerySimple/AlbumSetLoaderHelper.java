package com.gk969.gallerySimple;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.gk969.UltimateImgSpider.StaticValue;
import com.gk969.UltimateImgSpider.WatchdogService;

import java.io.File;
import java.util.ArrayList;

public class AlbumSetLoaderHelper implements ThumbnailLoaderHelper
{
    private final static String TAG = "AlbumSetLoaderHelper";

    private ThumbnailLoader mThumbnailLoader;

    private ArrayList<String> projectList = new ArrayList<String>();

    private boolean needRefreshList=true;

    private String appPath;

    public AlbumSetLoaderHelper(String path)
    {
        appPath=path;
    }

    @Override
    public String getLabelString(int index)
    {
        return (index<projectList.size())?projectList.get(index):"";
    }

    @Override
    public boolean needLabel()
    {
        return true;
    }

    private void refreshProjectList(String appPath)
    {
        projectList.clear();

        File appDir = new File(appPath);

        Log.i(TAG, "refreshProjectList path:"+appPath);

        File[] fileList = appDir.listFiles();
        //ArrayList<File> projectDir = new ArrayList<File>();
        for (File file : fileList)
        {
            if (file.isDirectory())
            {
                Log.i(TAG, "firject dir:"+file.getPath());

                if (WatchdogService.projectDataIsSafe(file.getPath() + StaticValue.PROJECT_DATA_DIR))
                {
                    //projectDir.add(file);
                    projectList.add(file.getName());
                }
            }
        }

        Log.i(TAG, "projectList.size "+projectList.size());
        mThumbnailLoader.setAlbumTotalImgNum(projectList.size());
    }

    public void addProject(String siteDomain)
    {
        projectList.add(siteDomain);
        mThumbnailLoader.setAlbumTotalImgNum(projectList.size());
    }

    @Override
    public Bitmap getThumbnailByIndex(int index, Bitmap container)
    {
        Log.i(TAG, "try to load index:" + index);

        if (needRefreshList)
        {
            refreshProjectList(appPath);
            needRefreshList=false;
        }

        if(index<projectList.size())
        {
            String fileName = String.format("%s/%s/%s/%d/%03d.jpg", appPath, projectList.get(index),
                    StaticValue.THUMBNAIL_DIR_NAME, 0, 0);

            BitmapFactory.Options bmpOpts = new BitmapFactory.Options();
            bmpOpts.inPreferredConfig = Bitmap.Config.RGB_565;
            bmpOpts.inBitmap = container;
            bmpOpts.inSampleSize = 1;

            return BitmapFactory.decodeFile(fileName, bmpOpts);
        }

        return null;
    }

    @Override
    public void setLoader(ThumbnailLoader loader)
    {
        mThumbnailLoader=loader;
    }

}