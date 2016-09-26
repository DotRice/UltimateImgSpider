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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import android.os.SystemClock;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.gk969.Utils.Utils;

public class WatchdogService extends Service {
    private final static String TAG = "WatchdogService";

    private String dataDirPath;

    private native int jniGetAshmem(String name, int size);
    private native void jniRestoreProjectData(String path);
    private native void jniStoreProjectData(String path);
    private static native String jniGetProjectInfo(String dataFileFullPath, long[] imgParam, long[] pageParam);


    private static final String[] DATA_FILE={StaticValue.PROJECT_DATA_NAME, StaticValue.PROJECT_DATA_BACKUP_NAME};
    private static final String[] DATA_HASH_FILE={StaticValue.PROJECT_DATA_MD5, StaticValue.PROJECT_DATA_BACKUP_MD5};

    private Handler mHandler = new Handler();
    private ExecutorService singleThreadPool = Executors.newSingleThreadExecutor();

    private int saveDataBackupIndex=0;

    static {
        System.loadLibrary("UltimateImgSpider");
    }

    final RemoteCallbackList<IRemoteWatchdogServiceCallback> mCallbacks
            = new RemoteCallbackList<IRemoteWatchdogServiceCallback>();

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "onCreate");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        mCallbacks.kill();

        System.exit(0);
    }

    private void storeProjectData(final boolean onStop) {
        singleThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                String dataFileName = DATA_FILE[saveDataBackupIndex];
                String hashDataName = DATA_HASH_FILE[saveDataBackupIndex];

                saveDataBackupIndex=(saveDataBackupIndex+1)%DATA_FILE.length;

                String dataFileFullPath = dataDirPath + dataFileName;
                long time = SystemClock.uptimeMillis();
                jniStoreProjectData(dataFileFullPath);
                Log.i(TAG, "jniStoreProjectData " + dataFileName + " time " + (SystemClock.uptimeMillis() - time));

                time = SystemClock.uptimeMillis();
                String md5String = Utils.getFileMD5String(dataFileFullPath);
                Log.i(TAG, "getFileMD5String " + hashDataName + " time " + (SystemClock.uptimeMillis() - time));
                Utils.stringToFile(md5String, dataDirPath + hashDataName);

                if(onStop) {
                    stopSelf();
                }else{
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            projectDataSaved();
                        }
                    });
                }
            }
        });
    }




    private static boolean projectDataIsSafe(String dataFileFullPath, String hashFileFullPath) {
        String md5OfFile = Utils.getFileMD5String(dataFileFullPath);

        String md5InRec = Utils.fileToString(hashFileFullPath);
        Log.i(TAG, "projectDataIsSafe " + md5OfFile + " " + md5InRec);

        if(md5OfFile != null) {
            return md5OfFile.equals(md5InRec);
        }

        return false;
    }


    public static String getProjectInfo(String dataDirPath, long[] imgParam, long[] pageparam){
        return getProjectInfo(dataDirPath, imgParam, pageparam, null);
    }

    public static String getProjectInfo(String dataDirPath, long[] imgParam, long[] pageParam, int[] bestDataFileIndex){
        String srcUrl=null;

        if(imgParam==null){
            imgParam=new long[StaticValue.IMG_PARA_NUM];
        }
        if(pageParam==null){
            pageParam=new long[StaticValue.PAGE_PARA_NUM];
        }

        for(long imgInfo:imgParam){
            imgInfo=0;
        }
        for(long pageInfo:pageParam){
            pageInfo=0;
        }

        for(int i=0; i<DATA_FILE.length; i++){
            String dataFile=dataDirPath+DATA_FILE[i];
            if(projectDataIsSafe(dataFile, dataDirPath+DATA_HASH_FILE[i])){
                long[] curImgParam=new long[StaticValue.IMG_PARA_NUM];
                long[] curPageParam=new long[StaticValue.PAGE_PARA_NUM];
                String curSrcUrl=jniGetProjectInfo(dataFile, curImgParam, curPageParam);

                if(curImgParam[StaticValue.PARA_TOTAL]>imgParam[StaticValue.PARA_TOTAL]
                        ||curImgParam[StaticValue.PARA_PROCESSED]>imgParam[StaticValue.PARA_PROCESSED]
                        ||curPageParam[StaticValue.PARA_TOTAL]>pageParam[StaticValue.PARA_TOTAL]
                        ||curPageParam[StaticValue.PARA_PROCESSED]>pageParam[StaticValue.PARA_PROCESSED]) {

                    Log.i(TAG, "better dataFilePath " + dataFile);

                    srcUrl=curSrcUrl;
                    System.arraycopy(curImgParam, 0, imgParam, 0, StaticValue.IMG_PARA_NUM);
                    System.arraycopy(curPageParam, 0, pageParam, 0, StaticValue.PAGE_PARA_NUM);

                    if(bestDataFileIndex!=null){
                        bestDataFileIndex[0]=i;
                    }
                }
            }
        }

        return srcUrl;
    }

    private void tryToRestoreProjectData(String path) {
        Log.i(TAG, "tryToRestoreProjectData " + path);

        if((path != null) && (dataDirPath == null)) {
            File dataDir = new File(path + StaticValue.PROJECT_DATA_DIR);
            if(!dataDir.exists() || !dataDir.isDirectory()) {
                dataDir.mkdirs();
            }
            dataDirPath = path + StaticValue.PROJECT_DATA_DIR;

            int[] bestDataFileIndex=new int[1];
            if(getProjectInfo(dataDirPath, null, null, bestDataFileIndex)!=null){
                String bestDataFilePath=dataDirPath+DATA_FILE[bestDataFileIndex[0]];
                Log.i(TAG, "bestDataFilePath " + bestDataFilePath);
                saveDataBackupIndex=(bestDataFileIndex[0]+1)%DATA_FILE.length;
                jniRestoreProjectData(bestDataFilePath);
            }
        }

    }

    private void projectPathRecved() {
        Log.i(TAG, "projectPathRecved");

        int numOfCallback = mCallbacks.beginBroadcast();
        for(int i = 0; i < numOfCallback; i++) {
            try {
                mCallbacks.getBroadcastItem(i).projectPathRecved();
            } catch(RemoteException e) {
                e.printStackTrace();
            }
        }
        mCallbacks.finishBroadcast();
    }

    private void projectDataSaved() {
        Log.i(TAG, "projectDataSaved");

        int numOfCallback = mCallbacks.beginBroadcast();
        for(int i = 0; i < numOfCallback; i++) {
            try {
                mCallbacks.getBroadcastItem(i).projectDataSaved();
            } catch(RemoteException e) {
                e.printStackTrace();
            }
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int cmd = intent.getIntExtra(StaticValue.BUNDLE_KEY_CMD, StaticValue.CMD_NOTHING);
        final String path = intent.getStringExtra(StaticValue.BUNDLE_KEY_PRJ_PATH);

        Log.i(TAG, "onStartCommand:" + StaticValue.CMD_DESC[cmd] + " path:" + path);

        switch(cmd) {
            case StaticValue.CMD_START: {
                singleThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        tryToRestoreProjectData(path);

                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                projectPathRecved();
                            }
                        });
                    }
                });

                break;
            }

            case StaticValue.CMD_STOP_STORE: {
                singleThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        storeProjectData(true);
                    }
                });
                break;
            }

            case StaticValue.CMD_JUST_STOP: {
                stopSelf();
                break;
            }

            case StaticValue.CMD_JUST_STORE: {
                storeProjectData(false);
                break;
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind:" + intent.getAction());

        if(IRemoteWatchdogService.class.getName().equals(intent.getAction())) {
            return mBinder;
        }
        return null;
    }

    /**
     * The IRemoteInterface is defined through IDL
     */
    private final IRemoteWatchdogService.Stub mBinder = new IRemoteWatchdogService.Stub() {
        @Override
        public ParcelFileDescriptor getAshmem(String name, int size)
                throws RemoteException {
            ParcelFileDescriptor parcelFd = null;
            try {
                parcelFd = ParcelFileDescriptor.fromFd(jniGetAshmem(name,
                        size));
            } catch(IOException e) {
                e.printStackTrace();
            }

            return parcelFd;
        }

        public void registerCallback(IRemoteWatchdogServiceCallback cb) {
            if(cb != null) {
                Log.i(TAG, "registerCallback");
                mCallbacks.register(cb);
            }
        }

        public void unregisterCallback(IRemoteWatchdogServiceCallback cb) {
            if(cb != null) {
                mCallbacks.unregister(cb);
            }
        }
    };
}