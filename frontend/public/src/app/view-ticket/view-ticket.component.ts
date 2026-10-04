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
import {AdditionalField} from '../model/ticket';
import {TranslateService} from '@ngx-translate/core';
import {groupAdditionalData, GroupedAdditionalServiceWithData} from '../shared/util';
import {DatePipe} from '@angular/common';

@Component({
  selector: 'app-view-ticket',
  templateUrl: './view-ticket.component.html',
  styleUrls: ['./view-ticket.component.scss'],
  providers: [ DatePipe ]
})
export class ViewTicketComponent implements OnInit {

  event: Event;
  ticketIdentifier: string;
  ticketInfo: TicketInfo;
  groupedAdditionalServices: GroupedAdditionalServiceWithData[] = [];
  private walletConfiguration: WalletConfiguration;

  constructor(
    private ticketService: TicketService,
    private route: ActivatedRoute,
    private router: Router,
    private eventService: EventService,
    private i18nService: I18nService,
    private analytics: AnalyticsService,
    private infoService: InfoService,
    private translate: TranslateService,
    private dateFormat: DatePipe) { }

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
        this.groupedAdditionalServices = groupAdditionalData(ticketInfo.additionalServiceWithData);
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

  getValue(field: AdditionalField): string {
    if (field.type === 'input:dateOfBirth') {
      return this.dateFormat.transform(field.value, this.translate.instant('common.date-format'));
    }
    return field.description[this.translate.currentLang]?.restrictedValuesDescription[field.value] || field.value;
  }
}
