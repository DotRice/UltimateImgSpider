package com.gk969.UltimateImgSpider;

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
    private String      TAG          = "ParaConfigActivity";
    
    private WebView     wvParaConfig;
    private WebSettings wsParaConfig;
    
    private Handler     mHandler     = new Handler();

    private Context appCtx;
    
    final static String assetParaUrl = "file:///android_asset/paraConfig.html";
    
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_para_config);

        appCtx=getApplicationContext();

        Log.i(TAG, "onCreate");
        
        curUrl = getIntent().getExtras().getString(
                StaticValue.BUNDLE_KEY_SOURCE_URL);
        
        if (curUrl != null)
        {
            Log.i(TAG, "curUrl:" + curUrl);
            webViewInit();
        }

    }
    
    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        
        if (wvParaConfig != null)
        {
            
            Log.i(TAG, "clearCache");
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
                Log.i(TAG, "UrlLoading " + URL);
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
        
        // 使能javascript
        wsParaConfig.setJavaScriptEnabled(true);
        wsParaConfig.setJavaScriptCanOpenWindowsAutomatically(false);
        
        wvParaConfig.addJavascriptInterface(this, "paraConfig");
        
        wvParaConfig.loadUrl(assetParaUrl);
        
    }
    
    @JavascriptInterface
    public void setHomeUrl(String URL)
    {
        Log.i(TAG, "setHomeUrl");
        
        if (!URL.isEmpty())
        {
            if (URL.equals("curUrl"))
            {
                URL = curUrl;
            }
            
            if (URLUtil.isNetworkUrl(URL))
            {
                ParaConfig.setHomeURL(appCtx, URL);
                
                Toast.makeText(this, "已设置主页:" + URL, Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @JavascriptInterface
    public String getHomeUrl()
    {
        return ParaConfig.getHomeURL(appCtx);
    }
    
    @JavascriptInterface
    public void setUserAgent(String ua)
    {
        Log.i(TAG, "setHomeUrl");
        
        if (!ua.isEmpty())
        {
            ParaConfig.setUserAgent(appCtx, ua);
            
            Toast.makeText(this, "已设置UA:" + ua, Toast.LENGTH_SHORT).show();
        }
    }
    
    @JavascriptInterface
    public String getUserAgent()
    {
        return ParaConfig.getUserAgent(appCtx);
    }
    
    @JavascriptInterface
    public void setSearchEngine(int seIndex)
    {
        Log.i(TAG, "setHomeUrl");
        
        if (ParaConfig.setSearchEngine(appCtx, seIndex))
        {
            Toast.makeText(this,
                    "已设置搜索引擎:" + ParaConfig.getSearchEngineName(appCtx),
                    Toast.LENGTH_SHORT).show();
        }
    }
    
    @JavascriptInterface
    public String getSearchEngine()
    {
        return ParaConfig.getSearchEngineName(appCtx);
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
        Log.i(TAG, "onKeyDown " + keyCode);
        
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            Log.i(TAG, "goBack ");
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
            Log.i(TAG, "Landscape");
        }
        else
        {
            Log.i(TAG, "Portrait");
        }
    }
}
