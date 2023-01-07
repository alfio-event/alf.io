import {Component, OnInit} from '@angular/core';
import {EventService} from '../shared/event.service';
import {ActivatedRoute, Router} from '@angular/router';
import {BasicEventInfo} from '../model/basic-event-info';
import {I18nService} from '../shared/i18n.service';
import {Language, TermsPrivacyLinksContainer} from '../model/event';
import {TranslateService} from '@ngx-translate/core';
import {AnalyticsService} from '../shared/analytics.service';
import {InfoService} from '../shared/info.service';
import {zip} from 'rxjs';
import {SubscriptionService} from '../shared/subscription.service';
import {BasicSubscriptionInfo} from '../model/subscription';
import {mergeMap} from 'rxjs/operators';
import {SearchParams} from '../model/search-params';
import {globalTermsPrivacyLinks} from '../model/info';
import {filterAvailableLanguages} from '../model/purchase-context';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {

  events: BasicEventInfo[];
  allEvents: BasicEventInfo[];
  subscriptions: BasicSubscriptionInfo[];
  allSubscriptions: BasicSubscriptionInfo[];
  languages: Language[];
  linksContainer: TermsPrivacyLinksContainer;
  private searchParams?: SearchParams;

  constructor(
    private eventService: EventService,
    private subscriptionService: SubscriptionService,
    private i18nService: I18nService,
    private router: Router,
    public translate: TranslateService,
    private info: InfoService,
    private analytics: AnalyticsService,
    private route: ActivatedRoute) { }

    public ngOnInit(): void {
      zip(this.route.params, this.route.queryParams).pipe(
        mergeMap(([pathParams, queryParams]) => {
          this.searchParams = SearchParams.fromQueryAndPathParams(queryParams, pathParams);
          return zip(
            this.eventService.getEvents(this.searchParams),
            this.subscriptionService.getSubscriptions(this.searchParams),
            this.info.getInfo(),
            this.i18nService.getAvailableLanguages());
        })
      ).subscribe(([res, subscriptions, info, activeLanguages]) => {
        if (res.length === 1 && subscriptions.length === 0) {
          this.router.navigate(['/event', res[0].shortName], {replaceUrl: true});
        } else {
          this.allEvents = res;
          this.events = res.slice(0, 4);
          this.allSubscriptions = subscriptions;
          this.subscriptions = subscriptions.slice(0, 4);
          this.analytics.pageView(info.analyticsConfiguration);
          this.linksContainer = globalTermsPrivacyLinks(info);
          const allPurchaseContexts = [...res, ...subscriptions];
          this.languages = filterAvailableLanguages(activeLanguages, allPurchaseContexts);
        }
      });

      this.i18nService.setPageTitle('event-list.header.title', null);
    }

    get containsEvents(): boolean {
      return this.events != null && this.events.length > 0;
    }

    get displayViewAllEventsButton() {
      return this.allEvents.length > 4;
    }

    get containsSubscriptions(): boolean {
      return this.subscriptions != null && this.subscriptions.length > 0;
    }

    get displayViewAllSubscriptionsButton() {
      return this.allSubscriptions.length > 4;
    }

    get allEventsPath(): Array<string> {
      if (this.searchParams?.organizerSlug != null) {
        return ['/o', this.searchParams.organizerSlug, 'events-all'];
      }
      return ['events-all'];
    }

    get allSubscriptionsPath(): Array<string> {
      if (this.searchParams?.organizerSlug != null) {
        return ['/o', this.searchParams.organizerSlug, 'subscriptions-all'];
      }
      return ['subscriptions-all'];
    }

}
