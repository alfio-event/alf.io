import {Injectable} from '@angular/core';
import {AnalyticsConfiguration} from '../model/analytics-configuration';
import {Observable} from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class AnalyticsService {

  private gaScript: Observable<[Function, AnalyticsConfiguration, string]> = null;

  constructor() {
  }

  pageView(conf: AnalyticsConfiguration): void {
    const locationPathName = location.pathname;
    if (conf.googleAnalyticsKey) {
      this.handleGoogleAnalytics(conf, locationPathName);
    }
  }


  private handleGoogleAnalytics(conf: AnalyticsConfiguration, locationPathName: string) {
    if (this.gaScript === null) {
      this.gaScript = new Observable<[Function, AnalyticsConfiguration, string]>(subscribe => {
        if (!document.getElementById('GA_SCRIPT')) { // <- script is not created
          const scriptElem = document.createElement('script');
          scriptElem.id = 'GA_SCRIPT';
          scriptElem.addEventListener('load', () => {
            subscribe.next([window['ga'], conf, locationPathName]);
          });
          scriptElem.src = 'https://ssl.google-analytics.com/analytics.js';
          scriptElem.async = true;
          scriptElem.defer = true;
          document.body.appendChild(scriptElem);
        } else if (!window['ga']) { // <- script has been created, but not loaded
          document.getElementById('GA_SCRIPT').addEventListener('load', () => {
            subscribe.next([window['ga'], conf, locationPathName]);
          });
        } else { // <- script has been loaded
          subscribe.next([window['ga'], conf, locationPathName]);
        }
      });
    }

    this.gaScript.subscribe(([ga, configuration, pathname]) => {
      if (configuration.googleAnalyticsScrambledInfo) {
        ga('create', configuration.googleAnalyticsKey, {'anonymizeIp': true, 'storage': 'none', 'clientId': configuration.clientId});
      } else {
        ga('create', configuration.googleAnalyticsKey);
      }
      ga('send', 'pageview', pathname);
    });
  }
}
