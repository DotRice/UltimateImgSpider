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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

public class SpiderActivity extends Activity
{
    private final static String TAG = "SpiderActivity";
    public final static int REQUST_SRC_URL = 0;


    private static final int STATE_IDLE = 0;
    private static final int STATE_CONNECTED = 1;
    private static final int STATE_WAIT_DISCONNECT = 2;
    private static final int STATE_WAIT_CONNECT = 3;
    private static final int STATE_DISCONNECTED = 4;

    private int serviceState = STATE_DISCONNECTED;

    private ImageButton btnPauseOrContinue;

    private DrawerInfo infoDrawer;

    private String projectSrcUrl;
    private String projectPath;
    private String appPath;


    private MessageHandler mHandler = new MessageHandler(this);

    private static final int BUMP_MSG = 1;

    private ThumbnailLoader mThumbnailLoader;
    private AlbumLoaderHelper albumLoaderHelper;
    private AlbumSetLoaderHelper albumSetLoaderHelper;


    private ServiceConnection mConnection;
    private IRemoteSpiderServiceCallback mCallback;

    private boolean inDeleting = false;

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

                serviceState = STATE_CONNECTED;
                Log.i(TAG, "onServiceConnected");
            }

            public void onServiceDisconnected(ComponentName className)
            {
                mService = null;

                Log.i(TAG, "onServiceDisconnected");

                if (serviceState == STATE_WAIT_DISCONNECT)
                {
                    Log.i(TAG, "prepare restart service");
                    mHandler.postDelayed(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            serviceState = STATE_WAIT_CONNECT;
                            sendCmdToSpiderService(StaticValue.CMD_START);
                        }
                    }, 500);
                }
                else
                {
                    serviceState = STATE_DISCONNECTED;

                    if (inDeleting)
                    {
                        new Thread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                Utils.deleteDir(projectPath);
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

                        theActivity.infoDrawer.refreshInfoValues(jsonReport);

                        int serviceNativeMem = jsonReport.getInt("serviceNativeMem") >> 10;

                        if (jsonReport.getBoolean("siteScanCompleted"))
                        {
                            theActivity.btnPauseOrContinue.setImageResource(R.drawable.start);
                        }
                        else if (freeMem < 50 || serviceNativeMem > 50)
                        {
                            theActivity.serviceState = theActivity.STATE_WAIT_DISCONNECT;
                            theActivity.sendCmdToSpiderService(StaticValue.CMD_RESTART);

                        }

                        theActivity.mThumbnailLoader.setAlbumTotalImgNum(
                                jsonReport.getInt("imgDownloadNum"));
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

        panelViewInit();

        firstRunOperat();

        getSrcUrlAndPath(ParaConfig.getHomeURL(getApplicationContext()));
        serviceInterfaceInit();

        sendCmdToSpiderService(StaticValue.CMD_PAUSE_ON_START);
        checkStorage();

        albumViewInit();
    }

    void getSrcUrlAndPath(String SrcUrl)
    {
        projectSrcUrl = SrcUrl;
        Log.i(TAG, "projectSrcUrl " + projectSrcUrl);

        try
        {
            appPath = Utils.getDirInExtSto(getString(R.string.appPackageName)).getPath();
            projectPath = appPath + "/" + new URL(projectSrcUrl).getHost();

        } catch (MalformedURLException e)
        {
            e.printStackTrace();
        }
    }

    private void albumViewInit()
    {
        GLRootView glRootView = (GLRootView) findViewById(R.id.gl_root_view);

        albumLoaderHelper = new AlbumLoaderHelper(projectPath);
        albumSetLoaderHelper = new AlbumSetLoaderHelper(appPath);
        mThumbnailLoader = new ThumbnailLoader(glRootView, albumSetLoaderHelper);
        SlotView slotView = new SlotView(this, mThumbnailLoader, glRootView);
        slotView.setOnDoubleTap(new Runnable()
        {
            @Override
            public void run()
            {
                Log.i(TAG, "slotView OnDoubleTap");
            }
        });

        glRootView.setContentPane(slotView);
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
        checkNetworkBeforeStart();
    }


    private void checkNetworkBeforeStart()
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
                        btnPauseOrContinue.setImageResource(R.drawable.pause);
                        sendCmdToSpiderService(StaticValue.CMD_START);
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

    private void firstRunOperat()
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
        Log.i(TAG, "onResume");

    }

    protected void onPause()
    {
        super.onPause();
        Log.i(TAG, "onPause");
    }

    protected void onStop()
    {
        super.onStop();
        Log.i(TAG, "onStop");

        mThumbnailLoader.stopLoader();
        handleSpiderServiceOnFinish();
    }

    private void tryToStopSpiderService()
    {
        Log.i(TAG, "tryToStopSpiderService " + serviceState + " " + STATE_DISCONNECTED);
        if (serviceState != STATE_DISCONNECTED)
        {
            unbindSpiderService();
            sendCmdToSpiderService(StaticValue.CMD_STOP_STORE);
            serviceState = STATE_DISCONNECTED;
        }
    }

    private void handleSpiderServiceOnFinish()
    {
        tryToStopSpiderService();
    }

    protected void onDestroy()
    {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
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
                    getSrcUrlAndPath(data.getAction());
                    Log.i(TAG, "REQUST_SRC_URL " + projectSrcUrl);

                    btnPauseOrContinue.setImageResource(R.drawable.pause);

                    albumLoaderHelper.setProjectPath(projectPath);

                    if (serviceState == STATE_CONNECTED || serviceState == STATE_WAIT_CONNECT)
                    {
                        sendCmdToSpiderService(StaticValue.CMD_JUST_STOP);
                        serviceState = STATE_WAIT_DISCONNECT;
                    }
                    else if (serviceState == STATE_DISCONNECTED || serviceState == STATE_WAIT_DISCONNECT)
                    {
                        sendCmdToSpiderService(StaticValue.CMD_START);
                        serviceState = STATE_WAIT_CONNECT;
                    }
                }
            }
        }
    }

    private void selectNewSourceUrl()
    {
        Intent intent = new Intent(SpiderActivity.this,
                SelSrcActivity.class);
        startActivityForResult(intent, REQUST_SRC_URL);
    }

    private void deleteProject()
    {
        if (serviceState != STATE_DISCONNECTED)
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

    private class DrawerInfo
    {
        private DrawerLayout drawer;

        TextView text_project_site;
        TextView image_download_num;
        TextView image_processed;
        TextView image_total;
        TextView image_download_payload;
        TextView image_tree_height;
        TextView page_scaned_num;
        TextView page_total;
        TextView page_tree_height;
        TextView page_load_time;
        TextView page_scan_time;
        TextView page_search_time;
        TextView download_speed;
        TextView ram_sys_total;
        TextView ram_sys_free;
        TextView ram_activity_vm;
        TextView ram_activity_native;
        TextView ram_service_vm;
        TextView ram_service_native;
        TextView cur_page;

        public void allowDrawer(boolean allow)
        {
            drawer.setDrawerLockMode(allow?DrawerLayout.LOCK_MODE_UNLOCKED:
                    DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        }

        public DrawerInfo()
        {
            drawer = (DrawerLayout) findViewById(R.id.main_drawer);

            text_project_site = (TextView) findViewById(R.id.text_project_site);
            image_download_num = (TextView) findViewById(R.id.image_download_num);
            image_processed = (TextView) findViewById(R.id.image_processed);
            image_total = (TextView) findViewById(R.id.image_total);
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

        public void openDrawer()
        {
            drawer.openDrawer(GravityCompat.START);
        }

        public void refreshInfoValues(JSONObject json)
        {
            try
            {
                text_project_site.setText(new URL(projectSrcUrl).getHost());

                image_download_num.setText(String.valueOf(json.getInt("imgDownloadNum")));
                image_processed.setText(String.valueOf(json.getInt("imgProcessedNum")));
                image_total.setText(String.valueOf(json.getInt("imgTotalNum")));

                image_download_payload.setText(String.valueOf(json.getInt("imgDownloaderPayload")));
                image_tree_height.setText(String.valueOf(json.getInt("imgTreeHeight")));

                page_scaned_num.setText(String.valueOf(json.getInt("pageProcessedNum")));
                page_total.setText(String.valueOf(json.getInt("pageTotalNum")));

                page_tree_height.setText(String.valueOf(json.getInt("pageTreeHeight")));
                page_load_time.setText(String.valueOf(json.getInt("pageLoadTime"))+"ms");
                page_scan_time.setText(String.valueOf(json.getInt("pageScanTime"))+"ms");
                page_search_time.setText(String.valueOf(json.getInt("pageSearchTime"))+"ms");

                download_speed.setText(json.getString("curNetSpeed"));

                ram_sys_total.setText(json.getString("sysTotalMem"));
                ram_sys_free.setText(json.getString("sysFreeMem"));
                ram_activity_vm.setText(json.getString("activityVmMem"));
                ram_activity_native.setText(json.getString("activityNativeMem"));
                ram_service_vm.setText(json.getInt("serviceVmMem")+"K ");
                ram_service_native.setText(json.getInt("serviceNativeMem")+"K");
                cur_page.setText(json.getString("curPage"));

            } catch (MalformedURLException e)
            {
                e.printStackTrace();
            } catch (JSONException e)
            {
                e.printStackTrace();
            }

        }

    }

    private boolean cmdIsStart = true;

    private void panelViewInit()
    {
        infoDrawer = new DrawerInfo();

        btnPauseOrContinue = (ImageButton) findViewById(R.id.buttonPauseOrContinue);

        btnPauseOrContinue.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if (!cmdIsStart)
                {
                    cmdIsStart = true;
                    btnPauseOrContinue.setImageResource(R.drawable.start);
                    sendCmdToSpiderService(StaticValue.CMD_PAUSE);
                }
                else
                {
                    cmdIsStart = false;
                    checkNetworkBeforeStart();
                }
            }
        });

        ImageButton buttonInfo = (ImageButton) findViewById(R.id.buttonInfo);
        buttonInfo.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                infoDrawer.openDrawer();
            }
        });

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
        bundle.putString(StaticValue.BUNDLE_KEY_SOURCE_URL, projectSrcUrl);
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
            /*
            if(infoDrawer.isDrawerOpen(infoList))
            {
                infoDrawer.closeDrawer(infoList);
                return true;
            }
            */

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
                handleSpiderServiceOnFinish();
            }

        }

        return super.onKeyDown(keyCode, event);
    }
}
