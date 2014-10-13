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
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.support.v13.app.FragmentPagerAdapter;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;

public class MainActivity extends Activity
{
	
	private String			LOG_TAG	= "MainActivity";
	
	SelSrcFragment	spiderSelSrc;

	SharedPreferences spMain;
	final static String SPMAIN_NAME="spMain";
	final static String SPIDERGO_NOT_CONFIRM="spiderGoConfirm";

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
            .setMultiChoiceItems(R.array.noLongerConfirm,
                    new boolean[]{false},
                    new DialogInterface.OnMultiChoiceClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton,
                                boolean isChecked) {

                            /* User clicked on a check box do some stuff */

							Editor editor = spMain.edit();
							editor.putBoolean(SPIDERGO_NOT_CONFIRM, isChecked);
							editor.commit();
                        }
                    })
            .setPositiveButton(R.string.OK,
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    /* User clicked Yes so do some stuff */
                	spiderGo();
                }
            })
            .setNegativeButton(R.string.cancel,
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {

                    /* User clicked No so do some stuff */
                }
            })
           .create();
		}

		}
		return null;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		final ActionBar bar = getActionBar();
		// bar.setDisplayHomeAsUpEnabled(true);
		bar.setDisplayShowTitleEnabled(false);
		
		spiderSelSrc = new SelSrcFragment();
		
		getFragmentManager().beginTransaction()
				.add(R.id.mainFrameLayout, spiderSelSrc).commit();
		
		spMain = getSharedPreferences(SPMAIN_NAME, 0);
		
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
				
				if(spMain.getBoolean(SPIDERGO_NOT_CONFIRM, false))
				{
					spiderGo();
				}
				else
				{
					showDialog(DLG.SPIDER_GO_CONFIRM.ordinal());
				}
					
				
				return true;

			case R.id.action_home:
				Log.i(LOG_TAG, "action_home");
				spiderSelSrc.wvSelSrc.loadUrl(spiderSelSrc.HOME_URL);
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
	
	
	public void spiderGo()
	{
		Log.i(LOG_TAG, "spiderGo");
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
