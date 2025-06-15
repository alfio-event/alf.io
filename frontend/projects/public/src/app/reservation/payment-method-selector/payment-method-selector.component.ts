import {Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {
  PaymentMethodDetails,
  PaymentProxy,
  staticPaymentMethodDetails,
  StaticPaymentMethodNames,
  PaymentProxyWithParameters,
  CustomOfflinePayment,
  type PaymentMethodId} from '../../model/event';
import {PaymentProvider} from '../../payment/payment-provider';
import {UntypedFormGroup} from '@angular/forms';
import {ReservationInfo} from '../../model/reservation-info';
import {PurchaseContext} from '../../model/purchase-context';
import {ReservationService} from '../../shared/reservation.service';
import {I18nService} from 'projects/public/src/app/shared/i18n.service';

@Component({
    selector: 'app-payment-method-selector',
    templateUrl: './payment-method-selector.component.html',
    styleUrls: ['./payment-method-selector.scss']
})
export class PaymentMethodSelectorComponent implements OnInit {

    @Input()
    purchaseContext: PurchaseContext;
    @Input()
    reservationInfo: ReservationInfo;
    @Input()
    overviewForm: UntypedFormGroup;
    @Output()
    selectedPaymentProvider = new EventEmitter<PaymentProvider>();

    customOfflinePaymentMethods: CustomOfflinePayment[] = [];

    constructor(private reservationService: ReservationService, private i8lnService: I18nService){}

    ngOnInit(): void {
        this.overviewForm.get('selectedPaymentMethod').valueChanges.subscribe(v => {
            // payment provider has changed, so we must reset the value hold by the container.
            this.registerCurrentPaymentProvider(null);
            const selectedMethod = this.reservationInfo.activePaymentMethods[v] || null;
            if(selectedMethod) {
                this.overviewForm.get('paymentProxy')?.setValue(selectedMethod.paymentProxy);
            }
        });

        this.reservationService.getApplicableCustomPaymentMethodDetails(this.reservationInfo.id).subscribe(methods => {
            this.customOfflinePaymentMethods = methods;
        });
    }

    get activePaymentMethods(): {[key: PaymentMethodId]: PaymentProxyWithParameters} {
        return this.reservationInfo.activePaymentMethods;
    }

    get sortedAvailablePaymentMethodIDs(): PaymentMethodId[] {
        return Object.keys(this.activePaymentMethods).sort();
    }

    get activePaymentsCount(): number {
        return Object.keys(this.activePaymentMethods).length;
    }

    get verticalLayout(): boolean {
        return this.activePaymentsCount > 3;
    }

    get selectedPaymentMethod(): PaymentMethodId {
        return this.overviewForm.get('selectedPaymentMethod')?.value;
    }

    get selectedPaymentProxy(): PaymentProxy {
        return this.overviewForm.get('paymentProxy').value as PaymentProxy;
    }

    getPaymentMethodDetails(paymentMethodId: PaymentMethodId): PaymentMethodDetails  {
        const currentLang = this.i8lnService.getCurrentLang();

        if(Object.keys(staticPaymentMethodDetails).includes(paymentMethodId)) {
            const staticMethodId = paymentMethodId as StaticPaymentMethodNames;
            return staticPaymentMethodDetails[staticMethodId];
        }

        const maybeCustomMethod = this.customOfflinePaymentMethods.find(pm => pm.paymentMethodId === paymentMethodId);
        if(maybeCustomMethod) {
            const method = maybeCustomMethod!;
            method.paymentMethodName

            const localizationKeys = Object.keys(method.localizations);
            let translatedPaymentName = method.localizations["en"]?.paymentName || method.localizations[localizationKeys[0]].paymentName;
            if(Object.keys(method.localizations).includes(currentLang)) {
                translatedPaymentName = method.localizations[currentLang].paymentName;
            }

            return {
                labelKey: translatedPaymentName,
                icon: ['fas', 'exchange-alt']
            };
        }

        return {
            labelKey: `UNKNOWN PAYMENT ${paymentMethodId}`,
            icon: ['fas', 'exchange-alt']
        };
    }

    registerCurrentPaymentProvider(paymentProvider: PaymentProvider) {
        this.selectedPaymentProvider.emit(paymentProvider);
    }

}
