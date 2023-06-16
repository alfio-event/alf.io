export type TicketAccessType = 'INHERIT' | 'IN_PERSON' | 'ONLINE';

export interface TicketTokenStatus {
  accessCodeId: number | null;
  code: string;
  id: number;
  priceInCents: number;
  recipientEmail: string | null;
  recipientName: string | null;
  sentTimestamp: string | null;
  status: string;
  ticketCategoryId: number;
}

export interface TicketCategory {
  accessRestricted: boolean;
  availableTickets: number | null;
  bounded: boolean;
  checkedInTickets: number;
  containingOrphans: boolean;
  containingStuckTickets: boolean;
  description: { [key: string]: string };
  displayTaxInformation: boolean;
  expired: boolean;
  formattedDiscountedPrice: string;
  formattedExpiration: { [key: string]: string };
  formattedFinalPrice: string;
  formattedInception: { [key: string]: string };
  free: boolean;
  hasDiscount: boolean;
  id: number;
  maximumSaleableTickets: number;
  maxTickets: number;
  name: string;
  notSoldTickets: number;
  pendingTickets: number;
  saleableAndLimitNotReached: boolean;
  saleInFuture: boolean;
  soldOutOrLimitReached: boolean;
  soldTickets: number;
  ticketAccessType: TicketAccessType;
  tokenStatus: TicketTokenStatus[];
}

export class UiTicketCategory implements TicketCategory {
  accessRestricted: boolean;
  availableTickets: number | null;
  bounded: boolean;
  checkedInTickets: number;
  containingOrphans: boolean;
  containingStuckTickets: boolean;
  description: { [key: string]: string };
  displayTaxInformation: boolean;
  displayWarning: boolean;
  expired: boolean;
  formattedDiscountedPrice: string;
  formattedExpiration: { [key: string]: string };
  formattedFinalPrice: string;
  formattedInception: { [key: string]: string };
  free: boolean;
  hasDiscount: boolean;
  id: number;
  maximumSaleableTickets: number;
  maxTickets: number;
  name: string;
  notSoldTickets: number;
  pendingTickets: number;
  saleableAndLimitNotReached: boolean;
  saleInFuture: boolean;
  soldOutOrLimitReached: boolean;
  soldTickets: number;
  ticketAccessType: TicketAccessType;
  tokenViewExpanded: boolean;
  attendeesList: { groupId: number; groupName: string } | null;
  tokenStatus: TicketTokenStatus[];

  constructor(ticketCategory: TicketCategory) {
    this.accessRestricted = ticketCategory.accessRestricted;
    this.availableTickets = ticketCategory.availableTickets;
    this.bounded = ticketCategory.bounded;
    this.checkedInTickets = ticketCategory.checkedInTickets;
    this.containingOrphans = ticketCategory.containingOrphans;
    this.containingStuckTickets = ticketCategory.containingStuckTickets;
    this.description = ticketCategory.description;
    this.displayTaxInformation = ticketCategory.displayTaxInformation;
    this.displayWarning =
      ticketCategory.containingStuckTickets || ticketCategory.containingOrphans;
    this.expired = ticketCategory.expired;
    this.formattedDiscountedPrice = ticketCategory.formattedDiscountedPrice;
    this.formattedExpiration = ticketCategory.formattedExpiration;
    this.formattedFinalPrice = ticketCategory.formattedFinalPrice;
    this.formattedInception = ticketCategory.formattedInception;
    this.free = ticketCategory.free;
    this.hasDiscount = ticketCategory.hasDiscount;
    this.id = ticketCategory.id;
    this.maximumSaleableTickets = ticketCategory.maximumSaleableTickets;
    this.maxTickets = ticketCategory.maxTickets;
    this.name = ticketCategory.name;
    this.saleableAndLimitNotReached = ticketCategory.saleableAndLimitNotReached;
    this.saleInFuture = ticketCategory.saleInFuture;
    this.soldOutOrLimitReached = ticketCategory.soldOutOrLimitReached;
    this.soldTickets = ticketCategory.soldTickets;
    this.ticketAccessType = ticketCategory.ticketAccessType;
    this.pendingTickets = ticketCategory.pendingTickets;
    this.notSoldTickets = ticketCategory.notSoldTickets;
    this.tokenStatus = ticketCategory.tokenStatus;
    this.tokenViewExpanded = false;
    this.attendeesList = null;
  }
}

export interface TicketCategoryFilter {
  active: boolean;
  expired: boolean;
  search: string;
}
