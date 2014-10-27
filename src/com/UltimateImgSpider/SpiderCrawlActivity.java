package com.UltimateImgSpider;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class SpiderCrawlActivity extends Activity
{
	private String sourceUrl;
	private String LOG_TAG="SpiderCrawlActivity";
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_spider_crawl);
		
		Intent intent = this.getIntent();        //��ȡ���е�intent����  
		Bundle bundle = intent.getExtras();    //��ȡintent�����bundle����  
		sourceUrl = bundle.getString(SelSrcActivity.SOURCE_URL_BUNDLE_KEY);    //��ȡBundle������ַ���  
		
		if(sourceUrl!=null)
		{
			Log.i(LOG_TAG, "SourceUrl:"+sourceUrl);
		}
	}
	
}
