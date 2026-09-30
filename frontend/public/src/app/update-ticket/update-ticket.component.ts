import {Component, OnInit} from '@angular/core';
import {Event} from '../model/event';
import {ActivatedRoute, Router} from '@angular/router';
import {EventService} from '../shared/event.service';
import {TicketService} from '../shared/ticket.service';
import {zip} from 'rxjs';
import {I18nService} from '../shared/i18n.service';
import {AnalyticsService} from '../shared/analytics.service';
import {HttpErrorResponse} from '@angular/common/http';
import {UntypedFormGroup} from '@angular/forms';
import {Ticket} from '../model/ticket';
import {handleServerSideValidationError} from '../shared/validation-helper';
import {TicketsByTicketCategory} from '../model/reservation-info';
import {TranslateService} from '@ngx-translate/core';
import {TicketAccessType} from '../model/ticket-category';

@Component({
  selector: 'app-update-ticket',
  templateUrl: './update-ticket.component.html',
  styleUrls: ['./update-ticket.scss']
})
export class UpdateTicketComponent implements OnInit {

  event: Event;
  ticketIdentifier: string;
  ticket: Ticket;
  formGroup: UntypedFormGroup;
  categoryName: string;
  emailSent: boolean;
  ticketFormVisible: boolean;
  ticketAccessType: TicketAccessType;

  constructor(
    private ticketService: TicketService,
    private route: ActivatedRoute,
    private router: Router,
    private eventService: EventService,
    private i18nService: I18nService,
    private analytics: AnalyticsService,
    private translate: TranslateService) { }

  public ngOnInit(): void {
    this.route.params.subscribe(params => {
      this.ticketIdentifier = params['ticketId'];

      const eventShortName = params['eventShortName'];

      zip(this.eventService.getEvent(eventShortName), this.ticketService.getTicket(eventShortName, this.ticketIdentifier))
      .subscribe(([event, ticketsByCategory]) => {
        this.event = event;
        this.i18nService.setPageTitle('show-ticket.header.title', event);
        this.handleTicketResponse(ticketsByCategory);
        this.analytics.pageView(event.analyticsConfiguration);
      }, e => {
        if (e instanceof HttpErrorResponse && e.status === 404) {
          this.router.navigate(['']);
        }
      });
    });
  }

  private handleTicketResponse(ticketsByCategory: TicketsByTicketCategory): void {
    this.ticketAccessType = ticketsByCategory.ticketAccessType;
    this.ticket = ticketsByCategory.tickets[0];
    this.formGroup = this.ticketService.buildFormGroupForTicket(this.ticket);
    this.categoryName = ticketsByCategory.name;
    this.ticketFormVisible = !this.ticket.assigned;
  }

  updateTicket(): void {
    this.ticketService.updateTicket(this.event.shortName, this.ticket.uuid, this.formGroup.value).subscribe(res => {
      if (res.success) {
        this.ticketService.getTicket(this.event.shortName, this.ticketIdentifier)
          .subscribe(t => this.handleTicketResponse(t));
      }
    }, (err) => {
      handleServerSideValidationError(err, this.formGroup);
    });
  }

  sendEmailForTicket(): void {
    this.ticketService.sendTicketByEmail(this.event.shortName, this.ticket.uuid).subscribe(res => {
      if (res) {
        this.emailSent = true;
      }
    });
  }

  releaseTicket() {
    this.ticketService.openReleaseTicket(this.ticket, this.event.shortName)
      .subscribe(released => {
        if (released) {
          this.router.navigate(['event', this.event.shortName], {replaceUrl: true});
        }
      });
  }

  get isOnlineTicket(): boolean {
    return this.event.format === 'ONLINE'
      || (this.event.format === 'HYBRID' && this.ticketAccessType === 'ONLINE');
  }

  get ticketOnlineCheckInDate(): string {
    return this.ticket.formattedOnlineCheckInDate[this.translate.currentLang];
  }

  get canUpdateTicket(): boolean {
    return !this.ticket.locked
      || this.ticket.ticketFieldConfigurationAfterStandard.length > 0
      || this.ticket.ticketFieldConfigurationBeforeStandard.length > 0;
  }

  get title(): string {
    return this.event.title[this.translate.currentLang];
  }
}
