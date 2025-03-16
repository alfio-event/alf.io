export interface AdditionalField {
    id: number;
    name: string;
    order: number;
    type: AdditionalFieldType;
    maxLength?: number;
    minLength?: number;
    required: boolean;
    editable: boolean;
    restrictedValues?: string[];
    context: AdditionalFieldContext;
    additionalServiceId?: number;
    categoryIds?: number[];
    disabledValues?: string[];
    displayAtCheckIn: boolean;
    description: {[lang: string]: PurchaseContextFieldDescriptionContainer};
}

export interface AdditionalFieldTemplate {
    name: string;
    type: AdditionalFieldType;
    maxLength?: number;
    minLength?: number;
    restrictedValues?: string[];
    description: {[lang: string]: PurchaseContextFieldDescription};
}

export interface AdditionalFieldStats {
    name: string;
    count: number;
    percentage: number;
}

export type AdditionalFieldContext = 'ATTENDEE' | 'ADDITIONAL_SERVICE' | 'SUBSCRIPTION';

export type AdditionalFieldType = 'input:text' | 'input:tel' | 'vat:eu' | 'textarea' | 'country' | 'select' | 'checkbox' | 'radio' | 'input:dateOfBirth';

const descriptions = {
    'input:text': 'Single-line text input',
    'input:tel': 'Phone number input',
    'vat:eu': 'European VAT number input',
    'textarea': 'Multi-line text input',
    'country': 'Country selection drop-down',
    'select': 'Single-choice drop-down',
    'radio': 'Single-choice radio buttons',
    'checkbox': 'Multiple-choice checkboxes',
    'input:dateOfBirth': 'Date of birth input'
};

export interface PurchaseContextFieldDescriptionContainer {
    locale: string;
    description: PurchaseContextFieldDescription;
    fieldName: string;
}

export interface PurchaseContextFieldDescription {
    label: string;
    placeholder?: string;
    restrictedValues?: {[lang: string]: string};
}

export function renderAdditionalFieldType(type: AdditionalFieldType): string {
    const mapped = descriptions[type];
    return mapped ?? 'unknown';
}

export function supportsMinMaxLength(type: AdditionalFieldType): boolean {
    return type === 'input:text' || type === 'input:tel' || type === 'textarea';
}
