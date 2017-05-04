import React, { Component, PropTypes } from 'react';
import ReactNative, {
  requireNativeComponent,
  DeviceEventEmitter,
  ActivityIndicator,
  StyleSheet,
  UIManager,
  View
} from 'react-native';
import resolveAssetSource from 'react-native/Libraries/Image/resolveAssetSource';

function keyMirror(obj) {
  const mirror = {};

  Object.keys(obj).forEach(v => {
    if (obj.hasOwnProperty(v)) {
      mirror[v] = v;
    }
  });

  return mirror;
}

const IAMPORT_WEBVIEW_REF = 'webview';
const WebViewState = keyMirror({ IDLE: null, LOADING: null, ERROR: null });
const defaultRenderLoading = () => (
  <View style={styles.loadingView}>
    <ActivityIndicator style={styles.loadingProgressBar} />
  </View>
);

const styles = StyleSheet.create({
  container: { flex: 1 },
  hidden: { height: 0, flex: 0 },
  loadingView: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  loadingProgressBar: { height: 20 },
});

const IAmPortWebView = requireNativeComponent('IAmPortWebView', IAmPort, {
  nativeOnly: {
    messagingEnabled: PropTypes.bool,
  },
});

class IAmPort extends Component {
  static defaultProps = {
    javaScriptEnabled: true,
    scalesPageToFit: true
  };

  state = {
    viewState: WebViewState.IDLE,
    lastErrorEvent: null,
    startInLoadingState: true
  };

  ref = {};

  componentWillMount() {
    if (this.props.startInLoadingState) {
      this.setState({ viewState: WebViewState.LOADING });
    }
  }

  componentDidMount() {
    DeviceEventEmitter.addListener('paymentEvent', this.onPaymentResultReceive);
  }

  componentWillUnmount() {
    DeviceEventEmitter.removeAllListeners('paymentEvent');
  }

  render() {
    let otherView = null;

    if (this.state.viewState === WebViewState.LOADING) {
      otherView = (this.props.renderLoading || defaultRenderLoading)();
    } else if (this.state.viewState === WebViewState.ERROR) {
      const errorEvent = this.state.lastErrorEvent;
      otherView = this.props.renderError && this.props.renderError(
        errorEvent.domain,
        errorEvent.code,
        errorEvent.description);
    } else if (this.state.viewState !== WebViewState.IDLE) {
      console.error(`RCTWebView invalid state encountered: ${this.state.loading}`);
    }

    const webViewStyles = [styles.container, this.props.style];
    if (this.state.viewState === WebViewState.LOADING ||
      this.state.viewState === WebViewState.ERROR) {
      webViewStyles.push(styles.hidden);
    }

    const source = this.props.source || {};
    if (this.props.html) {
      source.html = this.props.html;
    } else if (this.props.url || this.props.uri) {
      source.uri = this.props.url || this.props.uri;
    }

    if (source.method === 'POST' && source.headers) {
      console.warn('WebView: `source.headers` is not supported when using POST.');
    } else if (source.method === 'GET' && source.body) {
      console.warn('WebView: `source.body` is not supported when using GET.');
    }

    const resolvedSource = resolveAssetSource(source);
    console.log('resolvedSource', resolvedSource);
    if (resolvedSource && !resolvedSource.uri && !resolvedSource.html) {
      resolvedSource.html = this.getPurchasePage();
    }
    const webView = (
      <IAmPortWebView
        {...this.props}
        style={webViewStyles}
        source={resolvedSource}
        pg={this.props.pg || this.props.params.pg}
        params={this.props.params}
        appScheme={this.props.params.app_scheme}
        ref={(ref) => (this.ref[IAMPORT_WEBVIEW_REF] = ref)}
        key="webViewKey"
        scalesPageToFit={this.props.scalesPageToFit}
        injectedJavaScript={this.props.injectedJavaScript}
        userAgent={this.props.userAgent}
        javaScriptEnabled={this.props.javaScriptEnabled}
        domStorageEnabled={this.props.domStorageEnabled}
        messagingEnabled={typeof this.props.onMessage === 'function'}
        onMessage={this.onMessage}
        contentInset={this.props.contentInset}
        automaticallyAdjustContentInsets={this.props.automaticallyAdjustContentInsets}
        onContentSizeChange={this.props.onContentSizeChange}
        onLoadingStart={this.onLoadingStart}
        onLoadingFinish={this.onLoadingFinish}
        onLoadingError={this.onLoadingError}
        testID={this.props.testID}
        mediaPlaybackRequiresUserAction={this.props.mediaPlaybackRequiresUserAction}
        allowUniversalAccessFromFileURLs={this.props.allowUniversalAccessFromFileURLs}
        mixedContentMode={this.props.mixedContentMode}
      />
    );

    return (
      <View style={styles.container}>
        {webView}
        {otherView}
      </View>
    );
  }

  getPurchasePage() {
    const params = this.props.params;
    const HTML = `
    <!DOCTYPE html>
    <html>
      <head>
        <title>i'mport react native payment module</title>
        <meta http-equiv="content-type" content="text/html; charset=utf-8">
      </head>
      <body>
        <script type="text/javascript" src="http://code.jquery.com/jquery-latest.min.js" ></script>
        <script type="text/javascript" src="https://service.iamport.kr/js/iamport.payment-1.1.4.js"></script>
        <script type="text/javascript">
          var IMP = window.IMP;
          IMP.init('${params.code}');
          IMP.request_pay({
            pg : '${params.pg}',
            pay_method : '${params.pay_method}',
            merchant_uid : 'merchant_${new Date().getTime()}',
            ${params.pg === 'nice' ? `m_redirect_url : '${params.app_scheme}://success',` : ''}
            app_scheme : '${params.app_scheme}',
            name : '${params.name}',
            amount : ${params.amount},
            buyer_email : '${params.buyer_email}',
            buyer_name : '${params.buyer_name}',
            buyer_tel : '${params.buyer_tel}',
            buyer_addr : '${params.buyer_addr}',
            buyer_postcode : '${params.buyer_postcode}'
          }, function(rsp){
            if('${params.pg}' == 'nice'){ return; }
            iamport.receiveResult(response);
          });
        </script>
      </body>
    </html>
    `;

    return HTML;
  }

  onPaymentResultReceive(e) {
    if (this.props.onPaymentResultReceived) { this.props.onPaymentResultReceived(e); }
  }

  goForward = () => {
    UIManager.dispatchViewManagerCommand(
      this.getWebViewHandle(),
      UIManager.IAmPortWebView.Commands.goForward,
      null
    );
  };

  goBack = () => {
    UIManager.dispatchViewManagerCommand(
      this.getWebViewHandle(),
      UIManager.IAmPortWebView.Commands.goBack,
      null
    );
  };

  reload = () => {
    UIManager.dispatchViewManagerCommand(
      this.getWebViewHandle(),
      UIManager.IAmPortWebView.Commands.reload,
      null
    );
  };

  stopLoading = () => {
    UIManager.dispatchViewManagerCommand(
      this.getWebViewHandle(),
      UIManager.IAmPortWebView.Commands.stopLoading,
      null
    );
  };

  postMessage = (data) => {
    UIManager.dispatchViewManagerCommand(
      this.getWebViewHandle(),
      UIManager.IAmPortWebView.Commands.postMessage,
      [String(data)]
    );
  };

  injectJavaScript = (data) => {
    UIManager.dispatchViewManagerCommand(
      this.getWebViewHandle(),
      UIManager.IAmPortWebView.Commands.injectJavaScript,
      [data]
    );
  };

  updateNavigationState = (event) => {
    if (this.props.onNavigationStateChange) {
      this.props.onNavigationStateChange(event.nativeEvent);
    }
  };

  getWebViewHandle = () => ReactNative.findNodeHandle(this.ref[IAMPORT_WEBVIEW_REF]);

  onLoadingStart = (event) => {
    const { onLoadStart } = this.props;
    onLoadStart && onLoadStart(event);
    this.updateNavigationState(event);
  };

  onLoadingError = (event) => {
    event.persist(); // persist this event because we need to store it

    const { onError, onLoadEnd } = this.props;
    onError && onError(event);
    onLoadEnd && onLoadEnd(event);
    console.warn('Encountered an error loading page', event.nativeEvent);

    this.setState({
      lastErrorEvent: event.nativeEvent,
      viewState: WebViewState.ERROR
    });
  };

  onLoadingFinish = (event) => {
    const { onLoad, onLoadEnd } = this.props;
    onLoad && onLoad(event);
    onLoadEnd && onLoadEnd(event);
    this.setState({ viewState: WebViewState.IDLE });
    this.updateNavigationState(event);
  };

  onMessage = (event: Event) => {
    const { onMessage } = this.props;
    onMessage && onMessage(event);
  }
}

module.exports = IAmPort;
