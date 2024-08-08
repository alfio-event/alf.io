import {TicketCategory} from './ticket-category';
import {AdditionalService} from './additional-service';

export type ItemsByCategory = {

    ticketCategories: TicketCategory[];
    expiredCategories: TicketCategory[];
    additionalServices: AdditionalService[];

    waitingList: boolean;
    preSales: boolean;
    ticketCategoriesForWaitingList: TicketCategoryForWaitingList[];
}

export type TicketCategoryForWaitingList = {
    id: number;
    name: string;
}
