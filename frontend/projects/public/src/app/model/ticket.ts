export interface TicketIdentifier {
  index: number;
  uuid: string;
  firstName: string;
  lastName: string;
  categoryName: string;
}

export interface MoveAdditionalServiceRequest {
  index: number;
  itemId: number;
  currentTicketUuid: string;
  newTicketUuid: string;
}

export class Ticket {
    uuid: string;
    firstName: string;
    lastName: string;
    email: string;
    fullName: string;
    userLanguage: string;
    assigned: boolean;
    locked: boolean;
    acquired: boolean;
    cancellationEnabled: boolean;
    sendMailEnabled: boolean;
    downloadEnabled: boolean;
    ticketFieldConfigurationBeforeStandard: AdditionalField[];
    ticketFieldConfigurationAfterStandard: AdditionalField[];
    formattedOnlineCheckInDate: {[key: string]: string};
    onlineEventStarted: boolean;
}

export class AdditionalField {
    name: string;
    value: string;
    type: AdditionalFieldType;
    required: boolean;
    editable: boolean;
    minLength: number;
    maxLength: number;
    restrictedValues: string[];
    fields: Field[];
    description: {[key: string]: Description};
}

export class Description {
    label: string;
    placeholder: string;
    restrictedValuesDescription: {[key: string]: string};
}

export class Field {
    fieldIndex: number;
    fieldValue: string;
}

export type AdditionalFieldType = 'input:text' | 'input:tel' | 'vat:eu' | 'textarea' | 'country' | 'select' | 'checkbox' | 'radio' | 'input:dateOfBirth';
