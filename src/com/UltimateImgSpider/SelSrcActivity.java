package com.UltimateImgSpider;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SelSrcActivity extends Activity
{
    private final String                  LOG_TAG               = "SelSrcActivity";
    private long exitTim=0;
    
    
    private ArrayList<BrowserHistoryItem> browserHistory;
    private WebView                       wvSelSrc;
    private boolean                       lastPageFinished     = false;
    private boolean                       webAddrCanNotReach    = false;
    private long lastStartTime=0;

    private ProgressBar                   pbWebView;

    private RelativeLayout                layoutWvMask;
    private LinearLayout                  browserMenu;
    private final static int              MENU_ANI_TIME         = 250;
    private AlphaAnimation                wvMaskAlphaAni        = new AlphaAnimation(0, 1);

    private Button                        btnSelSearchEngine;
    private View.OnClickListener          oclSelSearchEngine;
    private EditText                      etURL;
    private RelativeLayout                URLbar;

    private Button                        btnURLcmd;

    private final int                     URL_CANCEL            = 0;
    private final int                     URL_REFRESH           = 1;
    private final int                     URL_ENTER             = 2;
    private final int                     URL_SEARCH            = 3;
    private final int                     URLCMD_ICON[]         = { R.drawable.cancel, R.drawable.refresh,
            R.drawable.enter, R.drawable.search                };
    private int                           URLcmd                = URL_CANCEL;

    private View.OnClickListener          oclBrowserBtn;

    private final int                     PROGRESS_MAX          = 100;

    private Handler                       mHandler              = new Handler();

    final static String                   SOURCE_URL_BUNDLE_KEY = "SourceUrl";

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

    class BrowserHistoryItem
    {
        public String  Url;
        public boolean isRedirecrt;

        public BrowserHistoryItem(String URL, boolean isRedrc)
        {
            Url = URL;
            isRedirecrt = isRedrc;
        }
    }

    private void browserLoadUrl(String URL, boolean isRedirect)
    {
        wvSelSrc.getSettings().setUserAgentString(ParaConfig.getUserAgent(SelSrcActivity.this));
        wvSelSrc.loadUrl(URL);

        Log.i(LOG_TAG, "UrlLoading " + URL);
        Log.i(LOG_TAG, "Redirect:" + isRedirect);
        
        browserHistory.add(new BrowserHistoryItem(URL, isRedirect));
        
        setUrlCmd(URL_CANCEL);
    }

    private boolean browserGoBack()
    {
        WebBackForwardList rec = wvSelSrc.copyBackForwardList();
        int wvIndex=rec.getCurrentIndex();
        if (wvSelSrc.canGoBack())
        {
            int historyIndex=browserHistory.size()-1;
            if(historyIndex>0)
            {
                int i;
                
                Log.i(LOG_TAG, "history back:");
                for(i=0; i<(historyIndex+1); i++)
                {
                    Log.i(LOG_TAG, i+" "+browserHistory.get(i).Url+" "+browserHistory.get(i).isRedirecrt);
                }
                
                Log.i(LOG_TAG, "webView back:");
                for(i=0; i<(wvIndex+1); i++)
                {
                    Log.i(LOG_TAG, i+" "+rec.getItemAtIndex(i).getUrl());
                }

                while(historyIndex>=0)
                {
                    BrowserHistoryItem history=browserHistory.get(historyIndex);
                    if(!history.isRedirecrt)
                    {
                        browserHistory.remove(historyIndex);
                        historyIndex--;
                        break;
                    }
                    
                    if(history.Url.equals(rec.getItemAtIndex(wvIndex).getUrl()))
                    {
                        wvIndex--;
                    }

                    browserHistory.remove(historyIndex);
                    historyIndex--;
                }
                
                wvIndex--;
                
                String tarBackUrl=browserHistory.get(historyIndex).Url;
                int wvCurIndex=rec.getCurrentIndex();
                int wvRecLen=wvCurIndex+1;
                for(i=0; i<wvRecLen; i++)
                {
                    if(tarBackUrl.equals(rec.getItemAtIndex(i).getUrl()))
                    {
                        wvIndex=i;
                        break;
                    }
                }
                
                int backSteps=wvIndex-wvCurIndex;
                if(wvSelSrc.canGoBackOrForward(backSteps))
                {
                    WebHistoryItem item=rec.getItemAtIndex(wvIndex);
                    String title=item.getTitle();
                    
                    etURL.setText((title!=null)?title:item.getUrl());
                    setUrlCmd(URL_CANCEL);
                    
                    Log.i(LOG_TAG, "backSteps:"+backSteps);
                    Log.i(LOG_TAG, "backToUrl:"+item.getUrl());
                    wvSelSrc.goBackOrForward(backSteps);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean browserGoForward()
    {
        if (wvSelSrc.canGoForward())
        {
            WebBackForwardList rec = wvSelSrc.copyBackForwardList();
            browserHistory.add(new BrowserHistoryItem(rec.getItemAtIndex(rec.getCurrentIndex()+1).getUrl(), false));
            wvSelSrc.goForward();
            return true;
        }
        return false;
    }

    private void browserGoHome()
    {
        browserLoadUrl(ParaConfig.getHomeURL(SelSrcActivity.this), false);
    }

    private void webViewInit()
    {
        pbWebView = (ProgressBar) findViewById(R.id.progressBarWebView);
        pbWebView.setMax(PROGRESS_MAX);

        browserHistory = new ArrayList<BrowserHistoryItem>();

        wvSelSrc = (WebView) findViewById(R.id.webViewSelectSrcUrl);

        wvSelSrc.setWebViewClient(new WebViewClient()
        {
            public boolean shouldOverrideUrlLoading(WebView view, String url)
            {
                browserLoadUrl(url, webAddrCanNotReach||((SystemClock.uptimeMillis()-lastStartTime)<300));
                return true;
            }

            public void onPageFinished(WebView view, String url)
            {
                Log.i(LOG_TAG, "onPageFinished " + url);
                setUrlCmd(URL_REFRESH);

                lastPageFinished=true;
                
                String title=wvSelSrc.copyBackForwardList().getCurrentItem().getTitle();
                if(title!=null)
                {
                    etURL.setText(title);
                }
            }

            public void onPageStarted(WebView view, String url, Bitmap favicon)
            {
                Log.i(LOG_TAG, "onPageStarted " + url);
                etURL.setText(url);
                lastPageFinished = false;
                webAddrCanNotReach = false;
                lastStartTime=SystemClock.uptimeMillis();
                setUrlCmd(URL_CANCEL);
            }

            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
            {
                Log.i(LOG_TAG, failingUrl + " ReceivedError " + errorCode + "  " + description);

                if (failingUrl.equals(view.getUrl()))
                {
                    webAddrCanNotReach = true;
                }
            }
        });

        wvSelSrc.setWebChromeClient(new WebChromeClient()
        {
            public void onProgressChanged(WebView view, int newProgress)
            {
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
            }
        });

        WebSettings setting = wvSelSrc.getSettings();
        setting.setUserAgentString(ParaConfig.getUserAgent(SelSrcActivity.this));

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

        wvSelSrc.requestFocus();

        browserLoadUrl(ParaConfig.getHomeURL(SelSrcActivity.this), false);
    }

    private void dispWebTitle(WebView view)
    {
        String title = view.getTitle();

        if (title != null)
        {
            if (!title.isEmpty())
            {
                etURL.setText(title);
                return;
            }
        }

        etURL.setText(view.getUrl());
    }

    private void clearURLbarFocus()
    {
        wvSelSrc.requestFocus();
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(etURL.getWindowToken(),
                InputMethodManager.HIDE_NOT_ALWAYS);

        if (pbWebView.getProgress() == 0)
        {
            setUrlCmd(URL_REFRESH);
        }
        else
        {
            setUrlCmd(URL_CANCEL);
        }

        dispWebTitle(wvSelSrc);

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
                    wvSelSrc.stopLoading();
                    pbWebView.setProgress(0);
                    setUrlCmd(URL_REFRESH);
                }
            break;

            case URL_REFRESH:
                wvSelSrc.reload();
                setUrlCmd(URL_CANCEL);
            break;

            case URL_ENTER:
                String tarURL = etURL.getText().toString();
                if (!wvSelSrc.getUrl().equals(tarURL))
                {
                    browserLoadUrl(tarURL, false);
                }
            break;

            case URL_SEARCH:
                browserLoadUrl(ParaConfig.getSearchEngineURL(SelSrcActivity.this) + etURL.getText().toString(), false);
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

                if ((viewId == R.id.buttonURLcmd) || (viewId == R.id.FrameLayoutURLcmd))
                {
                    executeURLcmd();
                }
                else
                {
                    switch (viewId)
                    {
                        case R.id.buttonBack:
                            browserGoBack();
                        break;

                        case R.id.buttonForward:
                            browserGoForward();
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
                            browserGoHome();
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
                            wvSelSrc.reload();
                        break;

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
                // Log.i(LOG_TAG, "mask Clicked");
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

                    setUrlCmd(URL_ENTER);

                    String url = wvSelSrc.getUrl();
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
                        setUrlCmd(URL_ENTER);
                        btnSelSearchEngine.setBackgroundResource(R.drawable.site);
                        etURL.setImeOptions(EditorInfo.IME_ACTION_GO);
                    }
                    else if (URL.isEmpty())
                    {
                        setUrlCmd(URL_CANCEL);
                        btnSelSearchEngine.setBackgroundResource(R.drawable.site);
                        etURL.setImeOptions(EditorInfo.IME_ACTION_NONE);
                    }
                    else
                    {
                        setUrlCmd(URL_SEARCH);
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
        webViewInit();
        
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

        wvSelSrc.stopLoading();
        wvSelSrc.clearCache(true);
    }

    private void openSettingPage()
    {
        Log.i(LOG_TAG, "openSettingPage");

        Intent intent = new Intent(this, ParaConfigActivity.class);

        String srcUrl = wvSelSrc.getUrl();

        Bundle bundle = new Bundle();
        bundle.putString(SOURCE_URL_BUNDLE_KEY, srcUrl);
        intent.putExtras(bundle);

        startActivity(intent);
    }

    public void spiderGo()
    {
        Log.i(LOG_TAG, "spiderGo");

        Intent intent = new Intent(this, SpiderCrawlActivity.class);

        String srcUrl = wvSelSrc.getUrl();

        Bundle bundle = new Bundle();
        bundle.putString(SOURCE_URL_BUNDLE_KEY, srcUrl);
        intent.putExtras(bundle);

        Toast.makeText(this, getString(R.string.srcUrl) + ":" + srcUrl, Toast.LENGTH_SHORT).show();
        
        startActivity(intent);// 直接切换Activity不接收返回结果
    }

    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        Log.i(LOG_TAG, "onKeyDown " + keyCode);

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
            else
            {
                if(SystemClock.uptimeMillis()-exitTim>2000)
                {
                    Toast.makeText(this, R.string.keyBackExitConfirm, Toast.LENGTH_SHORT).show();;
                    exitTim=SystemClock.uptimeMillis();
                    return true;
                }
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
            Log.i(LOG_TAG, "Landscape");
        }
        else
        {
            Log.i(LOG_TAG, "Portrait");
        }
    }
}
