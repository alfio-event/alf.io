import {AnalyticsConfiguration} from './analytics-configuration';
import {
  AssignmentConfiguration,
  CaptchaConfiguration,
  CurrencyDescriptor,
  InvoicingConfiguration,
  Language,
  TermsPrivacyLinksContainer
} from './event';
import {EmbeddingConfiguration} from './embedding-configuration';

export interface Localized {
  contentLanguages: Language[];
}

export function filterAvailableLanguages(activeLanguages: Language[], purchaseContexts: Localized[]): Language[] {
  if (purchaseContexts.length > 0) {
    const languagesFromPc = purchaseContexts.map(pc => pc.contentLanguages.map(l => l.locale)).reduce((accumulator, current) => {
      if (accumulator.length === 0) {
        accumulator.push(...current);
        return accumulator;
      } else {
        return accumulator.filter(l => current.some(l1 => l1 === l));
      }
    }, []);
    const filtered = activeLanguages.filter(al => languagesFromPc.some(l => l === al.locale));
    if (filtered.length > 0) {
      return filtered;
    }
  }
  return activeLanguages;
}

export interface PurchaseContext extends PurchaseContextPriceDescriptor, Localized, TermsPrivacyLinksContainer {
    title: { [lang: string]: string };
    invoicingConfiguration: InvoicingConfiguration;
    assignmentConfiguration: AssignmentConfiguration;
    analyticsConfiguration: AnalyticsConfiguration;
    captchaConfiguration: CaptchaConfiguration;
    offlinePaymentConfiguration: OfflinePaymentConfiguration;

    embeddingConfiguration: EmbeddingConfiguration;

    fileBlobId: string;

    bankAccount: string;
    bankAccountOwner: string[];

    organizationEmail: string;

    //
    websiteUrl: string;
    shortName: string;

    canApplySubscriptions: boolean;
}

export interface PurchaseContextPriceDescriptor {
  currencyDescriptor: CurrencyDescriptor;
  vat: string;
  currency: string;
  vatIncluded: boolean;
}

export interface OfflinePaymentConfiguration {
  showOnlyBasicInstructions: boolean;
}
