import {Component} from '@angular/core';
import {NgbActiveModal} from '@ng-bootstrap/ng-bootstrap';
import {Observable} from 'rxjs';

@Component({
    selector: 'app-cancel-reservation',
    templateUrl: './cancel-reservation.html'
})
export class CancelReservationComponent {

    /**
     * Optional action to run when the user confirms. If set, the modal stays open
     * (showing a spinner) until the action completes, then closes with 'yes',
     * or with 'error' if the action fails.
     */
    onConfirm: () => Observable<unknown>;
    processing = false;

    constructor(public activeModal: NgbActiveModal) {
    }

    confirm(): void {
        if (this.processing) {
            return;
        }
        if (this.onConfirm == null) {
            this.activeModal.close('yes');
            return;
        }
        this.processing = true;
        this.onConfirm().subscribe({
            next: () => this.activeModal.close('yes'),
            error: () => {
                this.processing = false;
                this.activeModal.close('error');
            }
        });
    }
}
