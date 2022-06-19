import {Component, Input, Output, EventEmitter} from '@angular/core';
import {TicketCategory} from '../model/ticket-category';
import {FormGroup} from '@angular/forms';

@Component({
  selector: 'app-ticket-quantity-selector',
  templateUrl: './ticket-quantity-selector.html'
})
export class TicketQuantitySelectorComponent {

  @Input()
  parentGroup: FormGroup;

  @Input()
  category: TicketCategory;

  @Input()
  quantityRange: number[];

  @Output()
  valueChange = new EventEmitter<number>();

  formGroup: FormGroup;

  selectionChanged(): void {
    this.valueChange.next(this.parentGroup.get('amount').value);
  }
}
