package com.jeongjuwon.iamport;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup.LayoutParams;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.facebook.common.logging.FLog;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.ReactConstants;
import com.facebook.react.common.annotations.VisibleForTesting;
import com.facebook.react.common.build.ReactBuildConfig;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.UIManagerModule;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.events.Event;
import com.facebook.react.uimanager.events.EventDispatcher;
import com.facebook.react.views.webview.ReactWebViewManager;
import com.facebook.react.views.webview.WebViewConfig;
import com.facebook.react.views.webview.events.TopLoadingErrorEvent;
import com.facebook.react.views.webview.events.TopLoadingFinishEvent;
import com.facebook.react.views.webview.events.TopLoadingStartEvent;
import com.siot.iamportsdk.KakaoWebViewClient;
import com.siot.iamportsdk.NiceWebViewClient;
import com.siot.iamportsdk.PaycoWebViewClient;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

@ReactModule(name = IAmPortViewManager.REACT_CLASS)
public class IAmPortViewManager extends ReactWebViewManager {
    public static final String REACT_CLASS = "IAmPortWebView";
    private static final String HTML_MIME_TYPE = "text/html";
    private HashMap<String, String> headerMap = new HashMap<>();
    private IAmPortPackage aPackage;
    private Activity activity;
    private WebViewConfig mWebViewConfig;

    public IAmPortViewManager() {
        mWebViewConfig = new WebViewConfig() {
            public void configWebView(WebView webView) {
            }
        };
    }

    public IAmPortViewManager(WebViewConfig webViewConfig) {
        mWebViewConfig = webViewConfig;
    }

    public static void debug(String messages) {
        Log.d("iamport", messages);
    }

    public static void info(String messages) {
        Log.i("iamport", messages);
    }

    public static void error(String messages) {
        Log.e("iamport", messages);
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    protected void addEventEmitters(ThemedReactContext reactContext, WebView view) {
        debug("addEventEmitters");
        view.setWebViewClient(new IAmPortWebViewClient());
    }

    private static void dispatchEvent(WebView webView, Event event) {
        ReactContext reactContext = (ReactContext) webView.getContext();
        EventDispatcher eventDispatcher =
                reactContext.getNativeModule(UIManagerModule.class).getEventDispatcher();
        eventDispatcher.dispatchEvent(event);
    }

    protected static class IAmPortWebViewClient extends ReactWebViewClient {
        private boolean mLastLoadFailed = false;

        @Override
        public void onPageFinished(WebView webView, String url) {
            debug("onPageFinished");
            super.onPageFinished(webView, url);
        }

        @Override
        public void onPageStarted(WebView webView, String url, Bitmap favicon) {
            debug("onPageStarted");
            super.onPageStarted(webView, url, favicon);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            debug("shouldOverrideUrlLoading");
            return super.shouldOverrideUrlLoading(view, url);
        }

        @Override
        public void onReceivedError(WebView webView, int errorCode, String description, String failingUrl) {
            debug("onReceivedError");
            super.onReceivedError(webView, errorCode, description, failingUrl);
        }

        @Override
        public void doUpdateVisitedHistory(WebView webView, String url, boolean isReload) {
            debug("doUpdateVisitedHistory");
            super.doUpdateVisitedHistory(webView, url, isReload);
        }

        private void emitFinishEvent(WebView webView, String url) {
            debug("emitFinishEvent( " + webView + ", " + url + " ");

            TopLoadingFinishEvent event = new TopLoadingFinishEvent(webView.getId(), createWebViewEvent(webView, url));
            dispatchEvent(webView, event);
        }

        private WritableMap createWebViewEvent(WebView webView, String url) {
            WritableMap event = Arguments.createMap();
            event.putDouble("target", webView.getId());
            event.putString("url", url);
            event.putBoolean("loading", !mLastLoadFailed && webView.getProgress() != 100);
            event.putString("title", webView.getTitle());
            event.putBoolean("canGoBack", webView.canGoBack());
            event.putBoolean("canGoForward", webView.canGoForward());

            return event;
        }
    }

    private class IAmPortLifeCycleEventListener implements LifecycleEventListener {
        private IAmPortViewManager mViewManager;
        public String appScheme;
        public String pg;
        public ReactWebView view;

        public IAmPortLifeCycleEventListener(IAmPortViewManager mViewManager, ReactWebView view) {
            this.mViewManager = mViewManager;
            this.view = view;

            view.getSettings().setBuiltInZoomControls(true);
            view.getSettings().setDisplayZoomControls(false);
            view.getSettings().setDomStorageEnabled(true);
            view.getSettings().setJavaScriptEnabled(true);
            view.getSettings().setBuiltInZoomControls(false);
            view.getSettings().setDomStorageEnabled(true);
            view.getSettings().setGeolocationEnabled(false);
            view.getSettings().setPluginState(WebSettings.PluginState.ON);
            view.getSettings().setAllowFileAccess(true);
            view.getSettings().setAllowFileAccessFromFileURLs(true);
            view.getSettings().setAllowUniversalAccessFromFileURLs(true);
            view.getSettings().setLoadsImagesAutomatically(true);
            view.getSettings().setBlockNetworkImage(false);
            view.getSettings().setBlockNetworkLoads(false);
        }

        @Override
        public void onHostResume() {
            Intent intent = activity.getIntent();

            mViewManager.debug("onHostResume - IAmPortWebView : " + intent);
            mViewManager.debug("view.getProgress() : " + view.getProgress());
            mViewManager.debug("view.getUrl() : " + view.getUrl());

            if (intent != null) {
                Uri intentData = intent.getData();

                mViewManager.debug("intentData:" + intentData);

                //카카오페이 인증 후 복귀했을 때 결제 후속조치
                if (intentData != null) {
                    String url = intentData.toString();

                    mViewManager.debug("receive URL - " + url);

                    if (url.startsWith(appScheme + "://process")) {
                        mViewManager.debug("process");
                        view.loadUrl("javascript:IMP.communicate({result:'process'})");
                    } else if (url.startsWith(appScheme + "://cancel")) {
                        mViewManager.debug("cancel");
                        view.loadUrl("javascript:IMP.communicate({result:'cancel'})");
                    } else if (url.startsWith(appScheme + "://success")) {
                        mViewManager.debug("success");

                        Uri uri = Uri.parse(url);
                        String imp_uid = uri.getQueryParameter("imp_uid");
                        String merchant_uid = uri.getQueryParameter("merchant_uid");

                        view.onMessage("{\"uri\":" + uri +
                                ",\"imp_uid\":" + imp_uid +
                                ", \"merchant_uid\":" + merchant_uid + "}");
                    }

                    intent.replaceExtras(new Bundle());
                    intent.setAction("");
                    intent.setData(null);
                    intent.setFlags(0);
                }
            }

//            ReactContext reactContext = (ReactContext) view.getContext();
//            EventDispatcher eventDispatcher = reactContext.getNativeModule(UIManagerModule.class).getEventDispatcher();
//            WritableMap event = Arguments.createMap();
//            event.putDouble("target", view.getId());
//            event.putString("url", view.getUrl());
//            event.putBoolean("loading", view.getProgress() != 100);
//            event.putString("title", view.getTitle());
//            event.putBoolean("canGoBack", view.canGoBack());
//            event.putBoolean("canGoForward", view.canGoForward());
//
//            eventDispatcher.dispatchEvent(new TopLoadingFinishEvent(view.getId(), event));
        }

        @Override
        public void onHostPause() {
        }

        @Override
        public void onHostDestroy() {
            listererMap.remove(this.view);
            view.onHostDestroy();
        }
    }

    private final HashMap<WebView, IAmPortLifeCycleEventListener> listererMap = new HashMap<>();

    @Override
    public WebView createViewInstance(ThemedReactContext reactContext) {
        WebView webView = super.createViewInstance(reactContext);
        IAmPortLifeCycleEventListener iamport = new IAmPortLifeCycleEventListener(this, (ReactWebView) webView);

        listererMap.put(webView, iamport);
        reactContext.addLifecycleEventListener(iamport);

        mWebViewConfig.configWebView(webView);
        activity = reactContext.getCurrentActivity();
        webView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        CookieManager.getInstance().setAcceptCookie(true); // add default cookie support
        CookieManager.getInstance().setAcceptFileSchemeCookies(true); // add default cookie support

        if (ReactBuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        return webView;
    }

    public void setPackage(IAmPortPackage aPackage) {
        this.aPackage = aPackage;
    }

    public IAmPortPackage getPackage() {
        return this.aPackage;
    }

    @ReactProp(name = "params")
    public void setParams(ReactWebView view, @Nullable ReadableMap params) {
        if (params.hasKey("app_scheme")) {
            setAppScheme(view, params.getString("app_scheme"));
        }

        if (params.hasKey("pg") && listererMap.get(view) != null && listererMap.get(view).pg != null) {
            setPG(view, params.getString("pg"));
        }
    }

    @ReactProp(name = "appScheme")
    public void setAppScheme(ReactWebView view, @Nullable String appScheme) {
        if (listererMap.get(view) != null) {
            listererMap.get(view).appScheme = appScheme;
        }
    }

    @ReactProp(name = "pg")
    public void setPG(ReactWebView view, @Nullable String pg) {
        if (listererMap.get(view) != null && listererMap.get(view).pg == null) {
            debug("PG - " + pg);

            listererMap.get(view).pg = pg;

//            if (pg.equals("nice")) {
//                view.setWebViewClient(new NiceWebViewClient(activity, view));
//            } else if (pg.equals("kakao")) {
//                view.setWebViewClient(new KakaoWebViewClient(activity, view));
//            } else if (pg.equals("payco")) {
//                view.setWebViewClient(new PaycoWebViewClient(activity, view));
//            }
        }
    }

    public void onDropViewInstance(ReactWebView webView) {
        super.onDropViewInstance(webView);
        ((ThemedReactContext) webView.getContext()).removeLifecycleEventListener(webView);
    }
}
