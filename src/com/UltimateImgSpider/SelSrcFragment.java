package com.UltimateImgSpider;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

public class SelSrcFragment extends Fragment
{
	
	WebView			wvSelSrc;
	WebSettings		wsSelSrc;
	
	ProgressBar		pbWebView;
	
	final String	LOG_TAG			= "SelSrcFragment";
	final String	HOME_URL		= "http://www.baidu.com/";
	final int		PROGRESS_MAX	= 100;
	
	public SelSrcFragment()
	{
		
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState)
	{
		View rootView = inflater.inflate(R.layout.fragment_sel_src, container,
				false);
		
		pbWebView = (ProgressBar) rootView
				.findViewById(R.id.progressBarWebView);
		pbWebView.setMax(PROGRESS_MAX);
		
		wvSelSrc = (WebView) rootView.findViewById(R.id.webViewSelectSrcUrl);
		
		wvSelSrc.requestFocus();
		
		wvSelSrc.setWebViewClient(new WebViewClient()
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
		    	pbWebView.setVisibility(View.VISIBLE);
		    }
		});
		
		wvSelSrc.setWebChromeClient(new WebChromeClient()
		{
			public void onProgressChanged(WebView view, int newProgress)
			{
				//Log.i(LOG_TAG, view.getUrl() + " Progress " + newProgress);
				
				pbWebView.setProgress(newProgress);
				if (newProgress == PROGRESS_MAX)
				{
					pbWebView.setVisibility(View.GONE);
				}
			}
		});
		
		wsSelSrc = wvSelSrc.getSettings();
		// wsSelSrc.setUserAgentString(getString(R.string.webViewUserAgent));
		
		// 启用缩放
		wsSelSrc.setSupportZoom(true);
		wsSelSrc.setBuiltInZoomControls(true);
		wsSelSrc.setUseWideViewPort(true);
		
		// 使能javascript
		wsSelSrc.setJavaScriptEnabled(true);
		wsSelSrc.setJavaScriptCanOpenWindowsAutomatically(false);
		
		// 自适应屏幕
		// wsSelSrc.setLayoutAlgorithm(LayoutAlgorithm.SINGLE_COLUMN);
		// wsSelSrc.setLoadWithOverviewMode(true);
		
		wvSelSrc.loadUrl(HOME_URL);
		
		return rootView;
	}
	
	public boolean webViewSelSrcGoBack()
	{
		if (wvSelSrc.canGoBack())
		{
			wvSelSrc.goBack();
			
			Log.i(LOG_TAG, "goBack ");
			// String curUrl = wvSelSrc.getUrl();
			// if(curUrl!=tvSrcUrl.getText())
			return true;
		}
		
		return false;
	}
	
}
