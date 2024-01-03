import {EMPTY, Observable, of} from 'rxjs';

export interface PaymentProvider {
    readonly paymentMethodDeferred: boolean;
    pay(): Observable<PaymentResult>;
    statusNotifications(): Observable<PaymentStatusNotification>;
}

export class PaymentResult {
    constructor(public success: boolean, public gatewayToken: string, public reason: string = null, public reservationChanged = false) {}
}

export class PaymentStatusNotification {
    constructor(public delayed: boolean, public indeterminate: boolean) {}
}

export class SimplePaymentProvider implements PaymentProvider {

    pay(): Observable<PaymentResult> {
        return of(new PaymentResult(true, null));
    }

    get paymentMethodDeferred(): boolean {
        return true;
    }

    statusNotifications(): Observable<PaymentStatusNotification> {
        return EMPTY;
    }
}
