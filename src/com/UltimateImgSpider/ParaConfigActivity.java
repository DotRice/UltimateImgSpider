package com.UltimateImgSpider;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

public class ParaConfigActivity extends Activity
{
	private String curUrl;
	private String LOG_TAG="ParaConfigActivity";
	
	private WebView wvParaConfig;
	private WebSettings wsParaConfig;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_para_config);
		
		Intent intent = this.getIntent();
		Bundle bundle = intent.getExtras();
		curUrl = bundle.getString(SelSrcActivity.SOURCE_URL_BUNDLE_KEY);
		
		if(curUrl!=null)
		{
			Log.i(LOG_TAG, "curUrl:"+curUrl);
		}
	}
	

	private void webViewInit()
	{
		wvParaConfig = (WebView) findViewById(R.id.webViewSelectSrcUrl);
		
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
				//Log.i(LOG_TAG, view.getUrl() + " Progress " + newProgress);
				
			}
		});
		
		wsParaConfig = wvParaConfig.getSettings();
		
		// 使能javascript
		wsParaConfig.setJavaScriptEnabled(true);
		wsParaConfig.setJavaScriptCanOpenWindowsAutomatically(false);
		
		// 自适应屏幕
		// wsParaConfig.setLayoutAlgorithm(LayoutAlgorithm.SINGLE_COLUMN);
		// wsParaConfig.setLoadWithOverviewMode(true);
		
		wvParaConfig.loadUrl(homeUrl);
		
	}
}
