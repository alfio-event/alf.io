import {Component, OnInit} from '@angular/core';
import {TicketService} from '../shared/ticket.service';
import {ActivatedRoute, Router} from '@angular/router';
import {EventService} from '../shared/event.service';
import {I18nService} from '../shared/i18n.service';
import {AnalyticsService} from '../shared/analytics.service';
import {zip} from 'rxjs';
import {HttpErrorResponse} from '@angular/common/http';
import {mergeMap} from 'rxjs/operators';
import {Event} from '../model/event';
import {Ticket} from '../model/ticket';
import {DateValidity} from '../model/date-validity';
import {TranslateService} from '@ngx-translate/core';

@Component({
  selector: 'app-waiting-room',
  templateUrl: './waiting-room.component.html'
})
export class WaitingRoomComponent implements OnInit {

  event: Event;
  ticketIdentifier: string;
  ticket: Ticket;
  checkInCode: string;
  categoryName: string;
  checkInInfo: DateValidity;

  constructor(
    private ticketService: TicketService,
    private route: ActivatedRoute,
    private router: Router,
    private eventService: EventService,
    private i18nService: I18nService,
    private analytics: AnalyticsService,
    private translate: TranslateService) { }

  ngOnInit(): void {
    this.route.params.pipe(mergeMap(params => {
      this.ticketIdentifier = params['ticketId'];
      const eventShortName = params['eventShortName'];
      this.checkInCode = params['ticketCodeHash'];
      return zip(
        this.eventService.getEvent(eventShortName),
        this.ticketService.getTicket(eventShortName, this.ticketIdentifier),
        this.ticketService.getOnlineCheckInInfo(eventShortName, this.ticketIdentifier, this.checkInCode)
      );
    })).subscribe(([event, ticketInfo, checkInInfo]) => {
      this.event = event;
      this.ticket = ticketInfo.tickets[0];
      this.categoryName = ticketInfo.name;
      this.i18nService.setPageTitle('show-ticket.header.title', event);
      this.analytics.pageView(event.analyticsConfiguration);
      this.checkInInfo = checkInInfo;
    }, e => {
      if (e instanceof HttpErrorResponse && e.status === 404) {
        this.router.navigate(['']);
      }
    });
  }

  get checkInDate(): string {
    return this.checkInInfo.formattedBeginDate[this.translate.currentLang];
  }

  get checkInTime(): string {
    return this.checkInInfo.formattedBeginTime[this.translate.currentLang];
  }

  get eventRunning(): boolean {
    const now = new Date().getTime();
    const dates = this.checkInInfo.datesWithOffset;
    return now > dates.startDateTime && now < dates.endDateTime;
  }

  get eventStartsInTheFuture(): boolean {
    return new Date().getTime() < this.checkInInfo.datesWithOffset.startDateTime;
  }

  get eventEnded(): boolean {
    return new Date().getTime() > this.checkInInfo.datesWithOffset.endDateTime;
  }
}
