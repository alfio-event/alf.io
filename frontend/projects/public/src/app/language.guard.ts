import {Injectable} from '@angular/core';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import {Observable} from 'rxjs';
import {I18nService} from './shared/i18n.service';
import {TranslateService} from '@ngx-translate/core';
import {catchError, map, switchMap} from 'rxjs/operators';
import {PurchaseContextService, PurchaseContextType} from './shared/purchase-context.service';

@Injectable({
  providedIn: 'root'
})
export class LanguageGuard  {

  constructor(private i18nService: I18nService, private purchaseContextService: PurchaseContextService, private translate: TranslateService) {
  }

  canActivate(next: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> {

    const langQueryParam = next.queryParams['lang'];
    const persisted = this.i18nService.getPersistedLanguage();

    const type: PurchaseContextType = next.data.type;
    const publicIdentifier = next.params[next.data.publicIdentifierParameter];
    const req = type && publicIdentifier ? this.getForContext(type, publicIdentifier) : this.getForApp();

    return req.pipe(switchMap(availableLanguages => {
      const lang = this.extractLang(availableLanguages, persisted, langQueryParam);
      return this.i18nService.useTranslation(type, publicIdentifier, lang);
    }));
  }

  private getForContext(type: PurchaseContextType, publicIdentifier: string): Observable<string[]> {
    return this.purchaseContextService.getContext(type, publicIdentifier).pipe(map(p => p.contentLanguages.map(v => v.locale))).pipe(catchError(val => this.getForApp()));
  }

  private getForApp(): Observable<string[]> {
    return this.i18nService.getAvailableLanguages().pipe(map(languages => languages.map(l => l.locale)));
  }

  private extractLang(availableLanguages: string[], persisted: string, override: string): string {
    let lang: string;
    if (override && availableLanguages.indexOf(override) >= 0) {
      lang = override;
    } else if (availableLanguages.indexOf(persisted) >= 0) {
      lang = persisted;
    } else if (availableLanguages.indexOf(this.translate.getBrowserLang()) >= 0) {
      lang = this.translate.getBrowserLang();
    } else if (availableLanguages.indexOf('en') >= 0) {
      // use english as a default choice if available in case of mismatching browser lang
      lang = 'en';
    } else {
      lang = availableLanguages[0];
    }
    return lang;
  }

}
