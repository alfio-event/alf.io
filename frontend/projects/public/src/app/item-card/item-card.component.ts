import {Component, Input} from '@angular/core';
import {UntypedFormGroup} from '@angular/forms';
import {TicketCategory} from '../model/ticket-category';
import {AdditionalService} from '../model/additional-service';
import {BasicEventInfo} from '../model/basic-event-info';

@Component({
  selector: 'app-item-card',
  templateUrl: './item-card.html',
  styleUrls: ['./item-card.scss']
})
export class ItemCardComponent {
  @Input()
  parentFormGroup: UntypedFormGroup;

  @Input()
  item: TicketCategory | AdditionalService;

  @Input()
  event: BasicEventInfo;

  @Input()
  additionalClass = '';

  @Input()
  currentLang: string;
}
