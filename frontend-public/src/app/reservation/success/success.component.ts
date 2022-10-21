import { Component, OnInit } from '@angular/core';
import { ReservationService } from '../../shared/reservation.service';
import { ActivatedRoute, Router } from '@angular/router';
import { Event } from 'src/app/model/event';
import { EventService } from 'src/app/shared/event.service';
import { TicketService } from 'src/app/shared/ticket.service';
import { Ticket } from 'src/app/model/ticket';
import { ReservationInfo, TicketsByTicketCategory } from 'src/app/model/reservation-info';
import { I18nService } from 'src/app/shared/i18n.service';
import { AnalyticsService } from 'src/app/shared/analytics.service';
import { handleServerSideValidationError } from 'src/app/shared/validation-helper';
import { UntypedFormGroup } from '@angular/forms';
import {TranslateService} from '@ngx-translate/core';

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

  constructor(
    private route: ActivatedRoute,
    private reservationService: ReservationService,
    private eventService: EventService,
    private ticketService: TicketService,
    private i18nService: I18nService,
    private analytics: AnalyticsService,
    private router: Router,
    private translateService: TranslateService) { }

  public ngOnInit(): void {
    this.route.params.subscribe(params => {
      this.eventShortName = params['eventShortName'];
      this.reservationId = params['reservationId'];
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
      this.reservationInfo = res;
      //
      this.ticketsAllAssigned = true;
      this.unlockedTicketCount = 0;
      //
      res.ticketsByCategory.forEach((tc) => {
        tc.tickets.forEach((ticket: Ticket) => {
          this.buildFormControl(ticket);
          if (!ticket.locked) {
            this.unlockedTicketCount += 1;
          }
          this.ticketsAllAssigned = this.ticketsAllAssigned && ticket.assigned;
        });
      });
    });
  }

  private buildFormControl(ticket: Ticket): void {
    this.ticketsFormControl[ticket.uuid] = this.ticketService.buildFormGroupForTicket(ticket);
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
    return this.event.invoicingConfiguration.userCanDownloadReceiptOrInvoice
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

}
