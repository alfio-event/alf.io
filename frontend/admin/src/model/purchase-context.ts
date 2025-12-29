
export interface PurchaseContext {
    type: PurchaseContextType;
    publicIdentifier: string;
    contentLanguages: ContentLanguage[];
    firstContentLanguage: ContentLanguage;
}

export type PurchaseContextType = 'event' | 'subscription';

export interface ContentLanguage {
    locale:          string;
    value:           number;
    language:        string;
    displayLanguage: string;
}

export interface LocalizedContent {
    [key: string]: string;
}
