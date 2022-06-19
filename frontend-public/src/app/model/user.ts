import {BillingDetails, ReservationStatus} from './reservation-info';
import {PurchaseContextType} from '../shared/purchase-context.service';

export interface AuthenticationStatus {
  enabled: boolean;
  user?: User;
}

export interface User {
  firstName?: string;
  lastName?: string;
  emailAddress?: string;
  profile?: UserProfile;
  external?: boolean;
}

export interface UserProfile {
  billingDetails: BillingDetails;
  additionalData: UserAdditionalData;
}

export interface UserAdditionalData {
  [key: string]: AdditionalInfoWithLabel;
}

export interface AdditionalInfoWithLabel {
  label: {[key: string]: string};
  values: string[];
}

export const ANONYMOUS: User = {};

export interface PurchaseContextWithReservation {
  title: { [lang: string]: string };
  publicIdentifier: string;
  type: PurchaseContextType;
  formattedStartDate?: {[key: string]: string};
  formattedEndDate?: {[key: string]: string};
  sameDay: boolean;
  reservations: Array<ReservationHeader>;
}

export interface ReservationHeader {
  id: string;
  status: ReservationStatus;
  formattedExpiresOn: {[key: string]: string};
  formattedConfirmedOn: {[key: string]: string};
  formattedCreatedOn: {[key: string]: string};
  invoiceNumber: string;
  finalPrice: number;
  currencyCode: string;
  usedVatPercent: string;
  vatStatus: string;
  items: Array<PurchaseContextItem>;
}

export interface ClientRedirect {
  targetUrl?: string;
  empty: boolean;
}

export interface PurchaseContextItem {
  id: string;
  firstName: string;
  lastName: string;
  type: { [k: string]: string };
}
