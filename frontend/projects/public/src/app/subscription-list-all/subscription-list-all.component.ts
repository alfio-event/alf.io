import { Component, type OnInit } from "@angular/core";
import type { ActivatedRoute, Params, Router } from "@angular/router";
import type { TranslateService } from "@ngx-translate/core";
import { of, zip } from "rxjs";
import { mergeMap } from "rxjs/operators";
import type { Language, TermsPrivacyLinksContainer } from "../model/event";
import { globalTermsPrivacyLinks } from "../model/info";
import { filterAvailableLanguages } from "../model/purchase-context";
import { SearchParams } from "../model/search-params";
import type { BasicSubscriptionInfo } from "../model/subscription";
import type { AnalyticsService } from "../shared/analytics.service";
import type { I18nService } from "../shared/i18n.service";
import type { InfoService } from "../shared/info.service";
import type { SubscriptionService } from "../shared/subscription.service";

@Component({
  selector: "app-subscription-list-all",
  templateUrl: "./subscription-list-all.component.html",
  styleUrls: ["./subscription-list-all.component.scss"],
})
export class SubscriptionListAllComponent implements OnInit {
  subscriptions: BasicSubscriptionInfo[];
  languages: Language[];
  linksContainer: TermsPrivacyLinksContainer;
  queryParams: Params;

  constructor(
    private subscriptionService: SubscriptionService,
    private i18nService: I18nService,
    private router: Router,
    public translate: TranslateService,
    private info: InfoService,
    private analytics: AnalyticsService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    zip(this.route.queryParams, this.route.params)
      .pipe(
        mergeMap(([params, pathParams]) => {
          const searchParams = SearchParams.fromQueryAndPathParams(
            params,
            pathParams,
          );
          return zip(
            this.subscriptionService.getSubscriptions(searchParams),
            this.info.getInfo(),
            of(searchParams),
            this.i18nService.getAvailableLanguages(),
          );
        }),
      )
      .subscribe(([res, info, searchParams, activeLanguages]) => {
        this.queryParams = searchParams.toParams();
        if (res.length === 1) {
          this.router.navigate(["/subscription", res[0].id], {
            replaceUrl: true,
            queryParams: this.queryParams,
          });
        } else {
          this.subscriptions = res;
          this.analytics.pageView(info.analyticsConfiguration);
          this.linksContainer = globalTermsPrivacyLinks(info);
          this.languages = filterAvailableLanguages(activeLanguages, res);
          this.i18nService.setPageTitle("subscription.header.title", null);
        }
      });
  }
}
