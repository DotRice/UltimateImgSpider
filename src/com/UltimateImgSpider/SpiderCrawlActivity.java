package com.UltimateImgSpider;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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
    private String                srcUrl;
    private String                LOG_TAG     = "SpiderCrawl";

    /*
     * 遍历一个网站所有页面，并且下载所有图片。
     * 
     * 深度优先搜索
     * 
     * 网页遍历算法：扫描当前网页上每一个本站URL，查找所有不在网页列表中的URL。
     * 将第1个符合此条件的URL作为下次要扫描的页面，其他符合此条件的URL存入网页列表并标记为等待状态，
     * 如无符合此条件的页面，将网页列表中第一个等待状态的URL作为下次要扫描的页面。 如果列表中全部都为已下载页面，则遍历结束。
     * 如果遍历没有结束，把当前URL存入网页列表并标记为已下载状态，进入下次要扫描的页面。
     * 
     * 资源下载算法：扫描当前网页上的所有图片，查找源URL不在图片列表中的图片。
     * 下载并将源URL存入列表，以下载序号作为文件名，如果此图片存在alt则将alt加下载序号作为文件名。
     */

    private final int URL_TIME_OUT=10000;
    
    private final int             URL_PENDING = 1;
    private final int             URL_SCANED  = 2;
    private ArrayList<SpiderNode> pageUrlList;
    private ArrayList<String>     imgUrlList;
    private long                  DownLoadIndex;
    private String                curUrl;
    private String                srcHost;
    private boolean curPageFinished;

    private long                  loadTimer;

    private WebView               spider;
    
    private Handler              mHandler              = new Handler();

    private boolean getSrcUrlFromBundle()
    {
        Intent intent = this.getIntent();
        Bundle bundle = intent.getExtras();

        srcUrl = bundle.getString(SelSrcActivity.SOURCE_URL_BUNDLE_KEY); // 获取Bundle里面的字符串
        Toast.makeText(this, getString(R.string.srcUrl) + ":" + srcUrl, Toast.LENGTH_SHORT).show();

        return srcUrl != null;
    }

    private void spiderInit()
    {
        pageUrlList = new ArrayList<SpiderNode>();
        imgUrlList = new ArrayList<String>();
        DownLoadIndex = 0;

        spider = (WebView) findViewById(R.id.wvSpider);

        spider.setWebViewClient(new WebViewClient()
        {
            public boolean shouldOverrideUrlLoading(WebView view, String url)
            {
                return false;
            }

            public void onPageFinished(WebView view, String url)
            {
                Log.i(LOG_TAG, "onPageFinished " + url + " loadTime:" + (System.currentTimeMillis() - loadTimer));
                // spider.getSettings().setLoadsImagesAutomatically(true);

                if((!curPageFinished)&&(curUrl!=null))
                {
                    if(curUrl.equals(url))
                    {
                        curPageFinished=true;
                        curUrl = null;
                        view.loadUrl("javascript:" + "var i;" + "var img=document.getElementsByTagName(\"img\");"
                                + "for(i=0; i<img.length; i++)" + "{SpiderCrawl.recvImgUrl(img[i].src)}"
                                + "var a=document.getElementsByTagName(\"a\");" + "for(i=0; i<a.length; i++)"
                                + "{SpiderCrawl.recvPageUrl(a[i].href)}" + "SpiderCrawl.onCurPageScaned();");
                    }
                }
            }

            public void onPageStarted(WebView view, String url, Bitmap favicon)
            {
                Log.i(LOG_TAG, "onPageStarted " + url);
                loadTimer = System.currentTimeMillis();
                // spider.getSettings().setLoadsImagesAutomatically(false);
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

        // 使能javascript
        setting.setJavaScriptEnabled(true);
        setting.setJavaScriptCanOpenWindowsAutomatically(false);

        spider.addJavascriptInterface(this, "SpiderCrawl");

        try
        {
            srcHost = new URL(srcUrl).getHost();
            spiderLoadUrl(srcUrl);
            pageUrlList.add(new SpiderNode(srcUrl, URL_SCANED));
            curPageFinished=false;
            curUrl=srcUrl;
        }
        catch (MalformedURLException e)
        {
            e.printStackTrace();
        }
    }

    private void spiderLoadUrl(String url)
    {
        spider.loadUrl(url);
        mHandler.postDelayed(new Runnable()
        {
            
            @Override
            public void run()
            {
                spider.stopLoading();
            }
        }, URL_TIME_OUT);
    }
    
    @JavascriptInterface
    public void recvImgUrl(String img)
    {
        // Log.i(LOG_TAG, "picSrc:"+picSrc);
    }

    @JavascriptInterface
    public void recvPageUrl(String urlStr)
    {
        // Log.i(LOG_TAG, "url:"+url);

        int i;

        int listSize = pageUrlList.size();
        for (i = 0; i < listSize; i++)
        {
            if (pageUrlList.get(i).url.equals(urlStr))
            {
                break;
            }
        }

        if (i == listSize)
        {
            try
            {
                URL url=new URL(urlStr);
                if ((urlStr.startsWith("http://") || urlStr.startsWith("https://")) && (url.getHost().equals(srcHost))
                        &&(url.getRef()==null))
                {
                    int status;

                    if (curUrl == null)
                    {
                        status = URL_SCANED;
                        curUrl = urlStr;
                    }
                    else
                    {
                        status = URL_PENDING;
                    }
                    pageUrlList.add(new SpiderNode(urlStr, status));
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
        if (curUrl == null)
        {
            int i;
            int listSize = pageUrlList.size();
            SpiderNode node=null;
            for (i = 0; i < listSize; i++)
            {
                node=pageUrlList.get(i);
                if (node.status == URL_PENDING)
                {
                    break;
                }
            }

            if (i < listSize)
            {
                curUrl = node.url;
                node.status=URL_SCANED;
            }
            else
            {
                Log.i(LOG_TAG, "site scan complete");
                return;
            }
        }
        
        mHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                spiderLoadUrl(curUrl);
            }
        });
        curPageFinished=false;
        
        Log.i(LOG_TAG, "list Size:"+pageUrlList.size());
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

        Log.i(LOG_TAG, (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) ? "Landscape" : "Portrait");
    }
}
