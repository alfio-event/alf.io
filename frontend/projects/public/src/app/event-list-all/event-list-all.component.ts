import {Component, OnInit} from '@angular/core';
import {BasicEventInfo} from '../model/basic-event-info';
import {Language, TermsPrivacyLinksContainer} from '../model/event';
import {EventService} from '../shared/event.service';
import {I18nService} from '../shared/i18n.service';
import {ActivatedRoute, Params, Router} from '@angular/router';
import {InfoService} from '../shared/info.service';
import {AnalyticsService} from '../shared/analytics.service';
import {TranslateService} from '@ngx-translate/core';
import {of, zip} from 'rxjs';
import {mergeMap} from 'rxjs/operators';
import {SearchParams} from '../model/search-params';
import {globalTermsPrivacyLinks} from '../model/info';
import {filterAvailableLanguages} from '../model/purchase-context';

@Component({
  selector: 'app-event-list-all',
  templateUrl: './event-list-all.component.html',
  styleUrls: ['./event-list-all.component.scss']
})
export class EventListAllComponent implements OnInit {

  events: BasicEventInfo[];
  languages: Language[];
  queryParams: Params;
  linksContainer: TermsPrivacyLinksContainer;

  constructor(
    private eventService: EventService,
    private i18nService: I18nService,
    private router: Router,
    public translate: TranslateService,
    private info: InfoService,
    private analytics: AnalyticsService,
    private route: ActivatedRoute) { }

    public ngOnInit(): void {

      zip(this.route.queryParams, this.route.params).pipe(
          mergeMap(([params, pathParams]) => {
            const searchParams = SearchParams.fromQueryAndPathParams(params, pathParams);
            return zip(this.eventService.getEvents(searchParams), this.info.getInfo(), of(searchParams), this.i18nService.getAvailableLanguages());
          })
      ).subscribe(([res, info, searchParams, activeLanguages]) => {
        this.queryParams = searchParams.toParams();
        if (res.length === 1) {
          this.router.navigate(['/event', res[0].shortName], {replaceUrl: true, queryParams: this.queryParams});
        } else {
          this.events = res;
          this.analytics.pageView(info.analyticsConfiguration);
          this.linksContainer = globalTermsPrivacyLinks(info);
          this.languages = filterAvailableLanguages(activeLanguages, res);
          this.i18nService.setPageTitle('event-list.header.title', null);
        }
      });
    }
}
