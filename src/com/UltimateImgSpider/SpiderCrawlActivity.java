package com.UltimateImgSpider;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class SpiderCrawlActivity extends Activity
{
    private String srcUrl;
    private String LOG_TAG = "SpiderCrawl";
    
    private WebView spider;
    
    private boolean getSrcUrlFromBundle()
    {
        Intent intent = this.getIntent(); // 获取已有的intent对象
        Bundle bundle = intent.getExtras(); // 获取intent里面的bundle对象
        
        srcUrl= bundle.getString(SelSrcActivity.SOURCE_URL_BUNDLE_KEY); // 获取Bundle里面的字符串
        Toast.makeText(this, getString(R.string.srcUrl) + ":" + srcUrl, Toast.LENGTH_SHORT).show();
        
        return srcUrl!=null;
    }
    
    private void spiderInit()
    {
        spider=(WebView) findViewById(R.id.wvSpider);
        
        spider.setWebViewClient(new WebViewClient()
        {
            public boolean shouldOverrideUrlLoading(WebView view, String url)
            {
                return false;
            }

            public void onPageFinished(WebView view, String url)
            {
                Log.i(LOG_TAG, "onPageFinished " + url);
                view.loadUrl("javascript:"
                        + "var i;"
                        + "var img=document.getElementsByTagName(\"img\");"
                        + "for(i=0; i<img.length; i++)"
                        + "{SpiderCrawl.recvPicSrc(img[i].src)}"
                        + "var a=document.getElementsByTagName(\"a\");"
                        + "for(i=0; i<a.length; i++)"
                        + "{SpiderCrawl.recvUrl(a[i].href)}");
            }

            public void onPageStarted(WebView view, String url, Bitmap favicon)
            {
                Log.i(LOG_TAG, "onPageStarted " + url);
            }

            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
            {
                Log.i(LOG_TAG, failingUrl + " ReceivedError " + errorCode + "  " + description);
            }
        });

        spider.setWebChromeClient(new WebChromeClient()
        {
            
        });
        
        WebSettings setting = spider.getSettings();
        setting.setUserAgentString(ParaConfig.getUserAgent(this));
        
        setting.setLoadsImagesAutomatically(false);
        setting.setBlockNetworkImage(true);
        // 使能javascript
        setting.setJavaScriptEnabled(true);
        setting.setJavaScriptCanOpenWindowsAutomatically(false);

        spider.addJavascriptInterface(this, "SpiderCrawl");
        
        spider.requestFocus();

        spider.loadUrl(srcUrl);
    }
    
    
    @JavascriptInterface
    public void recvPicSrc(String picSrc)
    {
        Log.i(LOG_TAG, "picSrc:"+picSrc);
    }
    
    @JavascriptInterface
    public void recvUrl(String url)
    {
        Log.i(LOG_TAG, "url:"+url);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spider_crawl);
        
        if(!getSrcUrlFromBundle())
        {
            return;
        }
        
        spiderInit();
    }


    protected void onDestroy()
    {
        super.onDestroy();
        Log.i(LOG_TAG, "onDestroy");

        spider.stopLoading();
        spider.clearCache(true);
        spider.destroy();
    }

    
    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        
        Log.i(LOG_TAG, (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)?"Landscape":"Portrait");
    }
}
