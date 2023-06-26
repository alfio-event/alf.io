import {Organization} from './organization';
import {TicketCategory} from './ticket-category';

export interface EventInfo {
  allowedPaymentProxies: string[]; // TODO: enum
  availableSeats: number;
  checkedInTickets: number;
  currency: string;
  description: { [lang: string]: string };
  displayName: string;
  displayStatistics: boolean;
  displayLanguage: string;
  contentLanguages: {
    displayLanguage: string;
    language: string;
  }[];
  dynamicAllocation: number;
  expired: boolean;
  fileBlobId: string;
  formattedBegin: string;
  formattedEnd: string;
  freeOfCharge: boolean;
  id: number;
  location: string;
  notAllocatedTickets: number;
  notSoldTickets: number;
  organizationId: number;
  online: boolean;
  pendingTickets: number;
  privacyPolicyUrl: string;
  releasedTickets: number;
  shortName: string;
  soldTickets: number;
  status: string; // TODO: enum
  termsAndConditionsUrl: string;
  timeZone: string;
  visibleForCurrentUser: boolean;
  warningNeeded: boolean;
  websiteUrl: string;
  grossIncome: number;
  regularPrice: number;
  finalPrice: number;
  vatPercentage: number;
  ticketCategories: TicketCategory[];
  format: string;
  containingUnboundedCategories: boolean;
}

export interface EventOrganizationInfo {
  event: EventInfo;
  organization: Organization;
}

export interface Event {
  id: number;
  displayName: string;
  shortName: string;
}

export interface EventTicketsStatistics {
  granularity: string;
  sold: {
    count: number;
    date: string;
  }[];
  reserved: {
    count: number;
    date: string;
  }[];
}
