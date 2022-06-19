import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
import { PaymentMethod, PaymentProxy } from 'src/app/model/event';
import { PaymentProvider, SimplePaymentProvider } from '../payment-provider';
import { FormGroup } from '@angular/forms';
import { MolliePaymentProvider } from './mollie-payment-provider';

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
  overviewForm: FormGroup;

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
