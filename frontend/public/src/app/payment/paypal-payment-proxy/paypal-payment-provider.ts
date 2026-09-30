import {SimplePaymentProvider} from '../payment-provider';

export class PayPalPaymentProvider extends SimplePaymentProvider {
    override get paymentMethodDeferred(): boolean {
        return false;
    }
}
