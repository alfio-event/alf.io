import { SimplePaymentProvider } from '../payment-provider';

export class PayPalPaymentProvider extends SimplePaymentProvider {
    get paymentMethodDeferred(): boolean {
        return false;
    }
}
