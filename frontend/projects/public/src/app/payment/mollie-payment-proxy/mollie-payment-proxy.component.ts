import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges} from '@angular/core';

import {PaymentProvider} from '../payment-provider';
import {UntypedFormGroup} from '@angular/forms';
import {MolliePaymentProvider} from './mollie-payment-provider';
import {PaymentMethod, PaymentProxy} from '../../model/event';

@Component({
  selector: 'app-mollie-payment-proxy',
  templateUrl: './mollie-payment-proxy.component.html'
})
export class MolliePaymentProxyComponent implements OnChanges {

  @Input()
  method: PaymentMethod;

  @Input()
  proxy: PaymentProxy;

  @Input()
  parameters: {[key: string]: any};

  @Input()
  overviewForm: UntypedFormGroup;

  @Output()
  paymentProvider: EventEmitter<PaymentProvider> = new EventEmitter<PaymentProvider>();

  private compatibleMethods: PaymentMethod[] = ['CREDIT_CARD', 'IDEAL', 'APPLE_PAY', 'BANCONTACT', 'BELFIUS', 'ING_HOME_PAY', 'KBC', 'PRZELEWY_24'];

  constructor() { }

  ngOnChanges(changes: SimpleChanges): void {
    if (this.matchProxyAndMethod && changes.method) {
      this.paymentProvider.emit(new MolliePaymentProvider());
    }
  }

  public get matchProxyAndMethod(): boolean {
    return (this.compatibleMethods.includes(this.method)) && this.proxy === 'MOLLIE';
  }

}
