package com.gk969.UltimateImgSpider;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.gk969.Utils.MemoryInfo;
import com.gk969.Utils.StorageUtils;
import com.gk969.Utils.Utils;
import com.gk969.View.ImageTextButton;
import com.gk969.gallery.gallery3d.glrenderer.GLCanvas;
import com.gk969.gallery.gallery3d.glrenderer.TiledTexture;
import com.gk969.gallery.gallery3d.ui.GLRootView;
import com.gk969.gallery.gallery3d.ui.GLView;
import com.gk969.gallery.gallery3d.util.GalleryUtils;
import com.gk969.gallerySimple.AlbumLoaderHelper;
import com.gk969.gallerySimple.AlbumSetLoaderHelper;
import com.gk969.gallerySimple.PhotoView;
import com.gk969.gallerySimple.ThumbnailLoader;
import com.gk969.gallerySimple.SlotView;
import com.gk969.gallerySimple.ThumbnailLoaderHelper;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

public class SpiderActivity extends Activity {
    private final static String TAG = "SpiderActivity";
    
    private static final int CONN_STATE_DISCONNECTED = 0;
    private static final int CONN_STATE_CONNECTED = 1;
    private static final int CONN_STATE_WAIT_DISCONNECT = 2;
    private static final int CONN_STATE_WAIT_CONNECT = 3;

    //private static final int MIN_FREE_MEM_TO_RESTART_SERVICE = 50;
    private static final int MAX_USED_MEM_TO_RESTART_SERVICE = (MemoryInfo.getTotalMemInMb()/1024+1)*50;
    private static final int MIN_FREE_STORAGE_TO_STOP_SERVICE = 200;

    private int serviceConnState = CONN_STATE_DISCONNECTED;


    private String downloadingProjectSrcUrl;
    private int downloadingProjectIndex;
    private SpiderProject.ProjectInfo downloadingProjectInfo;

    private int displayProjectIndex = SpiderProject.INVALID_INDEX;
    private SpiderProject.ProjectInfo displayProjectInfo;


    private MessageHandler mHandler = new MessageHandler(this);

    private static final int BUMP_MSG = 1;


    private GLRootView glRootView;
    private SlotView slotView;
    private PhotoView photoView;

    private ThumbnailLoader mThumbnailLoader;
    private AlbumLoaderHelper albumLoaderHelper;
    private AlbumSetLoaderHelper albumSetLoaderHelper;

    private SpiderProject spiderProject;

    private ServiceConnection mConnection;
    private IRemoteSpiderServiceCallback mCallback;

    private boolean inDeleting = false;
    
    private ImageButton buttonProjectCtrl;
    private TextView textViewProjectState;
    private String[] projectStateDesc;
    
    private ImageButton buttonAdd;
    private ImageButton buttonMenu;

    enum ProjectState {
        PAUSE, CHECK, DOWNLOADING, COMPLETE
    }

    private ProjectState projectState = ProjectState.PAUSE;

    public static final int ALBUM_SET_VIEW = 0;
    public static final int ALBUM_VIEW = 1;
    public static final int PHOTO_VIEW = 2;

    private volatile int curView = ALBUM_SET_VIEW;

    private InfoDrawer infoDrawer;

    private static final int BUTTON_MENU_ANIMATION_TIME = 250;
    private AtomicInteger buttonMenuDisplayTime = new AtomicInteger(0);
    private static final int MEMORY_REFRESH_TIME = 2;
    private static final int STORAGE_REFRESH_TIME = 5;

    private final static int SPIDER_SERVICE_REPORT_TIMEOUT = 60;
    private AtomicInteger spiderServiceReportTimOut = new AtomicInteger(0);

    private static final int TIMER_PERIOD = 1000;
    private static final int BUTTON_MENU_DISPLAY_SECOND = 4;
    private ScheduledExecutorService singleThreadPoolTimer = Executors.newSingleThreadScheduledExecutor();

    private final static long TIME_TO_DISP_TOAST = 1000;
    private long createTime;

    private StorageUtils storageInfo;

    IRemoteSpiderService mService = null;

    private URL newUrl=null;
    private boolean projectListHasLoaded=false;

    private final static int RESULT_SRC_URL = 0;

    private static final int PERMISSION_REQUEST_CODE=0;

    private void serviceInterfaceInit() {
        mConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                mService = IRemoteSpiderService.Stub.asInterface(service);

                try {
                    mService.registerCallback(mCallback);
                } catch(RemoteException e) {
                    e.printStackTrace();
                }

                serviceConnState = CONN_STATE_CONNECTED;
                Log.i(TAG, "onServiceConnected");
            }

            public void onServiceDisconnected(ComponentName className) {
                mService = null;

                Log.i(TAG, "onServiceDisconnected");

                infoDrawer.onSpiderStop();

                if(projectState == ProjectState.DOWNLOADING) {
                    Log.i(TAG, "prepare restart service");
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            serviceConnState = CONN_STATE_WAIT_CONNECT;
                            sendCmdToSpiderService(StaticValue.CMD_START);
                        }
                    }, 2000);
                } else {
                    Log.i(TAG, "NOT DOWNLOADING  Stop");
                    serviceConnState = CONN_STATE_DISCONNECTED;
                    if(inDeleting) {
                        singleThreadPoolTimer.execute(new Runnable() {
                            @Override
                            public void run() {
                                Utils.deleteDir(downloadingProjectInfo.dir);
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(SpiderActivity.this, R.string.deletedToast,
                                                Toast.LENGTH_SHORT).show();
                                        mThumbnailLoader.setAlbumTotalImgNum(0);
                                        inDeleting = false;
                                        dialogDelete.dismiss();
                                    }
                                });
                            }
                        });
                    }
                }
            }
        };

        mCallback = new IRemoteSpiderServiceCallback.Stub() {
            /**
             * Note that IPC calls are dispatched through a thread pool running
             * in each process, so the code executing here will NOT be running
             * in our main thread like most other things -- so, to update the
             * UI, we need to use a Handler to hop over there.
             */
            public void reportStatus(String value) {
                mHandler.sendMessage(mHandler.obtainMessage(BUMP_MSG, value));
            }
        };
    }


    private static class MessageHandler extends Handler {
        WeakReference<SpiderActivity> mActivity;

        MessageHandler(SpiderActivity activity) {
            mActivity = new WeakReference<SpiderActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            final SpiderActivity theActivity = mActivity.get();
            switch(msg.what) {
                case BUMP_MSG:
                    if(theActivity != null) {
                        theActivity.parseReport((String) msg.obj);
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }

    }

    private void parseReport(String report) {
        spiderServiceReportTimOut.set(SPIDER_SERVICE_REPORT_TIMEOUT);
        //Log.i(TAG, report);
        try {
            JSONObject jsonReport = new JSONObject(report);

            if(jsonReport.getString("srcHost").equals(
                    downloadingProjectInfo.host)) {
                int imgDownloadNum = jsonReport.getInt("imgDownloadNum");
                if(displayProjectIndex == downloadingProjectIndex) {
                    if(serviceConnState != CONN_STATE_DISCONNECTED) {
                        infoDrawer.refreshInfoByServiceReport(jsonReport);
                    }

                    mThumbnailLoader.setAlbumTotalImgNum(imgDownloadNum);
                }

                refreshDownloadingProjectInfo(String.valueOf(imgDownloadNum));

                int serviceNativeMem = jsonReport.getInt("serviceNativeMem") >> 10;

                if(jsonReport.getBoolean("networkFail")) {
                    setProjectState(ProjectState.PAUSE);
                    showSysFaultAlert(getString(R.string.prompt),
                            getString(R.string.uneffectiveNetworkPrompt), false);
                } else if(jsonReport.getBoolean("siteScanCompleted")) {
                    if(projectState!=ProjectState.COMPLETE) {
                        setProjectState(ProjectState.COMPLETE);
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.prompt)
                                .setMessage(R.string.project_complete)
                                .setPositiveButton(R.string.OK, null)
                                .create().show();
                    }
                } else if(serviceNativeMem >= MAX_USED_MEM_TO_RESTART_SERVICE) {
                    if(serviceConnState == CONN_STATE_CONNECTED) {
                        serviceConnState = CONN_STATE_WAIT_DISCONNECT;
                        sendCmdToSpiderService(StaticValue.CMD_RESTART);
                    }
                } else if(curProjectFreeStorageIsLow()) {
                    setProjectState(ProjectState.PAUSE);
                    sendCmdToSpiderService(StaticValue.CMD_STOP_STORE);
                }
            }
        } catch(JSONException e) {
            e.printStackTrace();
        }

    }

    private boolean curProjectFreeStorageIsLow() {
        return (storageInfo.getFreeSpace(downloadingProjectInfo.dir.getPath()) < (MIN_FREE_STORAGE_TO_STOP_SERVICE << 20));
    }

    private boolean freeStorageIsLow() {
        return storageInfo.getMaxFreeSize() < (MIN_FREE_STORAGE_TO_STOP_SERVICE << 20);
    }

    public void showSysFaultAlert(String title, String desc, final boolean exit) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(desc)
                .setPositiveButton(exit ? R.string.exit : R.string.OK,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                if(exit) {
                                    SpiderActivity.this.finish();
                                }
                            }
                        })
                .setNegativeButton(R.string.retry,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                setProjectState(ProjectState.CHECK);
                                checkAndStart(false);
                            }
                        }).create().show();
    }

    private AlertDialog dialogDelete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_spider);
        onFirstRun();

        Log.i(TAG, "onCreate");

        checkPermissionBeforeInit();
    }

    private void init(){
        createTime = SystemClock.uptimeMillis();
        storageInfo = new StorageUtils();
        serviceInterfaceInit();
        panelViewInit();
        albumViewInit();
        timerThreadPoolInit();
    }

    private void checkPermissionBeforeInit(){
        Log.i(TAG, "checkPermissionBeforeInit");
        if(Build.VERSION.SDK_INT >= 23){
            if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            }else{
                init();
            }
        }else {
            init();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                init();
            }else {
                finish();
            }
        }
    }

    private void timerThreadPoolInit() {
        singleThreadPoolTimer.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                if(buttonMenuDisplayTime.get() != 0) {
                    if(buttonMenuDisplayTime.decrementAndGet() == 0) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                showButtonMenu(false);
                            }
                        });
                    }
                }

                if(spiderServiceReportTimOut.get() != 0) {
                    if(spiderServiceReportTimOut.decrementAndGet() == 0) {
                        spiderServiceReportTimOut.set(SPIDER_SERVICE_REPORT_TIMEOUT);
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if(projectState == ProjectState.DOWNLOADING &&
                                        serviceConnState == CONN_STATE_CONNECTED) {
                                    serviceConnState = CONN_STATE_WAIT_DISCONNECT;
                                    sendCmdToSpiderService(StaticValue.CMD_RESTART);
                                }
                            }
                        });
                    }
                }
            }
        }, 0, TIMER_PERIOD, TimeUnit.MILLISECONDS);

        singleThreadPoolTimer.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                if(curView == ALBUM_VIEW) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            infoDrawer.refreshMemoryInfo();
                        }
                    });
                }
            }
        }, 0, MEMORY_REFRESH_TIME, TimeUnit.SECONDS);

    }

    private boolean startProject(int index) {
        Log.i(TAG, "startProject " + index);
        SpiderProject.ProjectInfo project = spiderProject.projectList.get(index);

        downloadingProjectIndex = index;

        boolean isNewProject = false;
        if(downloadingProjectInfo == null ||
                !project.dir.getPath().equals(downloadingProjectInfo.dir.getPath())) {
            isNewProject = true;
            infoDrawer.initDownloadingInfo();
            downloadingProjectInfo = project;
        }

        setProjectState(ProjectState.CHECK);
        checkAndStart(isNewProject);

        return true;
    }

    private void openAlbum(int index) {
        if(displayProjectIndex != index) {
            if(index < spiderProject.projectList.size()) {
                if(projectListHasLoaded || newUrl==null) {
                    displayProjectIndex = index;
                    displayProjectInfo = spiderProject.projectList.get(displayProjectIndex);
                    albumLoaderHelper.setProjectPath(displayProjectInfo.dir.getPath());
                    mThumbnailLoader.setHelper(albumLoaderHelper, displayProjectInfo.imgDownloadNum,
                            spiderProject.projectList.get(index).albumScrollDistance);
                    infoDrawer.onDisplayProjectChanged();

                    dispProjectState((displayProjectIndex != downloadingProjectIndex) ? ProjectState.PAUSE : projectState);

                    setView(ALBUM_VIEW);
                }else{
                    Toast.makeText(this, R.string.please_wait_to_load_project, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void openPhotoView(int index) {
        if(index < displayProjectInfo.imgDownloadNum) {
            setView(PHOTO_VIEW);
            photoView.openPhoto(displayProjectInfo.dir.getPath(), index, displayProjectInfo);
        }
    }

    private void backToAlbumView() {
        photoView.onDestroy();
        setView(ALBUM_VIEW);
    }

    private void backToAlbumSetView() {
        if(projectState == ProjectState.DOWNLOADING) {
            mThumbnailLoader.refreshSlotInfo(downloadingProjectIndex, "", true);
        } else {
            mThumbnailLoader.refreshSlotInfo(StaticValue.INDEX_INVALID, null, false);
        }

        final SpiderProject.ProjectInfo curProjectInfo=displayProjectInfo;
        singleThreadPoolTimer.execute(new Runnable() {
            @Override
            public void run() {
                curProjectInfo.saveParam();
            }
        });

        mThumbnailLoader.setHelper(albumSetLoaderHelper, spiderProject.projectList.size());
        displayProjectIndex = SpiderProject.INVALID_INDEX;
        setView(ALBUM_SET_VIEW);
    }

    public void refreshDownloadingProjectInfo(String infoStr) {
        if(curView == ALBUM_SET_VIEW) {
            mThumbnailLoader.refreshSlotInfo(downloadingProjectIndex, infoStr,
                    projectState == ProjectState.DOWNLOADING);
            glRootView.requestRender();
        }
    }

    private GLView getCurGLView(){
        return curView==PHOTO_VIEW?photoView:slotView;
    }

    private ImageButton getCurBottomButton(){
        return curView==ALBUM_SET_VIEW?buttonAdd:buttonMenu;
    }

    private void albumViewInit() {
        glRootView = (GLRootView) findViewById(R.id.gl_root_view);

        glRootView.setFocusable(true);
        glRootView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if(keyEvent.getAction()==KeyEvent.ACTION_DOWN) {
                    int keyCode=keyEvent.getKeyCode();
                    Log.i(TAG, "glRootView onKeyDown " + keyCode);
                    switch(keyCode){
                        case KeyEvent.KEYCODE_DPAD_UP:
                        case KeyEvent.KEYCODE_DPAD_DOWN:
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                        case KeyEvent.KEYCODE_ENTER:
                            ImageButton curButton=getCurBottomButton();
                            boolean processed = getCurGLView().onKeyDown(keyCode, curButton.getVisibility()==View.VISIBLE);
                            if(!processed) {
                                curButton.requestFocus();
                            }
                            return true;
                    }
                }
                return false;
            }
        });

        Runnable runOnFindProject = new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "runOnFindProject");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if(curView == ALBUM_SET_VIEW) {
                            mThumbnailLoader.setAlbumTotalImgNum(spiderProject.projectList.size());
                        }
                    }
                });
            }
        };

        Runnable runOnProjectLoadComplete = new Runnable() {
            @Override
            public void run() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG, "runOnProjectLoadComplete");
                        projectListHasLoaded=true;
                        if(newUrl!=null) {
                            processNewUrl();
                        }else if(curView == ALBUM_SET_VIEW) {
                            buttonShowAnim(buttonAdd, true);
                            if((SystemClock.uptimeMillis() - createTime) > TIME_TO_DISP_TOAST
                                    && spiderProject.projectList.size() > 0) {
                                Toast.makeText(SpiderActivity.this,
                                        R.string.project_load_complete, Toast.LENGTH_SHORT).show();
                            }
                        }

                    }
                });
            }
        };

        spiderProject = new SpiderProject(runOnFindProject, runOnProjectLoadComplete);

        albumLoaderHelper = new AlbumLoaderHelper();
        albumSetLoaderHelper = new AlbumSetLoaderHelper(spiderProject);
        mThumbnailLoader = new ThumbnailLoader(glRootView, albumSetLoaderHelper);


        slotView = new SlotView(this, mThumbnailLoader, glRootView);
        slotView.setOnClick(new SlotView.OnClickListener() {
            @Override
            public void onClick(int slotIndex) {
                Log.i(TAG, "Clicked " + slotIndex);
                if(curView == ALBUM_SET_VIEW) {
                    openAlbum(slotIndex);
                } else if(curView == ALBUM_VIEW) {
                    openPhotoView(slotIndex);
                }
            }
        });
        slotView.setOnScrollEnd(new SlotView.OnScrollEndListener() {
            @Override
            public void onScrollEnd(int curScrollDistance) {
                if(curView == ALBUM_VIEW) {
                    spiderProject.projectList.get(displayProjectIndex).albumScrollDistance = curScrollDistance;
                }
            }
        });

        slotView.setOnManuallyScroll(new SlotView.OnManuallyScrollListener() {
            @Override
            public void onManuallyScroll(boolean isUp) {
                if(curView == ALBUM_VIEW)
                {
                    if(buttonMenu.getAnimation()==null && (buttonMenu.getVisibility()==View.VISIBLE)!=isUp) {
                        showButtonMenu(isUp);
                    }
                }
            }
        });

        slotView.setOnStart(new SlotView.OnStartListener(){

            @Override
            public void onStart() {
                singleThreadPoolTimer.execute(new Runnable() {
                    @Override
                    public void run() {
                        spiderProject.refreshProjectList(StorageUtils.getAppStoDirs(), getString(R.string.appPackageName));
                    }
                });
            }
        });

        glRootView.setContentPane(slotView);

        photoView = new PhotoView(this, glRootView);

        downloadingProjectIndex = SpiderProject.INVALID_INDEX;
    }

    private void checkAndStart(boolean isNewProject) {
        Log.i(TAG, "checkAndStart");

        if(freeStorageIsLow()) {
            Log.i(TAG, "freeStorageIsLow");

            setProjectState(ProjectState.PAUSE);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showSysFaultAlert(getString(R.string.prompt), getString(R.string.storage_lack), false);
                }
            }, 50);

        } else {
            checkNetwork(isNewProject);
        }
    }


    private void checkNetwork(final boolean isNewProject) {
        singleThreadPoolTimer.execute(new Runnable() {
            @Override
            public void run() {
                Runnable networkValid = new Runnable() {
                    @Override
                    public void run() {
                        if(projectState == ProjectState.CHECK) {
                            infoDrawer.displayDownloadingInfo();
                            setProjectState(ProjectState.DOWNLOADING);

                            if(serviceConnState == CONN_STATE_CONNECTED ||
                                    serviceConnState == CONN_STATE_WAIT_CONNECT) {
                                if(isNewProject) {
                                    sendCmdToSpiderService(StaticValue.CMD_STOP_STORE);
                                    serviceConnState = CONN_STATE_WAIT_DISCONNECT;
                                } else {
                                    sendCmdToSpiderService(StaticValue.CMD_START);
                                }
                            } else if(serviceConnState == CONN_STATE_DISCONNECTED ||
                                    serviceConnState == CONN_STATE_WAIT_DISCONNECT) {
                                sendCmdToSpiderService(StaticValue.CMD_START);
                                serviceConnState = CONN_STATE_WAIT_CONNECT;
                            }
                        }
                    }
                };

                Runnable networkInvalid = new Runnable() {
                    @Override
                    public void run() {
                        setProjectState(ProjectState.PAUSE);
                        showSysFaultAlert(getString(R.string.prompt),
                                getString(R.string.uneffectiveNetworkPrompt), false);
                    }
                };
                mHandler.post(Utils.isNetworkEffective() ? networkValid : networkInvalid);
            }
        });
    }

    private void onFirstRun() {
        if(ParaConfig.isFirstRun(getApplicationContext())) {
            Toast.makeText(this, "first run", Toast.LENGTH_SHORT).show();
            ParaConfig.setFirstRun(getApplicationContext());
        }
    }

    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
    }

    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");

    }

    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
    }

    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");

    }

    private void tryToStopSpiderService() {
        Log.i(TAG, "tryToStopSpiderService " + serviceConnState + " " + CONN_STATE_DISCONNECTED);
        if(serviceConnState != CONN_STATE_DISCONNECTED) {
            projectState=ProjectState.PAUSE;
            //unbindSpiderService();
            sendCmdToSpiderService(StaticValue.CMD_STOP_STORE);
            serviceConnState = CONN_STATE_DISCONNECTED;
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");

        if(mThumbnailLoader!=null) {
            mThumbnailLoader.stopLoader();
            photoView.stopLoader();
            tryToStopSpiderService();
            storageInfo.stopRefresh();
        }
        singleThreadPoolTimer.shutdown();
    }

    // 返回至SelSrcActivity
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch(requestCode){
            case RESULT_SRC_URL:
                if(resultCode == RESULT_CANCELED) {
                    Log.i(TAG, "REQUEST_SRC_URL cancelled!");
                } else {
                    if(data != null) {
                        try {
                            newUrl = new URL(data.getAction());
                            Log.i(TAG, "REQUEST_SRC_URL " + newUrl.toString());
                            if(projectListHasLoaded) {
                                processNewUrl();
                            }
                        } catch(MalformedURLException e) {
                            e.printStackTrace();
                        }
                    }
                }
                break;
        }
    }

    private void processNewUrl(){
        String host = newUrl.getHost();
        downloadingProjectSrcUrl = newUrl.toString();
        newUrl=null;
        int albumIndex = spiderProject.findIndexBySite(host);
        if(albumIndex == SpiderProject.INVALID_INDEX) {
            showSelStoAlert(host);
        } else {
            openStartProject(albumIndex);
        }
    }

    private void openStartProject(int index) {
        openAlbum(index);
        startProject(index);
    }

    private void openStartNewProject(String host, String storagePath) {
        int albumIndex = spiderProject.projectList.size();
        long[] imgInfo = new long[StaticValue.IMG_PARA_NUM];
        long[] pageInfo = new long[StaticValue.PAGE_PARA_NUM];
        File appDir=new File(storagePath+"/"+getString(R.string.appPackageName));
        if((!appDir.isDirectory())||(!appDir.exists())){
            appDir.mkdirs();
        }
        spiderProject.projectList.add(new SpiderProject.ProjectInfo(
                host, appDir.getPath() + "/" + host, imgInfo, pageInfo));
        openStartProject(albumIndex);
    }

    private void showSelStoAlert(final String host) {
        storageInfo.getStorageDir(new StorageUtils.OnGottenStorageDirListener() {
            @Override
            public void onGotten(final LinkedList<StorageUtils.StorageDir> storageDirs) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        int stoNum = storageDirs.size();
                        if(stoNum != 0) {
                            if(stoNum == 1) {
                                openStartNewProject(host, storageDirs.get(0).path);
                            } else {
                                String[] storageInfo = new String[stoNum];
                                for(int i = 0; i < stoNum; i++) {
                                    String deviceName = getString((i == 0) ? R.string.deviceStorage : R.string.sdcardStorage);
                                    String totalSpace = getString(R.string.totalSpace) + Utils.byteSizeToString(storageDirs.get(i).totalSpace);
                                    String freeSpace = getString(R.string.freeSpace) + Utils.byteSizeToString(storageDirs.get(i).freeSpace);

                                    storageInfo[i] = deviceName + "  " + totalSpace + " " + freeSpace;
                                }
                                AlertDialog selStorageAlert=new AlertDialog.Builder(SpiderActivity.this)
                                        .setTitle(R.string.selStorageDevice)
                                        .setItems(storageInfo,
                                                new DialogInterface.OnClickListener() {
                                                    public void onClick(DialogInterface dialog,
                                                                        int whichButton) {
                                                        openStartNewProject(host, storageDirs.get(whichButton).path);
                                                    }
                                                })
                                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                            @Override
                                            public void onCancel(DialogInterface dialogInterface) {
                                                buttonShowAnim(buttonAdd, true);
                                            }
                                        }).create();
                                selStorageAlert.show();
                            }
                        } else {
                            new AlertDialog.Builder(SpiderActivity.this)
                                    .setTitle(R.string.badExternalStoragePrompt)
                                    .setMessage(R.string.notFindValidStorage)
                                    .setPositiveButton(R.string.OK, null)
                                    .create().show();
                        }
                    }
                });
            }
        });
    }

    private void deleteProject() {
        if(serviceConnState != CONN_STATE_DISCONNECTED) {
            sendCmdToSpiderService(StaticValue.CMD_JUST_STOP);
            inDeleting = true;
            dialogDelete = new AlertDialog.Builder(SpiderActivity.this)
                    .setTitle(getString(R.string.inDeletingTips))
                    .setView(LayoutInflater.from(SpiderActivity.this).inflate(R.layout.delete_dialog, null))
                    .setPositiveButton(R.string.OK, null)
                    .create();
            dialogDelete.show();
        }
    }

    private void setView(int view) {
        infoDrawer.setDrawer(view);
        curView = view;

        buttonShowAnim(buttonAdd, (view == ALBUM_SET_VIEW));
        showButtonMenu(view != ALBUM_SET_VIEW);

        glRootView.setContentPane((view == PHOTO_VIEW) ? photoView : slotView);
    }

    private void openSelSrcBrowser(String urlToOpen) {
        Intent intent = new Intent(SpiderActivity.this, SelSrcActivity.class);
        if(urlToOpen != null) {
            intent.putExtra(StaticValue.EXTRA_URL_TO_OPEN, urlToOpen);
        }
        startActivityForResult(intent, RESULT_SRC_URL);
    }

    private class InfoDrawer {

        private DrawerLayout drawer;

        private LinearLayout albumInfoDrawer;
        private LinearLayout albumSetInfoDrawer;

        private View[] views = new View[2];

        private LinearLayout downloadingInfo;

        private TextView text_project_site;
        private TextView image_download_num;
        private TextView image_processed;
        private TextView image_total;
        private TextView storage_total;
        private TextView storage_free;
        private TextView image_total_size;
        private TextView image_download_payload;
        private TextView image_tree_height;
        private TextView page_scaned_num;
        private TextView page_total;
        private TextView page_tree_height;
        private TextView page_load_time;
        private TextView page_scan_time;
        private TextView page_search_time;
        private TextView download_speed;
        private TextView ram_sys_total;
        private TextView ram_sys_free;
        private TextView ram_activity_vm;
        private TextView ram_activity_native;
        private TextView ram_service_vm;
        private TextView ram_service_native;
        private TextView cur_page_url;
        private TextView cur_page_title;

        private String totalMemString = MemoryInfo.getTotalMemInMb() + "M";

        public InfoDrawer() {
            drawer = (DrawerLayout) findViewById(R.id.main_drawer);
            albumInfoDrawer = (LinearLayout) findViewById(R.id.album_info_drawer);

            albumInfoDrawer.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {

                }
            });

            views[0] = albumSetInfoDrawer;
            views[1] = albumInfoDrawer;

            downloadingInfo = (LinearLayout) findViewById(R.id.downloading_info);

            text_project_site = (TextView) findViewById(R.id.text_project_site);
            image_download_num = (TextView) findViewById(R.id.image_download_num);
            image_processed = (TextView) findViewById(R.id.image_processed);
            image_total = (TextView) findViewById(R.id.image_total);
            image_total_size = (TextView) findViewById(R.id.image_total_size);
            storage_total = (TextView) findViewById(R.id.storage_total);
            storage_free = (TextView) findViewById(R.id.storage_free);
            image_download_payload = (TextView) findViewById(R.id.image_download_payload);
            image_tree_height = (TextView) findViewById(R.id.image_tree_height);
            page_scaned_num = (TextView) findViewById(R.id.page_processed);
            page_total = (TextView) findViewById(R.id.page_total);
            page_tree_height = (TextView) findViewById(R.id.page_tree_height);
            page_load_time = (TextView) findViewById(R.id.page_load_time);
            page_scan_time = (TextView) findViewById(R.id.page_scan_time);
            page_search_time = (TextView) findViewById(R.id.page_search_time);
            download_speed = (TextView) findViewById(R.id.download_speed);
            ram_sys_total = (TextView) findViewById(R.id.ram_sys_total);
            ram_sys_free = (TextView) findViewById(R.id.ram_sys_free);
            ram_activity_vm = (TextView) findViewById(R.id.ram_activity_vm);
            ram_activity_native = (TextView) findViewById(R.id.ram_activity_native);
            ram_service_vm = (TextView) findViewById(R.id.ram_service_vm);
            ram_service_native = (TextView) findViewById(R.id.ram_service_native);
            cur_page_url = (TextView) findViewById(R.id.cur_page_url);
            cur_page_title = (TextView) findViewById(R.id.cur_page_title);

            Button open_cur_page = (Button) findViewById(R.id.open_cur_page);
            open_cur_page.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    String curUrl = cur_page_url.getText().toString();
                    if(curUrl.startsWith("http")) {
                        openSelSrcBrowser(curUrl);
                    }
                }
            });
        }

        public void initDownloadingInfo() {
            page_load_time.setText("");
            page_scan_time.setText("");
            page_search_time.setText("");

            image_download_payload.setText("");
            download_speed.setText("");

            ram_service_vm.setText("");
            ram_service_native.setText("");
            cur_page_url.setText("");
            cur_page_title.setText("");
        }

        public void displayDownloadingInfo() {
            downloadingInfo.setVisibility(View.VISIBLE);
        }

        public void onDisplayProjectChanged() {
            downloadingInfo.setVisibility(displayProjectIndex == downloadingProjectIndex ?
                    View.VISIBLE : View.GONE);

            text_project_site.setText(displayProjectInfo.host);
            storage_total.setText(displayProjectInfo.dirTotalSpace);
            refreshBasicInfo();
            refreshMemoryInfo();
        }

        public void setDrawer(int viewIndex) {
            drawer.setDrawerLockMode(viewIndex == ALBUM_VIEW ? DrawerLayout.LOCK_MODE_UNLOCKED :
                    DrawerLayout.LOCK_MODE_LOCKED_CLOSED, albumInfoDrawer);
        }

        public void onInfoKey() {
            if(curView == ALBUM_VIEW) {
                View curInfoView = views[curView];
                if(drawer.isDrawerOpen(curInfoView)) {
                    drawer.closeDrawer(curInfoView);
                } else {
                    drawer.openDrawer(curInfoView);
                }
            }
        }

        public void refreshMemoryInfo() {
            storage_free.setText(Utils.byteSizeToString(storageInfo.getFreeSpace(displayProjectInfo.dir.getPath())));

            ram_sys_total.setText(totalMemString);
            ram_sys_free.setText(MemoryInfo.getFreeMemInMb(SpiderActivity.this) + "M");
            ram_activity_vm.setText((Runtime.getRuntime().totalMemory() >> 10) + "K");
            ram_activity_native.setText((Debug.getNativeHeapSize() >> 10) + "K");
        }

        private void refreshBasicInfo() {
            image_download_num.setText(String.valueOf(displayProjectInfo.imgDownloadNum));
            image_processed.setText(String.valueOf(displayProjectInfo.imgProcessedNum));
            image_total.setText(String.valueOf(displayProjectInfo.imgTotalNum));
            image_tree_height.setText(String.valueOf(displayProjectInfo.imgTreeHeight));

            page_scaned_num.setText(String.valueOf(displayProjectInfo.pageProcessedNum));
            page_total.setText(String.valueOf(displayProjectInfo.pageTotalNum));
            page_tree_height.setText(String.valueOf(displayProjectInfo.pageTreeHeight));

            image_total_size.setText(Utils.byteSizeToString(displayProjectInfo.imgTotalSize));
        }

        public void onSpiderStop() {
            download_speed.setText("0KB/s");
            image_download_payload.setText("0");
            ram_service_vm.setText("0K");
            ram_service_native.setText("0K");
        }

        public void refreshInfoByServiceReport(JSONObject json) {
            try {
                displayProjectInfo.imgDownloadNum = json.getInt("imgDownloadNum");
                displayProjectInfo.imgProcessedNum = json.getInt("imgProcessedNum");
                displayProjectInfo.imgTotalNum = json.getInt("imgTotalNum");
                displayProjectInfo.imgTotalSize = json.getLong("imgTotalSize");
                displayProjectInfo.imgTreeHeight = json.getInt("imgTreeHeight");

                displayProjectInfo.pageProcessedNum = json.getInt("pageProcessedNum");
                displayProjectInfo.pageTotalNum = json.getInt("pageTotalNum");
                displayProjectInfo.pageTreeHeight = json.getInt("pageTreeHeight");

                refreshBasicInfo();

                page_load_time.setText(String.valueOf(json.getInt("pageLoadTime")) + "ms");
                page_scan_time.setText(String.valueOf(json.getInt("pageScanTime")) + "ms");
                page_search_time.setText(String.valueOf(json.getInt("pageSearchTime")) + "ms");

                image_download_payload.setText(String.valueOf(json.getInt("imgDownloaderPayload")));
                download_speed.setText(json.getString("curNetSpeed"));

                ram_service_vm.setText(json.getInt("serviceVmMem") + "K ");
                ram_service_native.setText(json.getInt("serviceNativeMem") + "K");
                cur_page_url.setText(json.getString("curPageUrl"));
                cur_page_title.setText(json.getString("curPageTitle"));

            } catch(JSONException e) {
                e.printStackTrace();
            }

        }

    }

    private void dispProjectState(ProjectState state) {
        if(state == ProjectState.CHECK || state == ProjectState.DOWNLOADING) {
            buttonProjectCtrl.setImageResource(R.drawable.pause);
        } else {
            buttonProjectCtrl.setImageResource(R.drawable.start);
        }

        textViewProjectState.setText(projectStateDesc[state.ordinal()]);
    }

    private void setProjectState(ProjectState state) {
        projectState = state;
        dispProjectState(state);
    }

    private void panelViewInit() {
        buttonAdd = (ImageButton) findViewById(R.id.button_add);
        buttonAdd.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openSelSrcBrowser(null);
            }
        });
        buttonAdd.setVisibility(View.INVISIBLE);

        buttonMenu = (ImageButton) findViewById(R.id.button_menu);
        buttonMenu.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                infoDrawer.onInfoKey();
            }
        });
        buttonMenu.setVisibility(View.INVISIBLE);

        View.OnFocusChangeListener onFocusChangeListener=new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean focus) {
                view.setBackgroundResource(focus?
                        R.drawable.bottom_button_focus_background:R.drawable.bottom_button_blur_background);
            }
        };
        buttonAdd.setOnFocusChangeListener(onFocusChangeListener);
        buttonMenu.setOnFocusChangeListener(onFocusChangeListener);


        buttonProjectCtrl = (ImageButton) findViewById(R.id.project_ctrl);
        buttonProjectCtrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(displayProjectIndex == downloadingProjectIndex) {
                    switch(projectState) {
                        case DOWNLOADING:
                            setProjectState(ProjectState.PAUSE);
                            sendCmdToSpiderService(StaticValue.CMD_PAUSE);
                            break;

                        case PAUSE:
                            setProjectState(ProjectState.CHECK);
                            checkAndStart(false);
                            break;

                        case CHECK:
                            setProjectState(ProjectState.PAUSE);
                            break;
                    }
                } else {
                    startProject(displayProjectIndex);
                }
            }
        });

        projectStateDesc = getResources().getStringArray(R.array.project_state);
        textViewProjectState = (TextView) findViewById(R.id.project_state);

        setProjectState(ProjectState.PAUSE);

        infoDrawer = new InfoDrawer();
        infoDrawer.setDrawer(curView);
    }

    private void buttonShowAnim(final View view, final boolean isShow) {
        Animation lastAnimation=view.getAnimation();
        if(lastAnimation!=null){
            lastAnimation.cancel();
        }

        float fromY = isShow ? 1 : 0;
        TranslateAnimation animation = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, fromY,
                Animation.RELATIVE_TO_SELF, 1 - fromY);
        animation.setDuration(BUTTON_MENU_ANIMATION_TIME);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                if(isShow)
                    view.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if(!isShow)
                    view.setVisibility(View.GONE);
            }
        });
        view.startAnimation(animation);

    }

    private void showButtonMenu(boolean isShow) {
        buttonMenuDisplayTime.set(isShow?BUTTON_MENU_DISPLAY_SECOND:0);
        buttonShowAnim(buttonMenu, isShow);
    }

    private void unbindSpiderService() {
        // If we have received the service, and hence registered with
        // it, then now is the time to unregister.
        Log.i(TAG, "unbind SpiderService");
        if(mService != null) {
            try {
                mService.unregisterCallback(mCallback);
            } catch(RemoteException e) {
                // There is nothing special we need to do if the service
                // has crashed.
            }

            // Detach our existing connection.

            if(serviceBindSuccess) {
                unbindService(mConnection);
                serviceBindSuccess = false;
            }
        }

    }

    boolean serviceBindSuccess = false;

    private void sendCmdToSpiderService(int cmd) {
        Log.i(TAG, "sendCmdToSpiderService " + StaticValue.CMD_DESC[cmd]);
        Intent spiderIntent = new Intent(IRemoteSpiderService.class.getName());
        spiderIntent.setPackage(IRemoteSpiderService.class.getPackage()
                .getName());

        Bundle bundle = new Bundle();
        bundle.putInt(StaticValue.BUNDLE_KEY_CMD, cmd);

        if(cmd == StaticValue.CMD_START) {
            if(downloadingProjectSrcUrl != null) {
                try {
                    URL srcUrl=new URL(downloadingProjectSrcUrl);
                    if(!downloadingProjectInfo.host.equals(srcUrl.getHost())){
                        return;
                    }else {
                        bundle.putString(StaticValue.BUNDLE_KEY_SOURCE_URL, downloadingProjectSrcUrl);
                        downloadingProjectSrcUrl = null;
                    }
                } catch(MalformedURLException e) {
                    e.printStackTrace();
                }

            }
            bundle.putString(StaticValue.BUNDLE_KEY_PROJECT_PATH, downloadingProjectInfo.dir.getPath());
        }

        spiderIntent.putExtras(bundle);
        startService(spiderIntent);

        if(cmd == StaticValue.CMD_START) {
            serviceBindSuccess = bindService(spiderIntent, mConnection, BIND_ABOVE_CLIENT);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        Log.i(TAG, (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) ?
                "Landscape" : "Portrait");
    }

    private long exitTim = 0;

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.i(TAG, "onKeyDown " + keyCode);

        switch(keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if(curView == ALBUM_SET_VIEW) {
                    if(inDeleting) {
                        Toast.makeText(this, R.string.inDeletingToast, Toast.LENGTH_SHORT).show();
                        return true;
                    }

                    if(SystemClock.uptimeMillis() - exitTim > 2000) {
                        Toast.makeText(this, getString(R.string.keyBackExitConfirm)
                                + getString(R.string.app_name), Toast.LENGTH_SHORT).show();

                        exitTim = SystemClock.uptimeMillis();
                        return true;
                    } else {
                        Log.i(TAG, "finish");
                        //Sometimes onDestory() will not been called.So handle spider service here.
                        tryToStopSpiderService();
                    }
                } else if(curView == ALBUM_VIEW) {
                    backToAlbumSetView();
                    return true;
                } else if(curView == PHOTO_VIEW) {
                    backToAlbumView();
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_MENU:
                infoDrawer.onInfoKey();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                glRootView.requestFocus();
                getCurGLView().onKeyDown(keyCode, false);
                return true;
        }

        return super.onKeyDown(keyCode, event);
    }
}
