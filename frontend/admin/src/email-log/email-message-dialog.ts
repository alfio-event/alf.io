import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import type {SlDialog} from '@shoelace-style/shoelace';
import {badges, base, detailList, dialog, modernLayout, retroCompat, row} from '../styles.ts';
import {PurchaseContextType} from '../model/purchase-context.ts';
import {EmailMessage} from '../model/email-message.ts';
import {dispatchFeedback} from '../model/dom-events.ts';
import {EmailLogService} from '../service/email-log.ts';
import {dialogTitle} from '../components/prompt-dialog.ts';
import {emailStatus, emailStatusBadge} from './email-status.ts';
import '../components/format-date.ts';

/**
 * Shows the content and the delivery status of an e-mail. Read-only.
 */
@customElement('alfio-email-message-dialog')
export class EmailMessageDialog extends LitElement {
    @property({ type: String, attribute: 'purchase-context-type' }) purchaseContextType: PurchaseContextType = 'event';
    @property({ type: String, attribute: 'public-identifier' }) publicIdentifier = '';
    @property({ type: String, attribute: 'time-zone' }) timeZone = 'UTC';

    @state() private message: EmailMessage | null = null;

    @query('sl-dialog') private dialog!: SlDialog;

    static readonly styles = [
        base,
        retroCompat,
        badges,
        emailStatus,
        modernLayout,
        detailList,
        dialog,
        row,
        css`
            sl-dialog {
                --alfio-dialog-max-width: 48rem;
            }
            .message-body {
                margin: 0;
                padding: var(--sl-spacing-small) var(--sl-spacing-medium);
                max-height: 24rem;
                overflow: auto;
                background: var(--sl-color-gray-50);
                border: 1px solid var(--sl-color-gray-200);
                border-radius: var(--sl-input-border-radius);
                font-family: inherit;
                font-size: var(--sl-font-size-small);
                white-space: pre-wrap;
                overflow-wrap: anywhere;
            }
            .checksum {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-2x-small);
            }
        `,
    ];

    async open(messageId: number): Promise<void> {
        this.message = null;
        await this.updateComplete;
        // don't wait for the animation: show() never resolves when called right after the first render
        void this.dialog.show();
        try {
            this.message = await EmailLogService.load(this.purchaseContextType, this.publicIdentifier, messageId);
        } catch {
            dispatchFeedback({ type: 'danger', message: 'Failed to load the e-mail' }, this);
            await this.dialog.hide();
        }
    }

    render(): TemplateResult {
        const title = this.message?.subject ?? 'E-mail';
        return html`
            <sl-dialog class="responsive-dialog" label=${title} placement="bottom">
                ${dialogTitle('envelope', title, this.description())}
                ${when(this.message,
                    () => this.renderMessage(this.message!),
                    () => html`<div class="dialog-loading"><sl-spinner></sl-spinner></div>`)}
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => this.dialog.hide()}>Close</sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    private renderMessage(message: EmailMessage): TemplateResult {
        return html`
            <div class="dialog-form form-stack">
                <div class="section-card">
                    <div class="dialog-section-header">
                        <sl-icon name="send"></sl-icon>Delivery
                        <sl-badge variant="primary" pill>${this.timeZone}</sl-badge>
                    </div>
                    <div class="section-body">
                        <dl class="detail-list">
                            <dt>Status</dt>
                            <dd>${emailStatusBadge(message.status)}</dd>
                            <dt>Recipient</dt>
                            <dd>${message.recipient}</dd>
                            ${when(message.cc?.length > 0, () => html`
                                <dt>CC</dt>
                                <dd>${message.cc.join(', ')}</dd>
                            `)}
                            <dt>Requested</dt>
                            <dd><alfio-format-date date=${message.requestTimestamp} time-zone=${this.timeZone}></alfio-format-date></dd>
                            <dt>Sent</dt>
                            <dd>
                                <alfio-format-date date=${message.sentTimestamp ?? ''} time-zone=${this.timeZone}>Not sent yet</alfio-format-date>
                            </dd>
                            ${when(message.attempts > 0, () => html`
                                <dt>Attempts</dt>
                                <dd>${message.attempts.toLocaleString()}</dd>
                            `)}
                            <dt>Checksum</dt>
                            <dd class="checksum">
                                <span class="monospace">${message.checksum}</span>
                                <sl-copy-button value=${message.checksum} copy-label="Copy checksum"></sl-copy-button>
                            </dd>
                        </dl>
                    </div>
                </div>
                <div class="section-card">
                    <div class="dialog-section-header"><sl-icon name="card-text"></sl-icon>Message</div>
                    <div class="section-body">
                        <pre class="message-body">${message.message}</pre>
                    </div>
                </div>
            </div>
        `;
    }

    private description(): string {
        if (this.message == null) {
            return 'Loading the e-mail…';
        }
        return `Content and delivery status of the e-mail sent to ${this.message.recipient}.`;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-email-message-dialog': EmailMessageDialog;
    }
}
