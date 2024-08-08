import {Component, EventEmitter, Input, Output} from '@angular/core';
import {TicketCategory} from '../model/ticket-category';
import {UntypedFormGroup} from '@angular/forms';

@Component({
  selector: 'app-ticket-quantity-selector',
  templateUrl: './ticket-quantity-selector.html'
})
export class TicketQuantitySelectorComponent {

  @Input()
  parentGroup?: UntypedFormGroup;

  @Input()
  category?: TicketCategory;

  @Input()
  quantityRange: number[] = [];

  @Output()
  valueChange = new EventEmitter<number>();

  formGroup?: UntypedFormGroup;

  selectionChanged(): void {
    const val = this.parentGroup?.get('amount')?.value;
    this.valueChange.next(val);
  }
}
