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
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
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
import android.widget.TextView;
import android.widget.Toast;

public class SpiderService extends Service
{
	private final String LOG_TAG = "SpiderService";
	final RemoteCallbackList<IRemoteSpiderServiceCallback> mCallbacks = new RemoteCallbackList<IRemoteSpiderServiceCallback>();
	
	private int cmdVal=SpiderActivity.CMD_NOTHING;
	
	
	/** The primary interface we will be calling on the service. */
	IRemoteWatchdogService watchdogService = null;
	
	private ServiceConnection watchdogConnection = new ServiceConnection()
	{
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. We are communicating with our
			// service through an IDL interface, so get a client-side
			// representation of that from the raw service object.
			watchdogService = IRemoteWatchdogService.Stub.asInterface(service);
			
			stringFromJNI("ashmem");
			
			Log.i(LOG_TAG, "onServiceConnected");
		}
		
		public void onServiceDisconnected(ComponentName className)
		{
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			watchdogService = null;
			
			Log.i(LOG_TAG, "onServiceDisconnected");
		}
	};
	
	private void startWatchdog()
	{
		Log.i(LOG_TAG, "startWatchdog");
		
		Intent watchdogIntent = new Intent(IRemoteWatchdogService.class.getName());
		watchdogIntent.setPackage(IRemoteWatchdogService.class.getPackage()
		        .getName());
		
		startService(watchdogIntent);
		bindService(watchdogIntent, watchdogConnection, BIND_ABOVE_CLIENT);
	}
	
	
	@Override
	public void onCreate()
	{
		Log.i(LOG_TAG, "onCreate");
		startWatchdog();
	}
	
	@Override
	public void onDestroy()
	{
		Log.i(LOG_TAG, "onDestroy");
		// Unregister all callbacks.
		mCallbacks.kill();
		
		timerRunning = false;
		if(spider!=null)
		{
			spider.stopLoading();
			spider.clearCache(true);
			spider.destroy();
		}
		
		jniOnDestroy();
		
		System.exit(0);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		String url=intent.getStringExtra(SpiderActivity.SOURCE_URL_BUNDLE_KEY);
		if(url!=null)
		{
			Log.i(LOG_TAG, "onStartCommand url:"+url);
		
			if(url.startsWith("http://")||url.startsWith("https://"))
			{
				
				if(!srcUrl.equals(url))
				{
					srcUrl=url;
		
					jniOnDestroy();
					if(!jniUrlListInit())
					{
						stopSelf();
						return START_NOT_STICKY;
					}
					
					spiderInit();
					
				}
			}
		}
		
		
		cmdVal=intent.getIntExtra(SpiderActivity.CMD_BUNDLE_KEY, SpiderActivity.CMD_NOTHING);
		Log.i(LOG_TAG, "onStartCommand "+cmdVal);

		handleCmd();
		
		return START_REDELIVER_INTENT;
	}
	
	public boolean registerAshmemPool(int fd)
	{
		try
        {
	        try
            {
	            watchdogService.registerAshmem(ParcelFileDescriptor.fromFd(fd));
            }
            catch (IOException e)
            {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
            }
        }
        catch (RemoteException e)
        {
	        // TODO Auto-generated catch block
	        e.printStackTrace();
        }
		return true;
	}
	
	private void handleCmd()
	{
		switch(cmdVal)
		{
			case SpiderActivity.CMD_CLEAR:
				
				
				stopSelf();
				break;
			case SpiderActivity.CMD_CONTINUE:
				findNextUrlToLoad();
				break;
			case SpiderActivity.CMD_PREPARE_RESTART:
				stopSelf();
				break;
			default:
				break;
		}
	}
	
	@Override
	public void onTrimMemory(int level)
	{
		Log.i("onTrimMemory", "level:"+level);
		
		switch(level)
		{
			case TRIM_MEMORY_COMPLETE:
				Log.i("onTrimMemory", "TRIM_MEMORY_COMPLETE");
				break;
				
			case TRIM_MEMORY_MODERATE:
				Log.i("onTrimMemory", "TRIM_MEMORY_MODERATE");
				break;

			case TRIM_MEMORY_BACKGROUND:
				Log.i("onTrimMemory", "TRIM_MEMORY_BACKGROUND");
				break;
				
			case TRIM_MEMORY_UI_HIDDEN:
				Log.i("onTrimMemory", "TRIM_MEMORY_UI_HIDDEN");
				break;
		}
	}
	
	@Override
	public void onLowMemory()
	{
		Log.i("onLowMemory", "onLowMemory");
	}
	
	@Override
	public IBinder onBind(Intent intent)
	{
		Log.i(LOG_TAG, "onBind:"+intent.getAction());
		
		if (IRemoteSpiderService.class.getName().equals(intent.getAction()))
		{
			return mBinder;
		}
		return null;
	}
	
	@Override
	public void onTaskRemoved(Intent rootIntent)
	{
		Toast.makeText(this, "Task removed: " + rootIntent, Toast.LENGTH_LONG)
		        .show();
	}
	
	/**
	 * The IRemoteInterface is defined through IDL
	 */
	private final IRemoteSpiderService.Stub mBinder = new IRemoteSpiderService.Stub()
	{
		public void registerCallback(IRemoteSpiderServiceCallback cb)
		{
			if (cb != null)
				mCallbacks.register(cb);
		}
		
		public void unregisterCallback(IRemoteSpiderServiceCallback cb)
		{
			if (cb != null)
				mCallbacks.unregister(cb);
		}
		
		public int getPid()
		{
			return Process.myPid();
		}
	};
	
	
	
	
	

	/*
	 * ����һ����վ����ҳ�棬������������ͼƬ��
	 * 
	 * �����������
	 * 
	 * ��ҳ�����㷨�� ɨ�赱ǰ��ҳ��ÿһ����վURL���������в�����ҳ�б��е�URL�������б�����Ϊ�ȴ�״̬��
	 * ɨ����ҳ�б������еȴ�״̬��URL�����뵱ǰҳ��URL�����Ƶ�URL��Ϊ�´�Ҫɨ���ҳ�沢���Ϊ������״̬��
	 * ����б���ȫ����Ϊ������ҳ�棬�����������
	 * 
	 * ��Դ�����㷨�� ɨ�赱ǰ��ҳ�ϵ�����ͼƬ������ԴURL����ͼƬ�б��е�ͼƬ��
	 * ���ز���ԴURL�����б������������Ϊ�ļ����������ͼƬ����alt��alt�����������Ϊ�ļ�����
	 */
	
	private String srcUrl="about:blank";
	private String srcHost;
	
	private final int URL_TYPE_PAGE = 0;
	private final int URL_TYPE_IMG = 1;
	
	private String curUrl;
	
	private int pageUrlCnt = 0;
	private int pageScanCnt = 0;
	private int imgUrlCnt = 0;
	private int imgDownloadCnt = 0;
	
	private boolean pageFinished = false;
	private long loadTimer;
	private long loadTime;
	private long scanTimer;
	private long scanTime;
	
	private WebView spider;
	
	private Runnable loadNextUrlAfterScan;
	private Runnable urlLoadTimeOut;
	private Handler spiderHandler = new Handler();
	private boolean timerRunning = true;
	private final int URL_TIME_OUT = 10;
	private AtomicInteger urlLoadTimer = new AtomicInteger(URL_TIME_OUT);
	private AtomicBoolean urlLoadPostSuccess = new AtomicBoolean(true);
	
	
	public native String stringFromJNI(String srcStr);
	public native boolean jniUrlListInit();
	// Activity onDestoryʱ���ã���jniAddUrl��jniFindNextUrlToLoad����ͬһ�̣߳����ܻ����
	public native void jniOnDestroy();
	public native int jniAddUrl(String url, int hashCode, int type);
	private native String jniFindNextUrlToLoad(String prevUrl, int type);
	
	static
	{
		System.loadLibrary("UltimateImgSpider");
	}
	
	
	private void reportSpiderLog(boolean isCompleted)
	{
		String log="";
		
		if(isCompleted)
		{
			log="siteScanCompleted\r\n";
		}
		else
		{
			pageScanCnt++;
		}
		
		Runtime rt = Runtime.getRuntime();
		log = log+"VM:"+(rt.totalMemory() >> 20) + "M Native:"
		        + (Debug.getNativeHeapSize() >> 20) + "M pic:" + imgUrlCnt
		        + " page:" + pageScanCnt + "/" + pageUrlCnt + " loadTime:" + loadTime + " scanTime:"
		        + scanTime + "\r\n" + curUrl;
		
		
		//Log.i(LOG_TAG, log);
		
		int numOfCallback = mCallbacks.beginBroadcast();
		for (int i = 0; i < numOfCallback; i++)
		{
			try
			{
				mCallbacks.getBroadcastItem(i).valueChanged(log);
			}
			catch (RemoteException e)
			{
				
			}
		}
		mCallbacks.finishBroadcast();
		
	}

	private void scanPageWithJS()
	{
		scanTimer = System.currentTimeMillis();
		spider.loadUrl("javascript:" + "var i;"
		        + "var img=document.getElementsByTagName(\"img\");"
		        + "var imgSrc=\"\";"
		        + "for(i=0; i<img.length; i++)"
		        + "{imgSrc+=(img[i].src+'��');}"
		        + "SpiderCrawl.recvImgUrl(imgSrc);"
		        + "var a=document.getElementsByTagName(\"a\");"
		        + "var aHref=\"\";"
		        + "for(i=0; i<a.length; i++)"
		        + "{aHref+=(a[i].href+'��');}"
		        + "SpiderCrawl.recvPageUrl(aHref);"
		        + "SpiderCrawl.onCurPageScaned();");
	}
	
	private void spiderWebViewInit()
	{
		spider = new WebView(getApplicationContext()); 
		
		spider.setWebViewClient(new WebViewClient()
		{
			public boolean shouldOverrideUrlLoading(WebView view, String url)
			{
				return false;
			}
			
			public void onPageFinished(WebView view, String url)
			{
				loadTime = System.currentTimeMillis() - loadTimer;
				//Log.i(LOG_TAG, "onPageFinished " + url + " loadTime:" + loadTime);
				
				if (!pageFinished)
				{
					pageFinished = true;
					if (curUrl.equals(url))
					{
						urlLoadTimer.set(0);
						scanPageWithJS();
					}
				}
			}
			
			public void onPageStarted(WebView view, String url, Bitmap favicon)
			{
				//Log.i(LOG_TAG, "onPageStarted " + url);
				loadTimer = System.currentTimeMillis();
				pageFinished = false;
			}
			
			@Override
			public WebResourceResponse shouldInterceptRequest(WebView view,
			        String url)
			{
				WebResourceResponse response = null;
				if (!curUrl.equals(url))
				{
					response = new WebResourceResponse("image/png", "UTF-8",
					        null);
				}
				return response;
			}
			
			public void onReceivedError(WebView view, int errorCode,
			        String description, String failingUrl)
			{
				Log.i(LOG_TAG, failingUrl + " ReceivedError " + errorCode + "  " + description);
			}
		});
		
		spider.setWebChromeClient(new WebChromeClient()
		{
			public void onProgressChanged(WebView view, int newProgress)
			{
				if (newProgress == 100)
				{
					
				}
			}
		});
		
		WebSettings setting = spider.getSettings();
		setting.setUserAgentString(ParaConfig.getUserAgent(this));
		
		// ��ֹͼƬ
		setting.setLoadsImagesAutomatically(false);
		
		setting.setCacheMode(WebSettings.LOAD_DEFAULT);
		
		// ʹ��javascript
		setting.setJavaScriptEnabled(true);
		setting.setJavaScriptCanOpenWindowsAutomatically(false);
		
		// ��������
		setting.setSupportZoom(true);
		setting.setBuiltInZoomControls(true);
		setting.setUseWideViewPort(true);
		setting.setDisplayZoomControls(false);
		
		// ����Ӧ��Ļ
		setting.setLoadWithOverviewMode(true);
		
		spider.addJavascriptInterface(this, "SpiderCrawl");
		
	}
	
	private void findNextUrlToLoad()
	{
		curUrl = jniFindNextUrlToLoad(curUrl, URL_TYPE_PAGE);
		
		if(curUrl.isEmpty())
		{
			Log.i(LOG_TAG, "site scan complete");
			reportSpiderLog(true);
			stopSelf();
		}
		else
		{
			spiderLoadUrl(curUrl);
			reportSpiderLog(false);
		}
	}
	
	private void spiderInit()
	{
		spiderWebViewInit();
		
		loadNextUrlAfterScan = new Runnable()
		{
			@Override
			public void run()
			{
				if(cmdVal!=SpiderActivity.CMD_PAUSE)
				{
					findNextUrlToLoad();
				}
				cmdVal=SpiderActivity.CMD_NOTHING;
			}
		};
		
		urlLoadTimeOut = new Runnable()
		{
			
			@Override
			public void run()
			{
				Log.i(LOG_TAG, "Load TimeOut!!");
				spider.stopLoading();
				scanPageWithJS();
			}
		};
		
		new timerThread().start();
		
		try
		{
			srcHost = new URL(srcUrl).getHost();
			pageUrlCnt = jniAddUrl(srcUrl, srcUrl.hashCode(), URL_TYPE_PAGE);
			
			findNextUrlToLoad();
		}
		catch (MalformedURLException e)
		{
			// Log.e(LOG_TAG,e.toString());
		}
	}
	
	private class timerThread extends Thread
	{
		private final int TIMER_INTERVAL = 1000;
		private boolean urlTimeOutPostSuccess = true;
		
		public void run()
		{
			while (timerRunning)
			{
				// Log.i(LOG_TAG, "Timer");
				
				if (urlLoadTimer.get() != 0)
				{
					if (urlLoadTimer.decrementAndGet() == 0)
					{
						urlTimeOutPostSuccess = spiderHandler
						        .post(urlLoadTimeOut);
					}
				}
				else if (!urlTimeOutPostSuccess)
				{
					Log.i(LOG_TAG, "try again urlTimeOutPost");
					urlTimeOutPostSuccess = spiderHandler.post(urlLoadTimeOut);
				}
				
				if (!urlLoadPostSuccess.get())
				{
					Log.i(LOG_TAG, "try again urlLoadpost");
					urlLoadPostSuccess.set(spiderHandler
					        .post(loadNextUrlAfterScan));
				}
				
				try
				{
					sleep(TIMER_INTERVAL);
				}
				catch (InterruptedException e)
				{
					// Log.e(LOG_TAG,e.toString());
				}
			}
		}
	}
	
	private void spiderLoadUrl(String url)
	{
        //Log.i(LOG_TAG, "spiderLoadUrl:"+url);
		spider.loadUrl(url);
		urlLoadTimer.set(URL_TIME_OUT);
	}
	
	@JavascriptInterface
	public void recvImgUrl(String imgUrl)
	{
		//Log.i(LOG_TAG, "imgUrl:"+imgUrl);
		
		String[] list=imgUrl.split("��");

		//Log.i(LOG_TAG, "length:"+list.length);
		
		for(String urlInList:list)
		{
			if (urlInList.startsWith("http://") || urlInList.startsWith("https://"))
			{
				int urlNumAfterAdd = jniAddUrl(urlInList, urlInList.hashCode(),
				        URL_TYPE_IMG);
				
				if (urlNumAfterAdd != 0)
				{
					imgUrlCnt = urlNumAfterAdd;
				}
			}
		}
	}
	
	@JavascriptInterface
	public void recvPageUrl(String pageUrl)
	{
		//Log.i(LOG_TAG, "pageUrl:"+pageUrl);
		
		String[] list=pageUrl.split("��");

		//Log.i(LOG_TAG, "length:"+list.length);
		
		for(String urlInList:list)
		{
			try
			{
				URL url = new URL(urlInList);
				if ((urlInList.startsWith("http://") || urlInList
				        .startsWith("https://"))
				        && (url.getHost().equals(srcHost))
				        && (url.getRef() == null))
				{
					int urlNumAfterAdd = jniAddUrl(urlInList, urlInList.hashCode(),
					        URL_TYPE_PAGE);
					if (urlNumAfterAdd != 0)
					{
						pageUrlCnt = urlNumAfterAdd;
					}
				}
			}
			catch (MalformedURLException e)
			{
				// Log.e(LOG_TAG,e.toString());
			}
		}
	}
	
	@JavascriptInterface
	public void onCurPageScaned()
	{
		scanTime = System.currentTimeMillis() - scanTimer;
		urlLoadPostSuccess.set(spiderHandler.post(loadNextUrlAfterScan));
	}
	
}