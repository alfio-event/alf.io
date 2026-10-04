import {Component, EventEmitter, Input, Output} from '@angular/core';
import {ReservationInfo, SummaryRow} from '../../model/reservation-info';
import {PurchaseContext} from '../../model/purchase-context';
import {PurchaseContextType} from '../../shared/purchase-context.service';
import {TranslateService} from '@ngx-translate/core';

@Component({
  selector: 'app-summary-table',
  templateUrl: './summary-table.component.html',
  styleUrls: ['./summary-table.component.scss']
})
export class SummaryTableComponent {

  @Input()
  reservationInfo: ReservationInfo;

  @Input()
  purchaseContext: PurchaseContext;

  @Input()
  displayRemoveSubscription: boolean;

  @Input()
  purchaseContextType: PurchaseContextType;

  @Output()
  removeSubscription: EventEmitter<SummaryRow> = new EventEmitter<SummaryRow>();

  constructor(private translateService: TranslateService) {
  }

  get currentLang(): string {
    return this.translateService.currentLang;
  }

  get isSubscriptionPurchaseContext(): boolean {
    return this.purchaseContextType === 'subscription';
  }

  get displaySplitPaymentNote(): boolean {
    return !this.reservationInfo.orderSummary.free
      && this.reservationInfo.billingDetails.invoicingAdditionalInfo?.italianEInvoicing?.splitPayment;
  }

}
