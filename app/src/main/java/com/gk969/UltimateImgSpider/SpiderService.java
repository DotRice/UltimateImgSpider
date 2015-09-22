package com.gk969.UltimateImgSpider;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;

import com.gk969.Utils.Utils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
    private final String                                   TAG                = "SpiderService";
    final RemoteCallbackList<IRemoteSpiderServiceCallback> mCallbacks         = new RemoteCallbackList<IRemoteSpiderServiceCallback>();
    
    private final static int                               STAT_IDLE          = 0;
    private final static int                               STAT_DOWNLOAD_PAGE = 1;
    private final static int                               STAT_SCAN_PAGE     = 2;
    private final static int                               STAT_DOWNLOAD_IMG  = 3;
    private final static int                               STAT_PAUSE         = 4;
    private final static int                               STAT_COMPLETE      = 5;
    private final static int                               STAT_STOP          = 6;
    private AtomicInteger                                  state              = new AtomicInteger(
                                                                                      STAT_IDLE);
    private AtomicInteger                                  cmd                = new AtomicInteger(
                                                                                      SpiderActivity.CMD_NOTHING);
    
    private String                                         curSiteDirPath;
    private String                                         userAgent;
    
    /** The primary interface we will be calling on the service. */
    IRemoteWatchdogService                                 watchdogService    = null;
    
    private ServiceConnection                              watchdogConnection;
    
    private final IRemoteSpiderService.Stub                mBinder = new IRemoteSpiderService.Stub()
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
    
    private void watchdogInterfaceInit()
    {
        watchdogConnection = new ServiceConnection()
        {
            public void onServiceConnected(ComponentName className,
                    IBinder service)
            {
                
                watchdogService = IRemoteWatchdogService.Stub.asInterface(service);
                
                stringFromJNI("ashmem");
                
                if (!jniSpiderInit())
                {
                    stopSelfAndWatchdog();
                }
                
                spiderInit();
                
                Log.i(TAG, "onServiceConnected");
            }
            
            public void onServiceDisconnected(ComponentName className)
            {
                watchdogService = null;
                
                Log.i(TAG, "onServiceDisconnected");
                
                stopSelf();
            }
        };
    }
    
    private void startWatchdog()
    {
        Log.i(TAG, "startWatchdog");
        
        Intent watchdogIntent = new Intent(
                IRemoteWatchdogService.class.getName());
        watchdogIntent.setPackage(IRemoteWatchdogService.class.getPackage().getName());
        
        startService(watchdogIntent);
        bindService(watchdogIntent, watchdogConnection, BIND_ABOVE_CLIENT);
    }
    
    private void stopSelfAndWatchdog()
    {
        stopService(new Intent(this, WatchdogService.class));
        stopSelf();
    }
    
    private void sendCmdToWatchdog(int cmd)
    {
        Intent spiderIntent = new Intent(IRemoteWatchdogService.class.getName());
        spiderIntent.setPackage(IRemoteWatchdogService.class.getPackage().getName());
        
        Bundle bundle = new Bundle();
        bundle.putInt(SpiderActivity.BUNDLE_KEY_CMD, cmd);
        bundle.putString(SpiderActivity.BUNDLE_KEY_PRJ_PATH, curSiteDirPath);
        spiderIntent.putExtras(bundle);
        startService(spiderIntent);
    }
    
    @Override
    public void onCreate()
    {
        Log.i(TAG, "onCreate");
        watchdogInterfaceInit();
        startWatchdog();
    }
    
    @Override
    public void onDestroy()
    {
        Log.i(TAG, "onDestroy");
        // Unregister all callbacks.
        mCallbacks.kill();
        
        timerRunning = false;
        if (spider != null)
        {
            Log.i(TAG, "clearCache");
            spider.stopLoading();
            spider.clearCache(true);
            spider.destroy();
        }
        
        System.exit(0);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if (intent != null)
        {
            String url = intent.getStringExtra(SpiderActivity.BUNDLE_KEY_SOURCE_URL);
            if (url != null)
            {
                Log.i(TAG, "onStartCommand url:" + url);
                
                if ((url.startsWith("http://") || url.startsWith("https://"))
                        && srcUrl.equals(SRCURL_DEFAULT_VALUE))
                {
                    srcUrl = url;
                    try
                    {
                        srcHost = new URL(srcUrl).getHost();
                    }
                    catch (MalformedURLException e)
                    {
                        e.printStackTrace();
                        stopSelfAndWatchdog();
                    }
                    File siteDir = Utils
                            .getDirInExtSto(getString(R.string.appPackageName)
                                    + "/download/" + srcHost);
                    if (siteDir == null)
                    {
                        stopSelfAndWatchdog();
                    }
                    else
                    {
                        curSiteDirPath = siteDir.getPath();
                    }
                }
            }
            
            int cmdVal = intent.getIntExtra(SpiderActivity.BUNDLE_KEY_CMD,SpiderActivity.CMD_NOTHING);
            Log.i(TAG, "onStartCommand " + cmdVal);
            
            cmd.set(cmdVal);
            switch (cmdVal)
            {
                case SpiderActivity.CMD_CLEAR:
                    stopSelfAndWatchdog();
                break;
                case SpiderActivity.CMD_STOP_STORE:
                    sendCmdToWatchdog(SpiderActivity.CMD_STOP_STORE);
                break;
                case SpiderActivity.CMD_CONTINUE:
                    if (state.get() == STAT_PAUSE)
                    {
                        mImgDownloader.startAllThread();
                    }
                break;
                case SpiderActivity.CMD_RESTART:
                    switch(state.get())
                    {
                        case STAT_PAUSE:
                            stopSelf();
                        break;

                        case STAT_DOWNLOAD_PAGE:
                            if (urlLoadTimer.get() != 0)
                            {
                                urlLoadTimer.set(1);
                            }
                        break;
                        
                        default:
                        break;
                    }
                break;
                case SpiderActivity.CMD_PAUSE:
                    if (urlLoadTimer.get() != 0)
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
        Log.i(TAG, "getAshmemFromWatchdog name:" + name + " size:" + size);
        
        int fd = -1;
        try
        {
            ParcelFileDescriptor parcelFd = watchdogService.getAshmem(name,
                    size);
            if (parcelFd != null)
            {
                fd = parcelFd.getFd();
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
        Log.i("onTrimMemory", "level:" + level);
        
        switch (level)
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
        Log.i(TAG, "onBind:" + intent.getAction());
        
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
     * 
     */
    
    private final static String SRCURL_DEFAULT_VALUE = "about:blank";
    private String              srcUrl               = SRCURL_DEFAULT_VALUE;
    private String              srcHost;
    
    private final int           URL_TYPE_PAGE        = 0;
    private final int           URL_TYPE_IMG         = 1;
    
    private String              curPageUrl;
    
    private final static int    PARA_TOTAL           = 0;
    private final static int    PARA_PROCESSED       = 1;
    private final static int    PARA_HEIGHT          = 2;
    private final static int    PARA_PAYLOAD         = 3;
    
    private int[]               pageProcParam;
    private int[]               imgProcParam;
    
    private boolean             pageFinished         = false;
    private long                loadTimer;
    private long                loadTime;
    private long                scanTimer;
    private long                scanTime;
    private long                searchTime;
    
    private WebView             spider;
    
    private Handler             spiderHandler        = new Handler();
    private boolean             timerRunning         = true;
    private final static int    URL_TIME_OUT         = 10;
    private AtomicInteger       urlLoadTimer         = new AtomicInteger(URL_TIME_OUT);
    
    private MessageDigest       md5;
    
    private final static int    MAX_SIZE_PER_URL     = 4095;
    
    private native String stringFromJNI(String srcStr);
    
    private native boolean jniSpiderInit();
    
    private static final int JNI_OPERATE_GET = 0;
    private static final int JNI_OPERATE_ADD = 1;
    
    private native void jniAddUrl(String url, byte[] md5, int type, int[] param);
    
    private native String jniFindNextUrlToLoad(String prevUrl, byte[] md5,
            int type, int[] param);
    
    private Utils.ReadWaitLock pageProcessLock = new Utils.ReadWaitLock();
    
    private ImgDownloader      mImgDownloader  = new ImgDownloader();
    
    static
    {
        System.loadLibrary("UltimateImgSpider");
    }
    
    private synchronized String jniOperateUrl(String url, int urlType,
            int[] param, int operating)
    {
        byte[] md5Value = null;
        if (url != null)
        {
            md5Value = md5.digest(url.getBytes());
        }
        
        if (operating == JNI_OPERATE_GET)
        {
            return jniFindNextUrlToLoad(url, md5Value, urlType, param);
        }
        else if (operating == JNI_OPERATE_ADD)
        {
            jniAddUrl(url, md5Value, urlType, param);
        }
        
        return null;
    }
    
    private synchronized boolean shouldStopDownloader()
    {
        boolean ret = true;
        switch(cmd.get())
        {
            case SpiderActivity.CMD_PAUSE:
                state.set(STAT_PAUSE);
            break;
            
            case SpiderActivity.CMD_RESTART:
                state.set(STAT_STOP);
                stopSelf();
            break;
            
            case SpiderActivity.CMD_CLEAR:
                
            break;
            
            default:
                ret=false;
            break;
        }
        
        return ret;
    }
    
    
    class ImgDownloader
    {
        private static final int   IMG_DOWNLOADER_NUM = 10;
        private DownloaderThread[] downloaderThreads  = new DownloaderThread[IMG_DOWNLOADER_NUM];
        private String[] downloadingCacheFilePath     = new String[IMG_DOWNLOADER_NUM];
        private static final String CACHE_MARK        = ".cache";

        private final static int IMG_VALID_FILE_MIN   = 512 * 1024;
        private final static int IMG_VALID_WIDTH_MIN  = 200;
        private final static int IMG_VALID_HEIGHT_MIN = 200;
        
        private final static int IMG_DOWNLOAD_BLOCK   = 16 * 1024;
        
        private final static int REDIRECT_MAX         = 5;
        
        void startAllThread()
        {
            for (int i = 0; i < IMG_DOWNLOADER_NUM; i++)
            {
                downloadingCacheFilePath[i]="";
                
                if (downloaderThreads[i] != null)
                {
                    if (!downloaderThreads[i].isAlive())
                    {
                        downloaderThreads[i].start();
                    }
                }
                else
                {
                    downloaderThreads[i] = new DownloaderThread();
                    downloaderThreads[i].numId=i;
                    downloaderThreads[i].start();
                }
            }
        }
        

        private synchronized File getImgDownloadCacheFile(String imgUrl, int tid)
        {
            String[] urlSplit = imgUrl.split("/");
            String imgFileRawName=null;
            try
            {
                imgFileRawName = URLDecoder.decode(urlSplit[urlSplit.length - 1], "utf-8");
            }
            catch (UnsupportedEncodingException e)
            {
                e.printStackTrace();
            }
            
            String cacheFileName=imgFileRawName + CACHE_MARK;
            
            //Log.i(TAG, "cache file name:" + cacheFileName);
            
            
            File cacheFile = new File(curSiteDirPath + "/" + cacheFileName);
            
            int i=0;
            while(cacheFile.exists())
            {
                cacheFile = new File(curSiteDirPath + "/"
                        + "(" + i + ") " + cacheFileName);
                i++;
            }
            
            String cacheFilePath=cacheFile.getPath();
            while(true)
            {
                int h;
                for(h=0; h<IMG_DOWNLOADER_NUM; h++)
                {
                    if(downloadingCacheFilePath[h].equals(cacheFilePath))
                    {
                        break;
                    }
                }
                
                if(h<IMG_DOWNLOADER_NUM)
                {
                    i++;
                    cacheFilePath=curSiteDirPath + "/"
                            + "(" + i + ") " + cacheFileName;
                    
                }
                else
                {
                    break;
                }
            }
            downloadingCacheFilePath[tid]=cacheFilePath;
            
            Log.i(TAG, "cache file path:"+cacheFilePath);
            
            return new File(cacheFilePath);
        }
        
        private synchronized void changeFileNameAfterDownload(File file)
        {
            Log.i(TAG, "chang file name "+file.getName());
            String imgFileRawName=file.getName();
            imgFileRawName=imgFileRawName.substring(0, imgFileRawName.length()-CACHE_MARK.length());
            
            File finalFile = new File(curSiteDirPath + "/" + imgFileRawName);
            
            int i = 0;
            while(finalFile.exists())
            {
                finalFile = new File(curSiteDirPath + "/"
                        + "(" + i + ") " + imgFileRawName);
                i++;
            }
            
            file.renameTo(finalFile);
            
            Log.i(TAG, "new name "+file.getName());
        }
        
        
        class DownloaderThread extends Thread
        {
            private byte[]           cacheBuf             = new byte[IMG_VALID_FILE_MIN];
            private byte[]           blockBuf             = new byte[IMG_DOWNLOAD_BLOCK];
            
            private String           containerUrl         = null;
            private String           imgUrl               = null;
            
            public int               numId;
            
            public void run()
            {
                while (!shouldStopDownloader())
                {
                    pageProcessLock.waitIfLocked();
                    String urlSet = jniOperateUrl(imgUrl, URL_TYPE_IMG,
                            imgProcParam, JNI_OPERATE_GET);
                    if (urlSet != null)
                    {
                        Log.i(TAG, urlSet);
                        
                        String[] urls = urlSet.split(" ");
                        imgUrl = urls[0];
                        containerUrl = urls[1];
                        
                        downloadImgByUrl(imgUrl);
                        
                        spiderHandler.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                reportSpiderLog(false);
                            }
                        });
                    }
                    else
                    {
                        imgUrl = null;
                        
                        pageProcessLock.lock();
                        spiderHandler.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                findNextUrlToLoad();
                            }
                        });
                    }
                }
            }
            
            private void recvImgDataLoop(InputStream input, String url) throws IOException
            {
                int totalLen = 0;
                int cacheUsege=0;
                File imgFile=null;
                OutputStream output = null;
                
                try
                {
                    while (true)
                    {
                        int len = input.read(blockBuf);
                        if (len != -1)
                        {
                            if ((cacheUsege + len) < IMG_VALID_FILE_MIN)
                            {
                                System.arraycopy(blockBuf, 0, cacheBuf, cacheUsege, len);
                                cacheUsege += len;
                            }
                            else
                            {
                                if (output == null)
                                {
                                    imgFile=getImgDownloadCacheFile(url, numId);
                                    output = new FileOutputStream(imgFile);
                                }
                                Log.i(TAG, totalLen+" "+url);
                                output.write(cacheBuf, 0, cacheUsege);
                                System.arraycopy(blockBuf, 0, cacheBuf, 0, len);
                                cacheUsege=len;
                            }
                            
                            totalLen += len;
                        }
                        else
                        {
                            break;
                        }
                    }
                    
                    if (totalLen < IMG_VALID_FILE_MIN)
                    {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(cacheBuf, 0, totalLen, opts);
                        
                        Log.i(TAG, "size:" + totalLen + " " + opts.outWidth + "*" + opts.outHeight);
                        
                        if (opts.outHeight > IMG_VALID_HEIGHT_MIN
                                && opts.outWidth > IMG_VALID_WIDTH_MIN)
                        {
                            imgFile=getImgDownloadCacheFile(url, numId);
                            output = new FileOutputStream(imgFile);
                            output.write(cacheBuf, 0, totalLen);
                        }
                    }
                    else
                    {
                        output.write(cacheBuf, 0, cacheUsege);
                    }
                }
                finally
                {
                    if (output != null)
                    {
                        output.flush();
                        output.close();
                    }
                }
                
                if(imgFile!=null)
                {
                    changeFileNameAfterDownload(imgFile);
                }
            }
            
            private void downloadImgByUrl(String urlStr)
            {
                for(int redirectCnt=0; redirectCnt<REDIRECT_MAX; redirectCnt++)
                {
                    try
                    {
                        URL url = new URL(urlStr);
                        HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
                        
                        try
                        {
                            urlConn.setInstanceFollowRedirects(false);
                            urlConn.setConnectTimeout(30000);
                            urlConn.setReadTimeout(120000);
                            urlConn.setRequestProperty("Referer", containerUrl);
                            urlConn.setRequestProperty("User-Agent", userAgent);
                            
                            int res=urlConn.getResponseCode();
                            Log.i(TAG, res+" "+urlStr);
                            
                            if((res/100) == 3)
                            {
                                String redirUrl=urlConn.getHeaderField("Location");
                                
                                if(redirUrl!=null)
                                {
                                    urlStr=redirUrl.replaceAll(" ", "%20");
                                }
                                else
                                {
                                    break;
                                }
                                //int fileNamePos=urlStr.lastIndexOf("/")+1;
                                //urlStr=urlStr.substring(0, fileNamePos)+
                                //        URLEncoder.encode(urlStr.substring(fileNamePos), "utf_8");
                            }
                            else
                            {
                                if (res == 200)
                                {
                                    InputStream input = urlConn.getInputStream();
                                    recvImgDataLoop(input, urlStr);
                                }
                                break;
                            }
                        }
                        finally
                        {
                            if (urlConn != null)
                            {
                                urlConn.disconnect();
                            }
                        }
                        
                    }
                    catch (MalformedURLException e)
                    {
                        e.printStackTrace();
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    private void reportSpiderLog(boolean isCompleted)
    {
        String log = "";
        
        if (isCompleted)
        {
            log = "siteScanCompleted\r\n";
        }
        
        Runtime rt = Runtime.getRuntime();
        log = log + "VM:" + (rt.totalMemory() >> 20) + "M Native:"
                + (Debug.getNativeHeapSize() >> 20) + "M pic:"
                + imgProcParam[PARA_PAYLOAD] + "/"
                + imgProcParam[PARA_PROCESSED] + "/" + imgProcParam[PARA_TOTAL]
                + "|" + imgProcParam[PARA_HEIGHT] + " page:"
                + pageProcParam[PARA_PROCESSED] + "/"
                + pageProcParam[PARA_TOTAL] + "|" + pageProcParam[PARA_HEIGHT]
                + " loadTime:" + loadTime + " scanTime:" + scanTime
                + " searchTime:" + searchTime + "\r\n" + curPageUrl;
        
        // Log.i(TAG, log);
        
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
    
    // javascript回调不在主线程。
    private void scanPageWithJS()
    {
        // 扫描页面耗时较少，因此此处不检测暂停或者停止命令
        scanTimer = System.currentTimeMillis();
        spider.loadUrl("javascript:" 
                + "var i;"
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
                // Log.i(TAG, "onPageFinished " + url + " loadTime:" +
                // loadTime);
                
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
                Log.i(TAG, "onPageStarted " + url);
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
                Log.i(TAG, failingUrl + " ReceivedError " + errorCode + "  "
                        + description);
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
        
        userAgent = ParaConfig.getUserAgent(this);
        
        WebSettings setting = spider.getSettings();
        setting.setUserAgentString(userAgent);
        
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
        searchTime = System.currentTimeMillis();
        curPageUrl = jniOperateUrl(curPageUrl, URL_TYPE_PAGE, pageProcParam,
                JNI_OPERATE_GET);
        searchTime = System.currentTimeMillis() - searchTime;
        Log.i(TAG, "loading:" + curPageUrl);
        if (curPageUrl == null)
        {
            state.set(STAT_COMPLETE);
            // Log.i(TAG, "site scan complete");
            reportSpiderLog(true);
        }
        else
        {
            state.set(STAT_DOWNLOAD_PAGE);
            spiderLoadUrl(curPageUrl);
            reportSpiderLog(false);
        }
    }
    
    private void spiderInit()
    {
        spiderWebViewInit();
        
        pageProcParam = new int[3];
        imgProcParam = new int[4];
        
        new TimerThread().start();
        
        try
        {
            md5 = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }
        
        jniOperateUrl(srcUrl, URL_TYPE_PAGE, pageProcParam, JNI_OPERATE_ADD);
        
        pageProcessLock.lock();
        findNextUrlToLoad();
        
        mImgDownloader.startAllThread();
    }
    
    private class TimerThread extends Thread
    {
        private final int TIMER_INTERVAL = 1000;
        
        public void run()
        {
            while (timerRunning)
            {
                // Log.i(TAG, "Timer");
                
                if (urlLoadTimer.get() != 0)
                {
                    if (urlLoadTimer.decrementAndGet() == 0)
                    {
                        spiderHandler.post(new Runnable()
                        {
                            
                            @Override
                            public void run()
                            {
                                Log.i(TAG, "Load TimeOut!!");
                                spider.stopLoading();
                                scanPageWithJS();
                            }
                        });
                    }
                }
                
                try
                {
                    sleep(TIMER_INTERVAL);
                }
                catch (InterruptedException e)
                {
                    // Log.e(TAG,e.toString());
                }
            }
        }
    }
    
    private void spiderLoadUrl(String url)
    {
        // Log.i(TAG, "spiderLoadUrl:"+url);
        spider.loadUrl(url);
        urlLoadTimer.set(URL_TIME_OUT);
    }
    
    @JavascriptInterface
    public void recvImgUrl(String imgUrlSet)
    {
        // Log.i(TAG, "imgUrl:"+imgUrl);
        
        String[] list = imgUrlSet.split(" ");
        
        // Log.i(TAG, "length:"+list.length);
        
        for (String imgUrl : list)
        {
            if ((imgUrl.startsWith("http://") || imgUrl.startsWith("https://"))
                    && (imgUrl.endsWith(".jpg") || imgUrl.endsWith(".png") || imgUrl
                            .endsWith(".gif"))
                    && imgUrl.length() < MAX_SIZE_PER_URL)
            {
                jniOperateUrl(imgUrl, URL_TYPE_IMG, imgProcParam,
                        JNI_OPERATE_ADD);
            }
        }
    }
    
    @JavascriptInterface
    public void recvPageUrl(String pageUrlSet)
    {
        // Log.i(TAG, "pageUrl:"+pageUrl);
        
        String[] list = pageUrlSet.split(" ");
        
        // Log.i(TAG, "length:"+list.length);
        
        for (String pageUrl : list)
        {
            try
            {
                URL url = new URL(pageUrl);
                
                if ((pageUrl.startsWith("http://") || pageUrl
                        .startsWith("https://"))
                        && (url.getHost().equals(srcHost))
                        && (url.getRef() == null)
                        && (pageUrl.length() < MAX_SIZE_PER_URL))
                {
                    jniOperateUrl(pageUrl, URL_TYPE_PAGE, pageProcParam,
                            JNI_OPERATE_ADD);
                }
            }
            catch (MalformedURLException e)
            {
                // Log.e(TAG,e.toString());
            }
        }
    }
    
    @JavascriptInterface
    public void onCurPageScaned()
    {
        Log.i(TAG, "onCurPageScaned");
        scanTime = System.currentTimeMillis() - scanTimer;
        
        pageProcessLock.unlock();
    }
    
}