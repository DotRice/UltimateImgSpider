package com.UltimateImgSpider;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
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
    int status;
    String url;
    
    public SpiderNode(String srcUrl, int stat)
    {
        url=srcUrl;
        status=stat;
    }
}

public class SpiderCrawlActivity extends Activity
{
    private String srcUrl;
    private String LOG_TAG = "SpiderCrawl";
    
    /*
     * ����һ����վ����ҳ�棬������������ͼƬ��
     * 
     * �����������
     * 
     * ��ҳ�����㷨��ɨ�赱ǰ��ҳ��ÿһ����վURL���������в�����ҳ�б��е�URL��
     * ����1�����ϴ�������URL��Ϊ�´�Ҫɨ���ҳ�棬�������ϴ�������URL������ҳ�б����Ϊ�ȴ�״̬��
     * ���޷��ϴ�������ҳ�棬����ҳ�б��е�һ���ȴ�״̬��URL��Ϊ�´�Ҫɨ���ҳ�档
     * ����б���ȫ����Ϊ������ҳ�棬�����������
     * �������û�н������ѵ�ǰURL������ҳ�б����Ϊ������״̬�������´�Ҫɨ���ҳ�档
     * 
     * ��Դ�����㷨��ɨ�赱ǰ��ҳ�ϵ�����ͼƬ������ԴURL����ͼƬ�б��е�ͼƬ��
     * ���ز���ԴURL�����б������������Ϊ�ļ����������ͼƬ����alt��alt�����������Ϊ�ļ�����
    */
    
    private final int URL_PENDING=1;
    private final int URL_DOWNLOADED=2;
    private ArrayList<SpiderNode> pageUrlList;
    private ArrayList<String> imgUrlList;
    private long DownLoadIndex;
    
    private long loadTimer;
    
    private WebView spider;
    
    private boolean getSrcUrlFromBundle()
    {
        Intent intent = this.getIntent(); // ��ȡ���е�intent����
        Bundle bundle = intent.getExtras(); // ��ȡintent�����bundle����
        
        srcUrl= bundle.getString(SelSrcActivity.SOURCE_URL_BUNDLE_KEY); // ��ȡBundle������ַ���
        Toast.makeText(this, getString(R.string.srcUrl) + ":" + srcUrl, Toast.LENGTH_SHORT).show();
        
        return srcUrl!=null;
    }
    
    private void spiderInit()
    {
        pageUrlList=new ArrayList<SpiderNode>();
        imgUrlList=new ArrayList<String>();
        DownLoadIndex=0;
        
        spider=(WebView) findViewById(R.id.wvSpider);
        
        spider.setWebViewClient(new WebViewClient()
        {
            public boolean shouldOverrideUrlLoading(WebView view, String url)
            {
                return false;
            }

            public void onPageFinished(WebView view, String url)
            {
                Log.i(LOG_TAG, "onPageFinished " + url+" loadTime:"+(System.currentTimeMillis()-loadTimer));
                //spider.getSettings().setLoadsImagesAutomatically(true);
                
                view.loadUrl("javascript:"
                        + "var i;"
                        + "var img=document.getElementsByTagName(\"img\");"
                        + "for(i=0; i<img.length; i++)"
                        + "{SpiderCrawl.recvImgUrl(img[i].src)}"
                        + "var a=document.getElementsByTagName(\"a\");"
                        + "for(i=0; i<a.length; i++)"
                        + "{SpiderCrawl.recvPageUrl(a[i].href)}");
            }

            public void onPageStarted(WebView view, String url, Bitmap favicon)
            {
                Log.i(LOG_TAG, "onPageStarted " + url);
                loadTimer=System.currentTimeMillis();
                //spider.getSettings().setLoadsImagesAutomatically(false);
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
        
        spider.requestFocus();

        spider.loadUrl(srcUrl);
    }
    
    
    @JavascriptInterface
    public void recvImgUrl(String img)
    {
        //Log.i(LOG_TAG, "picSrc:"+picSrc);
    }
    
    @JavascriptInterface
    public void recvPageUrl(String url)
    {
        //Log.i(LOG_TAG, "url:"+url);
        boolean urlInList=false;
        
        int i;
        
        int listSize=pageUrlList.size();
        for(i=0; i<listSize; i++)
        {
            if(pageUrlList.get(i).url.equals(url))
            {
                urlInList=true;
                break;
            }
        }
        
        if(!urlInList)
        {
            pageUrlList.add(new SpiderNode(url, URL_PENDING));
        }
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
