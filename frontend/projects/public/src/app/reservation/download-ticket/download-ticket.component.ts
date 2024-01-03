import {Component, Input} from '@angular/core';
import {Ticket} from '../../model/ticket';
import {NgbActiveModal} from '@ng-bootstrap/ng-bootstrap';
import {WalletConfiguration} from '../../model/info';

@Component({
  selector: 'app-download-ticket',
  templateUrl: './download-ticket.component.html'
})
export class DownloadTicketComponent {
  @Input()
  ticket: Ticket;

  @Input()
  eventName: string;

  @Input()
  walletConfiguration: WalletConfiguration;

  constructor(private activeModal: NgbActiveModal) {
  }

  close(): void {
    this.activeModal.dismiss();
  }

  get gWalletEnabled(): boolean {
    return this.walletConfiguration != null && this.walletConfiguration.gWalletEnabled;
  }

  get passEnabled(): boolean {
    return this.walletConfiguration != null && this.walletConfiguration.passEnabled;
  }
}
