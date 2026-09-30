import {SimplePaymentProvider} from '../payment-provider';

export class MolliePaymentProvider extends SimplePaymentProvider {
  override get paymentMethodDeferred(): boolean {
        return false;
    }
}
