import {Component, Input} from '@angular/core';
import {SubscriptionSummaryData} from '../model/subscription';
import {getLocalizedContent} from '../shared/subscription.service';
import {isDifferentTimeZone} from '../shared/event.service';
import {TranslateService} from '@ngx-translate/core';
import {SubscriptionOwner} from '../model/reservation-info';
import {AdditionalField} from '../model/ticket';
import {DatePipe} from '@angular/common';

@Component({
  selector: 'app-subscription-summary',
  templateUrl: './subscription-summary.component.html',
  styleUrls: ['./subscription-summary.component.scss'],
  providers: [DatePipe]
})
export class SubscriptionSummaryComponent {

  @Input()
  subscription: SubscriptionSummaryData;
  @Input()
  owner?: SubscriptionOwner;
  @Input()
  fieldConfigurationBeforeStandard: AdditionalField[] = [];
  @Input()
  fieldConfigurationAfterStandard: AdditionalField[] = [];

  constructor(private translateService: TranslateService, private dateFormat: DatePipe) {
  }

  get hasOnSaleTo(): boolean {
    return this.subscription.formattedOnSaleTo != null;
  }

  /*
  get onSaleFrom(): string {
    return getLocalizedContent(this.subscription.formattedOnSaleFrom, this.translateService.currentLang);
  }

  get onSaleTo(): string {
    return getLocalizedContent(this.subscription.formattedOnSaleTo, this.translateService.currentLang);
  }
   */

  get displayTimeZoneInfo(): boolean {
    const datesWithOffset = this.subscription.salePeriod;
    return isDifferentTimeZone(datesWithOffset.startDateTime, datesWithOffset.startTimeZoneOffset)
      || (datesWithOffset.endDateTime > 0 && isDifferentTimeZone(datesWithOffset.endDateTime, datesWithOffset.endTimeZoneOffset));
  }

  get validFrom(): string {
    if (this.subscription.formattedValidFrom != null) {
      return getLocalizedContent(this.subscription.formattedValidFrom, this.translateService.currentLang);
    }
    return '';
  }

  get hasValidTo(): boolean {
    return this.subscription.formattedValidTo != null;
  }

  get validTo(): string {
    if (this.hasValidTo) {
      return getLocalizedContent(this.subscription.formattedValidTo, this.translateService.currentLang);
    }
    return '';
  }

  hasData(owner: SubscriptionOwner): boolean {
    return owner != null
      && owner.firstName != null
      && owner.lastName != null
      && owner.email != null;
  }

  getValue(field: AdditionalField): string {
    if (field.type === 'input:dateOfBirth') {
      return this.dateFormat.transform(field.value, this.translateService.instant('common.date-format'));
    }
    return field.description[this.translateService.currentLang]?.restrictedValuesDescription[field.value] || field.value;
  }
}
