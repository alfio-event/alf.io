import type { AdditionalService } from "./additional-service";
import type { TicketCategory } from "./ticket-category";

export class ItemsByCategory {
  ticketCategories: TicketCategory[];
  expiredCategories: TicketCategory[];
  additionalServices: AdditionalService[];

  waitingList: boolean;
  preSales: boolean;
  ticketCategoriesForWaitingList: TicketCategoryForWaitingList[];
}

export class TicketCategoryForWaitingList {
  id: number;
  name: string;
}
