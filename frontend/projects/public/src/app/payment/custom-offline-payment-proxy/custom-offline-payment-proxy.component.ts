import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges} from '@angular/core';

import {PaymentProvider} from '../payment-provider';
import {UntypedFormGroup} from '@angular/forms';
import { CustomOfflinePaymentProvider } from './custom-offline-payment-provider';
import { CustomOfflinePayment, type PaymentMethodId } from '../../model/event';
import {PaymentProxy} from '../../model/event';

@Component({
  selector: 'app-custom-offline-payment-proxy',
  templateUrl: './custom-offline-payment-proxy.component.html'
})
export class CustomOfflinePaymentProxyComponent implements OnChanges {

  @Input()
  method?: PaymentMethodId;

  @Input()
  proxy?: PaymentProxy;

  @Input()
  availableMethods?: CustomOfflinePayment[];

  @Input()
  parameters?: {[key: string]: any};

  @Input()
  overviewForm?: UntypedFormGroup;

  @Output()
  paymentProvider: EventEmitter<PaymentProvider> = new EventEmitter<PaymentProvider>();

  constructor() { }

  ngOnChanges(changes: SimpleChanges): void {
    if (this.matchProxyAndMethod && changes['method']) {
      this.paymentProvider.emit(new CustomOfflinePaymentProvider());
    }
  }

  public get matchProxyAndMethod(): boolean {
    if (!this.method || !this.availableMethods) {
        return false;
    }

    return this.availableMethods.some(pm => pm.paymentMethodId === this.method)
        && this.proxy === 'CUSTOM_OFFLINE';
  }

  get selectedPaymentMethodDescription(): string {
    const maybeMethod = this.availableMethods?.find(pm => pm.paymentMethodId === this.method)
    if(!maybeMethod) {
        return "";
    }

    // FIXME: Use localizations instead of hardcoding 'en'.
    const method = maybeMethod!;
    return method.localizations.en.paymentDescription
  }

}
