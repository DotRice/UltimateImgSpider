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
     * ����һ����վ����ҳ�棬������������ͼƬ��
     * 
     * �����������
     * 
     * ��ҳ�����㷨��
     * ɨ�赱ǰ��ҳ��ÿһ����վURL���������в�����ҳ�б��е�URL�������б�����Ϊ�ȴ�״̬��
     * ɨ����ҳ�б������еȴ�״̬��URL�����뵱ǰҳ��URL�����Ƶ���Ϊ�´�Ҫɨ���ҳ�档����б���ȫ����Ϊ������ҳ�棬�����������
     * 
     * ��Դ�����㷨��
     * ɨ�赱ǰ��ҳ�ϵ�����ͼƬ������ԴURL����ͼƬ�б��е�ͼƬ��
     * ���ز���ԴURL�����б������������Ϊ�ļ����������ͼƬ����alt��alt�����������Ϊ�ļ�����
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

        srcUrl = bundle.getString(SelSrcActivity.SOURCE_URL_BUNDLE_KEY); // ��ȡBundle������ַ���
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

                if(!curPageFinished)
                {
                    curPageFinished=true;
                    if(curUrl.equals(url))
                    {
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

        // ʹ��javascript
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
                    pageUrlList.add(new SpiderNode(urlStr, URL_PENDING));
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
        //todo ����URL�б����뵱ǰURL���ƶ���ߵ�URL
        boolean scanComplete=true;
        int i;
        int listSize = pageUrlList.size();
        int urlComp=0;
        for (i = 0; i < listSize; i++)
        {
            SpiderNode node=pageUrlList.get(i);
            if (node.status == URL_PENDING)
            {
                scanComplete=false;
                
                urlComp=node.url.compareTo(curUrl);
            }
        }

        if (scanComplete)
        {
            Log.i(LOG_TAG, "site scan complete");
            return;
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
