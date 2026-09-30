import {Component, Input} from '@angular/core';
import {Event} from '../model/event';
import {TranslateService} from '@ngx-translate/core';
import {DateValidity} from '../model/date-validity';

@Component({
  selector: 'app-event-summary',
  templateUrl: './event-summary.component.html'
})
export class EventSummaryComponent {

  @Input()
  event: Event;

  @Input()
  dateValidityProvider: DateValidity;

  constructor(public translate: TranslateService) { }

  get isEventOnline(): boolean {
    return this.event.format === 'ONLINE';
  }

}
