import {AnalyticsConfiguration} from './analytics-configuration';
import {
  AssignmentConfiguration,
  CaptchaConfiguration,
  CurrencyDescriptor,
  InvoicingConfiguration,
  Language
} from './event';
import {
  Localized,
  OfflinePaymentConfiguration,
  PurchaseContext,
  PurchaseContextPriceDescriptor
} from './purchase-context';
import {DatesWithOffset} from './date-validity';
import {EmbeddingConfiguration} from './embedding-configuration';

export interface SubscriptionSummaryData extends PurchaseContextPriceDescriptor, Localized {
  salePeriod: DatesWithOffset;
  formattedOnSaleFrom: { [key: string]: string };
  formattedOnSaleTo?: { [key: string]: string };
  formattedValidFrom?: { [key: string]: string };
  formattedValidTo?: { [key: string]: string };
  validityType: SubscriptionValidityType;
  usageType: SubscriptionUsageType;
  timeZone: string;
  formattedPrice: string;
  validityTimeUnit?: SubscriptionTimeUnit;
  validityUnits?: number;
  maxEntries?: number;
  organizationEmail: string;
  organizationName: string;
}

export class BasicSubscriptionInfo implements SubscriptionSummaryData {
  id: string;
  fileBlobId: string;
  title: { [lang: string]: string };
  description: { [lang: string]: string };

  salePeriod: DatesWithOffset;
  validityType: SubscriptionValidityType;
  usageType: SubscriptionUsageType;
  timeZone: string;
  validityTimeUnit?: SubscriptionTimeUnit;
  validityUnits?: number;
  maxEntries?: number;

  organizationEmail: string;
  organizationName: string;

  formattedPrice: string;
  currency: string;
  currencyDescriptor: CurrencyDescriptor;
  vat: string;
  vatIncluded: boolean;

  formattedOnSaleFrom: { [key: string]: string };
  formattedOnSaleTo?: { [key: string]: string };
  contentLanguages: Language[] = [];

}

export type SubscriptionValidityType = 'STANDARD' | 'CUSTOM' | 'NOT_SET';
export type SubscriptionTimeUnit = 'DAYS' | 'MONTHS' | 'YEARS';
export type SubscriptionUsageType = 'ONCE_PER_EVENT' | 'UNLIMITED';

export class SubscriptionInfo implements PurchaseContext, SubscriptionSummaryData {
  id: string;

  invoicingConfiguration: InvoicingConfiguration;
  assignmentConfiguration: AssignmentConfiguration;
  analyticsConfiguration: AnalyticsConfiguration;
  captchaConfiguration: CaptchaConfiguration;
  embeddingConfiguration: EmbeddingConfiguration;
  contentLanguages: Language[] = [];
  termsAndConditionsUrl: string;
  privacyPolicyUrl: string;
  fileBlobId: string;
  vat: string;
  currencyDescriptor: CurrencyDescriptor;
  currency: string;
  vatIncluded: boolean;
  title: { [lang: string]: string };
  description: { [lang: string]: string };
  formattedPrice: string;
  numAvailable: number;

  //
  bankAccount: string;
  bankAccountOwner: string[];
  //
  organizationEmail: string;
  organizationName: string;

  canApplySubscriptions: boolean;

  // FIXME / CHECK:
  websiteUrl: string;
  shortName: string;

  salePeriod: DatesWithOffset;
  formattedOnSaleFrom: { [key: string]: string };
  formattedOnSaleTo?: { [key: string]: string };
  timeZone: string;
  validityType: SubscriptionValidityType;
  usageType: SubscriptionUsageType;
  validityTimeUnit?: SubscriptionTimeUnit;
  validityUnits?: number;
  formattedValidFrom?: { [key: string]: string };
  formattedValidTo?: { [key: string]: string };
  maxEntries?: number;

  offlinePaymentConfiguration: OfflinePaymentConfiguration = {
    showOnlyBasicInstructions: false
  };
}
