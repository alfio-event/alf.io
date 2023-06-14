export type TicketAccessType = 'INHERIT' | 'IN_PERSON' | 'ONLINE';

export interface TicketCategory {
  id: number;
  name: string;
  ticketAccessType: TicketAccessType;
  bounded: boolean;
  maximumSaleableTickets: number;
  description: { [key: string]: string };
  free: boolean;
  formattedFinalPrice: string;
  hasDiscount: boolean;
  formattedDiscountedPrice: string;

  //
  expired: boolean;
  saleInFuture: boolean;
  formattedInception: { [key: string]: string };
  formattedExpiration: { [key: string]: string };
  //

  saleableAndLimitNotReached: boolean;
  accessRestricted: boolean;
  soldOutOrLimitReached: boolean;

  availableTickets: number | null;

  displayTaxInformation: boolean;
  containingStuckTickets: boolean;
  containingOrphans: boolean;
  maxTickets: number;
  soldTickets: number;
  checkedInTickets: number;
  pendingTickets: number;
  notSoldTickets: number;
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
  saleableAndLimitNotReached: boolean;
  saleInFuture: boolean;
  soldOutOrLimitReached: boolean;
  soldTickets: number;
  ticketAccessType: TicketAccessType;
  pendingTickets: number;
  notSoldTickets: number;

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
  }
}

export interface TicketCategoryFilter {
  active: boolean;
  expired: boolean;
  search: string;
}
