package com.UltimateImgSpider;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

import android.app.Activity;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.v13.app.FragmentPagerAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.text.Editable;
import android.text.Selection;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.webkit.WebView.HitTestResult;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SelSrcActivity extends Activity
{
    private final String         LOG_TAG               = "SelSrcActivity";

    private WebView              curWebView;
    private String originalURLrec="";
    private ViewPager   webViewPager;
    private ArrayList<WebView> webViewList;
    private final int WEBPAGE_BUFLEN=3;

    private ProgressBar          pbWebView;

    private RelativeLayout       layoutWvMask;
    private LinearLayout         browserMenu;
    private final static int     MENU_ANI_TIME         = 250;
    private AlphaAnimation       wvMaskAlphaAni=new AlphaAnimation(0, 1);

    private Button               btnSelSearchEngine;
    private View.OnClickListener oclSelSearchEngine;
    private EditText             etURL;
    private RelativeLayout       URLbar;

    private Button               btnURLcmd;

    private final int            URL_CANCEL            = 0;
    private final int            URL_REFRESH           = 1;
    private final int            URL_ENTER             = 2;
    private final int            URL_SEARCH            = 3;
    private final int            URLCMD_ICON[]         = { R.drawable.cancel, R.drawable.refresh, R.drawable.enter,
            R.drawable.search                         };
    private int                  URLcmd                = URL_CANCEL;

    private View.OnClickListener oclBrowserBtn;

    private final int            PROGRESS_MAX          = 100;

    private String               curWebPageTitle;

    private Handler              mHandler              = new Handler();

    final static String          SOURCE_URL_BUNDLE_KEY = "SourceUrl";

    private enum DLG
    {
        SPIDER_GO_CONFIRM, SEL_SEARCH_ENGINE
    };

    @Override
    protected Dialog onCreateDialog(int dlgId)
    {
        DLG dlg = DLG.values()[dlgId];
        switch (dlg)
        {
            case SPIDER_GO_CONFIRM:
            {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.spiderGoConfirm)
                        .setMultiChoiceItems(R.array.noLongerConfirm, new boolean[] { false },
                                new DialogInterface.OnMultiChoiceClickListener()
                                {
                                    public void onClick(DialogInterface dialog, int whichButton, boolean isChecked)
                                    {
                                        ParaConfig.setSpiderGoConfirm(SelSrcActivity.this, isChecked);
                                    }
                                }).setPositiveButton(R.string.OK, new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int whichButton)
                            {
                                /* User clicked Yes so do some stuff */
                                spiderGo();
                            }
                        }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int whichButton)
                            {

                                /* User clicked No so do some stuff */
                            }
                        }).create();
            }

            case SEL_SEARCH_ENGINE:
            {
                return new AlertDialog.Builder(this).setTitle("选择搜索引擎")
                        .setItems(ParaConfig.SEARCH_ENGINE_NAME, new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int whichButton)
                            {
                                Log.i(LOG_TAG, "whichButton:" + whichButton);
                                ParaConfig.setSearchEngine(SelSrcActivity.this, whichButton);
                                setCurSearchEngineIcon();
                            }
                        }).create();
            }
        }
        return null;
    }
    

    private void webViewPagerLoadURL(String URL)
    {
        WebView view=new WebView(this);
        webViewInit(view);
        webViewList.add(view);
        webViewPager.addView(view);
        webViewPager.getAdapter().notifyDataSetChanged();
        Log.i(LOG_TAG, "webViewList.size "+ webViewList.size());
        webViewPager.setCurrentItem(webViewList.size()-1);
        view.loadUrl(URL);
        curWebView=view;
        if(webViewList.size()>WEBPAGE_BUFLEN)
        {
            Log.i(LOG_TAG, "removeViewAt(0)");
            webViewPager.removeViewAt(0);
        }
    }
    
    private boolean webViewPagerGoBack()
    {
        return false;
    }
    
    private boolean webViewPagerGoForward()
    {
        return false;
    }
    
    private void webViewPagerGoHome()
    {
        curWebView.loadUrl(ParaConfig.getHomeURL(SelSrcActivity.this));
    }
    
    private void webViewPagerClearCache()
    {
        
    }
    
    
    private void webViewPagerInit()
    {
        pbWebView = (ProgressBar) findViewById(R.id.progressBarWebView);
        pbWebView.setMax(PROGRESS_MAX);

        webViewList=new ArrayList<WebView>();
        
        WebView view=new WebView(this);
        webViewInit(view);
        webViewList.add(view);
        
        
        webViewPager=(ViewPager)findViewById(R.id.webViewPager);
        webViewPager.setAdapter(new PagerAdapter()
        {
            
            @Override
            public boolean isViewFromObject(View arg0, Object arg1)
            {
                return arg0==arg1;
            }
            
            @Override
            public int getCount()
            {
                return webViewList.size();
            }
            

            @Override
            public void destroyItem(View container, int position, Object object) {
                ((ViewPager)container).removeView(webViewList.get(position));
                webViewList.remove(position);
                Log.i(LOG_TAG, "destroyItem "+position);
            }
 
            @Override
            public Object instantiateItem(View container, int position) {
                //((ViewPager)container).addView(webViewList.get(position));
                return webViewList.get(position);
            }
        });
        
        webViewPager.addView(view);
        
        webViewPager.setOnPageChangeListener(new OnPageChangeListener()
        {
            
            @Override
            public void onPageSelected(int pos)
            {
                Log.i(LOG_TAG, "Page "+pos+" Selected size:"+webViewList.size());
                curWebView=webViewList.get(pos);
            }
            
            @Override
            public void onPageScrolled(int arg0, float arg1, int arg2)
            {
                //Log.i(LOG_TAG, "Scroll "+arg0+" "+arg1+" "+arg2+" ");
            }
            
            @Override
            public void onPageScrollStateChanged(int arg0)
            {
                //Log.i(LOG_TAG, "ScrollStateChanged "+arg0);
            }
        });
        
        curWebView=webViewList.get(0);
        curWebView.requestFocus();
        curWebView.loadUrl(ParaConfig.getHomeURL(SelSrcActivity.this));
    }

    private int getUrlHttpCode(String tarUrl)
    {
        URL url;
        try
        {
            url = new URL(tarUrl);
            try
            {
                HttpURLConnection urlConn=(HttpURLConnection)url.openConnection();
                return urlConn.getResponseCode();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        catch (MalformedURLException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        return 0;
    }
    
    private void webViewInit(WebView view)
    {
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        
        view.setWebViewClient(new WebViewClient()
        {
            public boolean shouldOverrideUrlLoading(WebView view, String url)
            {
                Log.i(LOG_TAG, "UrlLoading " + url);
                
                String curOriginalURL=view.getOriginalUrl();
                Log.i(LOG_TAG, "OriginalUrl " + curOriginalURL);
                
                HitTestResult hit = view.getHitTestResult();
                Log.i(LOG_TAG, "HitTestResult " + hit.getType());
                
                
                /*
                if(originalURLrec.equals(curOriginalURL))
                {
                    webViewPagerLoadURL(url);
                    originalURLrec=curOriginalURL;
                }
                else
                    */
                {
                    view.loadUrl(url);
                }
                
                return true;
            }

            public void onPageFinished(WebView view, String url)
            {
                Log.i(LOG_TAG, "onPageFinished " + url);
                setURLcmd(URL_REFRESH);
            }

            public void onPageStarted(WebView view, String url, Bitmap favicon)
            {
                Log.i(LOG_TAG, "onPageStarted " + url);
                pbWebView.setVisibility(View.VISIBLE);
                etURL.setText(url);
                curWebPageTitle = "";
                setURLcmd(URL_CANCEL);
            }
        });

        view.setWebChromeClient(new WebChromeClient()
        {
            public void onProgressChanged(WebView view, int newProgress)
            {
                // Log.i(LOG_TAG, view.getUrl() + " Progress " + newProgress);

                if (newProgress == PROGRESS_MAX)
                {
                    if (pbWebView.getProgress() != 0)
                    {
                        pbWebView.setProgress(PROGRESS_MAX);
                        mHandler.postDelayed(new Runnable()
                        {
                            public void run()
                            {
                                pbWebView.setProgress(0);
                            }
                        }, 500);
                    }
                }
                else
                {
                    pbWebView.setProgress(newProgress);
                }

            }

            public void onReceivedTitle(WebView view, String title)
            {
                etURL.setText(title);
                curWebPageTitle = title;
            }
        });

        WebSettings setting = view.getSettings();
        setting.setUserAgentString(getString(R.string.webViewUserAgent));

        // 启用缩放
        setting.setSupportZoom(true);
        setting.setBuiltInZoomControls(true);
        setting.setUseWideViewPort(true);
        setting.setDisplayZoomControls(false);

        // 使能javascript
        setting.setJavaScriptEnabled(true);
        setting.setJavaScriptCanOpenWindowsAutomatically(false);

        // 自适应屏幕
        setting.setLoadWithOverviewMode(true);
    }

    private void clearURLbarFocus()
    {
        curWebView.requestFocus();
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(etURL.getWindowToken(),
                InputMethodManager.HIDE_NOT_ALWAYS);

        if (pbWebView.getProgress() == 0)
        {
            setURLcmd(URL_REFRESH);
        }
        else
        {
            setURLcmd(URL_CANCEL);
        }

        if (!curWebPageTitle.isEmpty())
        {
            etURL.setText(curWebPageTitle);
        }

        btnSelSearchEngine.setBackgroundResource(R.drawable.site);

    }

    private void focusOnWebView()
    {
        if (layoutWvMask.getVisibility() == View.VISIBLE)
        {
            clearURLbarFocus();
            showBrowserMenu(false);
        }
    }

    private void focusOnURL()
    {
        etURL.requestFocus();
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(etURL,
                InputMethodManager.SHOW_IMPLICIT);
    }
    
    private void setCurSearchEngineIcon()
    {
        btnSelSearchEngine.setBackgroundResource(ParaConfig.getSearchEngineIcon(SelSrcActivity.this));
    }

    private void setURLcmd(int cmd)
    {
        if (cmd < URLCMD_ICON.length)
        {
            URLcmd = cmd;
            btnURLcmd.setBackgroundResource(URLCMD_ICON[cmd]);
        }
    }

    private void executeURLcmd()
    {
        switch (URLcmd)
        {
            case URL_CANCEL:
                if (pbWebView.getProgress() != 0)
                {
                    curWebView.stopLoading();
                    pbWebView.setProgress(0);
                }
            break;

            case URL_REFRESH:
                curWebView.reload();
            break;

            case URL_ENTER:
                webViewPagerLoadURL(etURL.getText().toString());
            break;

            case URL_SEARCH:
                webViewPagerLoadURL(ParaConfig.getSearchEngineURL(SelSrcActivity.this) + etURL.getText().toString());
            break;

            default:
            break;
        }

        focusOnWebView();
    }

    private void oclBrowserBtnInit()
    {
        oclBrowserBtn = new View.OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                int viewId = v.getId();

                if ((viewId == R.id.buttonURLcmd) || (viewId == R.id.FrameLayoutURLcmd))
                {
                    executeURLcmd();
                }
                else
                {
                    switch (viewId)
                    {
                        case R.id.buttonBack:
                            webViewPagerGoBack();
                        break;

                        case R.id.buttonForward:
                            webViewPagerGoForward();
                        break;

                        case R.id.buttonSpiderGo:
                            if (ParaConfig.isSpiderGoNeedConfirm(SelSrcActivity.this))
                            {
                                spiderGo();
                            }
                            else
                            {
                                showDialog(DLG.SPIDER_GO_CONFIRM.ordinal());
                            }
                        break;

                        case R.id.buttonHome:
                            webViewPagerGoHome();
                        break;

                        case R.id.buttonMenu:
                            clearURLbarFocus();
                            if(wvMaskAlphaAni.hasEnded()||(!wvMaskAlphaAni.hasStarted()))
                            {
                                showBrowserMenu(layoutWvMask.getVisibility() != View.VISIBLE);
                            }
                            return;

                        case R.id.buttonSetting:
                            openSettingPage();
                        break;

                        case R.id.buttonExit:
                            finish();
                            return;

                        default:
                            Log.i(LOG_TAG, "oclBrowserBtn Unknown Button");
                        break;
                    }

                    focusOnWebView();
                }

            }
        };
    }

    private void showWebviewMask(final boolean isShow)
    {
        wvMaskAlphaAni = isShow ? (new AlphaAnimation(0, 1)) : (new AlphaAnimation(1, 0));

        wvMaskAlphaAni.setDuration(MENU_ANI_TIME);
        wvMaskAlphaAni.setAnimationListener(new AnimationListener()
        {

            @Override
            public void onAnimationStart(Animation animation)
            {
                if (isShow)
                    layoutWvMask.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation)
            {

            }

            @Override
            public void onAnimationEnd(Animation animation)
            {
                if (!isShow)
                    layoutWvMask.setVisibility(View.INVISIBLE);
            }
        });

        layoutWvMask.startAnimation(wvMaskAlphaAni);

    }

    private void showBrowserMenu(final boolean isShow)
    {
        showWebviewMask(isShow);

        if ((browserMenu.getVisibility() == View.VISIBLE) != isShow)
        {
            float fromY = isShow ? 1 : 0;
            TranslateAnimation translateAnimation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, fromY, Animation.RELATIVE_TO_SELF,
                    1 - fromY);
            translateAnimation.setDuration(MENU_ANI_TIME);
            translateAnimation.setAnimationListener(new AnimationListener()
            {

                @Override
                public void onAnimationStart(Animation animation)
                {
                    if (isShow)
                        browserMenu.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationRepeat(Animation animation)
                {

                }

                @Override
                public void onAnimationEnd(Animation animation)
                {
                    if (!isShow)
                        browserMenu.setVisibility(View.INVISIBLE);
                }
            });
            browserMenu.startAnimation(translateAnimation);
        }
    }

    private void browserMenuInit()
    {
        layoutWvMask = (RelativeLayout) findViewById(R.id.RelativeLayoutWvMask);
        layoutWvMask.setOnClickListener(new View.OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                Log.i(LOG_TAG, "mask Clicked");
                focusOnWebView();
            }
        });

        browserMenu = (LinearLayout) findViewById(R.id.browserMenu);

        findViewById(R.id.buttonExit).setOnClickListener(oclBrowserBtn);
        findViewById(R.id.buttonSetting).setOnClickListener(oclBrowserBtn);
    }

    private void URLbarInit()
    {
        URLbar = (RelativeLayout) findViewById(R.id.urlBar);
        URLbar.setOnClickListener(new View.OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                focusOnURL();
            }
        });

        oclSelSearchEngine = new View.OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                if (etURL.isFocused())
                {
                    if ((!URLUtil.isNetworkUrl(etURL.getText().toString())) && (etURL.getText().length() != 0))
                    {
                        showDialog(DLG.SEL_SEARCH_ENGINE.ordinal());
                    }
                }
                else
                {
                    focusOnURL();
                }
            }
        };
        btnSelSearchEngine = (Button) findViewById(R.id.buttonSelSearchEngine);
        btnSelSearchEngine.setOnClickListener(oclSelSearchEngine);
        findViewById(R.id.FrameLayoutSSEngineBackground).setOnClickListener(oclSelSearchEngine);
        findViewById(R.id.FrameLayoutSelSearchEngine).setOnClickListener(oclSelSearchEngine);

        btnURLcmd = (Button) findViewById(R.id.buttonURLcmd);
        btnURLcmd.setOnClickListener(oclBrowserBtn);

        findViewById(R.id.FrameLayoutURLcmd).setOnClickListener(oclBrowserBtn);

        etURL = (EditText) findViewById(R.id.editTextUrl);
        etURL.setOnFocusChangeListener(new View.OnFocusChangeListener()
        {

            @Override
            public void onFocusChange(View v, boolean hasFocus)
            {
                if (hasFocus)
                {
                    showWebviewMask(true);

                    setURLcmd(URL_ENTER);

                    String url=curWebView.getUrl();
                    etURL.setText(url);
                    etURL.selectAll();
                    etURL.setEnabled(false);
                    mHandler.postDelayed(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            etURL.setEnabled(true);
                            focusOnURL();
                        }
                    }, 50);
                }
            }
        });
        
        etURL.addTextChangedListener(new TextWatcher()
        {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {

            }

            @Override
            public void afterTextChanged(Editable s)
            {
                if (etURL.hasFocus())
                {
                    String URL = s.toString();
                    if (URLUtil.isNetworkUrl(URL))
                    {
                        setURLcmd(URL_ENTER);
                        btnSelSearchEngine.setBackgroundResource(R.drawable.site);
                        etURL.setImeOptions(EditorInfo.IME_ACTION_GO);
                    }
                    else if (URL.isEmpty())
                    {
                        setURLcmd(URL_CANCEL);
                        btnSelSearchEngine.setBackgroundResource(R.drawable.site);
                        etURL.setImeOptions(EditorInfo.IME_ACTION_NONE);
                    }
                    else
                    {
                        setURLcmd(URL_SEARCH);
                        setCurSearchEngineIcon();
                        etURL.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
                    }
                }
            }

        });

        etURL.setOnEditorActionListener(new TextView.OnEditorActionListener()
        {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
            {
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId == EditorInfo.IME_ACTION_NONE
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER))
                {
                    executeURLcmd();
                    return true;
                }
                return false;
            }
        });
    }

    private void naviBarInit()
    {
        findViewById(R.id.buttonBack).setOnClickListener(oclBrowserBtn);
        findViewById(R.id.buttonForward).setOnClickListener(oclBrowserBtn);
        findViewById(R.id.buttonSpiderGo).setOnClickListener(oclBrowserBtn);
        findViewById(R.id.buttonHome).setOnClickListener(oclBrowserBtn);
        findViewById(R.id.buttonMenu).setOnClickListener(oclBrowserBtn);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sel_src);

        oclBrowserBtnInit();
        browserMenuInit();
        URLbarInit();
        naviBarInit();
        webViewPagerInit();

        Log.i(LOG_TAG, "onCreate");
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

        curWebView.stopLoading();
        webViewPagerClearCache();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {

        // Inflate the menu; this adds items to the action bar if it is present.

        return true;
    }

    private void openSettingPage()
    {
        Log.i(LOG_TAG, "openSettingPage");

        Intent intent = new Intent(this, ParaConfigActivity.class);

        String srcUrl = curWebView.getUrl();

        Bundle bundle = new Bundle();
        bundle.putString(SOURCE_URL_BUNDLE_KEY, srcUrl);
        intent.putExtras(bundle);

        startActivity(intent);
    }

    public void spiderGo()
    {
        Log.i(LOG_TAG, "spiderGo");

        Intent intent = new Intent(this, SpiderCrawlActivity.class);

        String srcUrl = curWebView.getUrl();

        Bundle bundle = new Bundle();
        bundle.putString(SOURCE_URL_BUNDLE_KEY, srcUrl);
        intent.putExtras(bundle);

        Toast.makeText(this, getString(R.string.srcUrl) + ":" + srcUrl, Toast.LENGTH_SHORT).show();
        ;

        startActivity(intent);// 直接切换Activity不接收返回结果
    }

    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        Log.i(LOG_TAG, "onKeyDown " + keyCode);

        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if((layoutWvMask.getVisibility()==View.VISIBLE))
            {
                focusOnWebView();
                return true;
            }
            else if (webViewPagerGoBack())
            {
                Log.i(LOG_TAG, "goBack ");
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
        {
            Log.i(LOG_TAG, "Landscape");
        }
        else
        {
            Log.i(LOG_TAG, "Portrait");
        }
    }
}
