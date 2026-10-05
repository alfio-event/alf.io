import {css, html, TemplateResult} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import type {SlCheckbox} from '@shoelace-style/shoelace';
import {base, dialog, modernLayout, retroCompat, row} from '../../styles.ts';
import {shortReservationId} from '../../service/reservation-format.ts';
import {dialogTitle, PromptDialog} from '../../components/prompt-dialog.ts';

export interface CancelPaymentChoice {
    notify: boolean;
}

interface CancelModeConfig {
    icon: string;
    title: string;
    description: string;
    notifyLabel: string;
    variant: 'warning' | 'danger';
    notifyByDefault: boolean;
}

const CREDIT_NOTE: CancelModeConfig = {
    icon: 'arrow-counterclockwise',
    title: 'Issue credit note for',
    description: 'A credit note will be generated. The reservation will be listed under "Credit Note issued" in the reservations list.',
    notifyLabel: 'Send the credit note to the reservation contact person',
    variant: 'warning',
    notifyByDefault: false,
};

const DELETE: CancelModeConfig = {
    icon: 'trash',
    title: 'Delete reservation',
    description: 'The payment will be discarded and the reservation deleted for good.',
    notifyLabel: 'Send a notification email to the reservation contact person',
    variant: 'danger',
    notifyByDefault: true,
};

/**
 * Asks for confirmation before deleting a pending reservation, or issuing a credit note for it.
 */
@customElement('alfio-cancel-payment-dialog')
export class CancelPaymentDialog extends PromptDialog<CancelPaymentChoice> {
    @state() private reservationId = '';
    @state() private config: CancelModeConfig = DELETE;
    @state() private notify = true;

    static readonly styles = [
        base,
        retroCompat,
        dialog,
        modernLayout,
        row,
        css`
            sl-dialog {
                --alfio-dialog-max-width: 36rem;
            }
        `,
    ];

    open(reservationId: string, credit: boolean): Promise<CancelPaymentChoice | null> {
        this.reservationId = reservationId;
        this.config = DELETE;
        if (credit) {
            this.config = CREDIT_NOTE;
        }
        this.notify = this.config.notifyByDefault;
        return this.prompt();
    }

    render(): TemplateResult {
        const config = this.config;
        return html`
            <sl-dialog class="responsive-dialog" label="${config.title} ${shortReservationId(this.reservationId)}" placement="bottom"
                       @sl-after-hide=${this.onAfterHide}>
                ${dialogTitle(config.icon, `${config.title} ${shortReservationId(this.reservationId)}?`, config.description)}
                <sl-checkbox .checked=${this.notify}
                             @sl-change=${(e: Event) => { this.notify = (e.target as SlCheckbox).checked; }}>${config.notifyLabel}</sl-checkbox>
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => this.close(null)}>Cancel</sl-button>
                        <div></div>
                        <sl-button variant=${config.variant} size="large" @click=${() => this.close({ notify: this.notify })}>
                            <sl-icon name=${config.icon} slot="prefix"></sl-icon>
                            Proceed
                        </sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-cancel-payment-dialog': CancelPaymentDialog;
    }
}
