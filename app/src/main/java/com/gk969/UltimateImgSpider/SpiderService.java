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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import com.gk969.Utils.Utils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.TrafficStats;
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
    private final static int                               STAT_WORKING       = 1;
    private final static int                               STAT_PAUSE         = 2;
    private final static int                               STAT_COMPLETE      = 3;
    private final static int                               STAT_STOP          = 4;
    
    private AtomicInteger                                  state              = new AtomicInteger(STAT_IDLE);
    private AtomicInteger                                  cmd                = new AtomicInteger(SpiderActivity.CMD_NOTHING);
    
    private String                                         curSiteDirPath;
    private String                                         userAgent;
    
    /** The primary interface we will be calling on the service. */
    IRemoteWatchdogService                                 watchdogService    = null;
    
    private ServiceConnection                              watchdogConnection;

    private IRemoteWatchdogServiceCallback                 watchdogCallback;
    
    private final IRemoteSpiderService.Stub                mBinder = new IRemoteSpiderService.Stub()
    {
        public void registerCallback(IRemoteSpiderServiceCallback cb)
        {
            if (cb != null)
            {
                mCallbacks.register(cb);
            }
        }
        
        public void unregisterCallback(IRemoteSpiderServiceCallback cb)
        {
            if (cb != null)
            {
                mCallbacks.unregister(cb);
            }
        }
    };
    
    private void watchdogInterfaceInit()
    {
        watchdogCallback=new IRemoteWatchdogServiceCallback.Stub()
        {
            public void projectPathRecved()
            {
                Log.i(TAG, "projectPathRecved");

                spiderHandler.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        startSpider();
                    }
                });
            }
        };

        watchdogConnection = new ServiceConnection()
        {
            public void onServiceConnected(ComponentName className,
                    IBinder service)
            {
                Log.i(TAG, "onServiceConnected");
                
                watchdogService = IRemoteWatchdogService.Stub.asInterface(service);

                try
                {
                    watchdogService.registerCallback(watchdogCallback);

                    sendCmdToWatchdog(SpiderActivity.CMD_START);
                }
                catch (RemoteException e)
                {

                }

            }
            
            public void onServiceDisconnected(ComponentName className)
            {
                watchdogService = null;
                
                Log.i(TAG, "onServiceDisconnected");
                
                stopSelf();
            }
        };
    }

    private void startSpider()
    {
        stringFromJNI("ashmem");


        pageProcParam = new int[PAGE_PARA_NUM];
        imgProcParam = new int[IMG_PARA_NUM];


        if (!jniSpiderInit(imgProcParam, pageProcParam, ImgDownloader.IMG_DOWNLOADER_NUM))
        {
            stopSelfAndWatchdog();
        }

        spiderInit();
    }

    private void startWatchdog()
    {
        Log.i(TAG, "startWatchdog");
        
        Intent watchdogIntent = new Intent(IRemoteWatchdogService.class.getName());
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
        Intent watchdogIntent = new Intent(IRemoteWatchdogService.class.getName());
        watchdogIntent.setPackage(IRemoteWatchdogService.class.getPackage().getName());
        
        Bundle bundle = new Bundle();
        bundle.putInt(SpiderActivity.BUNDLE_KEY_CMD, cmd);
        bundle.putString(SpiderActivity.BUNDLE_KEY_PRJ_PATH, curSiteDirPath);
        watchdogIntent.putExtras(bundle);
        startService(watchdogIntent);
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

        if(cmd.get()==SpiderActivity.CMD_CLEAR)
        {
            sendCmdToWatchdog(SpiderActivity.CMD_CLEAR);
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
                    File siteDir = Utils.getDirInExtSto(getString(R.string.appPackageName)
                            + "/download/" + srcHost);
                    if (siteDir == null)
                    {
                        stopSelfAndWatchdog();
                    }
                    else
                    {
                        curSiteDirPath = siteDir.getPath();

                        watchdogInterfaceInit();
                        startWatchdog();
                    }
                }
            }
            
            int cmdVal = intent.getIntExtra(SpiderActivity.BUNDLE_KEY_CMD,SpiderActivity.CMD_NOTHING);
            Log.i(TAG, "onStartCommand:" + cmdVal+" state:"+state.get());
            
            cmd.set(cmdVal);
            switch (cmdVal)
            {
                case SpiderActivity.CMD_CLEAR:
                    state.set(STAT_STOP);
                    stopSelf();
                break;

                case SpiderActivity.CMD_STOP_STORE:
                    state.set(STAT_STOP);
                    jniDataLock.lock();
                    sendCmdToWatchdog(SpiderActivity.CMD_STOP_STORE);
                break;

                case SpiderActivity.CMD_CONTINUE:
                    if (state.get() == STAT_PAUSE)
                    {
                        state.set(STAT_WORKING);
                        mImgDownloader.startAllThread();
                    }
                break;

                case SpiderActivity.CMD_RESTART:
                    if(state.get()==STAT_WORKING)
                    {
                        state.set(STAT_STOP);
                        if (urlLoadTimer.get() != 0)
                        {
                            spider.stopLoading();
                            scanPageWithJS();
                        }


                        new Thread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                Log.i(TAG, "prepare restart. pageProcessLock "+pageProcessLock.isLocked.get());
                                pageProcessLock.lock();
                                Log.i(TAG, "pageProcessLock pass");
                                jniDataLock.lock();
                                Log.i(TAG, "jniDataLock pass");

                                Utils.handlerPostUntilSuccess(spiderHandler, new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        stopSelf();
                                    }
                                });
                            }
                        }).start();
                    }
                    else
                    {
                        stopSelf();
                    }
                break;

                case SpiderActivity.CMD_PAUSE:
                    state.set(STAT_PAUSE);
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
            ParcelFileDescriptor parcelFd = watchdogService.getAshmem(name, size);
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
    private final static int    PARA_DOWNLOAD        = 4;

    private final static int    PAGE_PARA_NUM        = 3;
    private final static int    IMG_PARA_NUM         = 5;
    
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
    
    private MessageDigest       md5ForPage;
    
    private final static int    MAX_SIZE_PER_URL     = 4095;
    private final static int    MAX_SIZE_PER_TITLE   = 255;
    
    private native String stringFromJNI(String srcStr);
    
    private native boolean jniSpiderInit(int[] imgPara, int[] pagePara, int imgDownloaderNum);
    
    private static final int JNI_OPERATE_GET = 0;
    private static final int JNI_OPERATE_ADD = 1;
    
    private native void jniAddUrl(String url, byte[] md5, int type, int[] param);

    private native String jniFindNextPageUrl(int[] param);
    private native String jniFindNextImgUrl(byte[] md5, int[] param);
    private native void jniSaveImgStorageInfo(int imgUrlAddr, int PageUrlAddr);
    private native void jniRecvPageTitle(String curPageTitle);
    
    private Utils.ReadWaitLock pageProcessLock = new Utils.ReadWaitLock();
    private ReentrantLock jniDataLock = new ReentrantLock();
    
    private ImgDownloader      mImgDownloader  = new ImgDownloader();

    static
    {
        System.loadLibrary("UltimateImgSpider");
    }
    
    private String jniOperateUrl(String url, byte[] md5Value, int urlType,
            int[] param, int operating)
    {
        //Log.i(TAG, (operating==JNI_OPERATE_GET?"get":"add")+" "+
        //        (urlType==URL_TYPE_PAGE?"page":"img")+" "+url);

        jniDataLock.lock();

        String ret=null;

        switch (operating)
        {
            case JNI_OPERATE_GET:
                ret = urlType == URL_TYPE_PAGE ? jniFindNextPageUrl(param) : jniFindNextImgUrl(md5Value, param);
                break;

            case JNI_OPERATE_ADD:
                jniAddUrl(url, md5Value, urlType, param);
                break;

            default:
                break;
        }
        jniDataLock.unlock();

        return ret;
    }

    class ImgDownloader
    {
        private static final int   MAX_IMG_FILE_PER_DIR=500;
        private static final int   IMG_DOWNLOADER_NUM = 10;
        private DownloaderThread[] downloaderThreads  = new DownloaderThread[IMG_DOWNLOADER_NUM];
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
                if(downloaderThreads[i]==null)
                {
                    downloaderThreads[i] = new DownloaderThread(i, null);
                }
                else if(!downloaderThreads[i].isAlive())
                {
                    String lastUrl=downloaderThreads[i].imgUrl;
                    downloaderThreads[i] = new DownloaderThread(i, lastUrl);
                }
            }
        }
        

        private synchronized File newImgDownloadCacheFile(String imgUrl)
        {
            int imgIndex=imgProcParam[PARA_DOWNLOAD];

            File dir=new File(curSiteDirPath+String.format("/%d", imgIndex/MAX_IMG_FILE_PER_DIR));
            if(!dir.exists())
            {
                dir.mkdir();
            }

            String cacheFilePath=String.format("/%d/%03d",
                    imgIndex/MAX_IMG_FILE_PER_DIR, imgIndex%MAX_IMG_FILE_PER_DIR)
                    +imgUrl.substring(imgUrl.lastIndexOf(".")) + CACHE_MARK;
            
            Log.i(TAG, "cache file name:" + cacheFilePath);

            imgProcParam[PARA_DOWNLOAD]++;

            return new File(curSiteDirPath + cacheFilePath);
        }
        
        private void changeFileNameAfterDownload(File file, int imgUrlAddr, int PageUrlAddr)
        {
            Log.i(TAG, "change file "+file.getPath());
            String path=file.getPath();
            String fullPathInProjectDir=path.substring(curSiteDirPath.length());
            fullPathInProjectDir=fullPathInProjectDir.substring(0, fullPathInProjectDir.length()-CACHE_MARK.length());
            
            File finalFile = new File(curSiteDirPath + fullPathInProjectDir);

            Log.i(TAG, "final file "+finalFile.getPath());

            file.renameTo(finalFile);

            jniDataLock.lock();
            jniSaveImgStorageInfo(imgUrlAddr, PageUrlAddr);
            jniDataLock.unlock();
        }
        
        
        class DownloaderThread extends Thread
        {
            private byte[] cacheBuf      = new byte[IMG_VALID_FILE_MIN];
            private byte[] blockBuf      = new byte[IMG_DOWNLOAD_BLOCK];

            private String containerUrl  = null;
            private String imgUrl        = null;


            private MessageDigest       md5ForImg;

            public int     threadIndex;

            private long    imgUrlJniAddr;
            private long    pageUrlJniAddr;

            public DownloaderThread(int index, String url)
            {
                imgUrl=url;
                threadIndex=index;
                try
                {
                    md5ForImg = MessageDigest.getInstance("md5");
                }
                catch (NoSuchAlgorithmException e)
                {
                    e.printStackTrace();
                }

                start();
            }

            public void run()
            {
                Log.i(TAG, "thread "+threadIndex+" start");
                while (true)
                {
                    pageProcessLock.waitIfLocked();

                    Log.i(TAG, "thread " + threadIndex + " work");

                    //Log.i(TAG, "state:"+state);
                    if(state.get()!=STAT_WORKING)
                    {
                        break;
                    }

                    byte[] curImgUrlMd5=(imgUrl==null)?null:md5ForImg.digest(imgUrl.getBytes());
                    String urlSet = jniOperateUrl(imgUrl, curImgUrlMd5,
                            URL_TYPE_IMG, imgProcParam, JNI_OPERATE_GET);
                    if (urlSet != null)
                    {
                        Log.i(TAG, urlSet);
                        
                        String[] urls = urlSet.split(" ");
                        imgUrl = urls[0];
                        containerUrl = urls[2];

                        imgUrlJniAddr=Long.parseLong(urls[1], 16);
                        pageUrlJniAddr=Long.parseLong(urls[3], 16);
                        
                        downloadImgByUrl(imgUrl);

                        spiderHandler.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                reportSpiderLog();
                            }
                        });
                    }
                    else
                    {
                        imgUrl = null;
                        
                        pageProcessLock.lock();
                        if(state.get()!=STAT_WORKING)
                        {
                            pageProcessLock.unlock();
                            break;
                        }

                        //Log.i(TAG, "post findNextUrlToLoad");
                        Utils.handlerPostUntilSuccess(spiderHandler, new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                findNextUrlToLoad();
                            }
                        });
                    }
                }

                Log.i(TAG, "thread "+threadIndex+" stop");
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
                                    imgFile=newImgDownloadCacheFile(url);
                                    output = new FileOutputStream(imgFile);
                                }
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

                    Log.i(TAG, "totalLen "+(totalLen/1024)+"K "+url);
                    if (totalLen < IMG_VALID_FILE_MIN)
                    {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(cacheBuf, 0, totalLen, opts);
                        
                        Log.i(TAG, "size:" + totalLen + " " + opts.outWidth + "*" + opts.outHeight);
                        
                        if (opts.outHeight > IMG_VALID_HEIGHT_MIN
                                && opts.outWidth > IMG_VALID_WIDTH_MIN)
                        {
                            imgFile=newImgDownloadCacheFile(url);
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
                        output.close();
                    }
                }
                
                if(imgFile!=null)
                {
                    changeFileNameAfterDownload(imgFile, (int)imgUrlJniAddr, (int)pageUrlJniAddr);
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
                            urlConn.setConnectTimeout(5000);
                            urlConn.setReadTimeout(120000);
                            urlConn.setRequestProperty("Referer", containerUrl);
                            urlConn.setRequestProperty("User-Agent", userAgent);
                            
                            int res=urlConn.getResponseCode();
                            //Log.i(TAG, "response "+res+" "+urlStr);
                            
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
    
    private void reportSpiderLog()
    {
        String log = "";
        
        if (state.get()==STAT_COMPLETE)
        {
            log = "siteScanCompleted\r\n";
        }

        float fNetTraffic=netTrafficPerSec.get();
        fNetTraffic/=1024;
        
        Runtime rt = Runtime.getRuntime();
        log = log + "VM:" + (rt.totalMemory() >> 20) + "M Native:"
                + (Debug.getNativeHeapSize() >> 20) + "M pic:"
                + imgProcParam[PARA_PAYLOAD] + "/" + imgProcParam[PARA_DOWNLOAD] + "/"
                + imgProcParam[PARA_PROCESSED] + "/" + imgProcParam[PARA_TOTAL]
                + "|" + imgProcParam[PARA_HEIGHT] + " page:"
                + pageProcParam[PARA_PROCESSED] + "/"
                + pageProcParam[PARA_TOTAL] + "|" + pageProcParam[PARA_HEIGHT]
                + " loadTime:" + loadTime + " scanTime:" + scanTime
                + " searchTime:" + searchTime
                + " netTraffic:" + String.format("%03.1f", fNetTraffic) + "KB/s"
                + "\r\n" + curPageUrl;
        
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

        reportStatusTimer.set(0);
    }
    
    // javascript回调不在主线程。
    private void scanPageWithJS()
    {
        String title=spider.getTitle();
        if(title!=null)
        {
            if (title.length() > MAX_SIZE_PER_TITLE)
            {
                title.substring(0, MAX_SIZE_PER_TITLE);
            }
            jniRecvPageTitle(title);
        }

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
                //Log.i(TAG, "onPageFinished " + url + " loadTime:" +loadTime+" tmr:"+urlLoadTimer.get());
                //Log.i(TAG, "curPageUrl "+curPageUrl);
                if (!pageFinished)
                {
                    pageFinished = true;
                    if((curPageUrl!=null)&&(urlLoadTimer.get()!=0))
                    {
                        if (curPageUrl.equals(url))
                        {
                            urlLoadTimer.set(0);

                            //Log.i(TAG, "scanPageWithJS");
                            scanPageWithJS();
                        }
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

                //Log.i(TAG, "shouldInterceptRequest "+curPageUrl+" "+url);

                if(curPageUrl!=null)
                {
                    if (!curPageUrl.equals(url))
                    {
                        response = new WebResourceResponse("image/png", "UTF-8",
                                null);
                    }
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
        
        userAgent = ParaConfig.getUserAgent(getApplicationContext());
        
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
        curPageUrl = jniOperateUrl(spider.getTitle(), null, URL_TYPE_PAGE, pageProcParam,
                JNI_OPERATE_GET);
        searchTime = System.currentTimeMillis() - searchTime;
        //Log.i(TAG, "loading:" + curPageUrl);
        if (curPageUrl == null)
        {
            state.set(STAT_COMPLETE);
            Log.i(TAG, "site scan complete");

            pageProcessLock.unlock();
        }
        else
        {
            Log.i(TAG, "new page url valid");
            spiderLoadUrl(curPageUrl);

        }

        reportSpiderLog();
    }
    
    private void spiderInit()
    {
        spiderWebViewInit();

        new TimerThread().start();
        
        try
        {
            md5ForPage = MessageDigest.getInstance("md5");
        }
        catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }

        jniOperateUrl(srcUrl, md5ForPage.digest(srcUrl.getBytes()), URL_TYPE_PAGE, pageProcParam, JNI_OPERATE_ADD);

        state.set(STAT_WORKING);
        pageProcessLock.lock();
        findNextUrlToLoad();
        
        mImgDownloader.startAllThread();
    }

    private int netTrafficCalcTimer=0;
    private static final int NET_TRAFFIC_CALC_INTVAL=2;
    private long netTraffic=0;
    private AtomicLong netTrafficPerSec = new AtomicLong(0);
    private long lastTraffic=0;

    private void refreshTrafficPerSec()
    {
        if(lastTraffic!=0)
        {
            netTrafficPerSec.set((netTraffic-lastTraffic)/NET_TRAFFIC_CALC_INTVAL);
        }

        lastTraffic=netTraffic;
        netTraffic=getNetTraffic();
    }

    private long getNetTraffic()
    {
        try
        {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(getPackageName(),
                    PackageManager.GET_ACTIVITIES);
            netTraffic=TrafficStats.getUidRxBytes(appInfo.uid)+TrafficStats.getUidTxBytes(appInfo.uid);
        } catch (PackageManager.NameNotFoundException e)
        {
            e.printStackTrace();
        }

        return netTraffic;
    }

    private static final int REPORT_STATUS_MAX_INTVAL=2;
    private AtomicInteger reportStatusTimer = new AtomicInteger(0);
    private class TimerThread extends Thread
    {
        private final int TIMER_INTERVAL = 1000;
        
        public void run()
        {
            while (timerRunning)
            {
                netTrafficCalcTimer++;
                if(netTrafficCalcTimer==NET_TRAFFIC_CALC_INTVAL)
                {
                    refreshTrafficPerSec();
                    netTrafficCalcTimer = 0;
                }

                if(reportStatusTimer.getAndIncrement()==REPORT_STATUS_MAX_INTVAL)
                {
                    reportStatusTimer.set(0);
                    spiderHandler.post(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            reportSpiderLog();
                        }
                    });
                }

                // Log.i(TAG, "Timer");
                
                if (urlLoadTimer.get() != 0)
                {
                    if (urlLoadTimer.decrementAndGet() == 0)
                    {
                        Utils.handlerPostUntilSuccess(spiderHandler, new Runnable()
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
        Log.i(TAG, "spiderLoadUrl:"+url);
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
                jniOperateUrl(imgUrl, md5ForPage.digest(imgUrl.getBytes()), URL_TYPE_IMG, imgProcParam,
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
                    jniOperateUrl(pageUrl, md5ForPage.digest(pageUrl.getBytes()), URL_TYPE_PAGE, pageProcParam,
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