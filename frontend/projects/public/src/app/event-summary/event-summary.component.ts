import { Component, Input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import type { DateValidity } from '../model/date-validity';
import type { Event } from '../model/event';

@Component({
    selector: 'app-event-summary',
    templateUrl: './event-summary.component.html',
})
export class EventSummaryComponent {
    @Input()
    event: Event;

    @Input()
    dateValidityProvider: DateValidity;

    constructor(public translate: TranslateService) {}

    get isEventOnline(): boolean {
        return this.event.format === 'ONLINE';
    }
}
