import {Component, Input} from '@angular/core';
import {TicketCategory} from '../model/ticket-category';
import {AdditionalService} from '../model/additional-service';

@Component({
  selector: 'app-item-sale-period',
  templateUrl: './item-sale-period.html'
})
export class ItemSalePeriodComponent {
  @Input()
  item: TicketCategory | AdditionalService;
  @Input()
  currentLang: string;
}
