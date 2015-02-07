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
        
        Log.i(LOG_TAG, "onCreate");
        
        curUrl = getIntent().getExtras().getString(SpiderActivity.SOURCE_URL_BUNDLE_KEY);

        if (curUrl != null)
        {
            Log.i(LOG_TAG, "curUrl:" + curUrl);
            webViewInit();
        }
        
    }
    

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        Log.i(LOG_TAG, "onDestroy");

        if(wvParaConfig!=null)
        {
	        wvParaConfig.clearCache(true);
	        wvParaConfig.destroy();
        }
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
                return false;
            }
        });

        wvParaConfig.setOnLongClickListener(new WebView.OnLongClickListener()
        {
            public boolean onLongClick(View v)
            {
                return true;
            }
        });

        wsParaConfig = wvParaConfig.getSettings();

        // ʹ��javascript
        wsParaConfig.setJavaScriptEnabled(true);
        wsParaConfig.setJavaScriptCanOpenWindowsAutomatically(false);

        wvParaConfig.addJavascriptInterface(this, "paraConfig");

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

                Toast.makeText(this, "��������ҳ:" + URL, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @JavascriptInterface
    public String getHomeUrl()
    {
        return ParaConfig.getHomeURL(this);
    }


    @JavascriptInterface
    public void setUserAgent(String ua)
    {
        Log.i(LOG_TAG, "setHomeUrl");

        if (!ua.isEmpty())
        {
            ParaConfig.setUserAgent(this, ua);

            Toast.makeText(this, "������UA:" + ua, Toast.LENGTH_SHORT).show();
        }
    }
    
    @JavascriptInterface
    public String getUserAgent()
    {
        return ParaConfig.getUserAgent(this);
    }

    @JavascriptInterface
    public void setSearchEngine(int seIndex)
    {
        Log.i(LOG_TAG, "setHomeUrl");
        
        if(ParaConfig.setSearchEngine(this, seIndex))
        {
            Toast.makeText(this, "��������������:" + ParaConfig.getSearchEngineName(this), Toast.LENGTH_SHORT).show();
        }
    }

    @JavascriptInterface
    public String getSearchEngine()
    {
        return ParaConfig.getSearchEngineName(this);
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
