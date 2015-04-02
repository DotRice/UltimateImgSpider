package com.UltimateImgSpider;

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
	private final String LOG_TAG = "WatchdogService";
	
	public native void jniRegisterAshmem(int fd);
	public native int jniGetAshmem(String name, int size);
	
	static
	{
		System.loadLibrary("UltimateImgSpider");
	}
	
	
	
	
	@Override
	public void onCreate()
	{
		Log.i(LOG_TAG, "onCreate");
	}
	
	@Override
	public void onDestroy()
	{
		Log.i(LOG_TAG, "onDestroy");
		
		System.exit(0);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		Log.i(LOG_TAG, "onStartCommand "+startId);
		
		int cmd=intent.getIntExtra(SpiderService.WATCHDOG_CMD, SpiderService.WATCHDOG_CMD_NOTHING);
		if(cmd==SpiderService.WATCHDOG_CMD_STOP)
		{
			stopSelf();
		}
		
		return START_REDELIVER_INTENT;
	}
	
	
	@Override
	public IBinder onBind(Intent intent)
	{
		Log.i(LOG_TAG, "onBind:"+intent.getAction());
		
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
        public void registerAshmem(ParcelFileDescriptor pfd) throws RemoteException
        {
			int fd=pfd.getFd();
	        Log.i(LOG_TAG, "registerAshmem "+fd);
	        jniRegisterAshmem(fd);
        }

		@Override
        public int getPid() throws RemoteException
        {
	        return Process.myPid();
        }

		@Override
        public ParcelFileDescriptor getAshmem(String name, int size) throws RemoteException
        {
			ParcelFileDescriptor parcelFd=null;
			try
            {
	        	parcelFd=ParcelFileDescriptor.fromFd(jniGetAshmem(name, size));
            }
            catch (IOException e)
            {
	            e.printStackTrace();
            }
			
			return parcelFd;
        }
	};
	
}