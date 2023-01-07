import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges} from '@angular/core';
import {PaymentMethod, PaymentProxy} from '../../model/event';
import {PaymentProvider, SimplePaymentProvider} from '../payment-provider';
import {UntypedFormGroup} from '@angular/forms';

@Component({
  selector: 'app-offline-payment-proxy',
  templateUrl: './offline-payment-proxy.component.html'
})
export class OfflinePaymentProxyComponent implements OnChanges {

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

  constructor() { }

  ngOnChanges(changes: SimpleChanges): void {
    if (this.matchProxyAndMethod && changes.method) {
      this.paymentProvider.emit(new SimplePaymentProvider());
    }
  }

  public get matchProxyAndMethod(): boolean {
    return this.method === 'BANK_TRANSFER' && this.proxy === 'OFFLINE';
  }

  handleRecaptchaResponse(recaptchaValue: string) {
    this.overviewForm.get('captcha').setValue(recaptchaValue);
  }

  public get deferred(): boolean {
    return this.parameters['deferred'];
  }
}
