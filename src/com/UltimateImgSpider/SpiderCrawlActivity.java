package com.UltimateImgSpider;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.utils.utils;

import android.app.Activity;
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

class SpiderNode
{
    int    status;
    String url;

    public SpiderNode(String srcUrl, int stat)
    {
        url = srcUrl;
        status = stat;
    }
}

public class SpiderCrawlActivity extends Activity
{
    private String                LOG_TAG            = "SpiderCrawl";

    /*
     * ����һ����վ����ҳ�棬������������ͼƬ��
     * 
     * �����������
     * 
     * ��ҳ�����㷨�� ɨ�赱ǰ��ҳ��ÿһ����վURL���������в�����ҳ�б��е�URL�������б�����Ϊ�ȴ�״̬��
     * ɨ����ҳ�б������еȴ�״̬��URL�����뵱ǰҳ��URL�����Ƶ���Ϊ�´�Ҫɨ���ҳ�档����б���ȫ����Ϊ������ҳ�棬�����������
     * 
     * ��Դ�����㷨�� ɨ�赱ǰ��ҳ�ϵ�����ͼƬ������ԴURL����ͼƬ�б��е�ͼƬ��
     * ���ز���ԴURL�����б������������Ϊ�ļ����������ͼƬ����alt��alt�����������Ϊ�ļ�����
     */

    private String                srcUrl;

    private final int             URL_PENDING        = 1;
    private final int             URL_SCANED         = 2;
    private ArrayList<SpiderNode> pageUrlList;
    private ArrayList<String>     imgUrlList;
    private long                  DownLoadIndex;
    private String                curUrl;
    private int                   pageIndex          = 0;
    private String                srcHost;

    private TextView              spiderLog;

    private long                  loadTimer;
    private long                  loadTime;
    private long                  scanTimer;
    private long                  scanTime;

    private WebView               spider;

    private Runnable              urlLoadAfterScan;
    private Runnable              urlLoadTimeOut;
    private Handler               spiderHandler      = new Handler();
    private boolean               timerRunning       = true;
    private final int             URL_TIME_OUT       = 10;
    private AtomicInteger         urlLoadTimer       = new AtomicInteger(URL_TIME_OUT);
    private AtomicBoolean         urlLoadpostSuccess = new AtomicBoolean(true);

    public native String stringFromJNI(String srcStr);

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

        Log.i(LOG_TAG, stringFromJNI("java source"));
        
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
    }

    
    private boolean getSrcUrlFromBundle()
    {
        Intent intent = this.getIntent();
        Bundle bundle = intent.getExtras();

        srcUrl = bundle.getString(SelSrcActivity.SOURCE_URL_BUNDLE_KEY); // ��ȡBundle������ַ���
        Toast.makeText(this, getString(R.string.srcUrl) + ":" + srcUrl, Toast.LENGTH_SHORT).show();

        return srcUrl != null;
    }

    private void dispSpiderLog()
    {
        pageIndex++;
        String log = imgUrlList.size() + " " + pageIndex + "/" + pageUrlList.size() + " " + loadTime + " " + scanTime
                + " " + curUrl;
        spiderLog.setText(log);
        Log.i(LOG_TAG, log);
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
        pageUrlList = new ArrayList<SpiderNode>();
        imgUrlList = new ArrayList<String>();
        DownLoadIndex = 0;

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

                if (curUrl.equals(url))
                {
                    if (urlLoadTimer.get() != 0)
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
                // spider.getSettings().setLoadsImagesAutomatically(false);
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

        });

        WebSettings setting = spider.getSettings();
        setting.setUserAgentString(ParaConfig.getUserAgent(this));

        // ��ֹͼƬ
        setting.setLoadsImagesAutomatically(false);

        //setting.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // ʹ��javascript
        setting.setJavaScriptEnabled(true);
        setting.setJavaScriptCanOpenWindowsAutomatically(false);

        // ��������
        setting.setSupportZoom(true);
        setting.setBuiltInZoomControls(true);
        setting.setUseWideViewPort(true);
        setting.setDisplayZoomControls(false);

        // ����Ӧ��Ļ
        setting.setLoadWithOverviewMode(true);

        spider.addJavascriptInterface(this, "SpiderCrawl");

        urlLoadAfterScan = new Runnable()
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
                spider.stopLoading();
                scanPageWithJS();
            }
        };

        new timerThread().start();

        try
        {
            srcHost = new URL(srcUrl).getHost();
            spiderLoadUrl(srcUrl);
            pageUrlList.add(new SpiderNode(srcUrl, URL_SCANED));
            curUrl = srcUrl;
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

                if (!urlLoadpostSuccess.get())
                {
                    Log.i(LOG_TAG, "try again urlLoadpost");
                    urlLoadpostSuccess.set(spiderHandler.post(urlLoadAfterScan));
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
        if (!imgUrlList.contains(imgUrl))
        {
            imgUrlList.add(imgUrl);
        }
    }

    @JavascriptInterface
    public void recvPageUrl(String pageUrl)
    {
        // Log.i(LOG_TAG, "url:"+url);

        int i;

        int listSize = pageUrlList.size();
        for (i = 0; i < listSize; i++)
        {
            if (pageUrlList.get(i).url.equals(pageUrl))
            {
                break;
            }
        }

        if (i == listSize)
        {
            try
            {
                URL url = new URL(pageUrl);
                if ((pageUrl.startsWith("http://") || pageUrl.startsWith("https://"))
                        && (url.getHost().equals(srcHost)) && (url.getRef() == null))
                {
                    pageUrlList.add(new SpiderNode(pageUrl, URL_PENDING));
                }
            }
            catch (MalformedURLException e)
            {
                e.printStackTrace();
            }
        }
    }

    @JavascriptInterface
    public void onCurPageScaned()
    {
        // todo ����URL�б����뵱ǰURL���ƶ���ߵ�URL
        boolean scanComplete = true;
        int i;
        int listSize = pageUrlList.size();
        int urlSim = 0;
        SpiderNode nextNode = null;
        for (i = 0; i < listSize; i++)
        {
            SpiderNode node = pageUrlList.get(i);
            if (node.status == URL_PENDING)
            {
                if (scanComplete)
                {
                    scanComplete = false;
                    urlSim = utils.strSimilarity(node.url, curUrl);
                    nextNode = node;
                }
                else
                {
                    int curSim = utils.strSimilarity(node.url, curUrl);
                    if (curSim > urlSim)
                    {
                        urlSim = curSim;
                        nextNode = node;
                    }
                }
            }
        }

        scanTime = System.currentTimeMillis() - scanTimer;
        if (scanComplete)
        {
            Log.i(LOG_TAG, "site scan complete");
        }
        else
        {
            nextNode.status = URL_SCANED;
            curUrl = nextNode.url;
            urlLoadpostSuccess.set(spiderHandler.post(urlLoadAfterScan));
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);

        Log.i(LOG_TAG, (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) ? "Landscape" : "Portrait");
    }
}
