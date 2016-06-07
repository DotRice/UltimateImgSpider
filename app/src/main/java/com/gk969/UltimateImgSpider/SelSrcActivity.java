package com.gk969.UltimateImgSpider;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ActionBar.LayoutParams;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.URLUtil;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings.RenderPriority;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SelSrcActivity extends Activity
{
    private final String         TAG            = "SelSrcActivity";
    
    private WebView              browser;
    private ProgressBar          pbWebView;
    private Bitmap               browserIcon;
    
    private RelativeLayout       layoutWvMask;
    private LinearLayout         browserMenu;
    private final static int     MENU_ANI_TIME  = 250;
    private AlphaAnimation       wvMaskAlphaAni = new AlphaAnimation(0, 1);
    
    private ImageButton          btnSelSearchEngine;
    private View.OnClickListener oclSelSearchEngine;

    private String               recvFailUrl="";
    
    private EditText             etUrl;
    private String               urlToDisp;
    
    private RelativeLayout       urlBar;
    
    private Button               btnURLcmd;

    private Context appCtx;
    
    private final int            URL_CANCEL     = 0;
    private final int            URL_REFRESH    = 1;
    private final int            URL_ENTER      = 2;
    private final int            URL_SEARCH     = 3;
    private final int            URLCMD_ICON[]  = { R.drawable.cancel,
            R.drawable.refresh, R.drawable.enter, R.drawable.search };
    private int                  URLcmd         = URL_CANCEL;
    
    private View.OnClickListener oclBrowserBtn;
    
    private final int            PROGRESS_MAX   = 100;
    
    private Handler              mHandler       = new Handler();
    
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
                        .setMultiChoiceItems(
                                R.array.noLongerConfirm,
                                new boolean[] { false },
                                new DialogInterface.OnMultiChoiceClickListener()
                                {
                                    public void onClick(DialogInterface dialog,
                                            int whichButton, boolean isChecked)
                                    {
                                        ParaConfig.setSpiderGoConfirm(appCtx, isChecked);
                                    }
                                })
                        .setPositiveButton(R.string.OK,
                                new DialogInterface.OnClickListener()
                                {
                                    public void onClick(DialogInterface dialog,
                                            int whichButton)
                                    {
                                        /* User clicked Yes so do some stuff */
                                        spiderGo();
                                    }
                                })
                        .setNegativeButton(R.string.cancel,
                                new DialogInterface.OnClickListener()
                                {
                                    public void onClick(DialogInterface dialog,
                                            int whichButton)
                                    {
                                        
                                        /* User clicked No so do some stuff */
                                    }
                                }).create();
            }
            
            case SEL_SEARCH_ENGINE:
            {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.selSearchEngine)
                        .setItems(ParaConfig.SEARCH_ENGINE_NAME,
                                new DialogInterface.OnClickListener()
                                {
                                    public void onClick(DialogInterface dialog,
                                            int whichButton)
                                    {
                                        Log.i(TAG, "whichButton:" + whichButton);
                                        ParaConfig.setSearchEngine(appCtx, whichButton);
                                        btnSelSearchEngine.setImageResource(ParaConfig
                                                .getSearchEngineIcon(SelSrcActivity.this));
                                    }
                                }).create();
            }
        }
        return null;
    }
    
    private void browserLoadUrl(String URL)
    {
        String Protocol = URL.toLowerCase();
        if (Protocol.startsWith("http://") || Protocol.startsWith("https://"))
        {
            browser.stopLoading();
            browser.loadUrl(URL);
            setBrowserTitle(URL);
            btnSelSearchEngine.setImageResource(R.drawable.site);
            setUrlCmd(URL_CANCEL);
        }
    }
    
    void setBrowserTitle(String title)
    {
        if (!etUrl.hasFocus())
        {
            etUrl.setText(title);
        }
    }

    private boolean browserGoBack()
    {
        if(browser.canGoBack())
        {
            browser.goBack();

            Log.i(TAG, urlToDisp+" "+recvFailUrl+" "+browser.getUrl());
            if(urlToDisp.equals(recvFailUrl) && (!urlToDisp.equals(browser.getUrl())))
            {
                Log.i(TAG, "Redirection Rail");
                if(browser.canGoBack())
                {
                    browser.goBack();
                }
            }

            return true;
        }

        return false;
    }

    private void browserInit(String urlToOpen)
    {
        pbWebView = (ProgressBar) findViewById(R.id.progressBarWebView);
        pbWebView.setMax(PROGRESS_MAX);
        
        browser = (WebView) findViewById(R.id.webViewSelectSrcUrl);
        
        browser.setWebViewClient(new WebViewClient()
        {
            public boolean shouldOverrideUrlLoading(WebView view, String url)
            {
                Log.i(TAG, "shouldOverrideUrlLoading " + url);
                return false;
            }
            
            public void onPageFinished(WebView view, String url)
            {
                Log.i(TAG, "onPageFinished " + url);
                setUrlCmd(URL_REFRESH);
                
                String title = browser.getTitle();
                if (title != null)
                {
                    setBrowserTitle(title);
                }
                
            }
            
            public void onPageStarted(WebView view, String url, Bitmap favicon)
            {
                Log.i(TAG, "onPageStarted " + url);
                setBrowserTitle(url);
                setUrlCmd(URL_CANCEL);
                
                browserIcon = BitmapFactory.decodeResource(getResources(),
                        R.drawable.site);
                btnSelSearchEngine.setImageBitmap(browserIcon);
                
                urlToDisp = url;
            }
            
            public void onReceivedError(WebView view, int errorCode,
                    String description, String failingUrl)
            {
                Log.i(TAG, "ReceivedError " + failingUrl + " " + errorCode + "  " + description+" "+
                        view.getUrl()+" OriginalUrl "+view.getOriginalUrl());

                recvFailUrl=failingUrl;
            }
        });
        
        browser.setWebChromeClient(new WebChromeClient()
        {
            public void onReceivedIcon(WebView view, Bitmap icon)
            {
                btnSelSearchEngine.setImageBitmap(icon);
                browserIcon = icon;
            }

            public void onProgressChanged(WebView view, int newProgress)
            {
                if(newProgress == PROGRESS_MAX)
                {
                    if(pbWebView.getProgress() != 0)
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
                setBrowserTitle(title);
            }
        });
        
        WebSettings setting = browser.getSettings();
        setting.setUserAgentString(ParaConfig.getUserAgent(appCtx));
        
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
        
        browser.requestFocus();

        if(urlToOpen==null)
        {
            urlToOpen=ParaConfig.getHomeURL(appCtx);
        }

        browserLoadUrl(urlToOpen);
        
        browserIcon = BitmapFactory.decodeResource(getResources(),
                R.drawable.site);
    }
    
    private void clearURLbarFocus()
    {
        browser.requestFocus();
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(etUrl.getWindowToken(),
                        InputMethodManager.HIDE_NOT_ALWAYS);
        
        if (pbWebView.getProgress() == 0)
        {
            setUrlCmd(URL_REFRESH);
        }
        else
        {
            setUrlCmd(URL_CANCEL);
        }
        
        String title = browser.getTitle();
        etUrl.setText((title != null) ? title : browser.getUrl());
        
        btnSelSearchEngine.setImageBitmap(browserIcon);
        
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
        etUrl.requestFocus();
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(etUrl, InputMethodManager.SHOW_IMPLICIT);
    }
    
    private void setUrlCmd(int cmd)
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
                    browser.stopLoading();
                    pbWebView.setProgress(0);
                    setUrlCmd(URL_REFRESH);
                }
            break;
            
            case URL_REFRESH:
                browser.reload();
                setUrlCmd(URL_CANCEL);
            break;
            
            case URL_ENTER:
                if (!browser.getUrl().equals(urlToDisp))
                {
                    browserLoadUrl(urlToDisp);
                }
            break;
            
            case URL_SEARCH:
                String target;
                try
                {
                    target = URLEncoder.encode(urlToDisp, "UTF-8");
                }
                catch (UnsupportedEncodingException e)
                {
                    e.printStackTrace();
                    break;
                }
                browserLoadUrl(ParaConfig.getSearchEngineURL(appCtx) + target);
            break;
            
            default:
            break;
        }
        
        focusOnWebView();
    }
    
    void responseMenuKey()
    {
        clearURLbarFocus();
        if (wvMaskAlphaAni.hasEnded() || (!wvMaskAlphaAni.hasStarted()))
        {
            showBrowserMenu(layoutWvMask.getVisibility() != View.VISIBLE);
        }
    }
    
    private void oclBrowserBtnInit()
    {
        oclBrowserBtn = new View.OnClickListener()
        {
            
            @Override
            public void onClick(View v)
            {
                int viewId = v.getId();
                
                if ((viewId == R.id.buttonURLcmd)
                        || (viewId == R.id.FrameLayoutURLcmd))
                {
                    executeURLcmd();
                }
                else
                {
                    switch (viewId)
                    {
                        case R.id.buttonBack:
                            Log.i(TAG, "buttonBack");
                            browserGoBack();
                        break;
                        
                        case R.id.buttonForward:
                            if (browser.canGoForward())
                            {
                                browser.goForward();
                            }
                        break;
                        
                        case R.id.buttonSpiderGo:
                            if (ParaConfig.isSpiderGoNeedConfirm(appCtx))
                            {
                                spiderGo();
                            }
                            else
                            {
                                showDialog(DLG.SPIDER_GO_CONFIRM.ordinal());
                            }
                        break;
                        
                        case R.id.buttonHome:
                            browserLoadUrl(ParaConfig.getHomeURL(appCtx));
                        break;
                        
                        case R.id.buttonMenu:
                            responseMenuKey();
                            return;
                            
                        case R.id.buttonSetting:
                            openSettingPage();
                        break;
                        
                        case R.id.buttonExit:
                            finish();
                            return;
                        case R.id.buttonRefresh:
                            browser.reload();
                        break;
                        
                        default:
                            Log.i(TAG, "oclBrowserBtn Unknown Button");
                        break;
                    }
                    
                    focusOnWebView();
                }
                
            }
        };
    }
    
    private void showWebviewMask(final boolean isShow)
    {
        wvMaskAlphaAni = isShow ? (new AlphaAnimation(0, 1))
                : (new AlphaAnimation(1, 0));
        
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
            TranslateAnimation browserMenuAnim = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF,
                    0f, Animation.RELATIVE_TO_SELF, fromY,
                    Animation.RELATIVE_TO_SELF, 1 - fromY);
            browserMenuAnim.setDuration(MENU_ANI_TIME);
            browserMenuAnim.setAnimationListener(new AnimationListener()
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
            browserMenu.startAnimation(browserMenuAnim);
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
                // Log.i(TAG, "mask Clicked");
                focusOnWebView();
            }
        });
        
        browserMenu = (LinearLayout) findViewById(R.id.browserMenu);
        
        findViewById(R.id.buttonExit).setOnClickListener(oclBrowserBtn);
        findViewById(R.id.buttonSetting).setOnClickListener(oclBrowserBtn);
        findViewById(R.id.buttonRefresh).setOnClickListener(oclBrowserBtn);
    }
    
    private void URLbarInit()
    {
        urlBar = (RelativeLayout) findViewById(R.id.urlBar);
        urlBar.setOnClickListener(new View.OnClickListener()
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
                if (etUrl.isFocused())
                {
                    if ((!URLUtil.isNetworkUrl(etUrl.getText().toString()))
                            && (etUrl.getText().length() != 0))
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
        btnSelSearchEngine = (ImageButton) findViewById(R.id.buttonSelSearchEngine);
        btnSelSearchEngine.setOnClickListener(oclSelSearchEngine);
        findViewById(R.id.FrameLayoutSSEngineBackground).setOnClickListener(
                oclSelSearchEngine);
        findViewById(R.id.FrameLayoutSelSearchEngine).setOnClickListener(
                oclSelSearchEngine);
        
        btnURLcmd = (Button) findViewById(R.id.buttonURLcmd);
        btnURLcmd.setOnClickListener(oclBrowserBtn);
        
        findViewById(R.id.FrameLayoutURLcmd).setOnClickListener(oclBrowserBtn);
        
        etUrl = (EditText) findViewById(R.id.editTextUrl);
        etUrl.setOnFocusChangeListener(new View.OnFocusChangeListener()
        {
            
            @Override
            public void onFocusChange(View v, boolean hasFocus)
            {
                if (hasFocus)
                {
                    showWebviewMask(true);
                    
                    setUrlCmd(URL_ENTER);
                    
                    etUrl.setText(urlToDisp);
                    etUrl.selectAll();
                    etUrl.setEnabled(false);
                    mHandler.postDelayed(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            etUrl.setEnabled(true);
                            focusOnURL();
                        }
                    }, 50);
                }
            }
        });
        
        etUrl.addTextChangedListener(new TextWatcher()
        {
            
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after)
            {
                
            }
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                    int count)
            {
                
            }
            
            @Override
            public void afterTextChanged(Editable s)
            {
                if (etUrl.hasFocus())
                {
                    String url = s.toString();
                    
                    urlToDisp = url;
                    if (URLUtil.isNetworkUrl(url))
                    {
                        setUrlCmd(URL_ENTER);
                        btnSelSearchEngine.setImageResource(R.drawable.site);
                        etUrl.setImeOptions(EditorInfo.IME_ACTION_GO);
                    }
                    else if (url.isEmpty())
                    {
                        setUrlCmd(URL_CANCEL);
                        btnSelSearchEngine.setImageResource(ParaConfig.getSearchEngineIcon(appCtx));
                        etUrl.setImeOptions(EditorInfo.IME_ACTION_NONE);
                    }
                    else
                    {
                        setUrlCmd(URL_SEARCH);
                        btnSelSearchEngine.setImageResource(ParaConfig.getSearchEngineIcon(appCtx));
                        etUrl.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
                    }
                    
                }
            }
            
        });
        
        etUrl.setOnEditorActionListener(new TextView.OnEditorActionListener()
        {
            public boolean onEditorAction(TextView v, int actionId,
                    KeyEvent event)
            {
                if (actionId == EditorInfo.IME_ACTION_GO
                        || actionId == EditorInfo.IME_ACTION_SEARCH
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

        appCtx=getApplicationContext();
        
        oclBrowserBtnInit();
        browserMenuInit();
        URLbarInit();
        naviBarInit();
        browserInit(getIntent().getStringExtra(StaticValue.EXTRA_URL_TO_OPEN));
        
        /*
        Runnable tr=new Runnable()
        {
            @Override
            public void run()
            {
                Log.i(TAG, "spiderGo");
                spiderGo();
            }
        };
        
        mHandler.postDelayed(tr, 500);
        */
        Log.i(TAG, "onCreate");
    }
    
    protected void onStart()
    {
        super.onStart();
        Log.i(TAG, "onStart");
    }
    
    protected void onResume()
    {
        super.onResume();
        Log.i(TAG, "onResume");
        
        if (browser != null)
        {
            browser.getSettings().setUserAgentString(ParaConfig.getUserAgent(appCtx));
        }
    }
    
    protected void onPause()
    {
        super.onPause();
        Log.i(TAG, "onPause");
        
    }
    
    protected void onStop()
    {
        Log.i(TAG, "onStop");
        
        super.onStop();
    }
    
    protected void onDestroy()
    {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        
        Log.i(TAG, "clearCache");
        browser.stopLoading();
        browser.clearCache(true);
        browser.destroy();
        System.exit(0);
    }
    
    private void openSettingPage()
    {
        Log.i(TAG, "openSettingPage");
        
        Intent intent = new Intent(this, ParaConfigActivity.class);
        
        String srcUrl = browser.getUrl();
        
        Bundle bundle = new Bundle();
        bundle.putString(StaticValue.BUNDLE_KEY_SOURCE_URL, srcUrl);
        intent.putExtras(bundle);
        
        startActivity(intent);
    }
    
    public void spiderGo()
    {
        String srcUrl = browser.getUrl();
        
        Log.i(TAG, "spiderGo srcUrl:" + srcUrl);
        
        if (srcUrl != null)
        {
            setResult(RESULT_OK, (new Intent()).setAction(srcUrl));
        }
        finish();
    }
    
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        Log.i(TAG, "onKeyDown " + keyCode);
        
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if ((layoutWvMask.getVisibility() == View.VISIBLE))
            {
                focusOnWebView();
                return true;
            }
            else if (browserGoBack())
            {
                return true;
            }
        }
        else if (keyCode == KeyEvent.KEYCODE_MENU)
        {
            responseMenuKey();
        }
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
        {
            Log.i(TAG, "Landscape");
        }
        else
        {
            Log.i(TAG, "Portrait");
        }
    }
}
