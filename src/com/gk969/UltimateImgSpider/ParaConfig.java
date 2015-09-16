package com.gk969.UltimateImgSpider;

import android.content.Context;
import android.content.SharedPreferences.Editor;

public class ParaConfig
{
    
    final static String              SPMAIN_NAME          = "spMain";
    
    final static String              SPIDERGO_NOT_CONFIRM = "spiderGoConfirm";
    
    final static String              HOME_URL_KEY         = "homeUrl";
    
    final static String              USER_AGENT_KEY       = "userAgent";
    
    public static final int          SEARCH_ENGINE_ICON[] = { 
        R.drawable.baidu,
        R.drawable.bing, 
        R.drawable.sogou, 
        R.drawable.google 
    };
    public static final String       SEARCH_ENGINE_URL[]  = {
            "http://www.baidu.com/s?wd=", 
            "https://cn.bing.com/search?q=",
            "http://www.sogou.com/web?query=",
            "https://www.google.com/search?q="
    };
    
    public static final String       SEARCH_ENGINE_KEY    = "searchEngine";
    public static final CharSequence SEARCH_ENGINE_NAME[] = { "百度", "Bing", "搜狗", "Google"};
    
    public static boolean setSearchEngine(Context ctx, int searchEngineIndex)
    {
        if ((searchEngineIndex >= SEARCH_ENGINE_NAME.length)
                || (searchEngineIndex < 0))
        {
            return false;
        }
        
        Editor editor = ctx.getSharedPreferences(SPMAIN_NAME, 0).edit();
        editor.putInt(SEARCH_ENGINE_KEY, searchEngineIndex);
        editor.commit();
        return true;
    }
    
    public static int getSearchEngine(Context ctx)
    {
        int searchEngineIndex = ctx.getSharedPreferences(SPMAIN_NAME, 0)
                .getInt(SEARCH_ENGINE_KEY, 0);
        
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
    
    public static String getSearchEngineName(Context ctx)
    {
        return SEARCH_ENGINE_NAME[getSearchEngine(ctx)].toString();
    }
    
    public static void setSpiderGoConfirm(Context ctx, boolean noConfirm)
    {
        Editor editor = ctx.getSharedPreferences(SPMAIN_NAME, 0).edit();
        editor.putBoolean(SPIDERGO_NOT_CONFIRM, noConfirm);
        editor.commit();
    }
    
    public static boolean isSpiderGoNeedConfirm(Context ctx)
    {
        return ctx.getSharedPreferences(SPMAIN_NAME, 0).getBoolean(
                SPIDERGO_NOT_CONFIRM, false);
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
    
    public static void setUserAgent(Context ctx, String ua)
    {
        Editor editor = ctx.getSharedPreferences(SPMAIN_NAME, 0).edit();
        editor.putString(USER_AGENT_KEY, ua);
        editor.commit();
    }
    
    public static String getUserAgent(Context ctx)
    {
        return ctx.getSharedPreferences(SPMAIN_NAME, 0).getString(
                USER_AGENT_KEY, ctx.getString(R.string.defaultUserAgent));
    }
    
    public static boolean isFirstRun(Context ctx)
    {
        return ctx.getSharedPreferences(SPMAIN_NAME, 0).getBoolean("firstRun",
                true);
    }
    
    public static void setFirstRun(Context ctx)
    {
        Editor editor = ctx.getSharedPreferences(SPMAIN_NAME, 0).edit();
        editor.putBoolean("firstRun", false);
        editor.commit();
        
        setUserAgent(ctx, ctx.getString(R.string.defaultUserAgent));
    }
}