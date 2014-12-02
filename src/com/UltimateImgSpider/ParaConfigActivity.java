package com.UltimateImgSpider;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

public class ParaConfigActivity extends Activity
{
    private String      curUrl;
    private String      LOG_TAG      = "ParaConfigActivity";

    private WebView     wvParaConfig;
    private WebSettings wsParaConfig;

    private Handler     mHandler     = new Handler();

    final static String assetParaUrl = "file:///android_asset/paraConfig.html";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_para_config);

        Intent intent = this.getIntent();
        Bundle bundle = intent.getExtras();
        curUrl = bundle.getString(SelSrcActivity.SOURCE_URL_BUNDLE_KEY);

        if (curUrl != null)
        {
            Log.i(LOG_TAG, "curUrl:" + curUrl);
        }

        webViewInit();
    }

    @SuppressLint({ "JavascriptInterface", "SetJavaScriptEnabled" })
    private void webViewInit()
    {
        wvParaConfig = (WebView) findViewById(R.id.webViewParaConfig);

        wvParaConfig.requestFocus();

        wvParaConfig.setWebViewClient(new WebViewClient()
        {
            public boolean shouldOverrideUrlLoading(WebView view, String URL)
            {
                Log.i(LOG_TAG, "UrlLoading " + URL);
                view.loadUrl(URL);
                return true;
            }

            public void onPageFinished(WebView view, String URL)
            {
                Log.i(LOG_TAG, "onPageFinished " + URL);
            }

            public void onPageStarted(WebView view, String URL, Bitmap favicon)
            {

            }
        });

        wvParaConfig.setWebChromeClient(new WebChromeClient()
        {
            public void onProgressChanged(WebView view, int newProgress)
            {
                // Log.i(LOG_TAG, view.getUrl() + " Progress " + newProgress);

            }
        });

        wvParaConfig.setOnLongClickListener(new WebView.OnLongClickListener()
        {
            public boolean onLongClick(View v)
            {
                return true;
            }
        });

        // jsInterface=new JavaScriptinterface(this);

        wsParaConfig = wvParaConfig.getSettings();

        // 使能javascript
        wsParaConfig.setJavaScriptEnabled(true);
        wsParaConfig.setJavaScriptCanOpenWindowsAutomatically(false);

        wvParaConfig.addJavascriptInterface(this, "paraConfig");

        // 自适应屏幕
        // wsParaConfig.setLayoutAlgorithm(LayoutAlgorithm.SINGLE_COLUMN);
        // wsParaConfig.setLoadWithOverviewMode(true);

        wvParaConfig.loadUrl(assetParaUrl);

    }

    @JavascriptInterface
    public void setHomeUrl(String URL)
    {
        Log.i(LOG_TAG, "setHomeUrl");

        if (!URL.isEmpty())
        {
            if (URL.equals("curUrl"))
            {
                URL = curUrl;
            }

            if (URLUtil.isNetworkUrl(URL))
            {
                ParaConfig.setHomeURL(this, URL);

                Toast.makeText(this, "已设置主页:" + URL, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @JavascriptInterface
    public String getHomeUrl()
    {
        return ParaConfig.getHomeURL(this);
    }

    @JavascriptInterface
    public void finishConfig()
    {
        mHandler.post(new Runnable()
        {
            public void run()
            {
                ParaConfigActivity.this.finish();
            }
        });
    }

    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        Log.i(LOG_TAG, "onKeyDown " + keyCode);

        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            Log.i(LOG_TAG, "goBack ");
            wvParaConfig.loadUrl("javascript:goback()");
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
        {
            Log.i(LOG_TAG, "Landscape");
        }
        else
        {
            Log.i(LOG_TAG, "Portrait");
        }
    }
}
