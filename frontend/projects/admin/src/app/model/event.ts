import { Organization } from './organization';

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
