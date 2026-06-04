import { Component, EventEmitter, Input, Output } from "@angular/core";
import type { UntypedFormGroup } from "@angular/forms";
import type { TicketCategory } from "../model/ticket-category";

@Component({
  selector: "app-ticket-quantity-selector",
  templateUrl: "./ticket-quantity-selector.html",
})
export class TicketQuantitySelectorComponent {
  @Input()
  parentGroup: UntypedFormGroup;

  @Input()
  category: TicketCategory;

  @Input()
  quantityRange: number[];

  @Input()
  refreshInProgress: boolean = false;

  @Output()
  valueChange = new EventEmitter<number>();

  @Output()
  refreshCommand = new EventEmitter<number>();

  formGroup: UntypedFormGroup;

  selectionChanged(): void {
    this.valueChange.next(this.parentGroup.get("amount").value);
  }

  refreshCategories(): void {
    this.refreshCommand.next(new Date().getTime());
  }
}
