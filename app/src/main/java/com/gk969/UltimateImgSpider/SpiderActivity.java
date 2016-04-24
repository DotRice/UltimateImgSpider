package com.gk969.UltimateImgSpider;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.gk969.Utils.MemoryInfo;
import com.gk969.Utils.Utils;
import com.gk969.View.ImageTextButton;
import com.gk969.gallery.gallery3d.glrenderer.GLCanvas;
import com.gk969.gallery.gallery3d.glrenderer.TiledTexture;
import com.gk969.gallery.gallery3d.ui.GLRootView;
import com.gk969.gallery.gallery3d.ui.GLView;
import com.gk969.gallery.gallery3d.util.GalleryUtils;
import com.gk969.gallerySimple.AlbumLoaderHelper;
import com.gk969.gallerySimple.AlbumSetLoaderHelper;
import com.gk969.gallerySimple.ThumbnailLoader;
import com.gk969.gallerySimple.SlotView;
import com.gk969.gallerySimple.ThumbnailLoaderHelper;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
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

public class SpiderActivity extends Activity
{
    private final static String TAG = "SpiderActivity";
    public final static int REQUST_SRC_URL = 0;


    private static final int CONN_STATE_IDLE = 0;
    private static final int CONN_STATE_CONNECTED = 1;
    private static final int CONN_STATE_WAIT_DISCONNECT = 2;
    private static final int CONN_STATE_WAIT_CONNECT = 3;
    private static final int CONN_STATE_DISCONNECTED = 4;

    private int serviceConnState = CONN_STATE_DISCONNECTED;

    private ImageButton btnPauseOrContinue;

    private int downloadingProjectIndex;
    private URL downloadingProjectSrcUrl;
    private String downloadingProjectPath;

    private int displayProjectIndex;
    private String displayProjectPath;
    
    private File appPath;


    private MessageHandler mHandler = new MessageHandler(this);

    private static final int BUMP_MSG = 1;

    GLRootView glRootView;

    private ThumbnailLoader mThumbnailLoader;
    private AlbumLoaderHelper albumLoaderHelper;
    private AlbumSetLoaderHelper albumSetLoaderHelper;

    private AlbumSetLoaderHelper.ProjectInfo displayProjectInfo;

    private ServiceConnection mConnection;
    private IRemoteSpiderServiceCallback mCallback;

    private boolean inDeleting = false;


    enum ProjectState
    {
        PAUSE, CHECK, DOWNLOADING, COMPLETE
    }
    private ProjectState projectState=ProjectState.PAUSE;

    public static final int ALBUM_SET_VIEW=0;
    public static final int ALBUM_VIEW=1;

    private int curView;

    private InfoDrawer infoDrawer;
    private ProjectBar projectBar;



    private void serviceInterfaceInit()
    {
        mConnection = new ServiceConnection()
        {
            public void onServiceConnected(ComponentName className,
                                           IBinder service)
            {
                mService = IRemoteSpiderService.Stub.asInterface(service);

                try
                {
                    mService.registerCallback(mCallback);
                } catch (RemoteException e)
                {
                    e.printStackTrace();
                }

                serviceConnState = CONN_STATE_CONNECTED;
                Log.i(TAG, "onServiceConnected");
            }

            public void onServiceDisconnected(ComponentName className)
            {
                mService = null;

                Log.i(TAG, "onServiceDisconnected");

                if (serviceConnState == CONN_STATE_WAIT_DISCONNECT)
                {
                    Log.i(TAG, "prepare restart service");
                    mHandler.postDelayed(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            serviceConnState = CONN_STATE_WAIT_CONNECT;
                            sendCmdToSpiderService(StaticValue.CMD_START);
                        }
                    }, 500);
                }
                else
                {
                    serviceConnState = CONN_STATE_DISCONNECTED;

                    if (inDeleting)
                    {
                        new Thread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                Utils.deleteDir(downloadingProjectPath);
                                mHandler.post(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        btnPauseOrContinue.setImageResource(R.drawable.start);
                                        Toast.makeText(SpiderActivity.this, R.string.deletedToast,
                                                Toast.LENGTH_SHORT).show();
                                        mThumbnailLoader.setAlbumTotalImgNum(0);
                                        inDeleting = false;
                                        dialogDelete.dismiss();
                                    }
                                });
                            }
                        }).start();
                    }
                }
            }
        };

        mCallback = new IRemoteSpiderServiceCallback.Stub()
        {
            /**
             * Note that IPC calls are dispatched through a thread pool running
             * in each process, so the code executing here will NOT be running
             * in our main thread like most other things -- so, to update the
             * UI, we need to use a Handler to hop over there.
             */
            public void reportStatus(String value)
            {
                mHandler.sendMessage(mHandler.obtainMessage(BUMP_MSG, value));
            }
        };
    }


    private static class MessageHandler extends Handler
    {
        WeakReference<SpiderActivity> mActivity;

        MessageHandler(SpiderActivity activity)
        {
            mActivity = new WeakReference<SpiderActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg)
        {
            final SpiderActivity theActivity = mActivity.get();
            switch (msg.what)
            {
                case BUMP_MSG:
                    if (theActivity == null)
                    {
                        break;
                    }

                    String jsonReportStr = (String) msg.obj;

                    //Log.i(SpiderActivity.TAG, jsonReportStr);
                    try
                    {
                        long freeMem = MemoryInfo.getFreeMemInMb(theActivity);
                        jsonReportStr += "\"sysTotalMem\":\"" + MemoryInfo.getTotalMemInMb() + "M\",\r\n"
                                + "\"sysFreeMem\":\"" + freeMem + "M\",\r\n"
                                + "\"activityVmMem\":\"" + (Runtime.getRuntime().totalMemory() >> 10) + "K\",\n"
                                + "\"activityNativeMem\":\"" + (Debug.getNativeHeapSize() >> 10) + "K\"\r\n}";

                        JSONObject jsonReport = new JSONObject(jsonReportStr);

                        int imgDownloadNum=jsonReport.getInt("imgDownloadNum");
                        if(theActivity.displayProjectIndex==theActivity.downloadingProjectIndex)
                        {
                            theActivity.infoDrawer.refreshInfoByServiceReport(jsonReport);
                            theActivity.mThumbnailLoader.setAlbumTotalImgNum(imgDownloadNum);
                        }

                        theActivity.refreshDownloadingProjectInfo(String.valueOf(imgDownloadNum));

                        int serviceNativeMem = jsonReport.getInt("serviceNativeMem") >> 10;

                        if (jsonReport.getBoolean("siteScanCompleted"))
                        {
                            theActivity.projectState=ProjectState.COMPLETE;
                            theActivity.btnPauseOrContinue.setImageResource(R.drawable.start);
                        }
                        else if (freeMem < 50 || serviceNativeMem > 50)
                        {
                            theActivity.serviceConnState = theActivity.CONN_STATE_WAIT_DISCONNECT;
                            theActivity.sendCmdToSpiderService(StaticValue.CMD_RESTART);

                        }

                    } catch (JSONException e)
                    {
                        e.printStackTrace();
                    }

                    break;
                default:
                    super.handleMessage(msg);
            }
        }

    }

    public Dialog sysFaultAlert(String title, String desc, final boolean exit)
    {
        return new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(desc)
                .setPositiveButton(exit ? R.string.exit : R.string.OK,
                        new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog,
                                                int whichButton)
                            {
                                if (exit)
                                {
                                    SpiderActivity.this.finish();
                                }
                            }
                        })
                .setNegativeButton(R.string.retry,
                        new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which)
                            {
                                checkAndStart();
                            }
                        }).create();
    }

    private final static int DLG_NETWORK_PROMPT = 0;
    private final static int DLG_STORAGE_ERROR = 1;
    private final static int DLG_DELETE = 2;

    private Dialog dialogDelete;

    protected Dialog onCreateDialog(int dlgId)
    {
        switch (dlgId)
        {
            case DLG_NETWORK_PROMPT:
            {
                return sysFaultAlert(getString(R.string.prompt),
                        getString(R.string.uneffectiveNetworkPrompt), false);
            }

            case DLG_STORAGE_ERROR:
            {
                return sysFaultAlert(getString(R.string.prompt),
                        getString(R.string.badExternalStoragePrompt), true);
            }

            case DLG_DELETE:
            {
                return dialogDelete;
            }
        }

        return null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spider);

        Log.i(TAG, "onCreate");

        onFirstRun();

        appPath = Utils.getDirInExtSto(getString(R.string.appPackageName));

        serviceInterfaceInit();
        checkStorage();
        panelViewInit();
        albumViewInit();
    }

    private boolean startProject(int index, String srcUrl)
    {
        Log.i(TAG, "startProject " + index);
        try
        {
            URL newUrl = new URL((srcUrl==null)?
                    ("http://"+albumSetLoaderHelper.projectList.get(index).site+"/"):srcUrl);

            String newPath=appPath + "/" + newUrl.getHost();

            downloadingProjectIndex=index;

            boolean isNewProject=false;
            if(!newPath.equals(downloadingProjectPath))
            {
                downloadingProjectSrcUrl = newUrl;
                downloadingProjectPath = newPath;
                isNewProject=true;
            }

            projectState = ProjectState.CHECK;
            btnPauseOrContinue.setImageResource(R.drawable.pause);
            checkNetworkBeforeStart(isNewProject);
            infoDrawer.onDisplayProjectChanged();

            return true;
        } catch(MalformedURLException e)
        {
            e.printStackTrace();
        }

        return false;
    }
    
    private void openAlbum(int index)
    {
        displayProjectIndex=index;
        displayProjectInfo=albumSetLoaderHelper.projectList.get(displayProjectIndex);
        displayProjectPath=appPath.getPath()+"/"+displayProjectInfo.site;
        albumLoaderHelper.setProjectPath(displayProjectPath);
        mThumbnailLoader.setHelper(albumLoaderHelper, (int)displayProjectInfo.imgInfo[StaticValue.PARA_DOWNLOAD]);
        infoDrawer.onDisplayProjectChanged();

        btnPauseOrContinue.setImageResource((displayProjectIndex != downloadingProjectIndex ||
                (projectState == ProjectState.PAUSE || projectState == ProjectState.COMPLETE)) ?
                R.drawable.start : R.drawable.pause);
        
        setView(ALBUM_VIEW);
    }

    private void backToAlbumSetView()
    {
        displayProjectIndex=AlbumSetLoaderHelper.INVALID_INDEX;
        if(projectState==ProjectState.DOWNLOADING)
        {
            mThumbnailLoader.refreshSlotInfo(downloadingProjectIndex, "", true);
        }
        else
        {
            mThumbnailLoader.refreshSlotInfo(StaticValue.INDEX_INVALID, null, false);
        }

        mThumbnailLoader.setHelper(albumSetLoaderHelper, albumSetLoaderHelper.projectList.size());
        setView(ALBUM_SET_VIEW);
    }

    public void refreshDownloadingProjectInfo(String infoStr)
    {
        if(curView==ALBUM_SET_VIEW)
        {
            mThumbnailLoader.refreshSlotInfo(downloadingProjectIndex, infoStr,
                    projectState==ProjectState.DOWNLOADING);
            glRootView.requestRender();
        }
    }

    private void albumViewInit()
    {
        glRootView = (GLRootView) findViewById(R.id.gl_root_view);

        albumLoaderHelper = new AlbumLoaderHelper(downloadingProjectPath);
        albumSetLoaderHelper = new AlbumSetLoaderHelper(appPath.getPath());
        mThumbnailLoader = new ThumbnailLoader(glRootView, albumSetLoaderHelper);
        SlotView slotView = new SlotView(this, mThumbnailLoader, glRootView);
        slotView.setOnClick(new SlotView.OnClickListener()
        {
            @Override
            public void onClick(int slotIndex)
            {
                Log.i(TAG, "Clicked "+slotIndex);
                if(curView==ALBUM_SET_VIEW)
                {
                    openAlbum(slotIndex);
                }
            }
        });

        glRootView.setContentPane(slotView);
        downloadingProjectIndex=AlbumSetLoaderHelper.INVALID_INDEX;
    }

    private void checkStorage()
    {
        File appDir = Utils.getDirInExtSto(getString(R.string.appPackageName));
        if (appDir == null)
        {
            showDialog(DLG_STORAGE_ERROR);
            return;
        }
    }

    private void checkAndStart()
    {
        checkStorage();
        checkNetworkBeforeStart(false);
    }


    private void checkNetworkBeforeStart(final boolean isNewProject)
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                Runnable networkValid = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        if(projectState==ProjectState.CHECK)
                        {
                            projectState=ProjectState.DOWNLOADING;
                            
                            if (serviceConnState == CONN_STATE_CONNECTED || serviceConnState == CONN_STATE_WAIT_CONNECT)
                            {
                                if(isNewProject)
                                {
                                    sendCmdToSpiderService(StaticValue.CMD_STOP_STORE);
                                    serviceConnState = CONN_STATE_WAIT_DISCONNECT;
                                }
                                else
                                {
                                    sendCmdToSpiderService(StaticValue.CMD_START);
                                }
                            }
                            else if (serviceConnState == CONN_STATE_DISCONNECTED || serviceConnState == CONN_STATE_WAIT_DISCONNECT)
                            {
                                sendCmdToSpiderService(StaticValue.CMD_START);
                                serviceConnState = CONN_STATE_WAIT_CONNECT;
                            }
                        }
                    }
                };

                Runnable networkInvalid = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        showDialog(DLG_NETWORK_PROMPT);
                    }
                };
                mHandler.post(Utils.isNetworkEffective() ? networkValid : networkInvalid);
            }
        }).start();
    }

    private void onFirstRun()
    {
        if (ParaConfig.isFirstRun(getApplicationContext()))
        {
            Toast.makeText(this, "first run", Toast.LENGTH_SHORT).show();
            ParaConfig.setFirstRun(getApplicationContext());
        }
    }

    protected void onStart()
    {
        super.onStart();
        Log.i(TAG, "onStart");
    }

    protected void onResume()
    {
        super.onResume();
        mThumbnailLoader.onResume();
        Log.i(TAG, "onResume");

    }

    protected void onPause()
    {
        super.onPause();
        mThumbnailLoader.onPause();
        Log.i(TAG, "onPause");
    }

    protected void onStop()
    {
        super.onStop();
        Log.i(TAG, "onStop");

    }

    private void tryToStopSpiderService()
    {
        Log.i(TAG, "tryToStopSpiderService " + serviceConnState + " " + CONN_STATE_DISCONNECTED);
        if (serviceConnState != CONN_STATE_DISCONNECTED)
        {
            unbindSpiderService();
            sendCmdToSpiderService(StaticValue.CMD_STOP_STORE);
            serviceConnState = CONN_STATE_DISCONNECTED;
        }
    }

    protected void onDestroy()
    {
        super.onDestroy();
        Log.i(TAG, "onDestroy");

        mThumbnailLoader.stopLoader();
        tryToStopSpiderService();
    }

    // 返回至SelSrcActivity
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {

        if (requestCode == REQUST_SRC_URL)
        {
            if (resultCode == RESULT_CANCELED)
            {
                Log.i(TAG, "REQUST_SRC_URL cancelled!");
            }
            else
            {
                if (data != null)
                {
                    try
                    {
                        URL newUrl=new URL(data.getAction());

                        Log.i(TAG, "REQUST_SRC_URL " + newUrl.toString());
                        int albumIndex = albumSetLoaderHelper.findIndexBySite(newUrl.getHost());
                        if(albumIndex == AlbumSetLoaderHelper.INVALID_INDEX)
                        {
                            albumIndex=albumSetLoaderHelper.projectList.size();
                            long[] imgInfo = new long[StaticValue.IMG_PARA_NUM];
                            long[] pageInfo = new long[StaticValue.PAGE_PARA_NUM];
                            albumSetLoaderHelper.projectList.add(new AlbumSetLoaderHelper.ProjectInfo(
                                    newUrl.getHost(), imgInfo, pageInfo));
                        }

                        openAlbum(albumIndex);
                        startProject(albumIndex, newUrl.toString());
                    } catch(MalformedURLException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void deleteProject()
    {
        if (serviceConnState != CONN_STATE_DISCONNECTED)
        {
            sendCmdToSpiderService(StaticValue.CMD_JUST_STOP);
            inDeleting = true;
            if (dialogDelete == null)
            {
                dialogDelete = new AlertDialog.Builder(SpiderActivity.this)
                        .setTitle(getString(R.string.inDeletingTips))
                        .setView(LayoutInflater.from(SpiderActivity.this).inflate(R.layout.delete_dialog, null))
                        .setPositiveButton(R.string.OK, null)
                        .create();
            }
            showDialog(DLG_DELETE);
        }
    }

    private void setView(int view)
    {
        infoDrawer.setDrawer(view);
        projectBar.setBarView(view);
        curView=view;
    }

    private class ProjectBar
    {
        private LinearLayout projectBarLayout;

        public ProjectBar()
        {
            projectBarLayout=(LinearLayout)findViewById(R.id.project_bar);

            ImageButton btnAdd=(ImageButton)findViewById(R.id.button_add_project);
            btnAdd.setOnClickListener(new OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    startActivityForResult(new Intent(SpiderActivity.this, SelSrcActivity.class),
                            REQUST_SRC_URL);
                }
            });

            btnPauseOrContinue = (ImageButton) findViewById(R.id.buttonPauseOrContinue);

            btnPauseOrContinue.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    if(displayProjectIndex==downloadingProjectIndex)
                    {
                        switch(projectState)
                        {
                            case DOWNLOADING:
                                projectState = ProjectState.PAUSE;
                                btnPauseOrContinue.setImageResource(R.drawable.start);
                                sendCmdToSpiderService(StaticValue.CMD_PAUSE);
                                break;

                            case PAUSE:
                                projectState = ProjectState.CHECK;
                                btnPauseOrContinue.setImageResource(R.drawable.pause);
                                checkNetworkBeforeStart(false);
                                break;

                            case CHECK:
                                projectState = ProjectState.PAUSE;
                                btnPauseOrContinue.setImageResource(R.drawable.start);
                                break;
                        }
                    }
                    else
                    {
                        startProject(displayProjectIndex, null);
                    }
                }
            });

            ImageButton buttonInfo = (ImageButton) findViewById(R.id.buttonInfo);
            buttonInfo.setOnClickListener(new OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    infoDrawer.openDrawer(ALBUM_VIEW);
                }
            });

            ImageButton buttonSetting=(ImageButton)findViewById(R.id.buttonSetting);
            buttonSetting.setOnClickListener(new OnClickListener()
            {
                @Override
                public void onClick(View v)
                {

                }
            });

        }

        public void setBarView(final int view)
        {
            if(curView!=view)
            {
                projectBarLayout.setTranslationY(0 - projectBarLayout.getHeight() / 2 * view);
            }
        }
    }

    private class InfoDrawer
    {
        
        private DrawerLayout drawer;
        
        private LinearLayout albumInfoDrawer;
        private LinearLayout albumSetInfoDrawer;
        
        private View[] views=new View[2];

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
        private TextView cur_page;

        public InfoDrawer()
        {
            drawer = (DrawerLayout) findViewById(R.id.main_drawer);
            albumInfoDrawer = (LinearLayout)findViewById(R.id.album_info_drawer);

            views[0]=albumSetInfoDrawer;
            views[1]=albumInfoDrawer;

            downloadingInfo=(LinearLayout)findViewById(R.id.downloading_info);

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
            cur_page = (TextView) findViewById(R.id.cur_page);
        }

        public void onDisplayProjectChanged()
        {
            downloadingInfo.setVisibility(displayProjectIndex==downloadingProjectIndex ?
                    View.VISIBLE:View.GONE);

            text_project_site.setText(displayProjectInfo.site);
            refreshBasicInfo(displayProjectInfo.imgInfo, displayProjectInfo.pageInfo);
        }
        
        public void setDrawer(int viewIndex)
        {
            drawer.setDrawerLockMode(viewIndex==ALBUM_VIEW?DrawerLayout.LOCK_MODE_UNLOCKED:
                    DrawerLayout.LOCK_MODE_LOCKED_CLOSED, albumInfoDrawer);
        }

        public void openDrawer(int viewIndex)
        {
            if(curView==viewIndex)
            {
                drawer.openDrawer(views[viewIndex]);
            }
        }
        
        private void refreshBasicInfo(long[] imgInfo, long[] pageInfo)
        {
            image_download_num.setText(String.valueOf(imgInfo[StaticValue.PARA_DOWNLOAD]));
            image_processed.setText(String.valueOf(imgInfo[StaticValue.PARA_PROCESSED]));
            image_total.setText(String.valueOf(imgInfo[StaticValue.PARA_TOTAL]));
            image_tree_height.setText(String.valueOf(imgInfo[StaticValue.PARA_HEIGHT]));

            page_scaned_num.setText(String.valueOf(pageInfo[StaticValue.PARA_PROCESSED]));
            page_total.setText(String.valueOf(pageInfo[StaticValue.PARA_TOTAL]));
            page_tree_height.setText(String.valueOf(pageInfo[StaticValue.PARA_HEIGHT]));

            image_total_size.setText(Utils.byteSizeToString(imgInfo[StaticValue.PARA_TOTAL_SIZE]));
            storage_total.setText(Utils.byteSizeToString(appPath.getTotalSpace()));
            storage_free.setText(Utils.byteSizeToString(appPath.getFreeSpace()));

        }
        
        public void refreshInfoByServiceReport(JSONObject json)
        {
            try
            {
                long[] imgInfo=displayProjectInfo.imgInfo;
                imgInfo[StaticValue.PARA_DOWNLOAD]=json.getInt("imgDownloadNum");
                imgInfo[StaticValue.PARA_PROCESSED]=json.getInt("imgProcessedNum");
                imgInfo[StaticValue.PARA_TOTAL] = json.getInt("imgTotalNum");
                imgInfo[StaticValue.PARA_TOTAL_SIZE]=json.getLong("imgTotalSize");
                imgInfo[StaticValue.PARA_HEIGHT]=json.getInt("imgTreeHeight");

                long[] pageInfo=displayProjectInfo.pageInfo;
                pageInfo[StaticValue.PARA_PROCESSED]=json.getInt("pageProcessedNum");
                pageInfo[StaticValue.PARA_TOTAL]=json.getInt("pageTotalNum");
                pageInfo[StaticValue.PARA_HEIGHT]=json.getInt("pageTreeHeight");

                refreshBasicInfo(imgInfo, pageInfo);

                page_load_time.setText(String.valueOf(json.getInt("pageLoadTime"))+"ms");
                page_scan_time.setText(String.valueOf(json.getInt("pageScanTime"))+"ms");
                page_search_time.setText(String.valueOf(json.getInt("pageSearchTime"))+"ms");
    
                image_download_payload.setText(String.valueOf(json.getInt("imgDownloaderPayload")));
                download_speed.setText(json.getString("curNetSpeed"));

                ram_sys_total.setText(json.getString("sysTotalMem"));
                ram_sys_free.setText(json.getString("sysFreeMem"));
                ram_activity_vm.setText(json.getString("activityVmMem"));
                ram_activity_native.setText(json.getString("activityNativeMem"));
                ram_service_vm.setText(json.getInt("serviceVmMem")+"K ");
                ram_service_native.setText(json.getInt("serviceNativeMem")+"K");
                cur_page.setText(json.getString("curPage"));

            } catch (JSONException e)
            {
                e.printStackTrace();
            }

        }

    }


    private void panelViewInit()
    {
        infoDrawer = new InfoDrawer();
        projectBar=new ProjectBar();

        setView(ALBUM_SET_VIEW);
    }

    /**
     * The primary interface we will be calling on the service.
     */
    IRemoteSpiderService mService = null;


    private void unbindSpiderService()
    {
        // If we have received the service, and hence registered with
        // it, then now is the time to unregister.
        Log.i(TAG, "unbind SpiderService");
        if (mService != null)
        {
            try
            {
                mService.unregisterCallback(mCallback);
            } catch (RemoteException e)
            {
                // There is nothing special we need to do if the service
                // has crashed.
            }

            // Detach our existing connection.

            if (seviceBindSuccess)
            {
                unbindService(mConnection);
                seviceBindSuccess = false;
            }
        }

    }

    boolean seviceBindSuccess = false;

    private void sendCmdToSpiderService(int cmd)
    {
        Log.i(TAG, "sendCmdToSpiderService " + cmd);
        Intent spiderIntent = new Intent(IRemoteSpiderService.class.getName());
        spiderIntent.setPackage(IRemoteSpiderService.class.getPackage()
                .getName());

        Bundle bundle = new Bundle();
        bundle.putInt(StaticValue.BUNDLE_KEY_CMD, cmd);
        bundle.putString(StaticValue.BUNDLE_KEY_SOURCE_URL, downloadingProjectSrcUrl.toString());
        spiderIntent.putExtras(bundle);
        startService(spiderIntent);

        seviceBindSuccess = bindService(spiderIntent, mConnection, BIND_ABOVE_CLIENT);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);

        Log.i(TAG, (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) ?
                "Landscape" : "Portrait");
    }

    private long exitTim = 0;

    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        Log.i(TAG, "onKeyDown " + keyCode);


        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if(curView==ALBUM_VIEW)
            {
                backToAlbumSetView();
                return true;
            }

            if (inDeleting)
            {
                Toast.makeText(this, R.string.inDeletingToast, Toast.LENGTH_SHORT).show();
                return true;
            }

            if (SystemClock.uptimeMillis() - exitTim > 2000)
            {
                Toast.makeText(this, getString(R.string.keyBackExitConfirm)
                        + getString(R.string.app_name), Toast.LENGTH_SHORT).show();

                exitTim = SystemClock.uptimeMillis();
                return true;
            }
            else
            {
                Log.i(TAG, "finish");
                //Sometimes onDestory() will not been called.So handle spider service here.
                tryToStopSpiderService();
            }

        }

        return super.onKeyDown(keyCode, event);
    }
}
