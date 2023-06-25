import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {zip} from 'rxjs';
import {PurchaseContext} from '../../model/purchase-context';
import {ReservationInfo, ReservationSubscriptionInfo} from '../../model/reservation-info';
import {AnalyticsService} from '../../shared/analytics.service';
import {I18nService} from '../../shared/i18n.service';
import {PurchaseContextService, PurchaseContextType} from '../../shared/purchase-context.service';
import {ReservationService} from '../../shared/reservation.service';
import {TranslateService} from '@ngx-translate/core';
import {SubscriptionInfo} from '../../model/subscription';
import {FeedbackService} from '../../shared/feedback/feedback.service';
import {EventService} from '../../shared/event.service';
import {BasicEventInfo} from '../../model/basic-event-info';
import {SearchParams} from '../../model/search-params';
import {ReservationStatusChanged} from '../../model/embedding-configuration';
import {embedded, pollReservationStatus} from '../../shared/util';

@Component({
  selector: 'app-success-subscription',
  templateUrl: './success-subscription.component.html',
  styleUrls: ['./success-subscription.component.scss']
})
export class SuccessSubscriptionComponent implements OnInit {

  private publicIdentifier: string;
  reservationId: string;
  private purchaseContextType: PurchaseContextType;
  purchaseContext: PurchaseContext;
  reservationInfo: ReservationInfo;
  compatibleEvents: Array<BasicEventInfo> = [];

  reservationFinalized = true;
  invoiceReceiptReady = true;

  constructor(
    private route: ActivatedRoute,
    private purchaseContextService: PurchaseContextService,
    private i18nService: I18nService,
    private analytics: AnalyticsService,
    private reservationService: ReservationService,
    private translateService: TranslateService,
    private feedbackService: FeedbackService,
    private eventService: EventService
    ) { }

  ngOnInit(): void {
    zip(this.route.data, this.route.params).subscribe(([data, params]) => {
      this.publicIdentifier = params[data.publicIdentifierParameter];
      this.reservationId = params['reservationId'];
      this.purchaseContextType = data.type;

      this.purchaseContextService.getContext(this.purchaseContextType, this.publicIdentifier).subscribe(ev => {
        this.purchaseContext = ev;
        this.i18nService.setPageTitle('reservation-page.header.title', ev);
        this.loadReservation();
        this.analytics.pageView(ev.analyticsConfiguration);
      });
    });
  }

  private loadReservation() {
    this.reservationService.getReservationInfo(this.reservationId).subscribe(resInfo => {
      this.processReservationInfo(resInfo);
      if (!this.reservationFinalized) {
        pollReservationStatus(this.reservationId, this.reservationService, res1 => this.processReservationInfo(res1));
      }
    });
  }

  private processReservationInfo(resInfo: ReservationInfo) {
    const embeddingEnabled = this.purchaseContext.embeddingConfiguration.enabled;
    if (embedded && embeddingEnabled) {
      window.parent.postMessage(
        new ReservationStatusChanged(resInfo.status, this.reservationId),
        this.purchaseContext.embeddingConfiguration.notificationOrigin
      );
    }
    this.reservationInfo = resInfo;
    this.reservationFinalized = resInfo.status !== 'FINALIZING';
    this.invoiceReceiptReady = resInfo.metadata.readyForConfirmation;
    if (this.reservationFinalized && (!embedded || !embeddingEnabled)) {
      this.loadCompatibleEvents(this.subscriptionInfo.id);
    }
  }

  private loadCompatibleEvents(subscriptionId: string): void {
    this.eventService.getEvents(new SearchParams(subscriptionId, null, null, null)).subscribe(events => {
      this.compatibleEvents = events;
    });
  }

  get purchaseContextTitle(): string {
    return this.purchaseContext.title[this.translateService.currentLang];
  }

  get downloadBillingDocumentVisible(): boolean {
    return this.purchaseContext.invoicingConfiguration.userCanDownloadReceiptOrInvoice
      && this.reservationInfo.paid
      && this.reservationInfo.invoiceOrReceiptDocumentPresent;
  }

  get subscriptionInfo(): ReservationSubscriptionInfo {
    return this.reservationInfo.subscriptionInfos[0];
  }

  get displayPin(): boolean {
    if (this.subscriptionInfo.configuration != null) {
      return this.reservationFinalized && this.subscriptionInfo.configuration.displayPin;
    }
    return this.reservationFinalized; // by default, we display PIN
  }

  public reSendReservationEmail(): void {
    this.reservationService.reSendReservationEmail('subscription', this.publicIdentifier, this.reservationId, this.i18nService.getCurrentLang()).subscribe(() => {
      this.feedbackService.showSuccess('email.confirmation-email-sent');
    });
  }

  get subscription(): SubscriptionInfo {
    return this.purchaseContext as SubscriptionInfo;
  }

  public copied(payload: string): void {
    this.feedbackService.showSuccess('reservation-page-complete.subscription.copy.success');
  }

  get showReservationButtons(): boolean {
    return this.reservationFinalized
      && (!embedded || !this.purchaseContext.embeddingConfiguration.enabled)
      && !this.reservationInfo.metadata.hideConfirmationButtons;
  }

}
