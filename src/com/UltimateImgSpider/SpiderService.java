package com.UltimateImgSpider;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class SpiderService extends Service
{
    private final String LOG_TAG = "SpiderService";

    @Override
    public void onCreate()
    {
        super.onCreate();
        Log.i(LOG_TAG, "onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.i(LOG_TAG, "onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        Log.i(LOG_TAG, "onDestroy");
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        // TODO Auto-generated method stub
        return null;
    }

}