import {
    Component,
    EventEmitter,
    Input,
    type OnChanges,
    Output,
    type SimpleChanges,
} from '@angular/core';
import type { PaymentMethodId, PaymentProxy } from '../../model/event';
import type { ReservationInfo } from '../../model/reservation-info';
import type { PaymentProvider } from '../payment-provider';
import { PayPalPaymentProvider } from './paypal-payment-provider';

@Component({
    selector: 'app-paypal-payment-proxy',
    templateUrl: './paypal-payment-proxy.component.html',
})
export class PaypalPaymentProxyComponent implements OnChanges {
    @Input()
    method: PaymentMethodId;

    @Input()
    proxy: PaymentProxy;

    @Input()
    reservation: ReservationInfo;

    @Output()
    paymentProvider: EventEmitter<PaymentProvider> =
        new EventEmitter<PaymentProvider>();

    constructor() {}

    ngOnChanges(changes: SimpleChanges): void {
        if (this.matchProxyAndMethod && changes.method) {
            this.paymentProvider.emit(new PayPalPaymentProvider());
        }
    }

    public get matchProxyAndMethod(): boolean {
        return this.proxy === 'PAYPAL';
    }
}
