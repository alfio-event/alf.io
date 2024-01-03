import {DatesWithOffset, DateValidity} from './date-validity';
import {AnalyticsConfiguration} from './analytics-configuration';
import {IconName, IconPrefix} from '@fortawesome/fontawesome-svg-core';
import {OfflinePaymentConfiguration, PurchaseContext} from './purchase-context';
import {EmbeddingConfiguration} from './embedding-configuration';

export interface TermsPrivacyLinksContainer {
  privacyPolicyUrl?: string;
  termsAndConditionsUrl?: string;
}

export class Event implements DateValidity, PurchaseContext {
    shortName: string;
    title: { [lang: string]: string };
    format: EventFormat;
    fileBlobId: string;
    contentLanguages: Language[];
    websiteUrl: string;
    location: string;
    privacyPolicyUrl: string;
    termsAndConditionsUrl: string;
    mapUrl: string;

    organizationName: string;
    organizationEmail: string;

    description: {[key: string]: string};
    vatIncluded: boolean;
    vat: string;
    free: boolean;

    //
    bankAccount: string;
    bankAccountOwner: string[];
    //
    currency: string;
    currencyDescriptor: CurrencyDescriptor;

    // date related
    timeZone: string;
    datesWithOffset: DatesWithOffset;
    sameDay: boolean;
    formattedBeginDate: {[key: string]: string}; // day, month, year
    formattedBeginTime: {[key: string]: string}; // the hour/minute component
    formattedEndDate: {[key: string]: string};
    formattedEndTime: {[key: string]: string};
    //

    //
    invoicingConfiguration: InvoicingConfiguration;
    //
    captchaConfiguration: CaptchaConfiguration;
    //
    assignmentConfiguration: AssignmentConfiguration;
    //
    promotionsConfiguration: PromotionsConfiguration;
    //
    analyticsConfiguration: AnalyticsConfiguration;

    embeddingConfiguration: EmbeddingConfiguration;

    i18nOverride: {[lang: string]: {[key: string]: string}};

    availableTicketsCount: number | null;

    customCss: string | null;

    canApplySubscriptions: boolean;

    offlinePaymentConfiguration: OfflinePaymentConfiguration = {
      showOnlyBasicInstructions: false
    };
}

export class InvoicingConfiguration {
    userCanDownloadReceiptOrInvoice: boolean;
    euVatCheckingEnabled: boolean;
    invoiceAllowed: boolean;
    onlyInvoice: boolean;
    customerReferenceEnabled: boolean;
    enabledItalyEInvoicing: boolean;
    vatNumberStrictlyRequired: boolean;
}

export class Language {
    locale: string;
    displayLanguage: string;
}

export class PaymentProxyWithParameters {
    paymentProxy: PaymentProxy;
    parameters: {[key: string]: any};
}

export type EventFormat = 'IN_PERSON' | 'ONLINE' | 'HYBRID';

export type PaymentMethod = 'CREDIT_CARD' | 'PAYPAL' | 'IDEAL' | 'BANK_TRANSFER' | 'ON_SITE'
                            | 'APPLE_PAY' | 'BANCONTACT' | 'ING_HOME_PAY' | 'BELFIUS' | 'PRZELEWY_24' | 'KBC' | 'NONE';
export type PaymentProxy = 'STRIPE' | 'ON_SITE' | 'OFFLINE' | 'PAYPAL' | 'MOLLIE' | 'SAFERPAY';
export interface PaymentMethodDetails {
    labelKey: string;
    icon: [IconPrefix, IconName];
}

export const paymentMethodDetails: {[key in PaymentMethod]: PaymentMethodDetails} = {
    'CREDIT_CARD': {
        labelKey: 'reservation-page.credit-card',
        icon: ['fas', 'credit-card']
    },
    'PAYPAL': {
        labelKey: 'reservation-page.paypal',
        icon: ['fab', 'paypal']
    },
    'IDEAL': {
        labelKey: 'reservation-page.payment-method.ideal',
        icon: ['fab', 'ideal']
    },
    'BANCONTACT': {
        labelKey: 'reservation-page.payment-method.bancontact',
        icon: ['fas', 'exchange-alt']
    },
    'ING_HOME_PAY': {
        labelKey: 'reservation-page.payment-method.ing-home-pay',
        icon: ['fas', 'exchange-alt']
    },
    'BELFIUS': {
        labelKey: 'reservation-page.payment-method.belfius',
        icon: ['fas', 'exchange-alt']
    },
    'PRZELEWY_24': {
        labelKey: 'reservation-page.payment-method.przelewy-24',
        icon: ['fas', 'exchange-alt']
    },
    'BANK_TRANSFER': {
        labelKey: 'reservation-page.offline',
        icon: ['fas', 'exchange-alt']
    },
    'ON_SITE': {
        labelKey: 'reservation-page.on-site',
        icon: ['fas', 'money-bill']
    },
    'APPLE_PAY': {
        labelKey: 'reservation-page.payment-method.apple-pay',
        icon: ['fab', 'apple-pay']
    },
    'KBC': {
        labelKey: 'reservation-page.payment-method.kbc',
        icon: ['fas', 'money-check-alt']
    },
    'NONE': {
        labelKey: null,
        icon: ['fas', 'exchange-alt']
    }
};

export class CaptchaConfiguration {
    captchaForTicketSelection: boolean;
    captchaForOfflinePaymentAndFree: boolean;
    recaptchaApiKey: string;
}

export class AssignmentConfiguration {
    forceAssignment: boolean;
    enableAttendeeAutocomplete: boolean;
    enableTicketTransfer: boolean;
}

export class PromotionsConfiguration {
    hasAccessPromotions: boolean;
    usePartnerCode: boolean;
}

export interface CurrencyDescriptor {
    code: string;
    name: string;
    symbol: string;
    fractionDigits: number;
}
