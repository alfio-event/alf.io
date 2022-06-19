import { Component, Input, OnInit, OnDestroy } from '@angular/core';
import { FormGroup, FormArray } from '@angular/forms';
import { AdditionalField } from '../model/ticket';
import { TranslateService } from '@ngx-translate/core';
import { I18nService } from '../shared/i18n.service';
import { LocalizedCountry } from '../model/localized-country';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-additional-field',
  templateUrl: './additional-field.component.html'
})
export class AdditionalFieldComponent implements OnInit, OnDestroy {

  @Input()
  field: AdditionalField;

  @Input()
  form: FormGroup;

  @Input()
  ticketUUID: string;

  @Input()
  ticketAcquired: boolean;


  countries: LocalizedCountry[];

  private langChangeSub: Subscription;

  constructor(private translate: TranslateService, private i18nService: I18nService) { }

  ngOnInit(): void {
    if (this.field.type === 'country') {
      this.getCountries();
      this.langChangeSub = this.translate.onLangChange.subscribe(() => {
        this.getCountries();
      });
    }
  }

  ngOnDestroy(): void {
    if (this.langChangeSub) {
      this.langChangeSub.unsubscribe();
    }
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
    const fa = this.form.get(this.field.name) as FormArray;
    fa.controls[index].setValue(checked ? value : null, {emitEvent: false, emitViewToModelChange: false});
  }

  get labelId(): string {
    return this.ticketUUID + '-' + this.field.name.replace(/[^a-zA-Z0-9]/g, '+') + '-label';
  }

  get hideLabelForAssistiveTechnologies(): boolean {
    return this.field.type === 'checkbox';
  }
}
