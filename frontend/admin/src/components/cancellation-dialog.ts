import {css, html, TemplateResult} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import type {SlCheckbox} from '@shoelace-style/shoelace';
import {base, detailList, dialog, modernLayout, retroCompat, row} from '../styles.ts';
import {dialogTitle, PromptDialog} from './prompt-dialog.ts';
import {DetailEntry, detailList as renderDetailList} from './detail-list.ts';

export interface CancellationChoice {
    refund: boolean;
    issueCreditNote: boolean;
    notify: boolean;
}

export interface CancellationRequest {
    icon: string;
    title: string;
    description: string;
    action: string;
    variant: 'warning' | 'danger';
    // what is going to be cancelled
    details?: (DetailEntry | null | false)[];
    // e.g. "the payment cannot be refunded automatically"
    warning?: string;
    // labels of the options offered to the user. An option without label is not displayed
    options: Partial<Record<keyof CancellationChoice, string>>;
    defaults: CancellationChoice;
}

const OPTION_ORDER: (keyof CancellationChoice)[] = ['refund', 'issueCreditNote', 'notify'];

/**
 * Asks for confirmation before cancelling something that may have been paid (a reservation, a ticket…),
 * and lets the user choose whether to refund the payment, issue a credit note and notify the customer.
 * The caller describes the operation with a CancellationRequest.
 */
@customElement('alfio-cancellation-dialog')
export class CancellationDialog extends PromptDialog<CancellationChoice> {
    @state() private request: CancellationRequest | null = null;
    @state() private choice: CancellationChoice = { refund: false, issueCreditNote: false, notify: false };

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
            .options {
                display: grid;
                gap: var(--sl-spacing-small);
            }
        `,
    ];

    open(request: CancellationRequest): Promise<CancellationChoice | null> {
        this.request = request;
        this.choice = { ...request.defaults };
        return this.prompt();
    }

    render(): TemplateResult {
        const request = this.request;
        return html`
            <sl-dialog class="responsive-dialog" label=${request?.title ?? ''} placement="bottom"
                       @sl-after-hide=${this.onAfterHide}>
                ${when(request, () => this.renderContent(request!))}
            </sl-dialog>
        `;
    }

    private renderContent(request: CancellationRequest): TemplateResult {
        const options = OPTION_ORDER.filter(option => request.options[option] != null);
        return html`
            ${dialogTitle(request.icon, request.title, request.description)}
            <div class="form-stack">
                ${when(request.warning, () => html`
                    <sl-alert open variant="warning">
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        ${request.warning}
                    </sl-alert>
                `)}
                ${when(request.details, () => renderDetailList(request.details!))}
                <div class="options">
                    ${repeat(options, (option) => option, (option) => html`
                        <sl-checkbox name=${option} .checked=${this.choice[option]}
                                     @sl-change=${(e: Event) => this.toggle(option, (e.target as SlCheckbox).checked)}>
                            ${request.options[option]}
                        </sl-checkbox>
                    `)}
                </div>
            </div>
            <div slot="footer">
                <sl-divider></sl-divider>
                <div class="row" style="--alfio-row-cols: 3">
                    <sl-button variant="default" size="large" @click=${() => this.close(null)}>Cancel</sl-button>
                    <div></div>
                    <sl-button variant=${request.variant} size="large" @click=${() => this.close(this.choice)}>
                        <sl-icon name=${request.icon} slot="prefix"></sl-icon>
                        ${request.action}
                    </sl-button>
                </div>
            </div>
        `;
    }

    private toggle(option: keyof CancellationChoice, checked: boolean): void {
        this.choice = { ...this.choice, [option]: checked };
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-cancellation-dialog': CancellationDialog;
    }
}
