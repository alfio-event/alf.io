import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, of, zip} from 'rxjs';
import {Language} from '../model/event';
import {LocalizedCountry} from '../model/localized-country';
import {Title} from '@angular/platform-browser';
import {LangChangeEvent, TranslateLoader, TranslateService} from '@ngx-translate/core';
import {NavigationStart, Router} from '@angular/router';
import {catchError, map, mergeMap, shareReplay, tap} from 'rxjs/operators';
import {EventService} from './event.service';
import {PurchaseContextType} from './purchase-context.service';
import {PurchaseContext} from '../model/purchase-context';
import {getFromSessionStorage, loadPreloaded, writeToSessionStorage} from './util';

@Injectable({
  providedIn: 'root'
})
export class I18nService {


  private applicationLanguages: Observable<Language[]>;
  private countriesCache = new CountriesCache();

  private static getInterpolateParams(lang: string, ctx: PurchaseContext): any {
    if (ctx != null) {
      return {'0': ctx.title[lang]};
    }
    return null;
  }

  constructor(
    private http: HttpClient,
    private title: Title,
    private translateService: TranslateService,
    private router: Router,
    private customLoader: CustomLoader,
    private eventService: EventService) {
  }

  private initCountriesCache(): void {
      if (this.countriesCache.empty) {
          const vatCountries = document.getElementById('preload-vat-countries');
          if (vatCountries != null && vatCountries.getAttribute('data-param') != null) {
              this.countriesCache.countriesForVat[vatCountries.getAttribute('data-param')] = loadPreloaded('preload-vat-countries');
          }
          const allCountries = document.getElementById('preload-countries');
          if (allCountries != null && allCountries.getAttribute('data-param') != null) {
              this.countriesCache.allCountries[allCountries.getAttribute('data-param')] = loadPreloaded('preload-countries');
          }
          this.countriesCache.markAsInitialized();
      }
  }



  getCountries(locale: string): Observable<LocalizedCountry[]> {
    this.initCountriesCache();
    if (this.countriesCache.allCountries[locale] != null) {
        return of(this.countriesCache.allCountries[locale]);
    }
    return this.http.get<LocalizedCountry[]>(`/api/v2/public/i18n/countries/${locale}`)
        .pipe(tap(countries => {
            this.countriesCache.allCountries[locale] = countries;
        }));
  }

  getVatCountries(locale: string): Observable<LocalizedCountry[]> {
    this.initCountriesCache();
    if (this.countriesCache.countriesForVat[locale] != null) {
      return of(this.countriesCache.countriesForVat[locale]);
    }
    return this.http.get<LocalizedCountry[]>(`/api/v2/public/i18n/countries-vat/${locale}`)
        .pipe(tap(countries => {
            this.countriesCache.countriesForVat[locale] = countries;
        }));
  }

  getEUVatCountries(locale: string): Observable<LocalizedCountry[]> {
    return this.http.get<LocalizedCountry[]>(`/api/v2/public/i18n/eu-countries-vat/${locale}`);
  }

  getAvailableLanguages(): Observable<Language[]> {
    if (!this.applicationLanguages) {
      this.applicationLanguages = this.http.get<Language[]>(`/api/v2/public/i18n/languages`).pipe(shareReplay(1));
    }
    return this.applicationLanguages;
  }

  setPageTitle(titleCode: string, ctx?: PurchaseContext): void {

    const titleSub = this.translateService.onLangChange.subscribe((params: LangChangeEvent) => {
      this.title.setTitle(this.translateService.instant(titleCode, I18nService.getInterpolateParams(params.lang, ctx)));
    });

    const routerSub = this.router.events.subscribe(ev => {
      if (ev instanceof NavigationStart) {
        routerSub.unsubscribe();
        titleSub.unsubscribe();
        this.title.setTitle(null);
      }
    });

    this.title.setTitle(this.translateService.instant(titleCode, I18nService.getInterpolateParams(this.translateService.currentLang, ctx)));
  }

  persistLanguage(lang: string): void {
    writeToSessionStorage('ALFIO_LANG', lang);
  }

  getPersistedLanguage(): string {
    return getFromSessionStorage('ALFIO_LANG');
  }

  getCurrentLang(): string {
    return this.translateService.currentLang;
  }

  useTranslation(type: PurchaseContextType, publicIdentifier: string, lang: string): Observable<boolean> {
    const overrideBundle = this.getOverrideBundle(type, publicIdentifier, lang);
    return zip(this.customLoader.getTranslation(lang), overrideBundle).pipe(mergeMap(([root, override]) => {
      this.translateService.setTranslation(lang, root, false);
      this.translateService.setTranslation(lang, override, true);
      this.translateService.use(lang);
      return of(true);
    }));
  }

  useTranslationForRoot(lang: string): Observable<boolean> {
    return this.useTranslation(null, '', lang);
  }

  private getOverrideBundle(type: PurchaseContextType, publicIdentifier: string, lang: string): Observable<any> {
    if (type === 'event' && publicIdentifier) {
      return this.eventService.getEvent(publicIdentifier)
        .pipe(
          catchError(e => of({i18nOverride: {}})),
          map(e => e.i18nOverride[lang] || {})
        );
    }
    return of({});
  }
}

const translationCache: {[key: string]: Observable<any>} = {};

@Injectable({providedIn: 'root'})
export class CustomLoader implements TranslateLoader {

  constructor(private http: HttpClient) {
  }

  getTranslation(lang: string): Observable<any> {
    if (!translationCache[lang]) {
      const preloadBundle = document.getElementById('preload-bundle');
      if (preloadBundle && preloadBundle.getAttribute('data-param') === lang) {
        translationCache[lang] = of(loadPreloaded('preload-bundle')).pipe(shareReplay(1));
      } else {
        translationCache[lang] = this.http.get(`/api/v2/public/i18n/bundle/${lang}`).pipe(shareReplay(1));
      }
    }
    return translationCache[lang];
  }
}

class CountriesCache {
    countriesForVat: {[k:string]: LocalizedCountry[]} = {};
    allCountries: {[k:string]: LocalizedCountry[]} = {};
    empty: boolean = true;

    markAsInitialized() {
        this.empty = false;
    }
}
