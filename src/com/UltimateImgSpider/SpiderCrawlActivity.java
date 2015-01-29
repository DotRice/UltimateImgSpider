package com.UltimateImgSpider;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.utils.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class SpiderCrawlActivity extends Activity
{
	private String LOG_TAG = "SpiderCrawl";
	
	private String srcUrl;
	
	private TextView spiderLog;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_spider_crawl);
		
		if (!getSrcUrlFromBundle())
		{
			return;
		}
		
		spiderLog=(TextView)findViewById(R.id.tvSpiderLog);
		boundSpiderService();
	}
	
	protected void onStart()
	{
		super.onStart();
		Log.i(LOG_TAG, "onStart");
	}
	
	protected void onResume()
	{
		super.onResume();
		Log.i(LOG_TAG, "onResume");
		
	}
	
	protected void onPause()
	{
		super.onPause();
		Log.i(LOG_TAG, "onPause");
		
	}
	
	protected void onStop()
	{
		super.onStop();
		Log.i(LOG_TAG, "onStop");
		
	}
	
	protected void onDestroy()
	{
		super.onDestroy();
		Log.i(LOG_TAG, "onDestroy");
		
		unboundSpiderService();
	}
	
	private boolean getSrcUrlFromBundle()
	{
		Intent intent = this.getIntent();
		Bundle bundle = intent.getExtras();
		
		srcUrl = bundle.getString(SelSrcActivity.SOURCE_URL_BUNDLE_KEY); // 获取Bundle里面的字符串
		Toast.makeText(this, getString(R.string.srcUrl) + ":" + srcUrl,
		        Toast.LENGTH_SHORT).show();
		
		return srcUrl != null;
	}
	
	
	
	/** The primary interface we will be calling on the service. */
	IRemoteSpiderService mService = null;
	
	/**
	 * Class for interacting with the main interface of the service.
	 */
	private ServiceConnection mConnection = new ServiceConnection()
	{
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. We are communicating with our
			// service through an IDL interface, so get a client-side
			// representation of that from the raw service object.
			mService = IRemoteSpiderService.Stub.asInterface(service);
			
			// We want to monitor the service for as long as we are
			// connected to it.
			try
			{
				mService.registerCallback(mCallback);
				mService.setSpiderSrcUrl(srcUrl);
			}
			catch (RemoteException e)
			{
				// In this case the service has crashed before we could even
				// do anything with it; we can count on soon being
				// disconnected (and then reconnected if it can be restarted)
				// so there is no need to do anything here.
			}
			
			Log.i(LOG_TAG, "onServiceConnected");
		}
		
		public void onServiceDisconnected(ComponentName className)
		{
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			mService = null;
			
			Log.i(LOG_TAG, "onServiceDisconnected");
		}
	};
	
	private void boundSpiderService()
	{
		// Establish a couple connections with the service, binding
		// by interface names. This allows other applications to be
		// installed that replace the remote service by implementing
		// the same interface.
		bindService(new Intent(IRemoteSpiderService.class.getName()), mConnection,
		        Context.BIND_AUTO_CREATE);
		Log.i(LOG_TAG, "boundSpiderService");
	}
	
	private void unboundSpiderService()
	{
		// If we have received the service, and hence registered with
		// it, then now is the time to unregister.
		if (mService != null)
		{
			try
			{
				mService.unregisterCallback(mCallback);
			}
			catch (RemoteException e)
			{
				// There is nothing special we need to do if the service
				// has crashed.
			}
		}
		
		// Detach our existing connection.
		unbindService(mConnection);
		Log.i(LOG_TAG, "unbound SpiderService");
	}
	
	private void killSpiderProcess()
	{
		if (mService != null)
		{
			try
			{
				int pid = mService.getPid();
				Process.killProcess(pid);
				Log.i(LOG_TAG, "Killed service process.");
			}
			catch (RemoteException ex)
			{
				
				Log.i(LOG_TAG, "remote_call_failed");
			}
		}
	}
	
	
	private IRemoteSpiderServiceCallback mCallback = new IRemoteSpiderServiceCallback.Stub()
	{
		/**
		 * Note that IPC calls are dispatched through a thread pool
		 * running in each process, so the code executing here will NOT be
		 * running in our main thread like most other things -- so, to update
		 * the UI, we need to use a Handler to hop over there.
		 */
		public void valueChanged(String value)
		{
			mHandler.sendMessage(mHandler.obtainMessage(BUMP_MSG, value));
		}
	};
	
	private static final int BUMP_MSG = 1;
	
	private Handler mHandler = new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
				case BUMP_MSG:
					spiderLog.setText((String)msg.obj);
				break;
				default:
					super.handleMessage(msg);
			}
		}
		
	};
	
	
	
	
	
	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		
		Log.i(LOG_TAG,
		        (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) ? "Landscape"
		                : "Portrait");
	}
}
