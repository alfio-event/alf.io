import { Component, Input, type OnInit } from '@angular/core';
import type { UntypedFormGroup } from '@angular/forms';
import type { PurchaseContext } from '../../model/purchase-context';
import type { ReservationMetadata } from '../../model/reservation-info';
import type { Ticket } from '../../model/ticket';

@Component({
    selector: 'app-ticket-form',
    templateUrl: './ticket-form.component.html',
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
        if (
            this.form &&
            this.purchaseContext &&
            this.purchaseContext.contentLanguages &&
            this.purchaseContext.contentLanguages.length === 1
        ) {
            this.form
                .get('userLanguage')
                .setValue(this.purchaseContext.contentLanguages[0].locale);
        }
    }

    getAdditional(form: UntypedFormGroup) {
        return form.get('additional') as UntypedFormGroup;
    }

    get emailEditForbidden(): boolean {
        return (
            this.ticket.locked ||
            (this.reservationMetadata?.lockEmailEdit &&
                this.ticket.email != null)
        );
    }
}
