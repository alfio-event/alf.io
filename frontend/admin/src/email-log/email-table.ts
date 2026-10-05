import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, query} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {PurchaseContextType} from '../model/purchase-context.ts';
import {EmailMessage} from '../model/email-message.ts';
import {badges, base, modernLayout, modernTable, reservationTable, retroCompat} from '../styles.ts';
import {emptyState} from '../components/empty-state.ts';
import {emailStatus, emailStatusBadge} from './email-status.ts';
import {EmailMessageDialog} from './email-message-dialog.ts';
import '../components/format-date.ts';
import './email-message-dialog.ts';

/**
 * Table of e-mails. "View" displays the selected e-mail in a dialog.
 * Used by the E-mail log and by the reservation detail ("data-" attributes are set by AngularJS).
 */
@customElement('alfio-email-table')
export class EmailTable extends LitElement {
    // the host interpolates an empty attribute until the messages have been loaded, which Lit's Array converter turns into null
    @property({ type: Array, attribute: 'data-messages' }) messages: EmailMessage[] | null = [];
    @property({ type: String, attribute: 'data-purchase-context-type' }) purchaseContextType: PurchaseContextType = 'event';
    @property({ type: String, attribute: 'data-public-identifier' }) publicIdentifier = '';
    @property({ type: String, attribute: 'data-time-zone' }) timeZone = 'UTC';
    @property({ type: String, attribute: 'data-empty-message' }) emptyMessage = 'No e-mails found';
    @query('alfio-email-message-dialog') private messageDialog!: EmailMessageDialog;

    static readonly styles = [
        base,
        retroCompat,
        badges,
        emailStatus,
        modernTable,
        modernLayout,
        reservationTable,
        css`
            :host {
                display: block;
            }
            .table > thead > tr > th:nth-child(1) {
                width: 22%;
            }
            .table > thead > tr > th:nth-child(2) {
                width: 38%;
            }
            .table > thead > tr > th:nth-child(3) {
                width: 10%;
            }
            .table > thead > tr > th:nth-child(4),
            .table > thead > tr > th:nth-child(5) {
                width: 12%;
            }
            .table > thead > tr > th:nth-child(6) {
                width: 6%;
            }
            .subject {
                font-weight: var(--sl-font-weight-semibold);
            }
            .message-preview {
                display: -webkit-box;
                -webkit-box-orient: vertical;
                -webkit-line-clamp: 2;
                overflow: hidden;
                margin-top: var(--sl-spacing-3x-small);
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-small);
                overflow-wrap: anywhere;
            }
            .email-status {
                white-space: nowrap;
            }
        `,
    ];

    /**
     * Displays the given message in a dialog
     */
    async openMessage(messageId: number): Promise<void> {
        await this.updateComplete;
        await this.messageDialog.open(messageId);
    }

    render(): TemplateResult {
        const messages = this.messages ?? [];
        return html`
            ${when(messages.length === 0,
                () => emptyState(this.emptyMessage),
                () => this.renderTable(messages))}
            <alfio-email-message-dialog
                purchase-context-type=${this.purchaseContextType}
                public-identifier=${this.publicIdentifier}
                time-zone=${this.timeZone}
            ></alfio-email-message-dialog>
        `;
    }

    private renderTable(messages: EmailMessage[]): TemplateResult {
        return html`
            <div class="table-responsive">
                <table class="table table-striped">
                    <thead>
                        <tr>
                            <th>Recipient</th>
                            <th>Subject</th>
                            <th>Status</th>
                            <th class="hide-small">Requested</th>
                            <th class="hide-small">Sent</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(messages, (message) => message.id, (message) => html`
                            <tr>
                                <td class="email">${message.recipient}</td>
                                <td>
                                    <div class="subject">${message.subject}</div>
                                    <div class="message-preview hide-small">${message.message}</div>
                                </td>
                                <td>${emailStatusBadge(message.status)}</td>
                                <td class="timestamp hide-small">
                                    <alfio-format-date date=${message.requestTimestamp} time-zone=${this.timeZone}></alfio-format-date>
                                </td>
                                <td class="timestamp hide-small">
                                    <alfio-format-date date=${message.sentTimestamp ?? ''} time-zone=${this.timeZone}></alfio-format-date>
                                </td>
                                <td>
                                    <div class="actions-cell">
                                        <sl-button size="small" variant="default" outline
                                                   @click=${() => this.messageDialog.open(message.id)}>
                                            <sl-icon slot="prefix" name="eye"></sl-icon>
                                            View
                                        </sl-button>
                                    </div>
                                </td>
                            </tr>
                        `)}
                    </tbody>
                </table>
            </div>
        `;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-email-table': EmailTable;
    }
}
