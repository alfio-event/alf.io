import {AnalyticsConfiguration} from './analytics-configuration';
import {InvoicingConfiguration, TermsPrivacyLinksContainer} from './event';

export type Info = {
  demoModeEnabled: boolean;
  devModeEnabled: boolean;
  prodModeEnabled: boolean;
  analyticsConfiguration: AnalyticsConfiguration;
  globalPrivacyPolicyUrl?: string;
  globalTermsUrl?: string;
  invoicingConfiguration?: InvoicingConfiguration;
  announcementBannerContentHTML?: string;
  walletConfiguration: WalletConfiguration;
}

export type WalletConfiguration = {
  gWalletEnabled: boolean;
  passEnabled: boolean;
}

export function globalTermsPrivacyLinks(info: Info): TermsPrivacyLinksContainer {
  return { privacyPolicyUrl: info.globalPrivacyPolicyUrl, termsAndConditionsUrl: info.globalTermsUrl };
}
