import {Component} from '@angular/core';
import {NgbActiveModal} from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'app-release-ticket',
    templateUrl: './release-ticket.html'
})
export class ReleaseTicketComponent {
    constructor(public activeModal: NgbActiveModal) {
    }
}
