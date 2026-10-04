import {Component, Input, OnDestroy, OnInit} from '@angular/core';
import {UntypedFormGroup} from '@angular/forms';
import {TranslateService} from '@ngx-translate/core';
import {I18nService} from '../../shared/i18n.service';
import {Subscription} from 'rxjs';
import {LocalizedCountry} from '../../model/localized-country';
import {PurchaseContext} from '../../model/purchase-context';
import {InvoicingConfiguration} from '../../model/event';
import {mobile} from '../../shared/util';

@Component({
  selector: 'app-invoice-form',
  templateUrl: './invoice-form.component.html'
})
export class InvoiceFormComponent implements OnInit, OnDestroy {

  @Input()
  form: UntypedFormGroup;

  @Input()
  purchaseContext?: PurchaseContext;

  @Input()
  invoicingConfiguration?: InvoicingConfiguration;

  private langChangeSub: Subscription;

  countries: LocalizedCountry[];

  taxIdIsRequired = true;

  isMobile = mobile;

  constructor(private translate: TranslateService, private i18nService: I18nService) { }

  ngOnInit(): void {
    this.getCountries(this.translate.currentLang);
    this.langChangeSub = this.translate.onLangChange.subscribe(change => {
      this.getCountries(this.translate.currentLang);
    });

    this.updateItalyEInvoicingFields();

    this.form.get('italyEInvoicingReferenceType').valueChanges.subscribe(change => {
      this.updateItalyEInvoicingFields();
    });
    this.form.get('skipVatNr')?.valueChanges.subscribe(change => {
      this.taxIdIsRequired = !change;
    });
  }

  public ngOnDestroy(): void {
    if (this.langChangeSub) {
      this.langChangeSub.unsubscribe();
    }
  }


  updateItalyEInvoicingFields(): void {
    const refType = this.form.get('italyEInvoicingReferenceType').value;
    if (refType === 'ADDRESSEE_CODE') {
      this.form.get('italyEInvoicingReferencePEC').setValue(null);
    } else if (refType === 'PEC') {
      this.form.get('italyEInvoicingReferenceAddresseeCode').setValue(null);
    } else if (refType === 'NONE') {
      this.form.get('italyEInvoicingReferencePEC').setValue(null);
      this.form.get('italyEInvoicingReferenceAddresseeCode').setValue(null);
    }
  }

  get addresseeCodeSelected(): boolean {
    return this.form.get('italyEInvoicingReferenceType').value === 'ADDRESSEE_CODE';
  }

  get pecSelected(): boolean {
    return this.form.get('italyEInvoicingReferenceType').value === 'PEC';
  }

  getCountries(currentLang: string): void {
    this.i18nService.getVatCountries(currentLang).subscribe(countries => {
      this.countries = countries;
    });
  }

  get euVatCheckingEnabled(): boolean {
    return this.invoicingConf.euVatCheckingEnabled;
  }

  get customerReferenceEnabled(): boolean {
    return this.invoicingConf.customerReferenceEnabled;
  }

  get invoiceBusiness(): boolean {
    return this.form.value.addCompanyBillingDetails;
  }

  get vatNumberStrictlyRequired(): boolean {
    return this.invoicingConf.vatNumberStrictlyRequired;
  }

  get enabledItalyEInvoicing(): boolean {
    return this.invoicingConf.enabledItalyEInvoicing;
  }

  get italyEInvoicingFormDisplayed(): boolean {
    return this.enabledItalyEInvoicing && this.form.value.vatCountryCode === 'IT';
  }

  get countrySelected(): boolean {
    return this.form.value.vatCountryCode != null;
  }

  private get invoicingConf(): InvoicingConfiguration {
    return this.purchaseContext?.invoicingConfiguration || this.invoicingConfiguration;
  }

  searchCountry(term: string, country: LocalizedCountry): boolean {
    if (term) {
      term = term.toLowerCase();
      return country.isoCode.toLowerCase().indexOf(term) > -1 || country.name.toLowerCase().indexOf(term) > -1;
    }
    return true;
  }

    protected readonly mobile = mobile;
}
