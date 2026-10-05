import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import type {SlDialog, SlInput, SlTextarea} from '@shoelace-style/shoelace';
import {base, dialog, form, modernLayout, retroCompat, row} from '../../styles.ts';
import {PaymentTransaction} from '../../model/reservation.ts';
import {DateTimeModification} from '../../model/event.ts';
import {PurchaseContextType} from '../../model/purchase-context.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {fetchJson, postJson, putJson, toDateTimeModification} from '../../service/helpers.ts';
import {shortReservationId} from '../../service/reservation-format.ts';
import {formatDate, toDateTimeLocal} from '../../service/date-format.ts';
import {dialogTitle} from '../../components/prompt-dialog.ts';

const ONE_YEAR_MS = 365 * 24 * 60 * 60 * 1000;

interface PaymentInfoResult {
    success: boolean;
    data?: { transaction?: PaymentTransaction };
}

type DialogMode = 'edit' | 'confirm';

interface DialogModeConfig {
    title: string;
    description: string;
    action: string;
    variant: 'warning' | 'success';
    successMessage: string;
    errorMessage: string;
}

const MODES: Record<DialogMode, DialogModeConfig> = {
    edit: {
        title: 'Edit payment for',
        description: 'Update when the payment has been received and add internal notes.',
        action: 'Save',
        variant: 'warning',
        successMessage: 'Payment updated successfully',
        errorMessage: 'Failed to update payment',
    },
    confirm: {
        title: 'Confirm payment for',
        description: 'Register when the payment has been received. Tickets will be sent to the customer.',
        action: 'Confirm',
        variant: 'success',
        successMessage: 'Payment confirmed successfully',
        errorMessage: 'Failed to confirm payment',
    },
};

/**
 * Collects the metadata (payment date and notes) of a payment:
 * - open() loads and edits a confirmed payment
 * - openForConfirmation() confirms a pending (offline) payment. Events only.
 * Fires "alfio-dialog-closed" once the dialog is closed.
 */
@customElement('alfio-edit-payment-dialog')
export class EditPaymentDialog extends LitElement {
    @property({ type: String, attribute: 'purchase-context-type' }) purchaseContextType: PurchaseContextType = 'event';
    @property({ type: String, attribute: 'public-identifier' }) publicIdentifier = '';
    @property({ type: String, attribute: 'time-zone' }) timeZone = 'UTC';

    @state() private mode: DialogMode = 'edit';
    @state() private reservationId = '';
    @state() private transaction: PaymentTransaction | null = null;
    @state() private saving = false;
    @state() private errorMessage = '';
    private saved = false;

    @query('sl-dialog') private dialog!: SlDialog;
    @query('form') private form!: HTMLFormElement;

    static readonly styles = [
        base,
        retroCompat,
        form,
        dialog,
        modernLayout,
        row,
        css`
            sl-dialog {
                --alfio-dialog-max-width: 40rem;
            }
        `,
    ];

    async open(reservationId: string): Promise<void> {
        this.reset('edit', reservationId, null);
        await this.dialog.show();
        try {
            const result = await fetchJson<PaymentInfoResult>(this.baseUrl(`/admin/api/reservation`, `${reservationId}/payment-info`));
            if (!result.success || result.data?.transaction == null) {
                throw new Error('payment info not found');
            }
            this.transaction = result.data.transaction;
        } catch {
            dispatchFeedback({ type: 'danger', message: 'Failed to load payment details' }, this);
            await this.dialog.hide();
        }
    }

    async openForConfirmation(reservationId: string): Promise<void> {
        this.reset('confirm', reservationId, {
            timestamp: new Date().toISOString(),
            timestampEditable: true,
            notes: '',
        });
        await this.dialog.show();
    }

    private reset(mode: DialogMode, reservationId: string, transaction: PaymentTransaction | null): void {
        this.mode = mode;
        this.reservationId = reservationId;
        this.transaction = transaction;
        this.errorMessage = '';
        this.saved = false;
    }

    render(): TemplateResult {
        const config = MODES[this.mode];
        return html`
            <sl-dialog class="responsive-dialog" label="${config.title} ${shortReservationId(this.reservationId)}" placement="bottom"
                       @sl-request-close=${this.onRequestClose}
                       @sl-after-hide=${this.onAfterHide}>
                ${dialogTitle('cash-coin', `${config.title} ${shortReservationId(this.reservationId)}`, config.description)}
                ${when(this.transaction,
                    () => this.renderForm(this.transaction!),
                    () => html`<div class="dialog-loading"><sl-spinner></sl-spinner></div>`)}
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" ?disabled=${this.saving}
                                   @click=${() => this.dialog.hide()}>Cancel</sl-button>
                        <div></div>
                        <sl-button variant=${config.variant} size="large" ?disabled=${this.saving || this.transaction == null}
                                   @click=${() => this.save()}>
                            ${when(this.saving,
                                () => html`<sl-spinner slot="prefix"></sl-spinner>`,
                                () => html`<sl-icon name="check2" slot="prefix"></sl-icon>`)}
                            ${config.action}
                        </sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    private renderForm(transaction: PaymentTransaction): TemplateResult {
        return html`
            <form class="dialog-form form-stack" @submit=${(e: Event) => { e.preventDefault(); this.save(); }}>
                <div class="section-card">
                    <div class="dialog-section-header">
                        <sl-icon name="calendar-event"></sl-icon>Payment received on
                        <sl-badge variant="primary" pill>${this.timeZone}</sl-badge>
                    </div>
                    <div class="section-body">
                        ${when(transaction.timestampEditable,
                            () => html`
                                <sl-input type="datetime-local" name="timestamp" label="Date and time" required
                                          min=${this.minTimestamp(transaction)}
                                          value=${toDateTimeLocal(transaction.timestamp, this.timeZone)}></sl-input>`,
                            () => html`
                                <sl-input name="timestamp" label="Date and time" readonly
                                          help-text="Recorded by the payment provider, it cannot be changed."
                                          value=${formatDate(transaction.timestamp, { timeZone: this.timeZone })}>
                                    <sl-icon name="lock" slot="suffix"></sl-icon>
                                </sl-input>`)}
                    </div>
                </div>
                <div class="section-card">
                    <div class="dialog-section-header"><sl-icon name="card-text"></sl-icon>Notes</div>
                    <div class="section-body">
                        <sl-textarea name="notes" label="Internal notes" rows="3" resize="auto"
                                     help-text="Visible only to administrators."
                                     value=${transaction.notes ?? ''}></sl-textarea>
                    </div>
                </div>
                ${when(this.errorMessage, () => html`
                    <sl-alert open variant="danger">
                        <sl-icon name="exclamation-triangle" slot="icon"></sl-icon>
                        ${this.errorMessage}
                    </sl-alert>
                `)}
            </form>
        `;
    }

    private async save(): Promise<void> {
        if (this.saving || this.transaction == null || !this.form.reportValidity()) {
            return;
        }
        const timestamp = this.form.querySelector<SlInput>('sl-input[name="timestamp"]')!.value;
        const notes = this.form.querySelector<SlTextarea>('sl-textarea[name="notes"]')!.value;
        const config = MODES[this.mode];
        this.saving = true;
        this.errorMessage = '';
        try {
            const response = await this.submit({ timestamp: this.editableTimestamp(timestamp), notes });
            if (response.ok) {
                this.saved = true;
                await this.dialog.hide();
                dispatchFeedback({ type: 'success', message: config.successMessage }, this);
            } else {
                this.errorMessage = (await response.text()) || config.errorMessage;
            }
        } catch {
            this.errorMessage = config.errorMessage;
        } finally {
            this.saving = false;
        }
    }

    private submit(metadata: { timestamp: DateTimeModification | null, notes: string }): Promise<Response> {
        if (this.mode === 'confirm') {
            const eventName = encodeURIComponent(this.publicIdentifier);
            return postJson(`/admin/api/events/${eventName}/pending-payments/${this.reservationId}/confirm`, metadata);
        }
        return putJson(this.baseUrl('/admin/api/payments', `reservation/${this.reservationId}`), metadata);
    }

    // payments can be backdated up to one year, unless they have already been recorded before that
    private minTimestamp(transaction: PaymentTransaction): string {
        const oneYearAgo = toDateTimeLocal(new Date(Date.now() - ONE_YEAR_MS).toISOString(), this.timeZone);
        const current = toDateTimeLocal(transaction.timestamp, this.timeZone);
        if (current !== '' && current < oneYearAgo) {
            return current;
        }
        return oneYearAgo;
    }

    private editableTimestamp(value: string) {
        if (this.transaction?.timestampEditable) {
            return toDateTimeModification(value);
        }
        return null;
    }

    private baseUrl(prefix: string, suffix: string): string {
        return `${prefix}/${this.purchaseContextType}/${encodeURIComponent(this.publicIdentifier)}/${suffix}`;
    }

    private onRequestClose = (e: CustomEvent): void => {
        if (this.saving) {
            e.preventDefault();
        }
    };

    private onAfterHide = (e: Event): void => {
        // sl-after-hide bubbles from nested components (e.g. tooltips)
        if (e.target !== this.dialog) {
            return;
        }
        this.dispatchEvent(new CustomEvent('alfio-dialog-closed', {
            detail: { success: this.saved },
            bubbles: true,
            composed: true,
        }));
    };
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-edit-payment-dialog': EditPaymentDialog;
    }
}
