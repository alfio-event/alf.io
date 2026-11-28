import {AdditionalItem} from "./additional-item.ts";
import { t } from '../service/i18n';

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
    'input:text': t('admin.additional-field.type.text'),
    'input:tel': t('admin.additional-field.type.phone'),
    'vat:eu': t('admin.additional-field.type.vat'),
    'textarea': t('admin.additional-field.type.textarea'),
    'country': t('admin.additional-field.type.country'),
    'select': t('admin.additional-field.type.select'),
    'radio': t('admin.additional-field.type.radio'),
    'checkbox': t('admin.additional-field.type.checkbox'),
    'input:dateOfBirth': t('admin.additional-field.type.date-of-birth')
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

/**
 * The options of a field that can be chosen, without the disabled ones. Empty for a field without options.
 */
export function allowedValues(field: AdditionalField): string[] {
    return (field.restrictedValues ?? []).filter(value => !(field.disabledValues ?? []).includes(value));
}
