export interface DateValidity {
    timeZone: string;
    sameDay: boolean;
    datesWithOffset: DatesWithOffset;
    formattedBeginDate: {[key: string]: string}; // day, month, year
    formattedBeginTime: {[key: string]: string}; // the hour/minute component
    formattedEndDate: {[key: string]: string};
    formattedEndTime: {[key: string]: string};
}

export interface DatesWithOffset {
    startDateTime: number;
    startTimeZoneOffset: number;
    endDateTime: number;
    endTimeZoneOffset: number;
}
