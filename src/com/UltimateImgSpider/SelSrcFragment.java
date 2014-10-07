package com.UltimateImgSpider;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class SelSrcFragment extends Fragment
{
	String		LOG_TAG	= "SelSrcFragment";
	
	WebView		wvSelSrc;
	WebSettings	wsSelSrc;
	
	public SelSrcFragment()
	{
		
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState)
	{
		View rootView = inflater.inflate(R.layout.fragment_main, container,
				false);
		
		wvSelSrc = (WebView) rootView.findViewById(R.id.webViewSelectSrcUrl);
		
		wvSelSrc.requestFocus();
		
		wvSelSrc.setWebViewClient(new WebViewClient()
		{
			public boolean shouldOverrideUrlLoading(WebView view, String url)
			{
				view.loadUrl(url);
				return true;
			}
			
			public void onPageFinished(WebView view, String url)
			{
				Log.i(LOG_TAG, "onPageFinished "+url);
			}
		});
		
		wsSelSrc = wvSelSrc.getSettings();
		//wsSelSrc.setUserAgentString(getString(R.string.webViewUserAgent));
		wsSelSrc.setSupportZoom(true);
		wsSelSrc.setBuiltInZoomControls(true);
		wsSelSrc.setUseWideViewPort(true);
		//wsSelSrc.setLayoutAlgorithm(LayoutAlgorithm.SINGLE_COLUMN);
		//wsSelSrc.setLoadWithOverviewMode(true);
		
		wvSelSrc.loadUrl("http://www.baidu.com");
		
		return rootView;
	}
	
	public boolean webViewSelSrcGoBack()
	{
		if (wvSelSrc.canGoBack())
		{
			wvSelSrc.goBack();
			
			//String curUrl = wvSelSrc.getUrl();
			// if(curUrl!=tvSrcUrl.getText())
			return true;
		}
		
		return false;
	}
	
}
