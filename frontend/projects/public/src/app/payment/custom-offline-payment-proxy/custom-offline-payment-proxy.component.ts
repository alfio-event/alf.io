import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges} from '@angular/core';

import {PaymentProvider} from '../payment-provider';
import {UntypedFormGroup} from '@angular/forms';
import { CustomOfflinePaymentProvider } from './custom-offline-payment-provider';
import {PaymentMethod, PaymentProxy} from '../../model/event';

@Component({
  selector: 'app-custom-offline-payment-proxy',
  templateUrl: './custom-offline-payment-proxy.component.html'
})
export class CustomOfflinePaymentProxyComponent implements OnChanges {

  @Input()
  method?: PaymentMethod;

  @Input()
  proxy?: PaymentProxy;

  @Input()
  parameters?: {[key: string]: any};

  @Input()
  overviewForm?: UntypedFormGroup;

  @Output()
  paymentProvider: EventEmitter<PaymentProvider> = new EventEmitter<PaymentProvider>();

  private compatibleMethods: PaymentMethod[] | string[] = ['ETRANSFER'];

  constructor() { }

  ngOnChanges(changes: SimpleChanges): void {
    if (this.matchProxyAndMethod && changes['method']) {
      this.paymentProvider.emit(new CustomOfflinePaymentProvider());
    }
  }

  public get matchProxyAndMethod(): boolean {
    if (!this.method) {
        return false;
    }

    return (this.compatibleMethods.includes(this.method)) && this.proxy === 'CUSTOM_OFFLINE';
  }

}
