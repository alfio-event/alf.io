import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';

import { PaymentProvider } from '../payment-provider';
import { UntypedFormGroup } from '@angular/forms';
import { CustomOfflinePaymentProvider } from './custom-offline-payment-provider';
import { CustomOfflinePayment, type PaymentMethodId } from '../../model/event';
import { PaymentProxy } from '../../model/event';
import { I18nService } from 'projects/public/src/app/shared/i18n.service';

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

  constructor(private i18nService: I18nService) { }

  ngOnChanges(changes: SimpleChanges): void {
    if (this.matchProxyAndMethod && (changes['method'] || changes['availableMethods'])) {
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
    const currentLang = this.i18nService.getCurrentLang();
    const maybeMethod = this.availableMethods?.find(pm => pm.paymentMethodId === this.method)
    if(!maybeMethod) {
        return "";
    }

    const method = maybeMethod!;

    let translatedDescription = method.localizations['en']?.paymentDescription
        || method.localizations[0].paymentDescription;
    if (Object.keys(method.localizations).includes(currentLang)) {
        translatedDescription = method.localizations[currentLang].paymentDescription;
    }

    return translatedDescription;
  }

}
