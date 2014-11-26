package com.UltimateImgSpider;

import java.util.Locale;

import android.app.Activity;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.v13.app.FragmentPagerAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SelSrcActivity extends Activity
{
	private final String			LOG_TAG					= "SelSrcActivity";
	
	private WebView					wvSelSrc;
	private WebSettings				wsSelSrc;
	
	private ProgressBar				pbWebView;
	
	private Button	btnSelSearchEngine;
	private final int SEARCH_ENGINE_ICON[]={R.drawable.baidu, R.drawable.bing, R.drawable.sogou, R.drawable.google};
	private final String SEARCH_ENGINE_URL[]={"http://www.baidu.com/s?wd=", "https://cn.bing.com/search?q=", "http://www.sogou.com/web?query=", "https://www.google.com/search?q="};
	private final String SEARCH_ENGINE_KEY="searchEngine";
	
	private EditText				etURL;
	private RelativeLayout			URLbar;
	
	private Button					btnURLcmd;
	
	private final int				URL_CANCEL				= 0;
	private final int				URL_REFRESH				= 1;
	private final int				URL_ENTER				= 2;
	private final int				URL_SEARCH				= 3;
	private final int				URLCMD_ICON[]			= {
			R.drawable.cancel, R.drawable.refresh, R.drawable.enter,
			R.drawable.search								};
	private int						URLcmd					= URL_CANCEL;
	
	private View.OnClickListener	oclBrowserBtn;
	
	private final int				PROGRESS_MAX			= 100;
	
	private String					curWebPageTitle;
	
	private Handler					mHandler				= new Handler();
	
	SharedPreferences				spMain;
	final static String				SPMAIN_NAME				= "spMain";
	final static String				SPIDERGO_NOT_CONFIRM	= "spiderGoConfirm";
	final static String				HOME_URL_KEY			= "homeUrl";
	
	final static String				SOURCE_URL_BUNDLE_KEY	= "SourceUrl";
	
	private enum DLG
	{
		SPIDER_GO_CONFIRM
	};
	
	@Override
	protected Dialog onCreateDialog(int dlgId)
	{
		DLG dlg = DLG.values()[dlgId];
		switch (dlg)
		{
			case SPIDER_GO_CONFIRM:
			{
				return new AlertDialog.Builder(this)
						.setTitle(R.string.spiderGoConfirm)
						.setMultiChoiceItems(
								R.array.noLongerConfirm,
								new boolean[] { false },
								new DialogInterface.OnMultiChoiceClickListener()
								{
									public void onClick(DialogInterface dialog,
											int whichButton, boolean isChecked)
									{
										
										/* User clicked on a check box do some stuff */
										
										Editor editor = spMain.edit();
										editor.putBoolean(SPIDERGO_NOT_CONFIRM,
												isChecked);
										editor.commit();
									}
								})
						.setPositiveButton(R.string.OK,
								new DialogInterface.OnClickListener()
								{
									public void onClick(DialogInterface dialog,
											int whichButton)
									{
										/* User clicked Yes so do some stuff */
										spiderGo();
									}
								})
						.setNegativeButton(R.string.cancel,
								new DialogInterface.OnClickListener()
								{
									public void onClick(DialogInterface dialog,
											int whichButton)
									{
										
										/* User clicked No so do some stuff */
									}
								}).create();
			}
			
		}
		return null;
	}
	
	private void webViewInit()
	{
		pbWebView = (ProgressBar) findViewById(R.id.progressBarWebView);
		pbWebView.setMax(PROGRESS_MAX);
		
		wvSelSrc = (WebView) findViewById(R.id.webViewSelectSrcUrl);
		
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
				setURLcmd(URL_REFRESH);
			}
			
			public void onPageStarted(WebView view, String url, Bitmap favicon)
			{
				Log.i(LOG_TAG, "onPageStarted " + url);
				pbWebView.setVisibility(View.VISIBLE);
				etURL.setText(url);
				curWebPageTitle = "";
				setURLcmd(URL_CANCEL);
			}
		});
		
		wvSelSrc.setWebChromeClient(new WebChromeClient()
		{
			public void onProgressChanged(WebView view, int newProgress)
			{
				// Log.i(LOG_TAG, view.getUrl() + " Progress " + newProgress);
				
				if (newProgress == PROGRESS_MAX)
				{
					if (pbWebView.getProgress() != 0)
					{
						pbWebView.setProgress(PROGRESS_MAX);
						mHandler.postDelayed(new Runnable()
						{
							public void run()
							{
								pbWebView.setProgress(0);
							}
						}, 500);
					}
				}
				else
				{
					pbWebView.setProgress(newProgress);
				}
				
			}
			
			public void onReceivedTitle(WebView view, String title)
			{
				etURL.setText(title);
				curWebPageTitle = title;
			}
		});
		
		wvSelSrc.setOnTouchListener(new View.OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				if (etURL.isFocused())
				{
					clearURLfocus();
				}
				return false;
			}
		});
		
		wsSelSrc = wvSelSrc.getSettings();
		wsSelSrc.setUserAgentString(getString(R.string.webViewUserAgent));
		
		// 启用缩放
		wsSelSrc.setSupportZoom(true);
		wsSelSrc.setBuiltInZoomControls(true);
		wsSelSrc.setUseWideViewPort(true);
		wsSelSrc.setDisplayZoomControls(false);
		
		// 使能javascript
		wsSelSrc.setJavaScriptEnabled(true);
		wsSelSrc.setJavaScriptCanOpenWindowsAutomatically(false);
		
		// 自适应屏幕
		// wsSelSrc.setLayoutAlgorithm(LayoutAlgorithm.SINGLE_COLUMN);
		wsSelSrc.setLoadWithOverviewMode(true);
		
		wvSelSrc.loadUrl(getHomeUrl());
		
	}
	
	private void clearURLfocus()
	{
		// etURL.clearFocus();
		wvSelSrc.requestFocus();
		((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
				.hideSoftInputFromWindow(etURL.getWindowToken(),
						InputMethodManager.HIDE_NOT_ALWAYS);
		
		if (pbWebView.getProgress() == 0)
		{
			setURLcmd(URL_REFRESH);
		}
		else
		{
			setURLcmd(URL_CANCEL);
		}
		
		if (!curWebPageTitle.isEmpty())
		{
			etURL.setText(curWebPageTitle);
		}
	}
	
	private void getParaConfig()
	{
		spMain = getSharedPreferences(SPMAIN_NAME, 0);
	}
	
	public String getHomeUrl()
	{
		return spMain.getString(HOME_URL_KEY,
				getString(R.string.defaultHomeUrl));
	}
	
	private int getSearchEngine()
	{
		int searchEngineIndex=spMain.getInt(SEARCH_ENGINE_KEY, 0);
		
		if((searchEngineIndex<0)||(searchEngineIndex>SEARCH_ENGINE_URL.length))
		{
			searchEngineIndex=0;
		}
		
		return searchEngineIndex;
	}
	
	private void setSearchEngine(int searchEngineIndex)
	{
		Editor editor = spMain.edit();
		editor.putInt(SEARCH_ENGINE_KEY,
				searchEngineIndex);
		editor.commit();
	}
	
	private void setURLcmd(int cmd)
	{
		if (cmd < URLCMD_ICON.length)
		{
			URLcmd = cmd;
			btnURLcmd.setBackgroundResource(URLCMD_ICON[cmd]);
		}
	}
	
	private void oclBrowserBtnInit()
	{
		oclBrowserBtn = new View.OnClickListener()
		{
			
			@Override
			public void onClick(View v)
			{
				int viewId = v.getId();
				
				if ((viewId == R.id.buttonURLcmd)
						|| (viewId == R.id.FrameLayoutURLcmd))
				{
					switch (URLcmd)
					{
						case URL_CANCEL:
							if (pbWebView.getProgress() != 0)
							{
								wvSelSrc.stopLoading();
								pbWebView.setProgress(0);
							}
						break;
						
						case URL_REFRESH:
							wvSelSrc.reload();
						break;
						
						case URL_ENTER:
							wvSelSrc.loadUrl(etURL.getText().toString());
						break;
						
						case URL_SEARCH:
							wvSelSrc.loadUrl("http://www.baidu.com/s?wd="
									+ etURL.getText().toString());
						break;
						
						default:
						break;
					}
				}
				else
				{
					switch (viewId)
					{
						case R.id.buttonBack:
							if (wvSelSrc.canGoBack())
							{
								wvSelSrc.goBack();
							}
						break;
						
						case R.id.buttonForward:
							if (wvSelSrc.canGoForward())
							{
								wvSelSrc.goForward();
							}
						break;
						
						case R.id.buttonSpiderGo:
							if (spMain.getBoolean(SPIDERGO_NOT_CONFIRM, false))
							{
								spiderGo();
							}
							else
							{
								showDialog(DLG.SPIDER_GO_CONFIRM.ordinal());
							}
						break;
						
						case R.id.buttonHome:
							wvSelSrc.loadUrl(getHomeUrl());
						break;
						
						case R.id.buttonMenu:
							openSettingPage();
						break;
						
						default:
							Log.i(LOG_TAG, "oclBrowserBtn Unknown Button");
						break;
					}
				}
				
				if (etURL.isFocused())
				{
					clearURLfocus();
				}
			}
		};
	}
	
	private void URLbarInit()
	{
		URLbar = (RelativeLayout) findViewById(R.id.urlBar);
		URLbar.setOnClickListener(new View.OnClickListener()
		{
			
			@Override
			public void onClick(View v)
			{
				Log.i(LOG_TAG, "URLbar onclick");
				etURL.requestFocus();
				((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
						.showSoftInput(etURL, InputMethodManager.SHOW_IMPLICIT);
			}
		});
		
		btnURLcmd = (Button) findViewById(R.id.buttonURLcmd);
		btnURLcmd.setOnClickListener(oclBrowserBtn);
		
		findViewById(R.id.FrameLayoutURLcmd).setOnClickListener(oclBrowserBtn);
		
		etURL = (EditText) findViewById(R.id.editTextUrl);
		etURL.setSelectAllOnFocus(true);
		etURL.setOnFocusChangeListener(new View.OnFocusChangeListener()
		{
			
			@Override
			public void onFocusChange(View v, boolean hasFocus)
			{
				if (hasFocus)
				{
					// btnURLcmd.setVisibility(View.VISIBLE);
					etURL.setText(wvSelSrc.getUrl());
					etURL.selectAll();
					
					setURLcmd(URL_ENTER);
				}
			}
		});
		
		etURL.addTextChangedListener(new TextWatcher()
		{
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after)
			{
				
			}
			
			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count)
			{
				
			}
			
			@Override
			public void afterTextChanged(Editable s)
			{
				if (etURL.hasFocus())
				{
					setBtnCmdDisplayByURL(s.toString());
				}
			}
			
		});
	}
	
	private void setBtnCmdDisplayByURL(String URL)
	{
		if (URLUtil.isNetworkUrl(URL))
		{
			setURLcmd(URL_ENTER);
		}
		else if (URL.isEmpty())
		{
			setURLcmd(URL_CANCEL);
		}
		else
		{
			setURLcmd(URL_SEARCH);
		}
	}
	
	private void naviBarInit()
	{
		findViewById(R.id.buttonBack).setOnClickListener(oclBrowserBtn);
		findViewById(R.id.buttonForward).setOnClickListener(oclBrowserBtn);
		findViewById(R.id.buttonSpiderGo).setOnClickListener(oclBrowserBtn);
		findViewById(R.id.buttonHome).setOnClickListener(oclBrowserBtn);
		findViewById(R.id.buttonMenu).setOnClickListener(oclBrowserBtn);
		
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sel_src);
		
		getParaConfig();
		
		oclBrowserBtnInit();
		URLbarInit();
		naviBarInit();
		webViewInit();
		
		Log.i(LOG_TAG, "onCreate");
	}
	
	protected void onStart()
	{
		super.onStart();
		Log.i(LOG_TAG, "onStart");
	}
	
	protected void onResume()
	{
		super.onResume();
		Log.i(LOG_TAG, "onResume");
		
	}
	
	protected void onPause()
	{
		super.onPause();
		Log.i(LOG_TAG, "onPause");
		
	}
	
	protected void onStop()
	{
		Log.i(LOG_TAG, "onStop");
		
		super.onStop();
	}
	
	protected void onDestroy()
	{
		super.onDestroy();
		Log.i(LOG_TAG, "onDestroy");
		
		wvSelSrc.stopLoading();
		wvSelSrc.clearCache(true);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		
		// Inflate the menu; this adds items to the action bar if it is present.
		
		return true;
	}
	
	private void openSettingPage()
	{
		Log.i(LOG_TAG, "openSettingPage");
		
		Intent intent = new Intent(this, ParaConfigActivity.class);
		
		String srcUrl = wvSelSrc.getUrl();
		
		Bundle bundle = new Bundle();
		bundle.putString(SOURCE_URL_BUNDLE_KEY, srcUrl);
		intent.putExtras(bundle);
		
		startActivity(intent);
	}
	
	public void spiderGo()
	{
		Log.i(LOG_TAG, "spiderGo");
		
		Intent intent = new Intent(this, SpiderCrawlActivity.class);
		
		String srcUrl = wvSelSrc.getUrl();
		
		Bundle bundle = new Bundle();
		bundle.putString(SOURCE_URL_BUNDLE_KEY, srcUrl);
		intent.putExtras(bundle);
		
		Toast.makeText(this, getString(R.string.srcUrl) + ":" + srcUrl,
				Toast.LENGTH_SHORT).show();
		;
		
		startActivity(intent);// 直接切换Activity不接收返回结果
	}
	
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		Log.i(LOG_TAG, "onKeyDown " + keyCode);
		
		if (keyCode == KeyEvent.KEYCODE_BACK)
		{
			if (wvSelSrc.canGoBack())
			{
				wvSelSrc.goBack();
				
				Log.i(LOG_TAG, "goBack ");
				// String curUrl = wvSelSrc.getUrl();
				// if(curUrl!=tvSrcUrl.getText())
				return true;
			}
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
