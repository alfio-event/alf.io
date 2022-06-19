import { SimplePaymentProvider } from '../payment-provider';

export class MolliePaymentProvider extends SimplePaymentProvider {
    get paymentMethodDeferred(): boolean {
        return false;
    }
}
