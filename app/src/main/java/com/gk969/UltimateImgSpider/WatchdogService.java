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

    private final static String PROJECT_DATA_DIR = "/data";
    private final static String PROJECT_DATA_NAME = "project.dat";
    private final static String PROJECT_DATA_MD5 = "hash.dat";

    private String dataDirPath;

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
        super.onCreate();

        Log.i(TAG, "onCreate");
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        mCallbacks.kill();

        System.exit(0);
    }

    private void storeProjectData()
    {
        String dataFileFullPath=dataDirPath+PROJECT_DATA_NAME;
        jniStoreProjectData(dataFileFullPath);

        String md5String = Utils.getFileMD5String(dataFileFullPath);
        try
        {
            FileOutputStream md5FileOut=new FileOutputStream(dataDirPath+PROJECT_DATA_MD5);
            md5FileOut.write(md5String.getBytes());
            md5FileOut.close();
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
        String dataFileFullPath=dataDirPath+PROJECT_DATA_NAME;
        String md5OfFile = Utils.getFileMD5String(dataFileFullPath);

        try
        {
            byte[] buf=new byte[32];
            FileInputStream md5FileIn=new FileInputStream(dataDirPath+PROJECT_DATA_MD5);
            md5FileIn.read(buf);
            md5FileIn.close();
            String md5InRec=new String(buf);
            Log.i(TAG, "projectDataIsSafe " + md5OfFile + " " + md5InRec);

            if(md5OfFile!=null)
            {
                return md5InRec.equals(md5OfFile);
            }

        } catch (IOException e)
        {
            e.printStackTrace();
        }

        return false;
    }

    private void tryToRestoreProjectData(String path)
    {
        Log.i(TAG, "tryToRestoreProjectData "+path);

        if((path!=null)&&(dataDirPath==null))
        {
            File dataDir=new File(path+PROJECT_DATA_DIR);
            if(!dataDir.exists()||!dataDir.isDirectory())
            {
                dataDir.mkdirs();
            }
            dataDirPath = path+PROJECT_DATA_DIR+"/";

            if (projectDataIsSafe())
            {
                jniRestoreProjectData(dataDirPath+PROJECT_DATA_NAME);
            }
        }

    }

    private void projectPathRecved()
    {
        Log.i(TAG, "projectPathRecved");

        int numOfCallback = mCallbacks.beginBroadcast();
        for (int i = 0; i < numOfCallback; i++)
        {
            try
            {
                mCallbacks.getBroadcastItem(i).projectPathRecved();
            }
            catch (RemoteException e)
            {
                e.printStackTrace();
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void projectDataSaved()
    {
        Log.i(TAG, "projectDataSaved");

        int numOfCallback = mCallbacks.beginBroadcast();
        for (int i = 0; i < numOfCallback; i++)
        {
            try
            {
                mCallbacks.getBroadcastItem(i).projectDataSaved();
            }
            catch (RemoteException e)
            {
                e.printStackTrace();
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
                tryToRestoreProjectData(path);
                projectPathRecved();
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

            case StaticValue.CMD_JUST_STORE:
            {
                storeProjectData();
                projectDataSaved();
                break;
            }

        }

        return START_NOT_STICKY;
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