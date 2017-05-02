package com.jeongjuwon.iamport;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup.LayoutParams;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.annotations.VisibleForTesting;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.views.webview.ReactWebViewManager;
import com.siot.iamportsdk.KakaoWebViewClient;
import com.siot.iamportsdk.NiceWebViewClient;
import com.siot.iamportsdk.PaycoWebViewClient;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

public class IAmPortViewManager extends ReactWebViewManager { //SimpleViewManager<IAmPortWebView> {
    private static final String HTML_MIME_TYPE = "text/html";
    private HashMap<String, String> headerMap = new HashMap<>();
    private IAmPortPackage aPackage;
    private Activity activity;
    private ThemedReactContext reactContext;

    @VisibleForTesting
    public static final String REACT_CLASS = "IAmPortViewManager";

    @Override
    public String getName() { return REACT_CLASS; }

    @Override
    public IAmPortWebView createViewInstance(ThemedReactContext context) {
        IAmPortWebView webView = new IAmPortWebView(this, context);

        reactContext = context;
        activity = context.getCurrentActivity();

        // Fixes broken full-screen modals/galleries due to body
        // height being 0.
        webView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        CookieManager.getInstance().setAcceptCookie(true); // add default cookie support
        CookieManager.getInstance().setAcceptFileSchemeCookies(true); // add default cookie support

        return webView;
    }

    public void setPackage(IAmPortPackage aPackage) {
        this.aPackage = aPackage;
    }

    public IAmPortPackage getPackage() {
        return this.aPackage;
    }

    @ReactProp(name="appScheme")
    public void setAppScheme(IAmPortWebView view, @Nullable String appScheme) {
        view.setAppScheme(appScheme);
    }

    @ReactProp(name="pg")
    public void setPG(IAmPortWebView view, @Nullable String pg) {
        Log.e("iamport", "PG - " + pg);

        if (pg.equals("nice")) {
            view.setWebViewClient(new NiceWebViewClient(activity, view));
        } else if (pg.equals("kakao")) {
            view.setWebViewClient(new KakaoWebViewClient(activity, view));
        } else if (pg.equals("payco")) {
            view.setWebViewClient(new PaycoWebViewClient(activity, view));
        }
    }

    public void onDropViewInstance(IAmPortWebView webView) {
        super.onDropViewInstance(webView);
        ((ThemedReactContext) webView.getContext()).removeLifecycleEventListener(webView);
    }

    private class IAmPortWebView extends ReactWebView implements LifecycleEventListener {
        private final IAmPortViewManager mViewManager;
        private final ThemedReactContext mReactContext;
        private final String HTTP_METHOD_POST = "POST";

        private Activity activity;
        private String appScheme = "appScheme";
        private String charset = "UTF-8";
        private String baseUrl = "file:///";
        private String injectedJavaScript = null;
        private boolean allowUrlRedirect = false;
        private ReadableMap source;

        public IAmPortWebView(IAmPortViewManager viewManager, ThemedReactContext reactContext) {
            super(reactContext);

            Log.d("iamport", "new IAmPortWebView");

            mViewManager = viewManager;
            mReactContext = reactContext;
            activity = reactContext.getCurrentActivity();

            getSettings().setJavaScriptEnabled(true);
            getSettings().setBuiltInZoomControls(false);
            getSettings().setDomStorageEnabled(true);
            getSettings().setGeolocationEnabled(false);
            getSettings().setPluginState(WebSettings.PluginState.ON);
            getSettings().setAllowFileAccess(true);
            getSettings().setAllowFileAccessFromFileURLs(true);
            getSettings().setAllowUniversalAccessFromFileURLs(true);
            getSettings().setLoadsImagesAutomatically(true);
            getSettings().setBlockNetworkImage(false);
            getSettings().setBlockNetworkLoads(false);

            reactContext.addLifecycleEventListener(this);
//            addJavascriptInterface(new com.jeongjuwon.iamport.IAmPortWebView.IAmPortWebViewBridge(this), "iamport");
        }

        public void setAppScheme(String appScheme) {
            appScheme = appScheme;
        }

        public String getAppScheme() {
            return appScheme;
        }

        public void setBaseUrl(String baseUrl) {
            baseUrl = baseUrl;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public String getCharset() {
            return charset;
        }

        public void setSource(ReadableMap source) {
            this.source = source;
        }

        public ReadableMap getSource() {
            return source;
        }

        @Override
        public void onHostResume() {
            Intent intent = activity.getIntent();

            Log.i("iamport", "onHostResume - IAmPortWebView : " + intent);

            if (intent != null) {
                Uri intentData = intent.getData();

                Log.i("iamport", "intentData:" + intentData);

                if (intentData != null) {
                    //카카오페이 인증 후 복귀했을 때 결제 후속조치
                    String url = intentData.toString();

                    Log.i("iamport", "receive URL - " + url);

                    if (url.startsWith(getAppScheme() + "://process")) {
                        Log.i("iamport", "process");
                        loadUrl("javascript:IMP.communicate({result:'process'})");
                    } else if (url.startsWith(getAppScheme() + "://cancel")) {
                        Log.i("iamport", "cancel");
                        loadUrl("javascript:IMP.communicate({result:'cancel'})");
                    } else if (url.startsWith(getAppScheme() + "://success")) {
                        Log.i("iamport", "success");

                        Uri uri = Uri.parse(url);
                        String imp_uid = uri.getQueryParameter("imp_uid");
                        String merchant_uid = uri.getQueryParameter("merchant_uid");

                        onMessage("{\"uri\":" + uri +
                                        ",\"imp_uid\":" + imp_uid +
                                        ", \"merchant_uid\":" + merchant_uid + "}");

//                        emitPaymentEvent("success", imp_uid, merchant_uid);
                    }

                    intent.replaceExtras(new Bundle());
                    intent.setAction("");
                    intent.setData(null);
                    intent.setFlags(0);
                } else {
                    if (source != null && source.getString("uri") != null) {
                        Log.i("iamport", "uri - " + source.getString("uri"));
                        mViewManager.setSource(this, source);
                    } else {
                        Log.i("iamport", "source is null or uri in source is null - " + source);
                    }
                }
            }
        }

        @Override
        public void loadUrl(String url, Map<String, String> additionalHttpHeaders) {
            Log.d("iamport", "loadUrl - " + url + ", " + additionalHttpHeaders);
            super.loadUrl(url, additionalHttpHeaders);
        }

        @Override
        public void onHostPause() {
            Log.i("iamport", "onHostPause - IAmPortWebView");
        }

        @Override
        public void onHostDestroy() {
            Log.i("iamport", "onHostDestroy - IAmPortWebView");
            destroy();
        }
    }
}
