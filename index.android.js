import React, { Component } from 'react';
import { requireNativeComponent, DeviceEventEmitter } from 'react-native';

const IAmPortViewManager = requireNativeComponent('IAmPortViewManager', null);

class IAmPort extends Component {
  componentDidMount() {
    DeviceEventEmitter.addListener('paymentEvent', this.onPaymentResultReceive);
  }

  componentWillUnmount() {
    DeviceEventEmitter.removeAllListeners('paymentEvent');
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

  render() {
    const source = this.props.uri ? { uri: this.props.uri } : { html: this.getPurchasePage() };

    return (
      <IAmPortViewManager
        {...this.props}
        source={source}
        pg={this.props.params.pg}
        params={this.props.params}
        appScheme={this.props.params.app_scheme}
      />
    );
  }
}

module.exports = IAmPort;
