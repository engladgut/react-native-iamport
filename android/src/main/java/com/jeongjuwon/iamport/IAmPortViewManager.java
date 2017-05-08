package com.jeongjuwon.iamport;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.common.build.ReactBuildConfig;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.views.webview.ReactWebViewManager;
import com.facebook.react.views.webview.WebViewConfig;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
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
    private static final HashMap<WebView, IAmPortLifeCycleEventListener> listenerMap = new HashMap<>();

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
        view.setWebViewClient(new IAmPortWebViewClient());
    }

    public static Map<String, List<String>> splitQuery(String queries) throws UnsupportedEncodingException {
        final Map<String, List<String>> queryPairs = new LinkedHashMap<String, List<String>>();
        final String[] pairs = queries.split("&");
        for (String pair : pairs) {
            final int idx = pair.indexOf("=");
            final String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;

            if (!queryPairs.containsKey(key)) {
                queryPairs.put(key, new LinkedList<String>());
            }

            final String value = idx > 0 && pair.length() > idx + 1 ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8") : null;
            queryPairs.get(key).add(value);
        }

        return queryPairs;
    }

    protected static class IAmPortWebViewClient extends ReactWebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView webView, String url) {
            debug("shouldOverrideUrlLoading - " + url);

            if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("javascript:")) {
                Intent intent = null;

                try {
                    intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME); //IntentURI처리
                    Uri uri = Uri.parse(intent.getDataString());

                    webView.getContext().startActivity(new Intent(Intent.ACTION_VIEW, uri)); //해당되는 Activity 실행
                    return true;
                } catch (URISyntaxException ex) {
                    return false;
                } catch (ActivityNotFoundException e) {
                    if ( intent == null )   return false;

//                    if ( handleNotFoundPaymentScheme(intent.getScheme()) )  return true; //설치되지 않은 앱에 대해 사전 처리(Google Play이동 등 필요한 처리)

                    String packageName = intent.getPackage();
                    if (packageName != null) { //packageName이 있는 경우에는 Google Play에서 검색을 기본
                        webView.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName)));
                        return true;
                    }

                    return false;
                }
            }

            return false;

//            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://") || url.startsWith("javascript:")) {
//                return false;
//            } else {
//                if (url.startsWith("intent://")) {
//                    try {
//                        Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
//                        Intent existPackage = webView.getContext().getPackageManager().getLaunchIntentForPackage(intent.getPackage());
//
//                        debug("package : " + intent.getPackage());
//                        debug("scheme : " + intent.getScheme());
//
//                        if (existPackage != null) {
//                            webView.getContext().startActivity(intent);
//                        } else {
//                            Intent marketIntent = new Intent(Intent.ACTION_VIEW);
//                            marketIntent.setData(Uri.parse("market://details?id="+intent.getPackage()));
//                            webView.getContext().startActivity(marketIntent);
//                        }
//
//                        return true;
//                    } catch (URISyntaxException e) {
//                        e.printStackTrace();
//                    }
//
//                    return true;
//                }
//
//                return false;
//            }
        }

        @Override
        public void onReceivedError(WebView webView, int errorCode, String description, String failingUrl) {
            debug("onReceivedError - " + failingUrl);
            super.onReceivedError(webView, errorCode, description, failingUrl);
        }

        private boolean handleKakao(Context context, String url) throws ActivityNotFoundException {
            Intent intent = null;

            try {
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME); //IntentURI처리
                Uri uri = Uri.parse(intent.getDataString());

                debug("kakao - " + uri.toString());

                context.startActivity(new Intent(Intent.ACTION_VIEW, uri));

                return true;
            } catch (URISyntaxException ex) {
                return false;
            }
        }

        private boolean handleNicePay(WebView view, String url) throws ActivityNotFoundException {
            Intent intent = null;
            final int RESCODE = 1;
            Activity activity = getActivity(view);

            try {
            /* START - BankPay(실시간계좌이체)에 대해서는 예외적으로 처리 */
                if (url.startsWith("kftc-bankpay") && activity != null) {
                    try {
                        String reqParam = makeBankPayData(url);

                        intent = new Intent(Intent.ACTION_MAIN);
                        intent.setComponent(new ComponentName("com.kftc.bankpay.android", "com.kftc.bankpay.android.activity.MainActivity"));
                        intent.putExtra("requestInfo", reqParam);
                        activity.startActivityForResult(intent, RESCODE);

                        return true;
                    } catch (URISyntaxException e) {
                        return false;
                    }
                }
            /* END - BankPay(실시간계좌이체)에 대해서는 예외적으로 처리 */

                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME); //IntentURI처리
                Uri uri = Uri.parse(intent.getDataString());

                activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));

                return true;
            } catch (URISyntaxException ex) {
                return false;
            }
        }

        private boolean handlePayco(Context context, String url) throws ActivityNotFoundException {
            Intent intent = null;

            try {
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME); //IntentURI처리
                Uri uri = Uri.parse(intent.getDataString());

                Log.e("iamport", uri.toString());

                context.startActivity(new Intent(Intent.ACTION_VIEW, uri));
                return true;
            } catch (URISyntaxException ex) {
                return false;
            }
        }

        private Activity getActivity(View view) {
            Context context = view.getContext();

            while (context instanceof ContextWrapper) {
                if (context instanceof Activity) {
                    return (Activity) context;
                }
                context = ((ContextWrapper) context).getBaseContext();
            }
            return null;
        }

        private String makeBankPayData(String url) throws URISyntaxException {
            String BANK_TID = "";
            List<NameValuePair> params = URLEncodedUtils.parse(new URI(url), "UTF-8");

            StringBuilder ret_data = new StringBuilder();
            List<String> keys = Arrays.asList(new String[]{"firm_name", "amount", "serial_no", "approve_no", "receipt_yn", "user_key", "callbackparam2", ""});

            String k, v;
            for (NameValuePair param : params) {
                k = param.getName();
                v = param.getValue();

                if (keys.contains(k)) {
                    if ("user_key".equals(k)) {
                        BANK_TID = v;
                    }
                    ret_data.append("&").append(k).append("=").append(v);
                }
            }

            ret_data.append("&callbackparam1=" + "nothing");
            ret_data.append("&callbackparam3=" + "nothing");

            return ret_data.toString();
        }
    }

    private class IAmPortLifeCycleEventListener implements LifecycleEventListener {
        private IAmPortViewManager mViewManager;
        public String appScheme;
        public String pg;
        public ReactWebView webView;

        public IAmPortLifeCycleEventListener(IAmPortViewManager mViewManager, ReactWebView webView) {
            this.mViewManager = mViewManager;
            this.webView = webView;
        }

        @Override
        public void onHostResume() {
            Intent intent = activity.getIntent();

            debug("onHostResume - IAmPortWebView : " + intent);
            debug("webView.getProgress() : " + webView.getProgress());
            debug("webView.getUrl() : " + webView.getUrl());

            if (intent != null) {
                Uri intentData = intent.getData();

                debug("intentData:" + intentData);

                //카카오페이 인증 후 복귀했을 때 결제 후속조치
                if (intentData != null) {
                    String url = intentData.toString();

                    debug("receive URL - " + url);

                    if (url.startsWith(appScheme + "://process")) {
                        debug("process");
                        webView.loadUrl("javascript:IMP.communicate({result:'process'})");
                    } else if (url.startsWith(appScheme + "://cancel")) {
                        debug("cancel");
                        webView.loadUrl("javascript:IMP.communicate({result:'cancel'})");
                    } else if (url.startsWith(appScheme + "://success")) {
                        debug("success");

                        Uri uri = Uri.parse(url);
                        String imp_uid = uri.getQueryParameter("imp_uid");
                        String merchant_uid = uri.getQueryParameter("merchant_uid");

                        webView.onMessage("{\"uri\":" + uri +
                                ",\"imp_uid\":" + imp_uid +
                                ", \"merchant_uid\":" + merchant_uid + "}");
                    }

                    intent.replaceExtras(new Bundle());
                    intent.setAction("");
                    intent.setData(null);
                    intent.setFlags(0);
                }
            }
        }

        @Override
        public void onHostPause() {
        }

        @Override
        public void onHostDestroy() {
            listenerMap.remove(this.webView);
            webView.onHostDestroy();
        }
    }

    @Override
    public WebView createViewInstance(ThemedReactContext reactContext) {
        WebView webView = super.createViewInstance(reactContext);
        IAmPortLifeCycleEventListener iamport = new IAmPortLifeCycleEventListener(this, (ReactWebView) webView);

        listenerMap.put(webView, iamport);
        reactContext.addLifecycleEventListener(iamport);

        mWebViewConfig.configWebView(webView);
        activity = reactContext.getCurrentActivity();
        webView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptFileSchemeCookies(true);

        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView,true);
        }

        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setBuiltInZoomControls(false);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setGeolocationEnabled(false);
        webView.getSettings().setPluginState(WebSettings.PluginState.ON);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowFileAccessFromFileURLs(true);
        webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
        webView.getSettings().setLoadsImagesAutomatically(true);
        webView.getSettings().setBlockNetworkImage(false);
        webView.getSettings().setBlockNetworkLoads(false);

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

        if (params.hasKey("pg") && listenerMap.get(view) != null && listenerMap.get(view).pg != null) {
            setPG(view, params.getString("pg"));
        }
    }

    @ReactProp(name = "appScheme")
    public void setAppScheme(ReactWebView view, @Nullable String appScheme) {
        if (listenerMap.get(view) != null && listenerMap.get(view).appScheme == null) {
            listenerMap.get(view).appScheme = appScheme;
        }
    }

    @ReactProp(name = "pg")
    public void setPG(ReactWebView view, @Nullable String pg) {
        if (listenerMap.get(view) != null && listenerMap.get(view).pg == null) {
            debug("PG - " + pg);
            listenerMap.get(view).pg = pg;
        }
    }

    public void onDropViewInstance(ReactWebView webView) {
        super.onDropViewInstance(webView);
        ((ThemedReactContext) webView.getContext()).removeLifecycleEventListener(webView);
    }
}
