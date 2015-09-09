package com.gk969.UltimateImgSpider;

import java.io.IOException;
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
	
	private final static int STAT_IDLE=0;
	private final static int STAT_DOWNLOAD_PAGE=1;
	private final static int STAT_SCAN_PAGE=2;
	private final static int STAT_DOWNLOAD_IMG=3;
	private final static int STAT_PAUSE=4;
	private final static int STAT_COMPLETE=5;
    private final static int STAT_STOP=6;
    private final static int STAT_PRE_PAUSE=7;
    private final static int STAT_PRE_STOP=8;
	private int state=STAT_IDLE;
	
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

			if(!jniSpiderInit())
			{
				stopSelf();
			}
			
			spiderInit();
			
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
		    Log.i(LOG_TAG, "clearCache");
			spider.stopLoading();
			spider.clearCache(true);
			spider.destroy();
		}
		
		System.exit(0);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if(intent!=null)
		{
			String url=intent.getStringExtra(SpiderActivity.SOURCE_URL_BUNDLE_KEY);
			if(url!=null)
			{
				Log.i(LOG_TAG, "onStartCommand url:"+url);
			
				if((url.startsWith("http://")||url.startsWith("https://"))&&srcUrl.equals(SRCURL_DEFAULT_VALUE))
				{
					srcUrl=url;
				}
			}
			
			int cmdVal=intent.getIntExtra(SpiderActivity.CMD_BUNDLE_KEY, SpiderActivity.CMD_NOTHING);
			Log.i(LOG_TAG, "onStartCommand "+cmdVal);
	
			switch(cmdVal)
			{
				case SpiderActivity.CMD_CLEAR:
					stopService(new Intent(this, WatchdogService.class));
					stopSelf();
					state=STAT_IDLE;
					break;
				case SpiderActivity.CMD_CONTINUE:
					if(state==STAT_PAUSE)
					{
						findNextUrlToLoad();
					}
					break;
				case SpiderActivity.CMD_STOP:
					if(state==STAT_PAUSE)
					{
						stopSelf();
					}
					else if(state==STAT_DOWNLOAD_PAGE)
					{
					    state=STAT_PRE_STOP;
					    if(urlLoadTimer.get()!=0)
						{
							urlLoadTimer.set(1);
						}
					}
					break;
				case SpiderActivity.CMD_PAUSE:
                    state=STAT_PRE_PAUSE;
                    if(urlLoadTimer.get()!=0)
                    {
                        urlLoadTimer.set(1);
                    }
                    break;
				default:
					break;
			}
		}
		
		return START_STICKY;
	}
	
	public int getAshmemFromWatchdog(String name, int size)
	{
		Log.i(LOG_TAG, "getAshmemFromWatchdog name:"+name+" size:"+size);
		
		int fd=-1;
		try
        {
			ParcelFileDescriptor parcelFd=watchdogService.getAshmem(name, size);
			if(parcelFd!=null)
			{
				fd=parcelFd.getFd();
			}
        }
        catch (RemoteException e)
        {
	        e.printStackTrace();
        }
		return fd;
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
	};
	
	
	
	
	

	/*
	 * 遍历一个网站所有页面，并且下载所有图片。
	 * 
	 * 网页遍历算法： 扫描当前网页上每一个本站URL，查找所有不在网页列表中的URL，存入列表并设置为等待状态。
	 * 扫描网页列表中所有等待状态的URL，将与当前页面URL最相似的URL作为下次要扫描的页面并标记为已下载状态。
	 * 如果列表中全部都为已下载页面，则遍历结束。
	 * 
	 * 资源下载算法： 扫描当前网页上的所有图片，查找源URL不在图片列表中的图片。
	 * 下载并将源URL存入列表，以下载序号作为文件名，如果此图片存在alt则将alt加下载序号作为文件名。
	 * 
	 * 图片下载与显示：图片首先被下载至内存，然后判断图片尺寸，长或宽小于200的视为无效图片直接删除。
	 * 有效图片存储位置为外部存储器，每次任务开始或者恢复时的前10张图片下载一张存一张。之后的先用内存缓存，
	 * 缓存图片总大小超过5MB后一起写入外部存储器。
	 */
	
	private final static String SRCURL_DEFAULT_VALUE="about:blank";
	private String srcUrl=SRCURL_DEFAULT_VALUE;
	private String srcHost;
	
	private final int URL_TYPE_PAGE = 0;
	private final int URL_TYPE_IMG = 1;
	
	private String curPageUrl;
	private String curImgUrl;
	
	private final static int TOTAL=0;
	private final static int PROCESSED=1;
	private final static int HEIGHT=2;
	
	private int[] pageProcParam;
	private int[] imgProcParam;
	
	private boolean pageFinished = false;
	private long loadTimer;
	private long loadTime;
	private long scanTimer;
	private long scanTime;
	private long searchTime;
	
	private WebView spider;
	
	private Runnable runnableAfterScanPage;
	private Runnable urlLoadTimeOut;
	private Handler spiderHandler = new Handler();
	private boolean timerRunning = true;
	private final static int URL_TIME_OUT = 10;
	private AtomicInteger urlLoadTimer = new AtomicInteger(URL_TIME_OUT);
	private AtomicBoolean urlLoadPostSuccess = new AtomicBoolean(true);
	
	private MessageDigest md5;
	
	private final static int MAX_SIZE_PER_URL=4096;
	
	private native String stringFromJNI(String srcStr);
	private native boolean jniSpiderInit();
	// Activity onDestory时调用，与jniAddUrl、jniFindNextUrlToLoad不在同一线程，可能会出错。
	private native int jniAddUrl(String url, byte[] md5, int type, int[] param);
	private native String jniFindNextUrlToLoad(String prevUrl, int type, int[] param);
	
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
		
		Runtime rt = Runtime.getRuntime();
		log = log+"VM:"+(rt.totalMemory() >> 20) + "M Native:"
		        + (Debug.getNativeHeapSize() >> 20) + "M pic:" + imgProcParam[PROCESSED] + "/" + imgProcParam[TOTAL] + "|" + imgProcParam[HEIGHT]
		        + " page:" + pageProcParam[PROCESSED] + "/" + pageProcParam[TOTAL] + "|" + pageProcParam[HEIGHT] + " loadTime:" + loadTime + " scanTime:"
		        + scanTime + " searchTime:" + searchTime + "\r\n" + curPageUrl;
		
		
		//Log.i(LOG_TAG, log);
		
		int numOfCallback = mCallbacks.beginBroadcast();
		for (int i = 0; i < numOfCallback; i++)
		{
			try
			{
				mCallbacks.getBroadcastItem(i).reportStatus(log);
			}
			catch (RemoteException e)
			{
				
			}
		}
		mCallbacks.finishBroadcast();
		
	}

	//javascript回调不在主线程。
	private void scanPageWithJS()
	{
		state=STAT_SCAN_PAGE;
		scanTimer = System.currentTimeMillis();
		spider.loadUrl("javascript:" + "var i;"
		        + "var img=document.getElementsByTagName(\"img\");"
		        + "var imgSrc=\"\";"
		        + "for(i=0; i<img.length; i++)"
		        + "{imgSrc+=(img[i].src+' ');}"
		        + "SpiderCrawl.recvImgUrl(imgSrc);"
		        + "var a=document.getElementsByTagName(\"a\");"
		        + "var aHref=\"\";"
		        + "for(i=0; i<a.length; i++)"
		        + "{aHref+=(a[i].href+' ');}"
		        + "SpiderCrawl.recvPageUrl(aHref);"
		        + "SpiderCrawl.onCurPageScaned();");
	}
	
	private void spiderWebViewInit()
	{
		spider = new WebView(this); 
		
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
					if (curPageUrl.equals(url))
					{
						urlLoadTimer.set(0);
						scanPageWithJS();
					}
				}
			}
			
			public void onPageStarted(WebView view, String url, Bitmap favicon)
			{
				Log.i(LOG_TAG, "onPageStarted " + url);
				loadTimer = System.currentTimeMillis();
				pageFinished = false;
			}
			
			@Override
			public WebResourceResponse shouldInterceptRequest(WebView view,
			        String url)
			{
				WebResourceResponse response = null;
				if (!curPageUrl.equals(url))
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
		
		// 阻止图片
		setting.setLoadsImagesAutomatically(false);
		
		setting.setCacheMode(WebSettings.LOAD_DEFAULT);
		
		// 使能javascript
		setting.setJavaScriptEnabled(true);
		setting.setJavaScriptCanOpenWindowsAutomatically(false);
		
		spider.addJavascriptInterface(this, "SpiderCrawl");
		
	}
	
	private void findNextUrlToLoad()
	{
		searchTime=System.currentTimeMillis();
		curPageUrl = jniFindNextUrlToLoad(curPageUrl, URL_TYPE_PAGE, pageProcParam);
		searchTime=System.currentTimeMillis()-searchTime;
		Log.i(LOG_TAG, "loading:"+curPageUrl);
		if(curPageUrl.isEmpty())
		{
			state=STAT_COMPLETE;
			//Log.i(LOG_TAG, "site scan complete");
			reportSpiderLog(true);
		}
		else
		{
			state=STAT_DOWNLOAD_PAGE;
			spiderLoadUrl(curPageUrl);
			reportSpiderLog(false);
		}
	}
	
	private void downloadCurImg()
	{
	    
	}
	
	private void downloadNewImgAfterScan()
	{
		new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                while(true)
                {
                    if(state==STAT_PRE_PAUSE)
                    {
                        state=STAT_PAUSE;
                        break;
                    }
                    else if(state==STAT_PRE_STOP)
                    {
                        state=STAT_STOP;
                        stopSelf();
                        break;
                    }
                    else
                    {
                        curImgUrl = jniFindNextUrlToLoad(curImgUrl, URL_TYPE_IMG, imgProcParam);
                        if(curImgUrl.isEmpty())
                        {
                            break;
                        }
                        else
                        {
                            downloadCurImg();
                        }
                    }
                }
            }
        }).start();
	}
	
	private void spiderInit()
	{
		spiderWebViewInit();
		
		pageProcParam=new int[3];
		imgProcParam=new int[3];
		
		runnableAfterScanPage = new Runnable()
		{
			@Override
			public void run()
			{
				if(state==STAT_PRE_PAUSE)
				{
				    state=STAT_PAUSE;
				}
				else if(state==STAT_PRE_STOP)
				{
					state=STAT_STOP;
				    stopSelf();
				}
				else
				{
					findNextUrlToLoad();
				}
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
	        md5=MessageDigest.getInstance("MD5");
			
			srcHost = new URL(srcUrl).getHost();
			jniAddUrl(srcUrl, md5.digest(srcUrl.getBytes()), URL_TYPE_PAGE, pageProcParam);
			
			findNextUrlToLoad();
		}
		catch (MalformedURLException e)
		{
	        e.printStackTrace();
		}
        catch (NoSuchAlgorithmException e)
        {
	        e.printStackTrace();
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
					        .post(runnableAfterScanPage));
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
		
		String[] list=imgUrl.split(" ");

		//Log.i(LOG_TAG, "length:"+list.length);
		
		for(String urlInList:list)
		{
			if ((urlInList.startsWith("http://") || urlInList.startsWith("https://"))&&urlInList.length()<MAX_SIZE_PER_URL)
			{
				jniAddUrl(urlInList, md5.digest(urlInList.getBytes()), URL_TYPE_IMG, imgProcParam);
			}
		}
	}
	
	@JavascriptInterface
	public void recvPageUrl(String pageUrl)
	{
		//Log.i(LOG_TAG, "pageUrl:"+pageUrl);
		
		String[] list=pageUrl.split(" ");

		//Log.i(LOG_TAG, "length:"+list.length);

		for(String urlInList:list)
		{
			try
			{
				URL url = new URL(urlInList);
				
				if ((urlInList.startsWith("http://") || urlInList
				        .startsWith("https://"))
				        && (url.getHost().equals(srcHost))
				        && (url.getRef() == null)
				        && (urlInList.length()<MAX_SIZE_PER_URL))
				{
					jniAddUrl(urlInList, md5.digest(urlInList.getBytes()), URL_TYPE_PAGE, pageProcParam);
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
	    Log.i(LOG_TAG, "onCurPageScaned");
		scanTime = System.currentTimeMillis() - scanTimer;
		urlLoadPostSuccess.set(spiderHandler.post(runnableAfterScanPage));
	}
	
}