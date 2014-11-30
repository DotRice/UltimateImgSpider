package com.UltimateImgSpider;

import android.content.Context;
import android.content.SharedPreferences.Editor;

public class ParaConfig
{

	final static String				SPMAIN_NAME				= "spMain";
	
	final static String				SPIDERGO_NOT_CONFIRM	= "spiderGoConfirm";
	
	final static String				HOME_URL_KEY			= "homeUrl";
	

	public static final int				SEARCH_ENGINE_ICON[]	= {
			R.drawable.baidu, R.drawable.bing, R.drawable.sogou,
			R.drawable.google								};
	public static final String			SEARCH_ENGINE_URL[]		= {
			"http://www.baidu.com/s?wd=", "https://cn.bing.com/search?q=",
			"http://www.sogou.com/web?query=",
			"https://www.google.com/search?q="				};
	public static final String			SEARCH_ENGINE_KEY		= "searchEngine";
	public static final CharSequence	SEARCH_ENGINE_NAME[]	= { "°Ù¶È", "Bing",
			"ËÑ¹·", "Google"									};

	public static void setSearchEngine(Context ctx, int searchEngineIndex)
	{
		Editor editor = ctx.getSharedPreferences(SPMAIN_NAME, 0).edit();
		editor.putInt(SEARCH_ENGINE_KEY, searchEngineIndex);
		editor.commit();
	}
	
	public static int getSearchEngine(Context ctx)
	{
		int searchEngineIndex = ctx.getSharedPreferences(SPMAIN_NAME, 0).getInt(SEARCH_ENGINE_KEY, 0);
		
		if ((searchEngineIndex < 0)
				|| (searchEngineIndex > SEARCH_ENGINE_URL.length))
		{
			searchEngineIndex = 0;
		}
		
		return searchEngineIndex;
	}
	
	public static int getSearchEngineIcon(Context ctx)
	{
		return SEARCH_ENGINE_ICON[getSearchEngine(ctx)];
	}
	
	public static String getSearchEngineURL(Context ctx)
	{
		return SEARCH_ENGINE_URL[getSearchEngine(ctx)];
	}
	
	public static void setSpiderGoConfirm(Context ctx, boolean noConfirm)
	{
		Editor editor = ctx.getSharedPreferences(SPMAIN_NAME, 0).edit();
		editor.putBoolean(SPIDERGO_NOT_CONFIRM,
				noConfirm);
		editor.commit();
	}
	
	public static boolean isSpiderGoNeedConfirm(Context ctx)
	{
		return ctx.getSharedPreferences(SPMAIN_NAME, 0).getBoolean(SPIDERGO_NOT_CONFIRM, false);
	}
	
	public static void setHomeURL(Context ctx, String URL)
	{
		Editor editor = ctx.getSharedPreferences(SPMAIN_NAME, 0).edit();
		editor.putString(HOME_URL_KEY, URL);
		editor.commit();
	}
	
	public static String getHomeURL(Context ctx)
	{
		return ctx.getSharedPreferences(SPMAIN_NAME, 0).getString(HOME_URL_KEY,
				ctx.getString(R.string.defaultHomeUrl));
	}
}