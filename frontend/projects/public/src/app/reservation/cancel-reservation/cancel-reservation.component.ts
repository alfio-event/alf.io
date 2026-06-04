import { Component } from '@angular/core';
import type { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'app-cancel-reservation',
    templateUrl: './cancel-reservation.html',
})
export class CancelReservationComponent {
    constructor(public activeModal: NgbActiveModal) {}
}
