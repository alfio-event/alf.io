import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges} from '@angular/core';
import {PaymentMethod, PaymentProxy} from '../../model/event';
import {ReservationInfo} from '../../model/reservation-info';
import {PaymentProvider} from '../payment-provider';
import {SaferpayPaymentProvider} from './saferpay-payment-provider';

@Component({
  selector: 'app-saferpay-payment-proxy',
  templateUrl: './saferpay-payment-proxy.component.html'
})
export class SaferpayPaymentProxyComponent implements OnChanges {
  @Input()
  method: PaymentMethod;

  @Input()
  proxy: PaymentProxy;

  @Input()
  reservation: ReservationInfo;

  @Output()
  paymentProvider: EventEmitter<PaymentProvider> = new EventEmitter<PaymentProvider>();

  ngOnChanges(changes: SimpleChanges): void {
    if (this.matchProxyAndMethod && changes.method) {
      this.paymentProvider.emit(new SaferpayPaymentProvider());
    }
  }

  public get matchProxyAndMethod(): boolean {
    return this.proxy === 'SAFERPAY';
  }

}
