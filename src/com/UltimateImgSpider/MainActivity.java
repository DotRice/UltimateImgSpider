package com.UltimateImgSpider;

import android.support.v7.app.ActionBarActivity;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends ActionBarActivity
{
	private String	LOG_TAG	= "MainActivity";
	
	SelSrcFragment	spiderSelSrc;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		spiderSelSrc = new SelSrcFragment();
		
		getSupportFragmentManager().beginTransaction()
				.add(R.id.mainFrameLayout, spiderSelSrc).commit();
		
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
		
	}
	
	/*
	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		// TODO Auto-generated method stub
		super.onConfigurationChanged(newConfig);
		if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
		{
			Log.i(LOG_TAG, "∫·∆¡");
			setContentView(R.layout.activity_main);
		} else if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
		{
			Log.i(LOG_TAG, " ˙∆¡");
			setContentView(R.layout.activity_main);
		}
	}
	*/
	
	@SuppressLint("NewApi") @Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		
		getMenuInflater().inflate(R.menu.main, menu);
		
		final ActionBar bar = getActionBar();
		
		// bar.setDisplayHomeAsUpEnabled(true);
		bar.setDisplayShowTitleEnabled(false);
		
		return true;
	}
	
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
				return true;
				
			case R.id.action_refresh:
				Log.i(LOG_TAG, "action_refresh");
				spiderSelSrc.wvSelSrc.reload();
				return true;
				
			case R.id.action_settings:
				Log.i(LOG_TAG, "action_settings");
				return true;
				
			case R.id.action_exit:
				finish();
				return true;
				
			default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}
	
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		Log.i(LOG_TAG, "onKeyDown " + keyCode);
		
		if (keyCode == KeyEvent.KEYCODE_BACK)
		{
			if (spiderSelSrc.webViewSelSrcGoBack())
			{
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}
	
}
