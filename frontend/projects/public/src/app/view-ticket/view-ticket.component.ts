import {Component, OnInit} from '@angular/core';
import {Event} from '../model/event';
import {ActivatedRoute, Router} from '@angular/router';
import {EventService} from '../shared/event.service';
import {TicketService} from '../shared/ticket.service';
import {TicketInfo} from '../model/ticket-info';
import {zip} from 'rxjs';
import {I18nService} from '../shared/i18n.service';
import {AnalyticsService} from '../shared/analytics.service';
import {HttpErrorResponse} from '@angular/common/http';
import {InfoService} from '../shared/info.service';
import {WalletConfiguration} from '../model/info';

@Component({
  selector: 'app-view-ticket',
  templateUrl: './view-ticket.component.html',
  styleUrls: ['./view-ticket.component.scss']
})
export class ViewTicketComponent implements OnInit {

  event: Event;
  ticketIdentifier: string;
  ticketInfo: TicketInfo;
  private walletConfiguration: WalletConfiguration;

  constructor(
    private ticketService: TicketService,
    private route: ActivatedRoute,
    private router: Router,
    private eventService: EventService,
    private i18nService: I18nService,
    private analytics: AnalyticsService,
    private infoService: InfoService) { }

  public ngOnInit(): void {
    this.route.params.subscribe(params => {
      this.ticketIdentifier = params['ticketId'];

      const eventShortName = params['eventShortName'];

      zip(this.eventService.getEvent(eventShortName), this.ticketService.getTicketInfo(eventShortName, this.ticketIdentifier), this.infoService.getInfo())
      .subscribe(([event, ticketInfo, generalInfo]) => {
        this.event = event;
        this.ticketInfo = ticketInfo;
        this.i18nService.setPageTitle('show-ticket.header.title', event);
        this.analytics.pageView(event.analyticsConfiguration);
        this.walletConfiguration = generalInfo.walletConfiguration;
      }, e => {
        if (e instanceof HttpErrorResponse && e.status === 404) {
          this.router.navigate(['']);
        }
      });
    });
  }

  get walletIntegrationEnabled(): boolean {
    return this.walletConfiguration != null &&
      (this.walletConfiguration.gWalletEnabled || this.walletConfiguration.passEnabled);
  }

  get gWalletEnabled(): boolean {
    return this.walletConfiguration != null && this.walletConfiguration.gWalletEnabled;
  }

  get passEnabled(): boolean {
    return this.walletConfiguration != null && this.walletConfiguration.passEnabled;
  }
}
