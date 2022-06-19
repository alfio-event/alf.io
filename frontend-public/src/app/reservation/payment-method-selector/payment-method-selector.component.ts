import { Component, Input, OnInit, Output, EventEmitter } from '@angular/core';
import { PaymentMethod, PaymentProxyWithParameters, PaymentMethodDetails, paymentMethodDetails, PaymentProxy } from 'src/app/model/event';
import { PaymentProvider } from 'src/app/payment/payment-provider';
import { FormGroup } from '@angular/forms';
import { ReservationInfo } from 'src/app/model/reservation-info';
import { PurchaseContext } from 'src/app/model/purchase-context';

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
    overviewForm: FormGroup;
    @Output()
    selectedPaymentProvider = new EventEmitter<PaymentProvider>();

    ngOnInit(): void {
        this.overviewForm.get('selectedPaymentMethod').valueChanges.subscribe(v => {
            // payment provider has changed, so we must reset the value hold by the container.
            this.registerCurrentPaymentProvider(null);
            const selectedMethod = this.reservationInfo.activePaymentMethods[v as PaymentMethod];
            this.overviewForm.get('paymentProxy').setValue(selectedMethod.paymentProxy);
        });
    }

    get activePaymentMethods(): {[key in PaymentMethod]?: PaymentProxyWithParameters} {
        return this.reservationInfo.activePaymentMethods;
    }

    get sortedAvailablePaymentMethods(): PaymentMethod[] {
        const activeKeys = Object.keys(this.activePaymentMethods);
        return Object.keys(paymentMethodDetails)
            .filter(pd => activeKeys.includes(pd))
            .map(pd => pd as PaymentMethod);
    }

    get activePaymentsCount(): number {
        return Object.keys(this.activePaymentMethods).length;
    }

    get verticalLayout(): boolean {
        return this.activePaymentsCount > 3;
    }

    get selectedPaymentMethod(): PaymentMethod {
        return this.overviewForm.get('selectedPaymentMethod').value as PaymentMethod;
    }

    get selectedPaymentProxy(): PaymentProxy {
        return this.overviewForm.get('paymentProxy').value as PaymentProxy;
    }

    getPaymentMethodDetails(pm: PaymentMethod): PaymentMethodDetails  {
        return paymentMethodDetails[pm];
    }

    registerCurrentPaymentProvider(paymentProvider: PaymentProvider) {
        this.selectedPaymentProvider.emit(paymentProvider);
    }

}
