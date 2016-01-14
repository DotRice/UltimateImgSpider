package com.gk969.UltimateImgSpider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
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

import com.gk969.Utils.Utils;

public class WatchdogService extends Service
{
    private final static String TAG = "WatchdogService";

    private final static String PROJECT_FILE_NAME = "project.dat";

    private final static String SP_PROJECT_DATA_MD5="spProjectDataMd5";

    private String projectPath;
    private String dataFileFullPath;

    public native int jniGetAshmem(String name, int size);

    public native void jniRestoreProjectData(String path);

    public native void jniStoreProjectData(String path);

    final RemoteCallbackList<IRemoteWatchdogServiceCallback> mCallbacks
            = new RemoteCallbackList<IRemoteWatchdogServiceCallback>();

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
        mCallbacks.kill();

        System.exit(0);
    }

    private void storeProjectData()
    {
        jniStoreProjectData(dataFileFullPath);

        String md5OfFile = Utils.getFileMD5String(dataFileFullPath);
        SharedPreferences.Editor editor = getSharedPreferences(SP_PROJECT_DATA_MD5, 0).edit();
        editor.putString(projectPath, md5OfFile);
        editor.commit();
    }

    private boolean projectDataIsSafe()
    {
        String md5OfFile = Utils.getFileMD5String(dataFileFullPath);
        String md5InSp=getSharedPreferences(SP_PROJECT_DATA_MD5, 0).getString(projectPath, "");

        Log.i(TAG, "projectDataIsSafe "+md5OfFile+" "+md5InSp);

        if(md5OfFile!=null)
        {
            return md5InSp.equals(md5OfFile);
        }

        return false;
    }

    private void projectPathRecved(String path)
    {
        Log.i(TAG, "projectPathRecved "+path);

        if((path!=null)&&(projectPath==null))
        {
            projectPath = path;
            dataFileFullPath = path + "/" + PROJECT_FILE_NAME;
            if (projectDataIsSafe())
            {
                jniRestoreProjectData(dataFileFullPath);
            }
        }

        int numOfCallback = mCallbacks.beginBroadcast();
        for (int i = 0; i < numOfCallback; i++)
        {
            try
            {
                mCallbacks.getBroadcastItem(i).projectPathRecved();
            }
            catch (RemoteException e)
            {

            }
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        int cmdVal = intent.getIntExtra(StaticValue.BUNDLE_KEY_CMD, StaticValue.CMD_NOTHING);
        String path = intent.getStringExtra(StaticValue.BUNDLE_KEY_PRJ_PATH);

        Log.i(TAG, "onStartCommand:" + cmdVal + " path:" + path);

        switch (cmdVal)
        {
            case StaticValue.CMD_START:
            {
                projectPathRecved(path);
                break;
            }

            case StaticValue.CMD_STOP_STORE:
            {
                storeProjectData();
                stopSelf();
                break;
            }

            case StaticValue.CMD_JUST_STOP:
            {
                stopSelf();
                break;
            }
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
            } catch (IOException e)
            {
                e.printStackTrace();
            }

            return parcelFd;
        }

        public void registerCallback(IRemoteWatchdogServiceCallback cb)
        {
            if (cb != null)
            {
                Log.i(TAG, "registerCallback");
                mCallbacks.register(cb);
            }
        }

        public void unregisterCallback(IRemoteWatchdogServiceCallback cb)
        {
            if (cb != null)
            {
                mCallbacks.unregister(cb);
            }
        }
    };
}