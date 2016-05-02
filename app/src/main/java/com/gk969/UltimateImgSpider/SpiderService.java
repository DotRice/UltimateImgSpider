package com.gk969.UltimateImgSpider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import com.gk969.Utils.Utils;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class SpiderService extends Service
{
    private final String TAG = "SpiderService";

    final RemoteCallbackList<IRemoteSpiderServiceCallback> mCallbacks = new RemoteCallbackList<IRemoteSpiderServiceCallback>();

    private final static int STAT_IDLE = 0;
    private final static int STAT_WORKING = 1;
    private final static int STAT_PAUSE = 2;
    private final static int STAT_COMPLETE = 3;
    private final static int STAT_STOP = 4;
    private final static int STAT_PAUSE_ON_START = 5;

    private AtomicInteger state = new AtomicInteger(STAT_IDLE);

    private String projectPath;
    private String projectCachePath;

    private String userAgent;

    /**
     * The primary interface we will be calling on the service.
     */
    IRemoteWatchdogService watchdogService = null;

    private ServiceConnection watchdogConnection;

    private IRemoteWatchdogServiceCallback watchdogCallback;

    private final IRemoteSpiderService.Stub mBinder = new IRemoteSpiderService.Stub()
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
        watchdogCallback = new IRemoteWatchdogServiceCallback.Stub()
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

            public void projectDataSaved()
            {
                Log.i(TAG, "projectDataSaved");
                jniDataLock.unlock();
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

                    sendCmdToWatchdog(StaticValue.CMD_START);
                } catch (RemoteException e)
                {
                    e.printStackTrace();
                }

            }

            public void onServiceDisconnected(ComponentName className)
            {
                watchdogService = null;

                Log.i(TAG, "onServiceDisconnected");
            }
        };
    }

    private void startSpider()
    {
        stringFromJNI("ashmem");


        pageProcParam = new long[StaticValue.PAGE_PARA_NUM];
        imgProcParam = new long[StaticValue.IMG_PARA_NUM];


        if (jniSpiderInit(imgProcParam, pageProcParam))
        {
            spiderInit();
        }
        else
        {
            stopSelfAndWatchdog();
        }
    }


    private boolean seviceBindSuccess = false;

    private void startAndBindWatchdog()
    {
        Log.i(TAG, "startAndBindWatchdog");

        Intent watchdogIntent = new Intent(IRemoteWatchdogService.class.getName());
        watchdogIntent.setPackage(IRemoteWatchdogService.class.getPackage().getName());

        startService(watchdogIntent);
        seviceBindSuccess = bindService(watchdogIntent, watchdogConnection, BIND_ABOVE_CLIENT);
    }

    private void unbindWatchdog()
    {
        if (watchdogService != null)
        {
            try
            {
                watchdogService.unregisterCallback(watchdogCallback);
            } catch (RemoteException e)
            {
                // There is nothing special we need to do if the service
                // has crashed.
            }

            // Detach our existing connection.

            if (seviceBindSuccess)
            {
                unbindService(watchdogConnection);
                Log.i(TAG, "unbind unbindWatchdog");
                seviceBindSuccess = false;
            }
        }
    }

    private void stopSelfAndWatchdog()
    {
        unbindWatchdog();
        stopService(new Intent(this, WatchdogService.class));
        stopSelf();
    }

    private void sendCmdToWatchdog(int cmd)
    {
        Log.i(TAG, "sendCmdToWatchdog " + cmd);
        Intent watchdogIntent = new Intent(IRemoteWatchdogService.class.getName());
        watchdogIntent.setPackage(IRemoteWatchdogService.class.getPackage().getName());

        Bundle bundle = new Bundle();
        bundle.putInt(StaticValue.BUNDLE_KEY_CMD, cmd);
        bundle.putString(StaticValue.BUNDLE_KEY_PRJ_PATH, projectPath);
        watchdogIntent.putExtras(bundle);
        startService(watchdogIntent);
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        Log.i(TAG, "onCreate");
    }

    private void stopSpider()
    {
        timerRunning.set(false);
        if (spider != null)
        {
            Log.i(TAG, "clearCache");
            spider.stopLoading();
            spider.clearCache(true);
            spider.destroy();
            spider = null;
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        Log.i(TAG, "onDestroy");
        // Unregister all callbacks.
        mCallbacks.kill();

        stopSpider();

        Utils.deleteDir(projectCachePath);

        System.exit(0);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if (intent != null)
        {
            int cmdVal = intent.getIntExtra(StaticValue.BUNDLE_KEY_CMD, StaticValue.CMD_NOTHING);
            Log.i(TAG, "onStartCommand:" + cmdVal + " state:" + state.get());

            switch (cmdVal)
            {
                case StaticValue.CMD_START:
                {
                    int prevState = state.get();
                    state.set(STAT_WORKING);
                    if (prevState == STAT_PAUSE)
                    {
                        mImgDownloader.startAllThread();
                    }
                    break;
                }

                case StaticValue.CMD_PAUSE_ON_START:
                {
                    state.set(STAT_PAUSE_ON_START);
                    break;
                }

                case StaticValue.CMD_JUST_STOP:
                {
                    state.set(STAT_STOP);
                    stopSelfAndWatchdog();
                    break;
                }

                case StaticValue.CMD_STOP_STORE:
                {
                    state.set(STAT_STOP);
                    jniDataLock.lock();
                    unbindWatchdog();
                    sendCmdToWatchdog(StaticValue.CMD_STOP_STORE);
                    stopSelf();
                    break;
                }

                case StaticValue.CMD_RESTART:
                {
                    if (state.get() == STAT_WORKING)
                    {
                        state.set(STAT_STOP);

                        if (urlLoadTimer.get() != 0)
                        {
                            spider.stopLoading();
                            scanPageWithJS();
                        }


                        unbindWatchdog();

                        //Prevent locks block main thread of service
                        new Thread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                Log.i(TAG, "prepare restart. pageProcessLock " +
                                        pageProcessLock.isLocked.get());
                                pageProcessLock.lock();
                                Log.i(TAG, "pageProcessLock pass");
                                jniDataLock.lock();
                                Log.i(TAG, "jniDataLock pass");

                                spiderHandler.post(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        Log.i(TAG, "stopSelf");
                                        stopSelf();
                                    }
                                });

                            }
                        }).start();
                    }
                    else if (state.get() != STAT_IDLE)
                    {
                        stopSelf();
                    }
                    break;
                }

                case StaticValue.CMD_PAUSE:
                {
                    state.set(STAT_PAUSE);
                    break;
                }

                default:
                    break;
            }


            String url = intent.getStringExtra(StaticValue.BUNDLE_KEY_SOURCE_URL);
            if (url != null)
            {
                Log.i(TAG, "onStartCommand url:" + url);

                if((url.startsWith("http://") || url.startsWith("https://")) && srcUrl == null)
                {
                    srcUrl = url;
                }
            }

            String host = intent.getStringExtra(StaticValue.BUNDLE_KEY_PROJECT_HOST);
            if (host != null)
            {
                Log.i(TAG, "onStartCommand host:" + host);
                if(srcHost==null)
                {
                    srcHost = host;
                    File siteDir = Utils.getDirInExtSto(getString(R.string.appPackageName) + "/" + srcHost);
                    if (siteDir == null)
                    {
                        stopSelfAndWatchdog();
                    }
                    else
                    {
                        projectPath = siteDir.getPath();

                        projectCachePath = projectPath + "/cache/";
                        File cacheDir = new File(projectCachePath);
                        if (!cacheDir.isDirectory())
                        {
                            cacheDir.mkdir();
                        }

                        watchdogInterfaceInit();
                        startAndBindWatchdog();
                    }
                }
            }
        }

        return START_NOT_STICKY;
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
        } catch (RemoteException e)
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
        Log.i(TAG, "onLowMemory");
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

    private String srcUrl;
    private String srcHost;

    private final int URL_TYPE_PAGE = 0;
    private final int URL_TYPE_IMG = 1;

    private String curPageUrl;

    private long[] pageProcParam;
    private long[] imgProcParam;

    private boolean pageFinished = false;
    private long loadTimer;
    private long loadTime;
    private long scanTimer;
    private long scanTime;
    private long searchTime;

    private WebView spider;

    private Handler spiderHandler = new Handler();
    private AtomicBoolean timerRunning = new AtomicBoolean(true);
    private final static int URL_TIME_OUT = 10;
    private AtomicInteger urlLoadTimer = new AtomicInteger(0);

    private MessageDigest md5ForPage;

    private final static int MAX_SIZE_PER_URL = 4095;
    private final static int MAX_SIZE_PER_TITLE = 255;

    private native String stringFromJNI(String srcStr);

    private native boolean jniSpiderInit(long[] imgPara, long[] pagePara);

    private native void jniSetSrcPageUrl(String pageUrl, byte[] srcPageUrlMd5, long[] pagePara);

    private native int jniAddUrl(String url, byte[] md5, int type, long[] param);

    private native String jniFindNextPageUrl(long[] param);

    private native String jniFindNextImgUrl(int lastImgUrlAddr, long[] param, boolean justDeleteCurNode);

    private native void jniSaveImgStorageInfo(int imgUrlAddr, int PageUrlAddr, long[] imgParam, int imgFileSize);

    private native void jniRecvPageTitle(String curPageTitle);

    private Utils.ReadWaitLock pageProcessLock = new Utils.ReadWaitLock();
    private Utils.ReadWaitLock jniDataLock = new Utils.ReadWaitLock();
    private ReentrantLock imgFileLock = new ReentrantLock();
    private ReentrantLock pageGetLock=new ReentrantLock();

    private ImgDownloader mImgDownloader = new ImgDownloader();

    static
    {
        System.loadLibrary("UltimateImgSpider");
    }

    class ImgDownloader
    {
        private static final int IMG_DOWNLOADER_NUM = 10;
        private DownloaderThread[] downloaderThreads = new DownloaderThread[IMG_DOWNLOADER_NUM];
        private static final String CACHE_MARK = ".cache";

        private final static int IMG_VALID_FILE_MIN = 512 * 1024;
        private final static int IMG_VALID_WIDTH_MIN = 200;
        private final static int IMG_VALID_HEIGHT_MIN = 200;

        private final static int SAVE_PROJECT_DATA = 50;


        private final static int IMG_DOWNLOAD_BLOCK = 16 * 1024;

        private final static int REDIRECT_MAX = 5;

        void startAllThread()
        {
            Log.i(TAG, "startAllThread");
            for (int i = 0; i < IMG_DOWNLOADER_NUM; i++)
            {
                if (downloaderThreads[i] == null)
                {
                    downloaderThreads[i] = new DownloaderThread(i);
                }
                else
                {
                    if (!downloaderThreads[i].isAlive())
                    {
                        downloaderThreads[i] = new DownloaderThread(i);
                    }
                    else
                    {
                        downloaderThreads[i].exitLock.lock();
                        if(downloaderThreads[i].readyToExit)
                        {
                            downloaderThreads[i] = new DownloaderThread(i);
                        }
                        else
                        {
                            downloaderThreads[i].exitLock.unlock();
                        }
                    }
                }
            }
        }

        class DownloaderThread extends Thread
        {
            private byte[] cacheBuf = new byte[IMG_VALID_FILE_MIN];
            private byte[] blockBuf = new byte[IMG_DOWNLOAD_BLOCK];

            private String containerUrl = null;
            private String imgUrl = null;

            public int threadIndex;

            private final static long URL_JNIADDR_INVALID = 0xFFFFFFFF;

            private long imgUrlJniAddr = URL_JNIADDR_INVALID;
            private long containerUrlJniAddr = URL_JNIADDR_INVALID;

            public ReentrantLock exitLock=new ReentrantLock();
            public volatile boolean readyToExit=false;

            public DownloaderThread(int index)
            {
                super("DownloaderThread " + index);
                threadIndex = index;
                setDaemon(true);
                start();
            }

            public void run()
            {
                Log.i(TAG, "thread " + threadIndex + " start");
                while (true)
                {
                    pageProcessLock.waitIfLocked();

                    Log.i(TAG, "thread " + threadIndex + " work");
                    //Log.i(TAG, "state:"+state);

                    exitLock.lock();
                    if (state.get() != STAT_WORKING)
                    {
                        jniDataLock.lock();
                        Log.i(TAG, "save img download");
                        jniFindNextImgUrl((int) imgUrlJniAddr, imgProcParam, true);
                        jniDataLock.unlock();

                        break;
                    }
                    exitLock.unlock();

                    jniDataLock.lock();
                    String urlSet = jniFindNextImgUrl((int) imgUrlJniAddr, imgProcParam, false);
                    jniDataLock.unlock();

                    if (urlSet != null)
                    {
                        Log.i(TAG, "img:"+urlSet);

                        String[] urls = urlSet.split(" ");
                        imgUrl = urls[0];
                        containerUrl = urls[2];

                        imgUrlJniAddr = Long.parseLong(urls[1], 16);
                        containerUrlJniAddr = Long.parseLong(urls[3], 16);

                        downloadImgByUrl(imgUrl);

                        reportSpiderLogByHandler();
                    }
                    else
                    {
                        imgUrlJniAddr = URL_JNIADDR_INVALID;

                        pageGetLock.lock();
                        if(!pageProcessLock.isLocked.get())
                        {
                            pageProcessLock.lock();
                            //Log.i(TAG, "post findAndLoadNextPage");
                            Utils.handlerPostUntilSuccess(spiderHandler, new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    findAndLoadNextPage();
                                }
                            });
                        }
                        pageGetLock.unlock();

                    }
                }

                readyToExit=true;
                exitLock.unlock();

                Log.i(TAG, "thread " + threadIndex + " stop");
            }

            private File newImgDownloadCacheFile(String imgUrl)
            {
                String newFileExt = imgUrl.substring(imgUrl.lastIndexOf(".")) + CACHE_MARK;

                long cacheRandIndex = new Random(SystemClock.currentThreadTimeMillis()).nextInt();
                File cacheFile;

                imgFileLock.lock();
                do
                {
                    cacheFile = new File(projectCachePath + cacheRandIndex + newFileExt);
                    cacheRandIndex++;
                } while (cacheFile.exists());
                imgFileLock.unlock();

                Log.i(TAG, "new cache file" + cacheFile.getPath());

                return cacheFile;
            }

            private void moveToImgDirAfterDownload(File file, int imgFileSize)
            {
                String cacheFilePath = file.getPath();
                String cacheFileWithoutMark = cacheFilePath.substring(0, cacheFilePath.length() - CACHE_MARK.length());
                String imgFileExt = cacheFileWithoutMark.substring(cacheFileWithoutMark.lastIndexOf("."));

                jniDataLock.lock();

                int imgIndex = (int)imgProcParam[StaticValue.PARA_DOWNLOAD];
                jniSaveImgStorageInfo((int) imgUrlJniAddr, (int) containerUrlJniAddr, imgProcParam, imgFileSize);

                if ((imgIndex % SAVE_PROJECT_DATA) == 0)
                {
                    Log.i(TAG, "post save data cmd imgIndex " + imgIndex);
                    sendCmdToWatchdog(StaticValue.CMD_JUST_STORE);
                }
                else
                {
                    jniDataLock.unlock();
                }

                imgFileLock.lock();
                String dirPath = projectPath + "/" + imgIndex / StaticValue.MAX_IMG_FILE_PER_DIR;
                File dir = new File(dirPath);
                if (!dir.exists())
                {
                    dir.mkdir();
                }

                String newName = String.format("%03d", imgIndex % StaticValue.MAX_IMG_FILE_PER_DIR);
                String newPath = dirPath + "/" + newName + imgFileExt;

                Log.i(TAG, "cache file " + cacheFilePath);
                Log.i(TAG, "final file " + newPath);

                File finalFile = new File(newPath);
                File thumbnailFile = null;


                for (int i = 0; i < 3; i++)
                {
                    if (file.renameTo(finalFile))
                    {
                        String thumbnailDirPath = projectPath + "/" + StaticValue.THUMBNAIL_DIR_NAME + "/" +
                                imgIndex / StaticValue.MAX_IMG_FILE_PER_DIR;
                        File thumbnailDir = new File(thumbnailDirPath);
                        if (!thumbnailDir.exists())
                        {
                            thumbnailDir.mkdirs();
                        }

                        thumbnailFile = new File(thumbnailDirPath + "/" + newName + StaticValue.THUMBNAIL_FILE_EXT);

                        break;
                    }
                    else
                    {
                        Log.i("rename fail", newPath);
                        try
                        {
                            sleep(200);
                        } catch (InterruptedException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }


                if (thumbnailFile != null)
                {
                    createThumbnail(finalFile, thumbnailFile);
                }
                else
                {
                    Log.i("createThumbnail", "thumbnailFile == null");
                }
                imgFileLock.unlock();
            }

            private void createThumbnail(File rawFile, File thumbnailFile)
            {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(rawFile.getPath(), opts);

                opts.inSampleSize=Math.min(opts.outHeight, opts.outWidth)/StaticValue.THUMBNAIL_SIZE;

                opts.inJustDecodeBounds = false;
                Bitmap rawBmp = BitmapFactory.decodeFile(rawFile.getPath(), opts);


                if (rawBmp != null)
                {
                    int rawWidth = rawBmp.getWidth();
                    int rawHeight = rawBmp.getHeight();
                    float scale;

                    int x, y, w, h;


                    if (rawHeight > rawWidth)
                    {
                        x = 0;
                        w = rawWidth;
                        y = (rawHeight - rawWidth) / 2;
                        h = rawWidth;
                        scale = StaticValue.THUMBNAIL_SIZE / (float) rawWidth;
                    }
                    else
                    {
                        y = 0;
                        h = rawHeight;
                        x = (rawWidth - rawHeight) / 2;
                        w = rawHeight;
                        scale = StaticValue.THUMBNAIL_SIZE / (float) rawHeight;
                    }

                    Matrix matrix = new Matrix();
                    matrix.postScale(scale, scale);

                    Bitmap thumbnail = Bitmap.createBitmap(rawBmp, x, y, w, h, matrix, true);

                    if(thumbnail!=rawBmp)
                    {
                        rawBmp.recycle();
                    }

                    FileOutputStream fileOut = null;
                    try
                    {
                        fileOut = new FileOutputStream(thumbnailFile);
                        thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, fileOut);
                        fileOut.flush();
                        fileOut.close();
                    } catch (FileNotFoundException e)
                    {
                        e.printStackTrace();
                        Log.i("createThumbnail", "FileNotFoundException");
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                        Log.i("createThumbnail", "IOException");
                    }

                    thumbnail.recycle();
                }
                else
                {
                    Log.i("createThumbnail", "rawBmp == null");
                }
            }

            private void recvImgDataLoop(InputStream input, String url) throws IOException
            {
                int totalLen = 0;
                int cacheUsage = 0;
                File imgFile = null;
                FileOutputStream output = null;

                while (true)
                {
                    int len = input.read(blockBuf);
                    if (len != -1)
                    {
                        if ((cacheUsage + len) < IMG_VALID_FILE_MIN)
                        {
                            System.arraycopy(blockBuf, 0, cacheBuf, cacheUsage, len);
                            cacheUsage += len;
                        }
                        else
                        {
                            if (output == null)
                            {
                                imgFile = newImgDownloadCacheFile(url);
                                output = new FileOutputStream(imgFile);
                            }
                            output.write(cacheBuf, 0, cacheUsage);
                            System.arraycopy(blockBuf, 0, cacheBuf, 0, len);
                            cacheUsage = len;
                        }

                        totalLen += len;
                    }
                    else
                    {
                        break;
                    }
                }

                //Log.i(TAG, "totalLen "+(totalLen/1024)+"K "+url);
                if (totalLen < IMG_VALID_FILE_MIN)
                {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(cacheBuf, 0, totalLen, opts);

                    //Log.i(TAG, "size:" + totalLen + " " + opts.outWidth + "*" + opts.outHeight);

                    if (opts.outHeight > IMG_VALID_HEIGHT_MIN
                            && opts.outWidth > IMG_VALID_WIDTH_MIN)
                    {
                        imgFile = newImgDownloadCacheFile(url);
                        output = new FileOutputStream(imgFile);
                        output.write(cacheBuf, 0, totalLen);
                    }
                }
                else
                {
                    output.write(cacheBuf, 0, cacheUsage);
                }

                if (output != null)
                {
                    output.close();
                }


                if (imgFile != null)
                {
                    moveToImgDirAfterDownload(imgFile, totalLen);
                }

            }

            private void downloadImgByUrl(String urlStr)
            {
                for (int redirectCnt = 0; redirectCnt < REDIRECT_MAX; redirectCnt++)
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

                            int res = urlConn.getResponseCode();
                            //Log.i(TAG, "response "+res+" "+urlStr);

                            if ((res / 100) == 3)
                            {
                                String redirUrl = urlConn.getHeaderField("Location");

                                if (redirUrl != null)
                                {
                                    urlStr = redirUrl.replaceAll(" ", "%20");
                                }
                                else
                                {
                                    break;
                                }
                            }
                            else
                            {
                                if (res == 200)
                                {
                                    recvImgDataLoop(urlConn.getInputStream(), urlStr);
                                }
                                break;
                            }
                        } finally
                        {
                            if (urlConn != null)
                            {
                                urlConn.disconnect();
                            }
                        }

                    } catch (MalformedURLException e)
                    {
                        e.printStackTrace();
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void reportSpiderLogByHandler()
    {
        spiderHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                reportSpiderLog();
            }
        });
    }

    private void reportSpiderLog()
    {
        String jsonReportStr = "{\r\n";


        jsonReportStr += "\"serviceVmMem\":" + (Runtime.getRuntime().totalMemory() >> 10) + ",\r\n";
        jsonReportStr += "\"serviceNativeMem\":" + (Debug.getNativeHeapSize() >> 10) + ",\r\n";

        jniDataLock.lock();
        jsonReportStr += "\"imgDownloaderPayload\":" + imgProcParam[StaticValue.PARA_PAYLOAD] + ",\r\n";
        jsonReportStr += "\"imgDownloadNum\":" + imgProcParam[StaticValue.PARA_DOWNLOAD] + ",\r\n";
        jsonReportStr += "\"imgProcessedNum\":" + imgProcParam[StaticValue.PARA_PROCESSED] + ",\r\n";
        jsonReportStr += "\"imgTotalNum\":" + imgProcParam[StaticValue.PARA_TOTAL] + ",\r\n";
        jsonReportStr += "\"imgTotalSize\":" + imgProcParam[StaticValue.PARA_TOTAL_SIZE] + ",\r\n";
        jsonReportStr += "\"imgTreeHeight\":" + imgProcParam[StaticValue.PARA_HEIGHT] + ",\r\n";
        jsonReportStr += "\"pageProcessedNum\":" + pageProcParam[StaticValue.PARA_PROCESSED] + ",\r\n";
        jsonReportStr += "\"pageTotalNum\":" + pageProcParam[StaticValue.PARA_TOTAL] + ",\r\n";
        jsonReportStr += "\"pageTreeHeight\":" + pageProcParam[StaticValue.PARA_HEIGHT] + ",\r\n";
        jniDataLock.unlock();

        jsonReportStr += "\"pageLoadTime\":" + loadTime + ",\r\n";
        jsonReportStr += "\"pageScanTime\":" + scanTime + ",\r\n";
        jsonReportStr += "\"pageSearchTime\":" + searchTime + ",\r\n";


        jsonReportStr += "\"curPage\":" + "\"" + curPageUrl + "\",\r\n";
        jsonReportStr += "\"curNetSpeed\":" + "\"" + Utils.byteSizeToString(
                netTrafficCalc.netTrafficPerSec.get()) + "/s\"" + ",\r\n";

        jsonReportStr += "\"siteScanCompleted\":" + ((state.get() == STAT_COMPLETE) ? "true" : "false");

        jsonReportStr += ",\r\n";

        int numOfCallback = mCallbacks.beginBroadcast();
        for (int i = 0; i < numOfCallback; i++)
        {
            try
            {
                mCallbacks.getBroadcastItem(i).reportStatus(jsonReportStr);
            } catch (RemoteException e)
            {
                e.printStackTrace();
            }
        }
        mCallbacks.finishBroadcast();

        reportStatusTimer.set(0);
    }

    // javascript回调不在主线程。
    private void scanPageWithJS()
    {
        pageFinished = true;

        String title = spider.getTitle();
        if (title != null)
        {
            if (title.length() > MAX_SIZE_PER_TITLE)
            {
                title.substring(0, MAX_SIZE_PER_TITLE);
            }
            jniDataLock.lock();
            jniRecvPageTitle(title);
            jniDataLock.unlock();
        }

        // 扫描页面耗时较少，因此此处不检测暂停或者停止命令
        scanTimer = System.currentTimeMillis();
        spider.loadUrl("javascript:"
                + "var i;"
                + "var imgSrc=\"\";"
                + "var img=document.getElementsByTagName(\"img\");"
                + "for(i=0; i<img.length; i++){"
                +     "imgSrc+=(img[i].src+' ');"
                + "}"
                + "var imgInput=document.getElementsByTagName(\"input\");"
                + "for(i=0; i<imgInput.length; i++)"
                + "{"
                +     "if(imgInput[i].src){"
                +         "imgSrc+=(imgInput[i].src+' ');"
                +     "}"
                + "}"
                + "SpiderCrawl.recvImgUrl(imgSrc);"
                + "var a=document.getElementsByTagName(\"a\");"
                + "var aHref=\"\";"
                + "for(i=0; i<a.length; i++){"
                +     "aHref+=(a[i].href+' ');"
                + "}"
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
                Log.i(TAG, "onPageFinished " + url + " loadTime:" + loadTime + " tmr:" + urlLoadTimer.get());
                //Log.i(TAG, "curPageUrl "+curPageUrl);
                if (!pageFinished)
                {
                    if (curPageUrl != null)
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
                //Log.i(TAG, "onPageStarted " + url);
                loadTimer = System.currentTimeMillis();
                pageFinished = false;
            }


            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view,
                                                              String url)
            {
                WebResourceResponse response = null;

                //Log.i(TAG, "shouldInterceptRequest "+curPageUrl+" "+url);

                if (curPageUrl != null)
                {
                    if (!curPageUrl.equals(url))
                    {
                        response = new WebResourceResponse("image/png", "UTF-8", null);
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

    private boolean findAndLoadNextPage()
    {
        searchTime = System.currentTimeMillis();

        jniDataLock.lock();
        curPageUrl = jniFindNextPageUrl(pageProcParam);
        jniDataLock.unlock();

        searchTime = System.currentTimeMillis() - searchTime;

        boolean newPageFind = false;
        //Log.i(TAG, "loading:" + curPageUrl);
        if (curPageUrl == null)
        {
            state.set(STAT_COMPLETE);
            Log.i(TAG, "site scan complete");

            pageProcessLock.unlock();
        }
        else
        {
            //Log.i(TAG, "new page url valid");
            spiderLoadUrl(curPageUrl);
            newPageFind = true;
        }

        reportSpiderLog();

        return newPageFind;
    }

    private void spiderInit()
    {
        spiderWebViewInit();

        try
        {
            md5ForPage = MessageDigest.getInstance("md5");
        } catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }

        if(srcUrl!=null)
        {
            jniSetSrcPageUrl(srcUrl, md5ForPage.digest(srcUrl.getBytes()), pageProcParam);
        }

        new TimerThread().start();

        if (state.get() == STAT_WORKING)
        {
            mImgDownloader.startAllThread();
        }
        else
        {
            state.set(STAT_PAUSE);
        }

        reportSpiderLog();
    }


    private Utils.NetTrafficCalc netTrafficCalc = new Utils.NetTrafficCalc(this);
    private AtomicInteger reportStatusTimer = new AtomicInteger(0);

    private class TimerThread extends Thread
    {
        private final int TIMER_INTERVAL = 1000;

        private static final int NET_TRAFFIC_CALC_INTERVAL = 5;

        private static final int REPORT_STATUS_MAX_INTERVAL = 2;

        public TimerThread()
        {
            super("TimerThread");
            setDaemon(true);
        }

        public void run()
        {
            while (timerRunning.get())
            {
                netTrafficCalc.refreshNetTraffic();

                if (reportStatusTimer.getAndIncrement() == REPORT_STATUS_MAX_INTERVAL)
                {
                    reportStatusTimer.set(0);
                    reportSpiderLogByHandler();
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
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    private void spiderLoadUrl(String url)
    {
        //Log.i(TAG, "spiderLoadUrl:"+url);
        spider.loadUrl(url);
        urlLoadTimer.set(URL_TIME_OUT);
    }

    @JavascriptInterface
    public void recvImgUrl(String imgUrlSet)
    {
        // Log.i(TAG, "imgUrl:"+imgUrl);

        String[] list = imgUrlSet.split(" ");

        // Log.i(TAG, "length:"+list.length);

        jniDataLock.lock();
        for (String imgUrl : list)
        {
            if ((imgUrl.startsWith("http://") || imgUrl.startsWith("https://"))
                    && (imgUrl.endsWith(".jpg") || imgUrl.endsWith(".png") || imgUrl
                    .endsWith(".gif"))
                    && imgUrl.length() < MAX_SIZE_PER_URL)
            {
                jniAddUrl(imgUrl, md5ForPage.digest(imgUrl.getBytes()), URL_TYPE_IMG,
                        imgProcParam);
            }
        }
        jniDataLock.unlock();
    }

    @JavascriptInterface
    public void recvPageUrl(String pageUrlSet)
    {
        // Log.i(TAG, "pageUrl:"+pageUrl);

        String[] list = pageUrlSet.split(" ");

        // Log.i(TAG, "length:"+list.length);


        jniDataLock.lock();
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
                    jniAddUrl(pageUrl, md5ForPage.digest(pageUrl.getBytes()), URL_TYPE_PAGE,
                            pageProcParam);
                }
            } catch (MalformedURLException e)
            {
                // Log.e(TAG,e.toString());
            }
        }
        jniDataLock.unlock();

    }

    @JavascriptInterface
    public void onCurPageScaned()
    {
        //Log.i(TAG, "onCurPageScaned");
        scanTime = System.currentTimeMillis() - scanTimer;

        pageProcessLock.unlock();
    }

}