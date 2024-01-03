import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {UntypedFormArray, UntypedFormGroup} from '@angular/forms';
import {AdditionalField} from '../model/ticket';
import {TranslateService} from '@ngx-translate/core';
import {I18nService} from '../shared/i18n.service';
import {LocalizedCountry} from '../model/localized-country';
import {Subscription} from 'rxjs';
import {mobile} from '../shared/util';

@Component({
  selector: 'app-additional-field',
  templateUrl: './additional-field.component.html'
})
export class AdditionalFieldComponent implements OnInit, OnDestroy {

  @Input()
  field: AdditionalField;

  @Input()
  form: UntypedFormGroup;

  @Input()
  elementUUID: string;

  @Input()
  ticketAcquired: boolean;

  countries: LocalizedCountry[];

  isMobile = mobile;

  private subscriptions: Subscription[] = [];
  private dateFormat: Intl.DateTimeFormat;
  yesterday: string;
  selectedDateDescription: string;

  constructor(private translate: TranslateService, private i18nService: I18nService) { }

  ngOnInit(): void {
    this.initFieldSpecificValues(true);
    this.subscriptions.push(this.translate.onLangChange.subscribe(() => {
      this.initFieldSpecificValues(false);
    }));
  }

  private initFieldSpecificValues(firstInit: boolean): void {
    if (this.field.type === 'country') {
      this.getCountries();
    } else if (this.field.type === 'input:dateOfBirth') {
      if (firstInit) {
        this.yesterday = new Date(new Date().getTime() - 24 * 60 * 60 * 1000)
          .toISOString()
          .substring(0, 10);
        this.subscriptions.push(this.form.get(this.field.name).valueChanges
          .subscribe(() => this.selectedDateDescription = this.localizedSelectedDate()));
      }
      this.dateFormat = new Intl.DateTimeFormat(this.translate.currentLang, {
        dateStyle: 'full'
      } as any);
      this.selectedDateDescription = this.localizedSelectedDate();
    }
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(s => s.unsubscribe());
  }

  get labelValue(): string {
    if (this.field && this.field.description && this.field.description[this.translate.currentLang]) {
      return this.field.description[this.translate.currentLang].label;
    } else {
      return '';
    }
  }

  get placeholder(): string {
    if (this.field && this.field.description && this.field.description[this.translate.currentLang]) {
      return this.field.description[this.translate.currentLang].placeholder;
    } else {
      return '';
    }
  }

  get editAllowed(): boolean {
    return !this.ticketAcquired || this.field.value == null || this.field.value.trim().length === 0 || this.field.editable;
  }

  getCountries(): void {
    this.i18nService.getCountries(this.translate.currentLang).subscribe(countries => {
      this.countries = countries;
    });
  }

  getRestrictedValueLabel(value: string): string {
    if (this.field.description[this.translate.currentLang]) {
      return this.field.description[this.translate.currentLang].restrictedValuesDescription[value] || value;
    } else {
      return value;
    }
  }

  selectedCheckBox(index: number, value: string, checked: boolean) {
    const fa = this.form.get(this.field.name) as UntypedFormArray;
    fa.controls[index].setValue(checked ? value : null, {emitEvent: false, emitViewToModelChange: false});
  }

  get labelId(): string {
    return this.elementUUID + '-' + this.field.name.replace(/[^a-zA-Z0-9]/g, '+') + '-label';
  }

  get hideLabelForAssistiveTechnologies(): boolean {
    return this.field.type === 'checkbox';
  }

  private localizedSelectedDate(): string {
    try {
      const value = (this.form.get(this.field.name) as UntypedFormArray).at(0)?.value;
      if (value != null && value.trim() !== '') {
        return this.dateFormat.format(Date.parse(value));
      }
    } catch (err) {
      return '';
    }
    return '';
  }
}
