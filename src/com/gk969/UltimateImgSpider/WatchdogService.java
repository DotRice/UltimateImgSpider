package com.gk969.UltimateImgSpider;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.MemoryFile;
import android.os.ParcelFileDescriptor;
import android.os.Message;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class WatchdogService extends Service
{
    private final static String TAG = "WatchdogService";
    
    private final static String PROJECT_FILE_NAME = "project.dat";
    
    public native int jniGetAshmem(String name, int size);
    public native void jniStoreProjectData(String dataFileFullPath);
    
    static
    {
        System.loadLibrary("UltimateImgSpider");
    }
    
    @Override
    public void onCreate()
    {
        Log.i(TAG, "onCreate");
    }
    
    @Override
    public void onDestroy()
    {
        Log.i(TAG, "onDestroy");
        
        System.exit(0);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        int cmdVal  = intent.getIntExtra(SpiderActivity.BUNDLE_KEY_CMD,SpiderActivity.CMD_NOTHING);
        String path = intent.getStringExtra(SpiderActivity.BUNDLE_KEY_PRJ_PATH);
        
        Log.i(TAG, "onStartCommand:" + cmdVal+ " path:"+path);
        
        if(cmdVal==SpiderActivity.CMD_STOP_STORE)
        {
            jniStoreProjectData(path+"/"+PROJECT_FILE_NAME);
            stopSelf();
        }
        
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent)
    {
        Log.i(TAG, "onBind:" + intent.getAction());
        
        if (IRemoteWatchdogService.class.getName().equals(intent.getAction()))
        {
            return mBinder;
        }
        return null;
    }
    
    /**
     * The IRemoteInterface is defined through IDL
     */
    private final IRemoteWatchdogService.Stub mBinder = new IRemoteWatchdogService.Stub()
    {
        @Override
        public ParcelFileDescriptor getAshmem(String name, int size)
                throws RemoteException
        {
            ParcelFileDescriptor parcelFd = null;
            try
            {
                parcelFd = ParcelFileDescriptor.fromFd(jniGetAshmem(name,
                        size));
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            
            return parcelFd;
        }
    };
}