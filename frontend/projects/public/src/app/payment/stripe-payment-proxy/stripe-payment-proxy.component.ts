import {Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges} from '@angular/core';
import {PaymentMethod, PaymentProxy} from '../../model/event';
import {ReservationInfo} from '../../model/reservation-info';
import {TranslateService} from '@ngx-translate/core';
import {PaymentProvider} from '../payment-provider';
import {ReservationService} from '../../shared/reservation.service';
import {STRIPE_CHECKOUT_ID_SCRIPT, StripeCheckoutPaymentProvider, StripePaymentV3} from './stripe-payment-providers';
import {removeDOMNode} from '../../shared/event.service';
import {PurchaseContext} from '../../model/purchase-context';

// global variable defined by stripe when the scripts are loaded
declare const Stripe: any;
//

const STRIPE_V3_ID_SCRIPT = 'stripe-payment-v3-script';

@Component({
  selector: 'app-stripe-payment-proxy',
  templateUrl: './stripe-payment-proxy.component.html',
  styleUrls: ['./stripe-payment-proxy.component.scss']
})
export class StripePaymentProxyComponent implements OnChanges, OnDestroy {

  @Input()
  purchaseContext: PurchaseContext;

  @Input()
  reservation: ReservationInfo;

  @Input()
  method: PaymentMethod;

  @Input()
  proxy: PaymentProxy;

  @Input()
  parameters: { [key: string]: any };

  @Output()
  paymentProvider: EventEmitter<PaymentProvider> = new EventEmitter<PaymentProvider>();

  constructor(
    private translate: TranslateService,
    private reservationService: ReservationService) { }

  ngOnChanges(changes: SimpleChanges): void {
    if (this.matchProxyAndMethod && changes.method) {
      if (this.parameters['enableSCA']) {
        this.loadSCA();
      } else {
        this.loadNonSCA();
      }
    } else {
      this.unloadAll();
    }
  }

  get useSCA(): boolean {
    return this.parameters && this.parameters['enableSCA'];
  }

  ngOnDestroy(): void {
    this.unloadAll();
  }

  private unloadAll(): void {
    const checkoutScript = document.getElementById(STRIPE_CHECKOUT_ID_SCRIPT);
    if (checkoutScript && removeDOMNode(checkoutScript)) {
      delete window['StripeCheckout']; // TODO: check
    }
    const stripeV3Script = document.getElementById(STRIPE_V3_ID_SCRIPT);
    if (stripeV3Script && removeDOMNode(stripeV3Script)) {
      delete window['Stripe']; // TODO: check
    }
  }

  private loadNonSCA(): void {
    this.paymentProvider.emit(new StripeCheckoutPaymentProvider(this.translate, this.parameters, this.reservation, this.purchaseContext));
  }

  public get matchProxyAndMethod(): boolean {
    return this.proxy === 'STRIPE';
  }


  //
  private loadSCA(): void {
    if (!document.getElementById(STRIPE_V3_ID_SCRIPT)) {
      const scriptElem = document.createElement('script');
      scriptElem.id = STRIPE_V3_ID_SCRIPT;
      scriptElem.src = 'https://js.stripe.com/v3/';
      scriptElem.async = true;
      scriptElem.defer = true;
      scriptElem.addEventListener('load', () => {
        this.configureSCA();
      });
      document.body.appendChild(scriptElem);
    } else if (!window['Stripe']) {
      document.getElementById(STRIPE_V3_ID_SCRIPT).addEventListener('load', () => {
        this.configureSCA();
      });
    } else {
      this.configureSCA();
    }
  }

  private configureSCA() {
    const options = {};

    if (this.parameters['stripeConnectedAccount']) {
      options['stripeAccount'] = this.parameters['stripeConnectedAccount'];
    }

    const stripeHandler = Stripe(this.parameters['stripe_p_key'], options);
    const card = stripeHandler.elements({ locale: this.translate.currentLang }).create('card', {
      style: {
        base: {
          color: '#495057',
          lineHeight: '18px',
          fontFamily: `"Source Sans Pro", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto,
                       "Helvetica Neue", Arial, "Noto Sans", sans-serif, "Apple Color Emoji",
                       "Segoe UI Emoji", "Segoe UI Symbol", "Noto Color Emoji"`,
          fontSmoothing: 'antialiased',
          fontSize: '1rem',
          '::placeholder': {
            color: '#aab7c4'
          }
        },
        invalid: {
          color: '#a94442',
          iconColor: '#a94442'
        }
      }
    });

    card.addEventListener('change', (ev) => {
      // TODO: show errors & co
      if (ev.complete) {
        this.paymentProvider.emit(new StripePaymentV3(this.reservationService, this.reservation, stripeHandler, card)); // enable payment
      } else {
        this.paymentProvider.emit(null); // -> disable submit buttons by providing an empty payment provider
      }
    });
    card.mount('#card-element');
  }
}
