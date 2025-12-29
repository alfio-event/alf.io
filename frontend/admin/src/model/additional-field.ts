import {AdditionalItem} from "./additional-item.ts";

export interface AdditionalField {
    id?: number;
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

export interface RestrictedValueRequest {
    value: string;
    enabled: boolean;
}

export interface DescriptionRequest {
    label: string;
    placeholder: string;
    restrictedValues?: {[k: string]: string};
}

export interface AdditionalFieldCreateRequest {
    order: number;
    userDefinedOrder: boolean;
    name: string;
    type: AdditionalFieldType;
    required: boolean;
    readOnly: boolean;
    minLength?: number;
    maxLength?: number;
    restrictedValues?: RestrictedValueRequest[];
    description: {[lang: string]: DescriptionRequest};
    forAdditionalService?: AdditionalItem;
    categoryIds: number[];
    displayAtCheckIn: boolean;
}


export interface AdditionalFieldTemplate {
    name: string;
    type: AdditionalFieldType;
    maxLength?: number;
    minLength?: number;
    restrictedValues?: string[];
    description: {[lang: string]: PurchaseContextFieldDescription};
}

export interface NewAdditionalFieldFromTemplate extends AdditionalFieldTemplate {
    order: number;
}

export interface AdditionalFieldStats {
    name: string;
    count: number;
    percentage: number;
}

export type AdditionalFieldContext = 'ATTENDEE' | 'ADDITIONAL_SERVICE' | 'SUBSCRIPTION';

export type AdditionalFieldType = 'input:text' | 'input:tel' | 'vat:eu' | 'textarea' | 'country' | 'select' | 'checkbox' | 'radio' | 'input:dateOfBirth';

export const additionalFieldTypesWithDescription = {
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

export function supportsPlaceholder(fieldType: AdditionalFieldType) {
    return fieldType === 'input:text'
        || fieldType === 'input:tel'
        || fieldType === 'vat:eu'
        || fieldType === 'textarea'
        || fieldType === 'input:dateOfBirth';
}

export function supportsRestrictedValues(fieldType: AdditionalFieldType) {
    return fieldType === 'checkbox'
        || fieldType === 'radio'
        || fieldType === 'select';
}

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
    const mapped = additionalFieldTypesWithDescription[type];
    return mapped ?? 'unknown';
}

export function supportsMinMaxLength(fieldType: AdditionalFieldType): boolean {
    return fieldType === 'input:text'
        || fieldType === 'input:tel'
        || fieldType === 'textarea'
        || fieldType === 'input:dateOfBirth';
}
