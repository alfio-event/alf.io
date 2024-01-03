import {EMPTY, Observable, Subject, Subscriber} from 'rxjs';
import {PaymentProvider, PaymentResult, PaymentStatusNotification} from '../payment-provider';
import {TranslateService} from '@ngx-translate/core';
import {ReservationInfo} from '../../model/reservation-info';
import {ReservationService} from '../../shared/reservation.service';
import {PurchaseContext} from '../../model/purchase-context';

// global variable defined by stripe when the scripts are loaded
declare const StripeCheckout: any;
//

export const STRIPE_CHECKOUT_ID_SCRIPT = 'stripe-payment-proxy-checkout';

export class StripeCheckoutPaymentProvider implements PaymentProvider {

    constructor(
        private translate: TranslateService,
        private parameters: { [key: string]: any },
        private reservation: ReservationInfo,
        private purchaseContext: PurchaseContext) {
    }

    get paymentMethodDeferred(): boolean {
        return false;
    }

    pay(): Observable<PaymentResult> {
        const obs = new Observable<PaymentResult>(subscriber => {
            this.loadScript(subscriber);
        });
        return obs;
    }

    loadScript(subscriber: Subscriber<PaymentResult>) {
        if (!document.getElementById(STRIPE_CHECKOUT_ID_SCRIPT)) {
            const scriptElem = document.createElement('script');
            scriptElem.id = STRIPE_CHECKOUT_ID_SCRIPT;
            scriptElem.src = 'https://checkout.stripe.com/checkout.js';
            scriptElem.async = true;
            scriptElem.defer = true;
            scriptElem.addEventListener('load', () => {
                this.configureAndOpen(subscriber);
            });
            document.body.appendChild(scriptElem);
        } else if (!window['StripeCheckout']) {
            document.getElementById(STRIPE_CHECKOUT_ID_SCRIPT).addEventListener('load', () => {
                this.configureAndOpen(subscriber);
            });
        } else {
            this.configureAndOpen(subscriber);
        }
    }

    configureAndOpen(subscriber: Subscriber<PaymentResult>) {
        let tokenSubmitted = false;
        const stripeHandler = StripeCheckout.configure({
            key: this.parameters['stripe_p_key'],
            locale: this.translate.currentLang,
            token: (token) => {
                tokenSubmitted = true;
                subscriber.next(new PaymentResult(true, token.id));
            },
            closed: () => {
                if (!tokenSubmitted) {
                    subscriber.next(new PaymentResult(false, null));
                }
            }
        });
        stripeHandler.open({
            name: `${this.reservation.firstName} ${this.reservation.lastName}`,
            description: this.reservation.orderSummary.descriptionForPayment,
            zipCode: false,
            allowRememberMe: false,
            amount: this.reservation.orderSummary.priceInCents,
            currency: this.purchaseContext.currency,
            email: this.reservation.email
        });
    }

    statusNotifications(): Observable<PaymentStatusNotification> {
        return EMPTY;
    }
}

export class StripePaymentV3 implements PaymentProvider {

    private notificationSubject = new Subject<PaymentStatusNotification>();

    constructor(
        private reservationService: ReservationService,
        private reservation: ReservationInfo,
        private stripeHandler: any,
        private card: any
    ) {
    }

    get paymentMethodDeferred(): boolean {
        return false;
    }

    pay(): Observable<PaymentResult> {

        const obs = new Observable<PaymentResult>(subscriber => {

            this.reservationService.initPayment(this.reservation.id).subscribe(res => {

                if (res.reservationStatusChanged || res.clientSecret == null) {
                    subscriber.next(new PaymentResult(false, null, null, res.reservationStatusChanged));
                    return;
                }

                const clientSecret = res.clientSecret;
                let billingAddress = null;
                if (this.reservation.billingDetails.addressLine1 != null) {
                    billingAddress = {
                        line1: this.reservation.billingDetails.addressLine1,
                        postal_code: this.reservation.billingDetails.zip,
                        country: StripePaymentV3.toCountryISOCode(this.reservation.billingDetails.country).toLowerCase()
                   };
                }
                const paymentData = {
                    payment_method_data: {
                        billing_details: {
                            name: `${this.reservation.firstName} ${this.reservation.lastName}`,
                            email: this.reservation.email,
                            address: billingAddress
                        }
                    }
                };

                this.stripeHandler.handleCardPayment(clientSecret, this.card, paymentData).then(cardPaymentResult => {
                    let retryCount = 0;
                    let handleCheck: number;
                    const checkIfPaid = () => {
                        console.log('checking reservation status...');
                        retryCount++;
                        if (retryCount % 10 === 0) {
                            this.notificationSubject.next(new PaymentStatusNotification(true, retryCount > 120));
                        }
                        this.reservationService.getPaymentStatus(this.reservation.id).subscribe(status => {
                            if (cardPaymentResult.error || status.success) {
                                window.clearInterval(handleCheck);
                            }
                            if (status.success) {
                                subscriber.next(new PaymentResult(true, status.gatewayIdOrNull));
                            } else if (cardPaymentResult.error) {
                                subscriber.error(new PaymentResult(false, null, cardPaymentResult.error.message));
                            } else if (status.failure) {
                                subscriber.error(new PaymentResult(false, null));
                            }
                        }, err => console.log('got error while calling getStatus(). Retrying in 1s', err));
                    };
                    handleCheck = window.setInterval(checkIfPaid, 1000);
                }, err => {
                    subscriber.error(err);
                });
            }, err => {
                subscriber.error(err);
            });
        });
        return obs;
    }

    statusNotifications(): Observable<PaymentStatusNotification> {
        return this.notificationSubject.asObservable();
    }

    private static toCountryISOCode(country: string): string {
      // The form contains EU-VAT prefix for Greece (EL).
      // Here we need the country ISO Code instead
      return country === 'EL' ? 'GR' : country;
    }
}
