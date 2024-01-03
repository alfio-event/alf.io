import {Component, Input} from '@angular/core';
import {NgbActiveModal} from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'app-reservation-expired',
    templateUrl: './reservation-expired.component.html'
})
export class ReservationExpiredComponent {
    @Input()
    name: string;

    constructor(public activeModal: NgbActiveModal) {}
}
