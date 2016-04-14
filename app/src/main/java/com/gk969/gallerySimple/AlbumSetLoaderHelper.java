package com.gk969.gallerySimple;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.gk969.UltimateImgSpider.StaticValue;
import com.gk969.UltimateImgSpider.WatchdogService;

import java.io.File;
import java.util.ArrayList;

public class AlbumSetLoaderHelper extends ThumbnailLoaderHelper
{
    private final static String TAG = "AlbumSetLoaderHelper";
    public final static int INVALID_INDEX=0xFFFFFFFF;

    static public class ProjectInfo
    {
        public String site;
        public int[] imgInfo;
        public int[] pageInfo;

        public ProjectInfo(String siteHost, int[] paramImgInfo, int[] paramPageInfo)
        {
            site=siteHost;
            imgInfo=paramImgInfo;
            pageInfo=paramPageInfo;
        }
    }

    public ArrayList<ProjectInfo> projectList = new ArrayList<ProjectInfo>();

    private boolean needRefreshList = true;

    private String appPath;

    private ThumbnailLoader mThumbnailLoader;
    
    public native void jniGetProjectInfoOnStart(String path, int[] imgInfo, int[] pageInfo);
    static
    {
        System.loadLibrary("UltimateImgSpider");
    }

    public AlbumSetLoaderHelper(String path)
    {
        appPath = path;
    }



    public int findIndexBySite(String site)
    {
        int index=INVALID_INDEX;

        for(ProjectInfo project:projectList)
        {
            if(project.site.equals(site))
            {
                break;
            }
        }

        return index;
    }

    @Override
    public void setLoader(ThumbnailLoader loader)
    {
        mThumbnailLoader=loader;
    }

    @Override
    public String getLabelString(int index)
    {
        if(index < projectList.size())
        {
            Log.i(TAG, "getLabelString " + index + " " + projectList.get(index));
            return projectList.get(index).site + " " + projectList.get(index).imgInfo[StaticValue.PARA_DOWNLOAD];
        }

        return "";
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

        Log.i(TAG, "refreshProjectList path:" + appPath);

        File[] fileList = appDir.listFiles();

        for(File file : fileList)
        {
            if(file.isDirectory())
            {
                Log.i(TAG, "project dir:" + file.getPath());

                String dataDirPath=file.getPath() + StaticValue.PROJECT_DATA_DIR;
                if(WatchdogService.projectDataIsSafe(dataDirPath))
                {
                    int[] imgInfo=new int[StaticValue.IMG_PARA_NUM];
                    int[] pageInfo=new int[StaticValue.PAGE_PARA_NUM];
                    jniGetProjectInfoOnStart(dataDirPath+StaticValue.PROJECT_DATA_NAME, imgInfo, pageInfo);
                    projectList.add(new ProjectInfo(file.getName(), imgInfo, pageInfo));
                }
            }
        }

        mThumbnailLoader.setAlbumTotalImgNum(projectList.size());
        Log.i(TAG, "projectList.size " + projectList.size());
    }

    @Override
    public Bitmap getThumbnailByIndex(int index, Bitmap container)
    {
        Log.i(TAG, "try to load index:" + index);

        if(needRefreshList)
        {
            refreshProjectList(appPath);
            needRefreshList = false;
        }

        if(index < projectList.size())
        {
            String fileName = String.format("%s/%s/%s/%d/%03d.jpg", appPath, projectList.get(index).site,
                    StaticValue.THUMBNAIL_DIR_NAME, 0, 0);

            BitmapFactory.Options bmpOpts = new BitmapFactory.Options();
            bmpOpts.inPreferredConfig = Bitmap.Config.RGB_565;
            bmpOpts.inBitmap = container;
            bmpOpts.inSampleSize = 1;

            return BitmapFactory.decodeFile(fileName, bmpOpts);
        }

        return null;
    }

}