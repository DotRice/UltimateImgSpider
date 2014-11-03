package com.UltimateImgSpider;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

public class ParaConfigActivity extends Activity
{
	private String		curUrl;
	private String		LOG_TAG			= "ParaConfigActivity";
	
	private WebView		wvParaConfig;
	private WebSettings	wsParaConfig;
	
	private Handler		mHandler		= new Handler();
	
	final static String	assetParaUrl	= "file:///android_asset/paraConfig.html";
	
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
			public boolean shouldOverrideUrlLoading(WebView view, String url)
			{
				Log.i(LOG_TAG, "UrlLoading " + url);
				view.loadUrl(url);
				return true;
			}
			
			public void onPageFinished(WebView view, String url)
			{
				Log.i(LOG_TAG, "onPageFinished " + url);
			}
			
			public void onPageStarted(WebView view, String url, Bitmap favicon)
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
	public void setHomeUrl(String url)
	{
		mHandler.post(new Runnable()
		{
			public void run()
			{
				wvParaConfig.goBack();
				Log.i(LOG_TAG, "setHomeUrl");
			}
		});
		
		if(!url.isEmpty())
		{
			if(url.equals("curUrl"))
			{
				url=curUrl;
			}
			
			Editor editor = getSharedPreferences(SelSrcActivity.SPMAIN_NAME, 0).edit();
			editor.putString(SelSrcActivity.HOME_URL_KEY, url);
			editor.commit();
			
			Toast.makeText(this, "已设置主页:" + url, Toast.LENGTH_SHORT).show();
		}
	}

	@JavascriptInterface
	public String getHomeUrl()
	{
		return getSharedPreferences(SelSrcActivity.SPMAIN_NAME, 0).getString(
				SelSrcActivity.HOME_URL_KEY, getString(R.string.defaultHomeUrl));
	}
	
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		Log.i(LOG_TAG, "onKeyDown " + keyCode);
		
		if (keyCode == KeyEvent.KEYCODE_BACK)
		{
			if (wvParaConfig.canGoBack())
			{
				wvParaConfig.goBack();
				
				Log.i(LOG_TAG, "goBack ");
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}
	
}
