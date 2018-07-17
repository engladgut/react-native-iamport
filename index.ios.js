import React, { Component } from 'react';
import { WebView, Linking } from 'react-native';

export default class IAmPort extends Component {
  getPurchasePage = () => {
    const { params } = this.props;
    const HTML = `
    <!DOCTYPE html>
    <html>
      <head>
        <title>${this.props.title || 'i\'mport react native payment module'}</title>
        <meta http-equiv="content-type" content="text/html; charset=utf-8">
      </head>
      <body>
        <script src="http://code.jquery.com/jquery-latest.min.js" ></script>
        <script src="https://service.iamport.kr/js/iamport.payment-1.1.4.js"></script>
        <script>
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
           window.postMessage(JSON.stringify(rsp));
         });
        </script>
      </body>
    </html>
    `;

    return HTML;
  };

  getParameterByName = (name, url = window.location.href) => {
    const regex = new RegExp(`[?&]${name.replace(/[[\]]/g, '\\$&')}(=([^&#]*)|&|#|$)`);
    const results = regex.exec(url);

    if (!results) { return null; }
    if (!results[2]) { return ''; }

    return decodeURIComponent(results[2].replace(/\+/g, ' '));
  };

  onMessage = (e) => {
    if (this.props.onMessage) {
      this.props.onMessage(e);
    }

    const response = JSON.parse(e.nativeEvent.data);
    this.props.onPaymentResultReceived && this.props.onPaymentResultReceived(response);
  };

  onShouldStartLoadWithRequest = (e) => {
    const { url } = e;

    // console.log('onShouldStartLoadWithRequest', e);

    const impUid = this.getParameterByName('imp_uid', url);
    const merchantUid = this.getParameterByName('merchant_uid', url);
    let result = '';

    if (this.props.params && this.props.params.app_scheme) {
      if (url.includes(`${this.props.params.app_scheme}://success`)) {
        result = 'success';
      } else if (url.includes(`${this.props.params.app_scheme}://cancel`)) {
        result = 'cancel';
      }
      if (result) {
        this.props.onPaymentResultReceived({ result, impUid, merchantUid });
      }
    }

    if (url.startsWith('http://') || url.startsWith('https://')) {
      return true;
    } else {
      Linking.canOpenURL(url).then(supported => {
        if (supported) {
          return Linking.openURL(url);
        } else {
          return false;
        }
      });
      return false;
    }
  };

  /* eslint no-var: 0, func-names: 0 */
  injectPostMessageFetch = () => {
    const patchPostMessageFunction = function () {
      var originalPostMessage = window.postMessage;

      var patchedPostMessage = function (message, targetOrigin, transfer) {
        originalPostMessage(message, targetOrigin, transfer);
      };

      patchedPostMessage.toString = function () {
        return String(Object.hasOwnProperty).replace('hasOwnProperty', 'postMessage');
      };

      window.postMessage = patchedPostMessage;
    };

    return `(${String(patchPostMessageFunction)})();`;
  };

  render() {
    const source = this.props.uri ? { uri: this.props.uri } : { html: this.getPurchasePage() };

    return (
      <WebView
        {...this.props}
        style={this.props.style}
        source={source}
        renderError={(e) => null}
        injectedJavaScript={this.injectPostMessageFetch()}
        onMessage={this.onMessage}
        onShouldStartLoadWithRequest={this.onShouldStartLoadWithRequest}
      />
    );
  }
}

module.exports = IAmPort;
