package com.jeongjuwon.iamport;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter;
import com.facebook.react.uimanager.ThemedReactContext;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

class IAmPortWebView extends WebView implements LifecycleEventListener {
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

    public class IAmPortWebViewBridge {
        IAmPortWebView mContext;

        IAmPortWebViewBridge(IAmPortWebView c) {
            mContext = c;
        }

        @JavascriptInterface
        public void receiveResult(String result, String imp_uid, String merchant_uid) {
            mContext.emitPaymentEvent(result, imp_uid, merchant_uid);
        }

        @JavascriptInterface
        public void postMessage(String message) {
//            mContext.onMessage(message);
        }
    }

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
        addJavascriptInterface(new IAmPortWebViewBridge(this), "iamport");
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

    public void emitPaymentEvent(String result, String imp_uid, String merchant_uid) {
        WritableMap params = Arguments.createMap();
        params.putString("result", result);
        params.putString("imp_uid", imp_uid);
        params.putString("merchant_uid", merchant_uid);

        mReactContext.getJSModule(RCTDeviceEventEmitter.class).emit("paymentEvent", params);
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

                    emitPaymentEvent("success", imp_uid, merchant_uid);
                }

                intent.replaceExtras(new Bundle());
                intent.setAction("");
                intent.setData(null);
                intent.setFlags(0);
            } else {
                if (source != null && source.getString("uri") != null) {
                    Log.i("iamport", "uri - " + source.getString("uri"));
                    loadUrl();
                } else {
                    Log.i("iamport", "source is null or uri in source is null - " + source);
                }
            }
        }
    }

    public void loadUrl() {
        String url = source.getString("uri");
        String previousUrl = getUrl();

        Log.d("iamport", "url:" + url + ", previousUrl:" + previousUrl);

        if (previousUrl != null && previousUrl.equals(url)) {
            return;
        }
        if (source.hasKey("method")) {
            String method = source.getString("method");

            if (method.equals(HTTP_METHOD_POST)) {
                byte[] postData = null;

                if (source.hasKey("body")) {
                    String body = source.getString("body");
                    try {
                        postData = body.getBytes("UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        postData = body.getBytes();
                    }
                }

                if (postData == null) {
                    postData = new byte[0];
                }
                postUrl(url, postData);
                return;
            }
        }

        HashMap<String, String> headerMap = new HashMap<>();

        if (source.hasKey("headers")) {
            ReadableMap headers = source.getMap("headers");
            ReadableMapKeySetIterator iter = headers.keySetIterator();

            while (iter.hasNextKey()) {
                String key = iter.nextKey();
                if ("user-agent".equals(key.toLowerCase(Locale.ENGLISH))) {
                    if (getSettings() != null) {
                        getSettings().setUserAgentString(headers.getString(key));
                    }
                } else {
                    headerMap.put(key, headers.getString(key));
                }
            }
        }

        this.loadUrl(url, headerMap);
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
