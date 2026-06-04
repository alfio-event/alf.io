import { Component } from '@angular/core';
import type { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'app-modal-remove-subscription',
    templateUrl: './modal-remove-subscription.component.html',
})
export class ModalRemoveSubscriptionComponent {
    constructor(public activeModal: NgbActiveModal) {}
}
