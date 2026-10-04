import {SimplePaymentProvider} from '../payment-provider';

export class CustomOfflinePaymentProvider extends SimplePaymentProvider {
  override get paymentMethodDeferred(): boolean {
        return true;
    }
}
