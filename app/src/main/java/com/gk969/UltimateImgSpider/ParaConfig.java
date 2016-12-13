package com.gk969.UltimateImgSpider;

import android.content.Context;
import android.content.SharedPreferences.Editor;

class ParaConfig {
    private final static String SPMAIN_NAME = "spMain";
    private final static String SPIDERGO_NOT_CONFIRM = "spiderGoConfirm";
    private final static String USER_AGENT_KEY = "userAgent";

    private static final String SEARCH_ENGINE_KEY = "searchEngine";
    private static final int SEARCH_ENGINE_ICON[] = {
            R.drawable.baidu,
            R.drawable.bing,
            R.drawable.sogou,
            R.drawable.google
    };
    private static final String SEARCH_ENGINE_URL[] = {
            "http://www.baidu.com/s?wd=",
            "https://cn.bing.com/search?q=",
            "http://www.sogou.com/web?query=",
            "https://www.google.com/search?q="
    };
    public static final CharSequence SEARCH_ENGINE_NAME[] = {"百度", "Bing", "搜狗", "Google"};
    
    public static boolean setSearchEngine(Context ctx, int searchEngineIndex) {
        if((searchEngineIndex >= SEARCH_ENGINE_NAME.length)
                || (searchEngineIndex < 0)) {
            return false;
        }
        
        Editor editor = ctx.getSharedPreferences(SPMAIN_NAME, 0).edit();
        editor.putInt(SEARCH_ENGINE_KEY, searchEngineIndex);
        editor.apply();
        return true;
    }
    
    public static int getSearchEngine(Context ctx) {
        int searchEngineIndex = ctx.getSharedPreferences(SPMAIN_NAME, 0)
                .getInt(SEARCH_ENGINE_KEY, 0);
        
        if((searchEngineIndex < 0)
                || (searchEngineIndex > SEARCH_ENGINE_URL.length)) {
            searchEngineIndex = 0;
        }
        
        return searchEngineIndex;
    }
    
    public static int getSearchEngineIcon(Context ctx) {
        return SEARCH_ENGINE_ICON[getSearchEngine(ctx)];
    }
    
    public static String getSearchEngineURL(Context ctx) {
        return SEARCH_ENGINE_URL[getSearchEngine(ctx)];
    }
    
    public static String getSearchEngineName(Context ctx) {
        return SEARCH_ENGINE_NAME[getSearchEngine(ctx)].toString();
    }
    
    public static void setSpiderGoConfirm(Context ctx, boolean noConfirm) {
        Editor editor = ctx.getSharedPreferences(SPMAIN_NAME, 0).edit();
        editor.putBoolean(SPIDERGO_NOT_CONFIRM, noConfirm);
        editor.apply();
    }
    
    public static boolean isSpiderGoNeedConfirm(Context ctx) {
        return ctx.getSharedPreferences(SPMAIN_NAME, 0).getBoolean(
                SPIDERGO_NOT_CONFIRM, false);
    }

    public static void setUserAgent(Context ctx, String ua) {
        Editor editor = ctx.getSharedPreferences(SPMAIN_NAME, Context.MODE_MULTI_PROCESS).edit();
        editor.putString(USER_AGENT_KEY, ua);
        editor.apply();
    }
    
    public static String getUserAgent(Context ctx) {
        return ctx.getSharedPreferences(SPMAIN_NAME, Context.MODE_MULTI_PROCESS).getString(
                USER_AGENT_KEY, ctx.getString(R.string.defaultUserAgent));
    }
    
    public static boolean isFirstRun(Context ctx) {
        return ctx.getSharedPreferences(SPMAIN_NAME, 0).getBoolean("firstRun",
                true);
    }
    
    public static void setFirstRun(Context ctx) {
        Editor editor = ctx.getSharedPreferences(SPMAIN_NAME, 0).edit();
        editor.putBoolean("firstRun", false);
        editor.apply();
        
        setUserAgent(ctx, ctx.getString(R.string.defaultUserAgent));
    }
}