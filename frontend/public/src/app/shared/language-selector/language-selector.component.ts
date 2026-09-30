import {Component, Input, OnChanges, OnInit, SimpleChanges} from '@angular/core';
import {Language} from '../../model/event';
import {Router} from '@angular/router';
import {I18nService} from '../i18n.service';
import {Observable} from 'rxjs';

@Component({
  selector: 'app-language-selector',
  templateUrl: './language-selector.component.html'
})
export class LanguageSelectorComponent implements OnInit, OnChanges {

  @Input()
  contentLanguages: Language[];
  private selectedLanguage: string;
  private initialized = false;
  currentLanguage: string;
  filteredLanguages: Language[];

  constructor(private i18nService: I18nService, private router: Router) { }

  ngOnInit(): void {
    this.selectedLanguage = this.i18nService.getCurrentLang();
    this.buildValues();
    this.initialized = true;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (this.initialized && changes.contentLanguages) {
      this.buildValues();
    }
  }



  private buildValues(): void {
    if (this.contentLanguages != null && this.contentLanguages.length > 0) {
      let currentLang = this.contentLanguages.find(cl => cl.locale === this.selectedLanguage);
      const languageNotFound = currentLang == null;
      if (languageNotFound) {
        currentLang = this.contentLanguages[0];
        this.changeLanguage(currentLang.locale);
      }
      this.currentLanguage = currentLang ? currentLang.displayLanguage : this.contentLanguages[0].displayLanguage;
      this.filteredLanguages = this.contentLanguages.filter(cl => cl !== currentLang)
        .sort((a, b) => a.displayLanguage.toLowerCase() > b.displayLanguage.toLowerCase() ? 1 : -1);
    }
  }

  public changeLanguage(lang: string): void {
    if (this.selectedLanguage === lang) {
      return;
    }
    const eventShortName = this.router.routerState.snapshot.root.firstChild ? this.router.routerState.snapshot.root.firstChild.params['eventShortName'] : null;
    let observable: Observable<boolean>;
    if (eventShortName != null) {
      observable = this.i18nService.useTranslation('event', eventShortName, lang);
    } else {
      observable = this.i18nService.useTranslationForRoot(lang);
    }
    observable.subscribe(() => {
      this.selectedLanguage = this.i18nService.getCurrentLang();
      this.buildValues();
    });
  }
}
