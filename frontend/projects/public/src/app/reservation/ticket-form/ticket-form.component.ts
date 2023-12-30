import {Component, Input, OnInit} from '@angular/core';
import {UntypedFormGroup} from '@angular/forms';
import {ReservationMetadata} from '../../model/reservation-info';
import {Ticket} from "../../model/ticket";
import {PurchaseContext} from "../../model/purchase-context";

@Component({
  selector: 'app-ticket-form',
  templateUrl: './ticket-form.component.html'
})
export class TicketFormComponent implements OnInit {

  @Input()
  form: UntypedFormGroup;

  @Input()
  ticket: Ticket;

  @Input()
  purchaseContext: PurchaseContext;

  @Input()
  reservationMetadata: ReservationMetadata;

  public ngOnInit(): void {
    if (this.form && this.purchaseContext && this.purchaseContext.contentLanguages && this.purchaseContext.contentLanguages.length === 1) {
      this.form.get('userLanguage').setValue(this.purchaseContext.contentLanguages[0].locale);
    }
  }

  getAdditional(form: UntypedFormGroup) {
    return form.get('additional') as UntypedFormGroup;
  }

  get emailEditForbidden(): boolean {
    return this.ticket.locked || (this.reservationMetadata?.lockEmailEdit && this.ticket.email != null);
  }
}
