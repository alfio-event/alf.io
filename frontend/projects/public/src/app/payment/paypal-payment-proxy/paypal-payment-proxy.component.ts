import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges} from '@angular/core';
import {PaymentMethod, PaymentProxy} from '../../model/event';
import {PaymentProvider} from '../payment-provider';
import {ReservationInfo} from '../../model/reservation-info';
import {PayPalPaymentProvider} from './paypal-payment-provider';

@Component({
  selector: 'app-paypal-payment-proxy',
  templateUrl: './paypal-payment-proxy.component.html',
})
export class PaypalPaymentProxyComponent implements OnChanges {

  @Input()
  method: PaymentMethod;

  @Input()
  proxy: PaymentProxy;

  @Input()
  reservation: ReservationInfo;

  @Output()
  paymentProvider: EventEmitter<PaymentProvider> = new EventEmitter<PaymentProvider>();

  constructor() { }

  ngOnChanges(changes: SimpleChanges): void {
    if (this.matchProxyAndMethod && changes.method) {
      this.paymentProvider.emit(new PayPalPaymentProvider());
    }
  }

  public get matchProxyAndMethod(): boolean {
    return this.proxy === 'PAYPAL';
  }
}
