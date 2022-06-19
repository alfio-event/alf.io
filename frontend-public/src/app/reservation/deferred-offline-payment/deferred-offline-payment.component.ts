import { Component, OnInit } from '@angular/core';
import { ReservationInfo } from 'src/app/model/reservation-info';
import { ActivatedRoute } from '@angular/router';
import { ReservationService } from 'src/app/shared/reservation.service';
import { TranslateService } from '@ngx-translate/core';
import { I18nService } from 'src/app/shared/i18n.service';
import { AnalyticsService } from 'src/app/shared/analytics.service';
import { zip } from 'rxjs';
import { PurchaseContextService, PurchaseContextType } from 'src/app/shared/purchase-context.service';
import { PurchaseContext } from 'src/app/model/purchase-context';

@Component({
    selector: 'app-deferred-offline-payment',
    templateUrl: './deferred-offline-payment.component.html'
})
export class DeferredOfflinePaymentComponent implements OnInit {

    reservationInfo: ReservationInfo;
    purchaseContextType: PurchaseContextType;
    publicIdentifier: string;
    reservationId: string;
    purchaseContext: PurchaseContext;

    constructor(
        private route: ActivatedRoute,
        private purchaseContextService: PurchaseContextService,
        private reservationService: ReservationService,
        public translate: TranslateService,
        private i18nService: I18nService,
        private analytics: AnalyticsService) { }

    ngOnInit(): void {
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
          this.i18nService.setPageTitle('reservation-page-waiting.header.title', ev);
          this.analytics.pageView(ev.analyticsConfiguration);
        });
      });
    }
}
