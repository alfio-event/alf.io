import {Component, OnInit} from '@angular/core';
import {ReservationService} from '../../shared/reservation.service';
import {ReservationInfo} from '../../model/reservation-info';
import {ActivatedRoute} from '@angular/router';
import {zip} from 'rxjs';
import {TranslateService} from '@ngx-translate/core';
import {I18nService} from '../../shared/i18n.service';
import {AnalyticsService} from '../../shared/analytics.service';
import {PurchaseContext} from '../../model/purchase-context';
import {PurchaseContextService, PurchaseContextType} from '../../shared/purchase-context.service';
import {pollReservationStatus} from '../../shared/util';

@Component({
  selector: 'app-offline-payment',
  templateUrl: './offline-payment.component.html',
  styleUrls: ['./offline-payment.component.scss']
})
export class OfflinePaymentComponent implements OnInit {

  reservationInfo: ReservationInfo;
  purchaseContextType: PurchaseContextType;
  publicIdentifier: string;
  reservationId: string;
  paymentReason: string;

  purchaseContext: PurchaseContext;

  reservationFinalized: boolean;

  constructor(
    private route: ActivatedRoute,
    private reservationService: ReservationService,
    public translate: TranslateService,
    private i18nService: I18nService,
    private analytics: AnalyticsService,
    private purchaseContextService: PurchaseContextService) { }

  public ngOnInit(): void {

    zip(this.route.data, this.route.params).subscribe(([data, params]) => {
      this.purchaseContextType = data.type;
      this.publicIdentifier = params[data.publicIdentifierParameter];
      this.reservationId = params['reservationId'];
      zip(
        this.purchaseContextService.getContext(this.purchaseContextType, this.publicIdentifier),
        this.reservationService.getReservationInfo(this.reservationId)
      ).subscribe(([ev, reservationInfo]) => {
        this.purchaseContext = ev;
        this.reservationInfo = reservationInfo;

        this.paymentReason = `<mark>${this.reservationInfo.shortId}</mark>`;

        this.i18nService.setPageTitle('reservation-page-waiting.header.title', ev);
        this.analytics.pageView(ev.analyticsConfiguration);
        if (this.reservationInfo.status === 'OFFLINE_FINALIZING') {
          this.reservationFinalized = false;
          pollReservationStatus(this.reservationId, this.reservationService, res => {
            if (res.status === 'DEFERRED_OFFLINE_PAYMENT') {
              // redirect to deferred payment. Reload the page
              location.reload();
            }
            this.reservationInfo = res;
            this.reservationFinalized = true;
          });
        } else {
          this.reservationFinalized = true;
        }
      });
    });
  }

  get invoiceAvailable(): boolean {
    return this.reservationFinalized
      && this.purchaseContext.invoicingConfiguration.userCanDownloadReceiptOrInvoice
      && this.reservationInfo.invoiceNumber !== null;
  }

}
