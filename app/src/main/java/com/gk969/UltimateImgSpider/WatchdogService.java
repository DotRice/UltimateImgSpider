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
    private final static String MD5_FILE_NAME = "md5.dat";

    private String projectPath;
    private String dataFileFullPath;

    private boolean getFirstAshmem = true;

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

        try
        {
            byte[] md5Value = Utils.getFileMD5(dataFileFullPath);
            OutputStream md5Out = new FileOutputStream(projectPath + "/" + MD5_FILE_NAME);
            md5Out.write(md5Value);
            md5Out.close();
        } catch (FileNotFoundException e)
        {
            e.printStackTrace();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private boolean projectDataIsSafe()
    {
        try
        {
            byte[] md5Value = Utils.getFileMD5(dataFileFullPath);

            byte[] md5InFile=new byte[16];
            FileInputStream md5In = new FileInputStream(projectPath + "/" + MD5_FILE_NAME);
            md5In.read(md5InFile);
            md5In.close();

            return Utils.isArrayEquals(md5Value, md5InFile);
        } catch (FileNotFoundException e)
        {
            e.printStackTrace();
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        return false;
    }

    private void projectPathRecved()
    {
        int numOfCallback = mCallbacks.beginBroadcast();
        for (int i = 0; i < numOfCallback; i++)
        {
            try
            {
                mCallbacks.getBroadcastItem(i).reportStatus();
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
        int cmdVal = intent.getIntExtra(SpiderActivity.BUNDLE_KEY_CMD, SpiderActivity.CMD_NOTHING);
        String path = intent.getStringExtra(SpiderActivity.BUNDLE_KEY_PRJ_PATH);

        Log.i(TAG, "onStartCommand:" + cmdVal + " path:" + path);

        if ((path != null)&&(projectPath==null))
        {
            projectPath = path;
            dataFileFullPath = path + "/" + PROJECT_FILE_NAME;

        }

        if (cmdVal == SpiderActivity.CMD_STOP_STORE)
        {
            storeProjectData();
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
                if (getFirstAshmem)
                {
                    if(projectDataIsSafe())
                    {
                        jniRestoreProjectData(dataFileFullPath);
                    }
                    getFirstAshmem = false;
                }
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