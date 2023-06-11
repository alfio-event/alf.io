import { Pipe, PipeTransform } from '@angular/core';

const PAYMENT_PROXY_DESCRIPTIONS = {
  STRIPE: 'Stripe: Credit cards',
  ON_SITE: 'On site (cash) payment',
  OFFLINE: 'Offline payment (bank transfer, invoice, etc.)',
  PAYPAL: 'PayPal',
  MOLLIE:
    'Mollie: Credit cards, iDEAL, Bancontact, ING Home Pay, Belfius, KBC/CBC, Przelewy24',
  SAFERPAY: 'Saferpay By SIX Payments',
} as any;

@Pipe({
  name: 'translatePaymentProxies',
})
export class TranslatePaymentProxiesPipe implements PipeTransform {
  transform(list: string[]): string[] {
    return list.map((item) =>
      PAYMENT_PROXY_DESCRIPTIONS[item] ? PAYMENT_PROXY_DESCRIPTIONS[item] : item
    );
  }
}
