import {css, html, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {base, detailList, dialog, modernLayout, retroCompat, row} from '../../styles.ts';
import {PendingPaymentTransaction} from '../../model/reservation.ts';
import {formatAmount, shortReservationId} from '../../service/reservation-format.ts';
import {dialogTitle, PromptDialog} from '../../components/prompt-dialog.ts';
import {detailList as renderDetailList} from '../../components/detail-list.ts';
import '../../components/format-date.ts';

export type MatchingTransactionChoice = 'confirm' | 'discard';

export type MatchingTransaction = Pick<PendingPaymentTransaction, 'transactionId' | 'timestamp' | 'priceInCents' | 'currency'>;

/**
 * Displays the transaction that the payment provider has matched with a pending payment,
 * and lets the user either confirm the payment or flag the transaction as not valid.
 */
@customElement('alfio-matching-transaction-dialog')
export class MatchingTransactionDialog extends PromptDialog<MatchingTransactionChoice> {
    @property({ type: String, attribute: 'time-zone' }) timeZone = 'UTC';
    @state() private reservationId = '';
    @state() private transaction: MatchingTransaction | null = null;

    static readonly styles = [
        base,
        retroCompat,
        dialog,
        modernLayout,
        detailList,
        row,
        css`
            sl-dialog {
                --alfio-dialog-max-width: 36rem;
            }
        `,
    ];

    open(reservationId: string, transaction: MatchingTransaction): Promise<MatchingTransactionChoice | null> {
        this.reservationId = reservationId;
        this.transaction = transaction;
        return this.prompt();
    }

    render(): TemplateResult {
        return html`
            <sl-dialog class="responsive-dialog" label="Matching transaction" placement="bottom"
                       @sl-after-hide=${this.onAfterHide}>
                ${dialogTitle('arrow-left-right',
                    `Matching transaction for ${shortReservationId(this.reservationId)}`,
                    'The payment provider has received a payment which matches this reservation.')}
                ${when(this.transaction, () => this.renderTransaction(this.transaction!))}
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => this.close(null)}>Cancel</sl-button>
                        <sl-button variant="danger" size="large" outline @click=${() => this.close('discard')}>
                            <sl-icon name="x-octagon" slot="prefix"></sl-icon>
                            Flag as not valid
                        </sl-button>
                        <sl-button variant="success" size="large" @click=${() => this.close('confirm')}>
                            <sl-icon name="check2" slot="prefix"></sl-icon>
                            Confirm
                        </sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    private renderTransaction(transaction: MatchingTransaction): TemplateResult {
        return renderDetailList([
            { label: 'Payment ID', value: transaction.transactionId ?? '', monospace: true },
            { label: 'Received on', value: html`<alfio-format-date date=${transaction.timestamp} time-zone=${this.timeZone}></alfio-format-date>` },
            { label: 'Paid amount', value: html`<strong>${formatAmount(transaction.priceInCents / 100, transaction.currency)}</strong>` },
        ]);
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-matching-transaction-dialog': MatchingTransactionDialog;
    }
}
