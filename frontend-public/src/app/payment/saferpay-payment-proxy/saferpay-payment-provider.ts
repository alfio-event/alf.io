import { SimplePaymentProvider } from '../payment-provider';

export class SaferpayPaymentProvider extends SimplePaymentProvider {
  get paymentMethodDeferred(): boolean {
    return false;
  }
}
