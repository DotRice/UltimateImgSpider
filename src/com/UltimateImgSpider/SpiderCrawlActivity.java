package com.UltimateImgSpider;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.utils.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

public class SpiderCrawlActivity extends Activity
{
    private String        LOG_TAG            = "SpiderCrawl";

    /*
     * 遍历一个网站所有页面，并且下载所有图片。
     * 
     * 深度优先搜索
     * 
     * 网页遍历算法： 扫描当前网页上每一个本站URL，查找所有不在网页列表中的URL，存入列表并设置为等待状态。
     * 扫描网页列表中所有等待状态的URL，将与当前页面URL最相似的作为下次要扫描的页面。如果列表中全部都为已下载页面，则遍历结束。
     * 
     * 资源下载算法： 扫描当前网页上的所有图片，查找源URL不在图片列表中的图片。
     * 下载并将源URL存入列表，以下载序号作为文件名，如果此图片存在alt则将alt加下载序号作为文件名。
     */

    private String        srcUrl;

    private final int     URL_TYPE_PAGE      = 0;
    private final int     URL_TYPE_IMG       = 1;

    private String        curUrl;
    private String        srcHost;

    private int           pageUrlCnt         = 0;
    private int           pageScanCnt        = 0;
    private int           imgUrlCnt          = 0;
    private int           imgDownloadCnt     = 0;

    private TextView      spiderLog;

    private boolean       pageFinished       = false;
    private long          loadTimer;
    private long          loadTime;
    private long          scanTimer;
    private long          scanTime;

    private WebView       spider;

    private Runnable      loadNextUrlAfterScan;
    private Runnable      urlLoadTimeOut;
    private Handler       spiderHandler      = new Handler();
    private boolean       timerRunning       = true;
    private final int     URL_TIME_OUT       = 10;
    private AtomicInteger urlLoadTimer       = new AtomicInteger(URL_TIME_OUT);
    private AtomicBoolean urlLoadPostSuccess = new AtomicBoolean(true);

    public native String stringFromJNI(String srcStr);

    public native boolean jniUrlListInit();

    public native void jniOnDestroy();

    public native int jniAddUrl(String url, int hashCode, int type);

    private native String jniFindNextUrlToLoad(String prevUrl, int type);

    static
    {
        System.loadLibrary("UltimateImgSpider");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spider_crawl);

        if (!getSrcUrlFromBundle())
        {
            return;
        }

        if (!jniUrlListInit())
        {
            Log.i(LOG_TAG, "jniUrlListInit fail");
        }

        spiderInit();

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
        timerRunning = false;
        spider.stopLoading();
        spider.clearCache(true);
        spider.destroy();
        jniOnDestroy();
    }

    private boolean getSrcUrlFromBundle()
    {
        Intent intent = this.getIntent();
        Bundle bundle = intent.getExtras();

        srcUrl = bundle.getString(SelSrcActivity.SOURCE_URL_BUNDLE_KEY); // 获取Bundle里面的字符串
        Toast.makeText(this, getString(R.string.srcUrl) + ":" + srcUrl, Toast.LENGTH_SHORT).show();

        return srcUrl != null;
    }

    private void dispSpiderLog()
    {
        pageScanCnt++;
        String log = imgUrlCnt + " " + pageScanCnt + "/" + pageUrlCnt + " " + loadTime + " " + scanTime + " " + curUrl;
        spiderLog.setText(log);
        // Log.i(LOG_TAG, log);
    }

    private void scanPageWithJS()
    {
        scanTimer = System.currentTimeMillis();
        spider.loadUrl("javascript:" + "var i;" + "var img=document.getElementsByTagName(\"img\");"
                + "for(i=0; i<img.length; i++)" + "{SpiderCrawl.recvImgUrl(img[i].src)}"
                + "var a=document.getElementsByTagName(\"a\");" + "for(i=0; i<a.length; i++)"
                + "{SpiderCrawl.recvPageUrl(a[i].href)}" + "SpiderCrawl.onCurPageScaned();");
    }

    private void spiderInit()
    {
        spiderLog = (TextView) findViewById(R.id.tvSpiderLog);

        spider = (WebView) findViewById(R.id.wvSpider);

        spider.setWebViewClient(new WebViewClient()
        {
            public boolean shouldOverrideUrlLoading(WebView view, String url)
            {
                return false;
            }

            public void onPageFinished(WebView view, String url)
            {
                loadTime = System.currentTimeMillis() - loadTimer;
                Log.i(LOG_TAG, "onPageFinished " + url + " loadTime:" + loadTime);

                if (!pageFinished)
                {
                    pageFinished = true;
                    if (curUrl.equals(url))
                    {
                        urlLoadTimer.set(0);
                        scanPageWithJS();
                    }
                }
            }

            public void onPageStarted(WebView view, String url, Bitmap favicon)
            {
                Log.i(LOG_TAG, "onPageStarted " + url);
                loadTimer = System.currentTimeMillis();
                pageFinished = false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url)
            {
                WebResourceResponse response = null;
                if (!curUrl.equals(url))
                {
                    response = new WebResourceResponse("image/png", "UTF-8", null);
                }
                return response;
            }

            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
            {
                Log.i(LOG_TAG, failingUrl + " ReceivedError " + errorCode + "  " + description);
            }
        });

        spider.setWebChromeClient(new WebChromeClient()
        {
            public void onProgressChanged(WebView view, int newProgress)
            {
                if (newProgress == 100)
                {

                }
            }
        });

        WebSettings setting = spider.getSettings();
        setting.setUserAgentString(ParaConfig.getUserAgent(this));

        // 阻止图片
        setting.setLoadsImagesAutomatically(false);

        // setting.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // 使能javascript
        setting.setJavaScriptEnabled(true);
        setting.setJavaScriptCanOpenWindowsAutomatically(false);

        // 启用缩放
        setting.setSupportZoom(true);
        setting.setBuiltInZoomControls(true);
        setting.setUseWideViewPort(true);
        setting.setDisplayZoomControls(false);

        // 自适应屏幕
        setting.setLoadWithOverviewMode(true);

        spider.addJavascriptInterface(this, "SpiderCrawl");

        loadNextUrlAfterScan = new Runnable()
        {
            @Override
            public void run()
            {
                spiderLoadUrl(curUrl);
                dispSpiderLog();
            }
        };

        urlLoadTimeOut = new Runnable()
        {

            @Override
            public void run()
            {
                Log.i(LOG_TAG, "Load TimeOut!!");
                spider.stopLoading();
                scanPageWithJS();
            }
        };

        new timerThread().start();

        try
        {
            srcHost = new URL(srcUrl).getHost();
            pageUrlCnt = jniAddUrl(srcUrl, srcUrl.hashCode(), URL_TYPE_PAGE);
            Log.i(LOG_TAG, "jniAddUrl " + srcUrl + " " + pageUrlCnt);
            curUrl = jniFindNextUrlToLoad(null, URL_TYPE_PAGE);
            spiderLoadUrl(curUrl);
            dispSpiderLog();
        }
        catch (MalformedURLException e)
        {
            e.printStackTrace();
        }
    }

    private class timerThread extends Thread
    {
        private final int timerInterval         = 1000;
        private boolean   urlTimeOutPostSuccess = true;

        public void run()
        {
            while (timerRunning)
            {
                // Log.i(LOG_TAG, "Timer");

                if (urlLoadTimer.get() != 0)
                {
                    if (urlLoadTimer.decrementAndGet() == 0)
                    {
                        urlTimeOutPostSuccess = spiderHandler.post(urlLoadTimeOut);
                    }
                }
                else if (!urlTimeOutPostSuccess)
                {
                    Log.i(LOG_TAG, "try again urlTimeOutPost");
                    urlTimeOutPostSuccess = spiderHandler.post(urlLoadTimeOut);
                }

                if (!urlLoadPostSuccess.get())
                {
                    Log.i(LOG_TAG, "try again urlLoadpost");
                    urlLoadPostSuccess.set(spiderHandler.post(loadNextUrlAfterScan));
                }

                try
                {
                    sleep(timerInterval);
                }
                catch (InterruptedException e)
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    private void spiderLoadUrl(String url)
    {
        spider.loadUrl(url);
        urlLoadTimer.set(URL_TIME_OUT);
    }

    @JavascriptInterface
    public void recvImgUrl(String imgUrl)
    {
        // Log.i(LOG_TAG, "picSrc:"+picSrc);

        if (imgUrl.startsWith("http://") || imgUrl.startsWith("https://"))
        {
            int urlNumAfterAdd = jniAddUrl(imgUrl, imgUrl.hashCode(), URL_TYPE_IMG);

            if (urlNumAfterAdd != 0)
            {
                imgUrlCnt = urlNumAfterAdd;
            }
        }

    }

    @JavascriptInterface
    public void recvPageUrl(String pageUrl)
    {
        // Log.i(LOG_TAG, "url:"+url);

        try
        {
            URL url = new URL(pageUrl);
            if ((pageUrl.startsWith("http://") || pageUrl.startsWith("https://")) && (url.getHost().equals(srcHost))
                    && (url.getRef() == null))
            {
                int urlNumAfterAdd = jniAddUrl(pageUrl, pageUrl.hashCode(), URL_TYPE_PAGE);
                if (urlNumAfterAdd != 0)
                {
                    pageUrlCnt = urlNumAfterAdd;
                }
            }
        }
        catch (MalformedURLException e)
        {
            e.printStackTrace();
        }
    }

    @JavascriptInterface
    public void onCurPageScaned()
    {
        // todo 查找URL列表中与当前URL相似度最高的URL

        curUrl = jniFindNextUrlToLoad(curUrl, URL_TYPE_PAGE);
        scanTime = System.currentTimeMillis() - scanTimer;
        if (!curUrl.isEmpty())
        {
            urlLoadPostSuccess.set(spiderHandler.post(loadNextUrlAfterScan));
        }
        else
        {
            Log.i(LOG_TAG, "page scan complete");
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);

        Log.i(LOG_TAG, (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) ? "Landscape" : "Portrait");
    }
}
