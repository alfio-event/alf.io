import {SimplePaymentProvider} from '../payment-provider';

export class SaferpayPaymentProvider extends SimplePaymentProvider {
  override get paymentMethodDeferred(): boolean {
    return false;
  }
}
