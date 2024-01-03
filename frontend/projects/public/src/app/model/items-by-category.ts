import {TicketCategory} from './ticket-category';
import {AdditionalService} from './additional-service';

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
