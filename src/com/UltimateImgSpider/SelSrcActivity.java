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
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SelSrcActivity extends Activity
{
	private final String	LOG_TAG					= "SelSrcActivity";
	
	private WebView			wvSelSrc;
	private WebSettings		wsSelSrc;
	
	private ProgressBar		pbWebView;
	
	private Button			btnGo;
	private EditText		etURL;
	private RelativeLayout	URLbar;
	
	private final int		PROGRESS_MAX			= 100;
	
	private String curWebPageTitle;
	
	private Handler			mHandler				= new Handler();
	
	SharedPreferences		spMain;
	final static String		SPMAIN_NAME				= "spMain";
	final static String		SPIDERGO_NOT_CONFIRM	= "spiderGoConfirm";
	final static String		HOME_URL_KEY			= "homeUrl";
	
	final static String		SOURCE_URL_BUNDLE_KEY	= "SourceUrl";
	
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
				// actionbar.setTitle(url);
			}
			
			public void onPageStarted(WebView view, String url, Bitmap favicon)
			{
				Log.i(LOG_TAG, "onPageStarted " + url);
				pbWebView.setVisibility(View.VISIBLE);
				// actionbar.setTitle(url);
				etURL.setText(url);
				curWebPageTitle="";
			}
		});
		
		wvSelSrc.setWebChromeClient(new WebChromeClient()
		{
			public void onProgressChanged(WebView view, int newProgress)
			{
				// Log.i(LOG_TAG, view.getUrl() + " Progress " + newProgress);
				
				pbWebView.setProgress(newProgress);
				
				if (newProgress == PROGRESS_MAX)
				{
					mHandler.postDelayed(new Runnable()
					{
						public void run()
						{
							pbWebView.setProgress(0);
						}
					}, 500);
				}
				
			}
			
			public void onReceivedTitle(WebView view, String title)
			{
				etURL.setText(title);
				curWebPageTitle=title;
			}
		});
		
		wvSelSrc.setOnTouchListener(new View.OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				// TODO Auto-generated method stub
				if (etURL.isFocused())
				{
					clearURLfocus();
					if (etURL.getText().length() == 0)
					{
						btnGo.setVisibility(View.GONE);
					}
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
	
	private void URLbarInit()
	{
		URLbar = (RelativeLayout) findViewById(R.id.urlBar);
		URLbar.setOnClickListener(new View.OnClickListener()
		{
			
			@Override
			public void onClick(View v)
			{
				// TODO Auto-generated method stub
				Log.i(LOG_TAG, "URLbar onclick");
				etURL.requestFocus();
				((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
						.showSoftInput(etURL, InputMethodManager.SHOW_IMPLICIT);
			}
		});
		
		btnGo = (Button) findViewById(R.id.buttonGo);
		//btnGo.setVisibility(View.GONE);
		btnGo.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				// TODO Auto-generated method stub
				String cmd = btnGo.getText().toString();
				if (cmd.equals(getString(R.string.cancel)))
				{
					Log.i(LOG_TAG, "URLbar Cancel");
					clearURLfocus();
				} else if (cmd.equals(getString(R.string.enter)))
				{
					
				} else if (cmd.equals(getString(R.string.search)))
				{
					
				}
			}
		});
		
		etURL = (EditText) findViewById(R.id.editTextUrl);
		etURL.setSelectAllOnFocus(true);
		etURL.setOnFocusChangeListener(new View.OnFocusChangeListener()
		{
			
			@Override
			public void onFocusChange(View v, boolean hasFocus)
			{
				// TODO Auto-generated method stub
				if (hasFocus)
				{
					//btnGo.setVisibility(View.VISIBLE);
					etURL.setText(wvSelSrc.getUrl());
					etURL.selectAll();
					btnGo.setBackgroundDrawable(null);;
					btnGo.setText(R.string.refresh);
				}
				else
				{
					btnGo.setBackgroundResource(R.drawable.spidergo);
					btnGo.setText("");
					if(!curWebPageTitle.isEmpty())
					{
						etURL.setText(curWebPageTitle);
					}
				}
			}
		});
		
		etURL.addTextChangedListener(new TextWatcher()
		{
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after)
			{
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count)
			{
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void afterTextChanged(Editable s)
			{
				// TODO Auto-generated method stub
				if (etURL.hasFocus())
				{
					setBtnGoDisplay(s.toString());
				}
			}
			
		});
	}
	
	private void setBtnGoDisplay(String URL)
	{
		String LowerCaseURL = URL.toLowerCase();
		
		if (LowerCaseURL.startsWith("http://")
				|| (LowerCaseURL.startsWith("https://")))
		{
			btnGo.setTextColor(Color
					.parseColor(getString(R.string.colorAction)));
			btnGo.setText(R.string.enter);
		} else if (URL.isEmpty())
		{
			btnGo.setTextColor(Color
					.parseColor(getString(R.string.colorCancel)));
			btnGo.setText(R.string.cancel);
		} else
		{
			btnGo.setTextColor(Color
					.parseColor(getString(R.string.colorAction)));
			btnGo.setText(R.string.search);
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sel_src);
		
		getParaConfig();
		
		URLbarInit();
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
		getMenuInflater().inflate(R.menu.main, menu);
		
		return true;
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		switch (id)
		{
			case R.id.action_spiderGo:
				Log.i(LOG_TAG, "action_spiderGo");
				
				if (spMain.getBoolean(SPIDERGO_NOT_CONFIRM, false))
				{
					spiderGo();
				} else
				{
					showDialog(DLG.SPIDER_GO_CONFIRM.ordinal());
				}
				
				return true;
				
			case R.id.action_home:
				Log.i(LOG_TAG, "action_home");
				wvSelSrc.loadUrl(getHomeUrl());
				return true;
				
			case R.id.action_refresh:
				Log.i(LOG_TAG, "action_refresh");
				
				wvSelSrc.reload();
				return true;
				
			case R.id.action_more:
				Log.i(LOG_TAG, "action_more");
				return true;
				
			case R.id.action_help:
				Log.i(LOG_TAG, "action_help");
				return true;
				
			case R.id.action_settings:
				Log.i(LOG_TAG, "action_settings");
				
				Intent intent = new Intent(this, ParaConfigActivity.class);
				
				String srcUrl = wvSelSrc.getUrl();
				
				Bundle bundle = new Bundle();
				bundle.putString(SOURCE_URL_BUNDLE_KEY, srcUrl);
				intent.putExtras(bundle);
				
				startActivity(intent);// 直接切换Activity不接收返回结果
				return true;
				
			case R.id.action_exit:
				Log.i(LOG_TAG, "action_exit");
				finish();
				return true;
				
			default:
			break;
		}
		
		return true;
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
		} else
		{
			Log.i(LOG_TAG, "Portrait");
		}
	}
}
