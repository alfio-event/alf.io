import {
    Component,
    EventEmitter,
    Input,
    type OnChanges,
    Output,
    type SimpleChanges,
} from '@angular/core';
import type { UntypedFormGroup } from '@angular/forms';
import type { PaymentMethodId, PaymentProxy } from '../../model/event';
import {
    type PaymentProvider,
    SimplePaymentProvider,
} from '../payment-provider';

@Component({
    selector: 'app-offline-payment-proxy',
    templateUrl: './offline-payment-proxy.component.html',
})
export class OfflinePaymentProxyComponent implements OnChanges {
    @Input()
    method: PaymentMethodId;

    @Input()
    proxy: PaymentProxy;

    @Input()
    parameters: { [key: string]: any };

    @Input()
    overviewForm: UntypedFormGroup;

    @Output()
    paymentProvider: EventEmitter<PaymentProvider> =
        new EventEmitter<PaymentProvider>();

    constructor() {}

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
