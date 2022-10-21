import { DateValidity, DatesWithOffset } from './date-validity';

export class TicketInfo implements DateValidity {
    fullName: string;
    email: string;
    uuid: string;

    ticketCategoryName: string;
    reservationFullName: string;
    reservationId: string;

    deskPaymentRequired: boolean;


    timeZone: string;
    sameDay: boolean;
    datesWithOffset: DatesWithOffset;
    formattedBeginDate: {[key: string]: string}; // day, month, year
    formattedBeginTime: {[key: string]: string}; // the hour/minute component
    formattedEndDate: {[key: string]: string};
    formattedEndTime: {[key: string]: string};
}
