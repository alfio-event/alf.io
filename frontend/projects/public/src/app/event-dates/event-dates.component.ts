import { Component, Input } from '@angular/core';
import type { TranslateService } from '@ngx-translate/core';
import type { DateValidity } from '../model/date-validity';
import { shouldDisplayTimeZoneInfo } from '../shared/event.service';

@Component({
    selector: 'app-event-dates',
    templateUrl: './event-dates.component.html',
})
export class EventDatesComponent {
    @Input()
    dateValidityProvider: DateValidity;
    @Input()
    displayIcon: boolean;

    constructor(public translate: TranslateService) {}

    get displayTimeZoneInfo(): boolean {
        return shouldDisplayTimeZoneInfo(this.dateValidityProvider);
    }

    get localizedStartDateForMultiDay(): string {
        return this.translate.instant('event-days.not-same-day', {
            '0': this.dateValidityProvider.formattedBeginDate[
                this.translate.currentLang
            ],
            '1': this.dateValidityProvider.formattedBeginTime[
                this.translate.currentLang
            ],
        });
    }

    get localizedEndDateForMultiDay(): string {
        return this.translate.instant('event-days.not-same-day', {
            '0': this.dateValidityProvider.formattedEndDate[
                this.translate.currentLang
            ],
            '1': this.dateValidityProvider.formattedEndTime[
                this.translate.currentLang
            ],
        });
    }
}
