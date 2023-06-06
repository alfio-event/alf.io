import { Organization } from './organization';

export interface EventInfo {
  allowedPaymentProxies: string[]; // TODO: enum
  availableSeats: number;
  checkedInTickets: number;
  description: { [lang: string]: string };
  displayName: string;
  displayStatistics: boolean;
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
