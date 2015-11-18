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
import com.gk969.gallery.gallery3d.ui.GLRootView;
import com.gk969.gallery.gallery3d.ui.GLView;
import com.gk969.gallery.gallery3d.util.GalleryUtils;

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
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
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

public class SpiderActivity extends Activity
{
    private final String     TAG                   = "SpiderActivity";
    public final static int  REQUST_SRC_URL        = 0;

    final static String      BUNDLE_KEY_SOURCE_URL = "SourceUrl";
    final static String      BUNDLE_KEY_CMD        = "cmd";
    final static String      BUNDLE_KEY_PRJ_PATH   = "projectPath";

    public final static int  CMD_NOTHING           = 0;
    public final static int  CMD_CLEAR             = 1;
    public final static int  CMD_PAUSE             = 2;
    public final static int  CMD_CONTINUE          = 3;
    public final static int  CMD_RESTART           = 4;
    public final static int  CMD_STOP_STORE        = 5;
    public final static int  CMD_START             = 6;

    private final int        STATE_IDLE            = 0;
    private final int        STATE_CONNECTED       = 1;
    private final int        STATE_WAIT_DISCONNECT = 2;
    private final int        STATE_WAIT_CONNECT    = 3;
    private final int        STATE_DISCONNECTED    = 4;

    private int              serviceState          = STATE_IDLE;

    private ImageTextButton  btPauseOrContinue;
    private ImageTextButton  btSelSrc;
    private ImageTextButton  btClear;

    String                   srcUrl;

    private TextView         spiderLog;

    private File             appDir;

    private MessageHandler   mHandler              = new MessageHandler(this);

    private static final int BUMP_MSG              = 1;


    private ServiceConnection            mConnection;
    private IRemoteSpiderServiceCallback mCallback;

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
                }
                catch (RemoteException e)
                {

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
                    mHandler.postDelayed(new Runnable()
                    {

                        @Override
                        public void run()
                        {
                            serviceState = STATE_WAIT_CONNECT;
                            startAndBindSpiderService(srcUrl);
                        }
                    }, 500);
                }
                else
                {
                    serviceState = STATE_DISCONNECTED;
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
            SpiderActivity theActivity = mActivity.get();
            switch (msg.what)
            {
                case BUMP_MSG:
                    String msgStr = (String) msg.obj;
                    long freeMem = MemoryInfo.getFreeMemInMb(theActivity);
                    int memUsedBySpider = Integer.parseInt(msgStr.substring(
                            msgStr.indexOf("Native:") + 7,
                            msgStr.indexOf("M pic:")));
                    // Log.i(theActivity.TAG,
                    // "mem:"+freeMem+" "+memUsedBySpider);

                    theActivity.spiderLog.setText("Total:"
                            + MemoryInfo.getTotalMemInMb() + "M Free:"
                            + freeMem + "M\r\n" + msgStr);
                    if (msgStr.contains("siteScanCompleted"))
                    {
                        theActivity.btPauseOrContinue.changeView(R.drawable.start, R.string.start);
                    }
                    else if (freeMem < 50 || memUsedBySpider > 100)
                    {
                        theActivity.serviceState = theActivity.STATE_WAIT_DISCONNECT;
                        theActivity.sendCmdToSpiderService(CMD_RESTART);
                    }
                break;
                default:
                    super.handleMessage(msg);
            }
        }

    };

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
    private final static int DLG_STORAGE_ERROR  = 1;

    protected Dialog onCreateDialog(int dlgId)
    {
        switch (dlgId)
        {
            case DLG_NETWORK_PROMPT:
            {
                return sysFaultAlert(getString(R.string.prompt),
                        getString(R.string.uneffectiveNetworkPrompt), true);
            }

            case DLG_STORAGE_ERROR:
            {
                return sysFaultAlert(getString(R.string.prompt),
                        getString(R.string.badExternalStoragePrompt), true);
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

        spiderLog = (TextView) findViewById(R.id.tvSpiderLog);
        projBarInit();

        firstRunOperat();

        srcUrl=ParaConfig.getHomeURL(getApplicationContext());
        Log.i(TAG, "srcUrl "+srcUrl);

        serviceInterfaceInit();
        checkAndStart();

        albumViewInit();
    }

    private void albumViewInit()
    {
        GLRootView glRootView=(GLRootView)findViewById(R.id.gl_root_view);
        glRootView.setContentPane(new GLView() {
            private final float mMatrix[] = new float[16];

            @Override
            protected void onLayout(
                    boolean changed, int left, int top, int right, int bottom) {

                // Set the mSlotView as a reference point to the open animation

                GalleryUtils.setViewPointMatrix(mMatrix,
                        (right - left) / 2, (bottom - top) / 2, 0-GalleryUtils.meterToPixel(0.3f));
            }

            @Override
            protected void render(GLCanvas canvas) {
                canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
                canvas.multiplyMatrix(mMatrix, 0);
                super.render(canvas);

                canvas.clearBuffer(new float[]{0f, 0.5f, 0.5f, 0.5f});
                canvas.restore();

                canvas.fillRect(100, 100, 500, 500, 0xFF00F040);
            }
        });
    }

    private void checkAndStart()
    {
        appDir = Utils.getDirInExtSto(getString(R.string.appPackageName)
                + "/download");
        if (appDir == null)
        {
            showDialog(DLG_STORAGE_ERROR);
            return;
        }

        checkNetwork();
    }

    private void checkNetwork()
    {
        new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                if (Utils.isNetworkEffective())
                {
                    mHandler.post(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            startAndBindSpiderService(srcUrl);
                        }
                    });
                }
                else
                {
                    mHandler.post(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            showDialog(DLG_NETWORK_PROMPT);
                        }
                    });
                }
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

    }

    protected void onDestroy()
    {
        super.onDestroy();
        Log.i(TAG, "onDestroy");

        if (serviceState != STATE_DISCONNECTED)
        {
            Log.i(TAG, "CMD_CLEAR");
            sendCmdToSpiderService(CMD_STOP_STORE);
            unboundSpiderService();
        }
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
                    srcUrl = data.getAction();
                    Log.i(TAG, "REQUST_SRC_URL " + srcUrl);

                    btPauseOrContinue.changeView(R.drawable.pause,
                            R.string.pause);
                    if (serviceState == STATE_CONNECTED
                            || serviceState == STATE_WAIT_CONNECT)
                    {
                        sendCmdToSpiderService(CMD_CLEAR);
                        serviceState = STATE_WAIT_DISCONNECT;
                    }
                    else if (serviceState == STATE_DISCONNECTED
                            || serviceState == STATE_WAIT_DISCONNECT)
                    {
                        startAndBindSpiderService(srcUrl);
                        serviceState = STATE_WAIT_CONNECT;
                    }
                }
            }
        }
    }

    private void projBarInit()
    {
        btPauseOrContinue = (ImageTextButton) findViewById(R.id.buttonPauseOrContinue);
        btPauseOrContinue.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                String cmd = btPauseOrContinue.textView.getText().toString();

                if (cmd.equals(getString(R.string.pause)))
                {
                    btPauseOrContinue.changeView(R.drawable.start,
                            R.string.goOn);

                    sendCmdToSpiderService(CMD_PAUSE);
                }
                else
                {
                    if (srcUrl != null)
                    {
                        btPauseOrContinue.changeView(R.drawable.pause,
                                R.string.pause);

                        startAndBindSpiderService(srcUrl);

                        if (cmd.equals(getString(R.string.goOn)))
                        {
                            sendCmdToSpiderService(CMD_CONTINUE);
                        }
                    }
                }
            }
        });

        btSelSrc = (ImageTextButton) findViewById(R.id.buttonSelSrc);
        btSelSrc.setOnClickListener(new View.OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                Intent intent = new Intent(SpiderActivity.this,
                        SelSrcActivity.class);
                startActivityForResult(intent, REQUST_SRC_URL);
            }
        });
        
        btClear = (ImageTextButton) findViewById(R.id.buttonClear);
        btClear.setOnClickListener(new View.OnClickListener()
        {
            
            @Override
            public void onClick(View v)
            {
                sendCmdToSpiderService(CMD_CLEAR);
                btPauseOrContinue.changeView(R.drawable.start, R.string.start);
            }
        });
        
    }
    
    /** The primary interface we will be calling on the service. */
    IRemoteSpiderService                 mService = null;
    
    
    
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
            
            // Detach our existing connection.
            unbindService(mConnection);
            Log.i(TAG, "unbound SpiderService");
        }
        
    }

    private void startAndBindSpiderService(String src)
    {
        Log.i(TAG, "startAndBindSpiderService src:" + src);

        Intent spiderIntent = new Intent(IRemoteSpiderService.class.getName());
        spiderIntent.setPackage(IRemoteSpiderService.class.getPackage()
                .getName());

        Bundle bundle = new Bundle();
        bundle.putString(BUNDLE_KEY_SOURCE_URL, src);
        spiderIntent.putExtras(bundle);
        startService(spiderIntent);

        bindService(spiderIntent, mConnection, BIND_ABOVE_CLIENT);
    }
    
    private void sendCmdToSpiderService(int cmd)
    {
        Intent spiderIntent = new Intent(IRemoteSpiderService.class.getName());
        spiderIntent.setPackage(IRemoteSpiderService.class.getPackage()
                .getName());
        
        Bundle bundle = new Bundle();
        bundle.putInt(BUNDLE_KEY_CMD, cmd);
        spiderIntent.putExtras(bundle);
        startService(spiderIntent);
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        
        Log.i(TAG,
                (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) ? "Landscape"
                        : "Portrait");
    }
    
    private long exitTim = 0;
    
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        Log.i(TAG, "onKeyDown " + keyCode);
        
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (SystemClock.uptimeMillis() - exitTim > 2000)
            {
                Toast.makeText(this,getString(R.string.keyBackExitConfirm)
                                + getString(R.string.app_name),Toast.LENGTH_SHORT).show();
                
                exitTim = SystemClock.uptimeMillis();
            }
            else
            {
                Log.i(TAG, "finish");
                finish();
            }

            return true;
        }

        return super.onKeyDown(keyCode, event);
    }
}
