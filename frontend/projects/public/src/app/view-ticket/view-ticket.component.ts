import { DatePipe } from "@angular/common";
import { HttpErrorResponse } from "@angular/common/http";
import { Component, type OnInit } from "@angular/core";
import type { ActivatedRoute, Router } from "@angular/router";
import type { TranslateService } from "@ngx-translate/core";
import { zip } from "rxjs";
import type { Event } from "../model/event";
import type { WalletConfiguration } from "../model/info";
import type { AdditionalField } from "../model/ticket";
import type { TicketInfo } from "../model/ticket-info";
import type { AnalyticsService } from "../shared/analytics.service";
import type { EventService } from "../shared/event.service";
import type { I18nService } from "../shared/i18n.service";
import type { InfoService } from "../shared/info.service";
import type { TicketService } from "../shared/ticket.service";
import {
  type GroupedAdditionalServiceWithData,
  groupAdditionalData,
} from "../shared/util";

@Component({
  selector: "app-view-ticket",
  templateUrl: "./view-ticket.component.html",
  styleUrls: ["./view-ticket.component.scss"],
  providers: [DatePipe],
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
    private dateFormat: DatePipe,
  ) {}

  public ngOnInit(): void {
    this.route.params.subscribe((params) => {
      this.ticketIdentifier = params["ticketId"];

      const eventShortName = params["eventShortName"];

      zip(
        this.eventService.getEvent(eventShortName),
        this.ticketService.getTicketInfo(eventShortName, this.ticketIdentifier),
        this.infoService.getInfo(),
      ).subscribe(
        ([event, ticketInfo, generalInfo]) => {
          this.event = event;
          this.ticketInfo = ticketInfo;
          this.i18nService.setPageTitle("show-ticket.header.title", event);
          this.analytics.pageView(event.analyticsConfiguration);
          this.walletConfiguration = generalInfo.walletConfiguration;
          this.groupedAdditionalServices = groupAdditionalData(
            ticketInfo.additionalServiceWithData,
          );
        },
        (e) => {
          if (e instanceof HttpErrorResponse && e.status === 404) {
            this.router.navigate([""]);
          }
        },
      );
    });
  }

  get walletIntegrationEnabled(): boolean {
    return (
      this.walletConfiguration != null &&
      (this.walletConfiguration.gWalletEnabled ||
        this.walletConfiguration.passEnabled)
    );
  }

  get gWalletEnabled(): boolean {
    return (
      this.walletConfiguration != null &&
      this.walletConfiguration.gWalletEnabled
    );
  }

  get passEnabled(): boolean {
    return (
      this.walletConfiguration != null && this.walletConfiguration.passEnabled
    );
  }

  getValue(field: AdditionalField): string {
    if (field.type === "input:dateOfBirth") {
      return this.dateFormat.transform(
        field.value,
        this.translate.instant("common.date-format"),
      );
    }
    return (
      field.description[this.translate.currentLang]
        ?.restrictedValuesDescription[field.value] || field.value
    );
  }
}
