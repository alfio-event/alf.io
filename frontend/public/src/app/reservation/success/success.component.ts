import {Component, OnInit} from '@angular/core';
import {ReservationService} from '../../shared/reservation.service';
import {ActivatedRoute, Router} from '@angular/router';
import {Event} from '../../model/event';
import {EventService} from '../../shared/event.service';
import {TicketService} from '../../shared/ticket.service';
import {Ticket} from '../../model/ticket';
import {AdditionalServiceWithData, ReservationInfo, TicketsByTicketCategory} from '../../model/reservation-info';
import {I18nService} from '../../shared/i18n.service';
import {AnalyticsService} from '../../shared/analytics.service';
import {handleServerSideValidationError} from '../../shared/validation-helper';
import {UntypedFormArray, UntypedFormGroup} from '@angular/forms';
import {TranslateService} from '@ngx-translate/core';
import {InfoService} from '../../shared/info.service';
import {first} from 'rxjs/operators';
import {WalletConfiguration} from '../../model/info';
import {ReservationStatusChanged} from '../../model/embedding-configuration';
import {
  embedded,
  groupAdditionalData,
  GroupedAdditionalServiceWithData,
  pollReservationStatus
} from '../../shared/util';

@Component({
  selector: 'app-success',
  templateUrl: './success.component.html',
  styleUrls: ['./success.component.scss']
})
export class SuccessComponent implements OnInit {

  reservationInfo: ReservationInfo;
  eventShortName: string;
  reservationId: string;

  event: Event;

  reservationMailSent = false;
  sendEmailForTicketStatus: {[key: string]: boolean} = {};
  ticketsFormControl: {[key: string]: UntypedFormGroup} = {};
  ticketsFormShow: {[key: string]: boolean} = {};
  ticketsReleaseShow: {[key: string]: boolean} = {};

  unlockedTicketCount = 0;
  ticketsAllAssigned = true;
  reservationFinalized = true;
  invoiceReceiptReady = true;
  private walletConfiguration: WalletConfiguration;

  private additionalServicesWithData: {[uuid: string]: AdditionalServiceWithData[]} = {};

  constructor(
    private route: ActivatedRoute,
    private reservationService: ReservationService,
    private eventService: EventService,
    private ticketService: TicketService,
    private i18nService: I18nService,
    private analytics: AnalyticsService,
    private router: Router,
    private translateService: TranslateService,
    private infoService: InfoService) { }

  public ngOnInit(): void {
    this.route.params.subscribe(params => {
      this.eventShortName = params['eventShortName'];
      this.reservationId = params['reservationId'];
      this.infoService.getInfo().pipe(first())
        .subscribe(info => this.walletConfiguration = info.walletConfiguration);
      this.eventService.getEvent(this.eventShortName).subscribe(ev => {
        this.event = ev;
        this.i18nService.setPageTitle('reservation-page-complete.header.title', ev);
        this.analytics.pageView(ev.analyticsConfiguration);
      });
      this.loadReservation();
    });
  }

  private loadReservation(): void {
    this.reservationService.getReservationInfo(this.reservationId).subscribe(res => {
      this.processReservationInfo(res);
      if (!this.reservationFinalized) {
        pollReservationStatus(this.reservationId, this.reservationService, res1 => this.processReservationInfo(res1));
      }
    });
  }

  private processReservationInfo(res: ReservationInfo) {
    if (embedded && this.event.embeddingConfiguration.enabled) {
      window.parent.postMessage(
        new ReservationStatusChanged(res.status, this.reservationId),
        this.event.embeddingConfiguration.notificationOrigin
      );
    }
    this.reservationInfo = res;
    //
    this.reservationFinalized = res.status !== 'FINALIZING';
    this.ticketsAllAssigned = res.status !== 'FINALIZING';
    this.invoiceReceiptReady = res.metadata.readyForConfirmation;
    this.unlockedTicketCount = 0;
    //
    const additionalServices = res.additionalServiceWithData ?? [];
    this.additionalServicesWithData = {};
    additionalServices.forEach(asd => {
      if (this.additionalServicesWithData[asd.ticketUUID] != null) {
        this.additionalServicesWithData[asd.ticketUUID].push(asd);
      } else {
        this.additionalServicesWithData[asd.ticketUUID] = [asd];
      }
    });
    res.ticketsByCategory.forEach((tc) => {
      tc.tickets.forEach((ticket: Ticket) => {
        this.buildFormControl(ticket);
        if (!ticket.locked) {
          this.unlockedTicketCount += 1;
        }
        this.ticketsAllAssigned = this.ticketsAllAssigned && ticket.assigned;
      });
    });
  }

  private buildFormControl(ticket: Ticket): void {
    this.ticketsFormControl[ticket.uuid] = this.ticketService.buildFormGroupForTicket(ticket, null, this.additionalServicesWithData[ticket.uuid]);
  }

  sendEmailForTicket(ticketIdentifier: string): void {
    this.ticketService.sendTicketByEmail(this.eventShortName, ticketIdentifier).subscribe(res => {
      if (res) {
        this.sendEmailForTicketStatus[ticketIdentifier] = true;
      }
    });
  }

  reSendReservationEmail(): void {
    this.reservationService.reSendReservationEmail('event', this.eventShortName, this.reservationId, this.i18nService.getCurrentLang()).subscribe(res => {
      this.reservationMailSent = res;
    });
  }

  updateTicket(uuid: string): void {
    const ticketValue = this.ticketsFormControl[uuid].value;
    this.ticketService.updateTicket(this.event.shortName, uuid, ticketValue).subscribe(res => {
      if (res.success) {
        this.loadReservation();
        this.hideTicketForm(uuid);
      }
    }, (err) => {
      handleServerSideValidationError(err, this.ticketsFormControl[uuid]);
    });
  }

  releaseTicket(ticket: Ticket) {
    this.ticketService.openReleaseTicket(ticket, this.event.shortName)
      .subscribe(released => {
        if (released) {
            const singleTicket = this.reservationInfo.ticketsByCategory.map((c) => c.tickets.length).reduce((c1, c2) => c1 + c2) === 1;
            if (singleTicket) {
              this.router.navigate(['event', this.event.shortName], {replaceUrl: true});
            } else {
              this.loadReservation();
            }
          }
        });
  }

  get ticketFormVisible(): boolean {
    return Object.keys(this.ticketsFormShow).length > 0;
  }

  hideTicketForm(uuid: string): void {
    delete this.ticketsFormShow[uuid];
  }

  get downloadBillingDocumentVisible(): boolean {
    return this.invoiceReceiptReady
        && this.event.invoicingConfiguration.userCanDownloadReceiptOrInvoice
        && this.reservationInfo.paid
        && this.reservationInfo.invoiceOrReceiptDocumentPresent;
  }

  public isOnlineTicket(category: TicketsByTicketCategory): boolean {
    return this.event.format === 'ONLINE'
      || (this.event.format === 'HYBRID' && category.ticketAccessType === 'ONLINE');
  }

  get purchaseContextTitle(): string {
    return this.event.title[this.translateService.currentLang];
  }

  get walletIntegrationEnabled(): boolean {
    return this.walletConfiguration != null &&
      (this.walletConfiguration.gWalletEnabled || this.walletConfiguration.passEnabled);
  }

  downloadTicket(ticket: Ticket): void {
    this.ticketService.openDownloadTicket(ticket, this.eventShortName, this.walletConfiguration)
      .subscribe(() => {});
  }

  get showReservationButtons(): boolean {
    return this.reservationFinalized
      && (!embedded || !this.event.embeddingConfiguration.enabled)
      && !this.reservationInfo.metadata.hideConfirmationButtons;
  }

  getAdditionalDataForm(ticket: Ticket): UntypedFormArray | null {
    const linksGroup = <UntypedFormGroup>(this.ticketsFormControl[ticket.uuid]).get('additionalServices');
    return linksGroup.contains(ticket.uuid) ? <UntypedFormArray>linksGroup.get(ticket.uuid) : null;
  }

  getAdditionalData(ticket: Ticket): AdditionalServiceWithData[] {
    return this.additionalServicesWithData[ticket.uuid] ?? [];
  }

  hasAdditionalData(ticket: Ticket): boolean {
    return this.getAdditionalData(ticket).length > 0;
  }

  getGroupedAdditionalData(ticket: Ticket): GroupedAdditionalServiceWithData[] {
    return groupAdditionalData(this.additionalServicesWithData[ticket.uuid] ?? []);
  }
}

