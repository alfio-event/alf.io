import { Component, Input } from '@angular/core';
import type { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import type { WalletConfiguration } from '../../model/info';
import type { Ticket } from '../../model/ticket';

@Component({
    selector: 'app-download-ticket',
    templateUrl: './download-ticket.component.html',
})
export class DownloadTicketComponent {
    @Input()
    ticket: Ticket;

    @Input()
    eventName: string;

    @Input()
    walletConfiguration: WalletConfiguration;

    constructor(private activeModal: NgbActiveModal) {}

    close(): void {
        this.activeModal.dismiss();
    }

    get gWalletEnabled(): boolean {
        return (
            this.walletConfiguration != null &&
            this.walletConfiguration.gWalletEnabled
        );
    }

    get passEnabled(): boolean {
        return (
            this.walletConfiguration != null &&
            this.walletConfiguration.passEnabled
        );
    }
}
