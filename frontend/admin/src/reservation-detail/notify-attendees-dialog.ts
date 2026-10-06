import {css, html, TemplateResult} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import type {SlCheckbox} from '@shoelace-style/shoelace';
import {base, dialog, modernLayout, retroCompat, row} from '../styles.ts';
import {Ticket} from '../model/reservation-detail.ts';
import {dialogTitle, PromptDialog} from '../components/prompt-dialog.ts';
import {formatFullName} from '../service/reservation-format.ts';

export interface AttendeesByCategory {
    name: string;
    tickets: Ticket[];
}

/**
 * Lets the user choose the attendees who will receive their ticket by e-mail.
 * The promise returned by open() is resolved with the IDs of the selected tickets.
 */
@customElement('alfio-notify-attendees-dialog')
export class NotifyAttendeesDialog extends PromptDialog<number[]> {
    @state() private categories: AttendeesByCategory[] = [];
    @state() private selected = new Set<number>();

    static readonly styles = [
        base,
        retroCompat,
        dialog,
        modernLayout,
        row,
        css`
            .attendees {
                display: grid;
                gap: var(--sl-spacing-x-small);
            }
            .attendee-email {
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-small);
            }
            .selection {
                display: flex;
                gap: var(--sl-spacing-x-small);
            }
        `,
    ];

    open(categories: AttendeesByCategory[]): Promise<number[] | null> {
        this.categories = categories;
        this.selectAll(true);
        return this.prompt();
    }

    render(): TemplateResult {
        return html`
            <sl-dialog class="responsive-dialog" label="Send tickets via e-mail" placement="bottom"
                       @sl-after-hide=${this.onAfterHide}>
                ${dialogTitle('envelope', 'Send tickets via e-mail', 'Select the attendees who will receive their ticket.')}
                <div class="dialog-form form-stack">
                    ${repeat(this.categories, (category) => category.name, (category) => html`
                        <div class="section-card">
                            <div class="dialog-section-header"><sl-icon name="ticket-perforated"></sl-icon>${category.name}</div>
                            <div class="section-body attendees">
                                ${repeat(category.tickets, (ticket) => ticket.id, (ticket) => html`
                                    <sl-checkbox .checked=${this.selected.has(ticket.id)}
                                                 @sl-change=${(e: Event) => this.toggle(ticket.id, (e.target as SlCheckbox).checked)}>
                                        ${formatFullName(ticket)}
                                        <span class="attendee-email">${ticket.email ?? ''}</span>
                                    </sl-checkbox>
                                `)}
                            </div>
                        </div>
                    `)}
                    <div class="selection">
                        <sl-button variant="text" size="small" @click=${() => this.selectAll(true)}>Select all</sl-button>
                        <sl-button variant="text" size="small" @click=${() => this.selectAll(false)}>Select none</sl-button>
                    </div>
                </div>
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => this.close(null)}>Cancel</sl-button>
                        <div></div>
                        <sl-button variant="warning" size="large" ?disabled=${this.selected.size === 0}
                                   @click=${() => this.close([...this.selected])}>
                            <sl-icon name="send" slot="prefix"></sl-icon>
                            Send
                        </sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    private selectAll(select: boolean): void {
        if (select) {
            this.selected = new Set(this.categories.flatMap(category => category.tickets.map(ticket => ticket.id)));
        } else {
            this.selected = new Set();
        }
    }

    private toggle(ticketId: number, checked: boolean): void {
        const selected = new Set(this.selected);
        if (checked) {
            selected.add(ticketId);
        } else {
            selected.delete(ticketId);
        }
        this.selected = selected;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-notify-attendees-dialog': NotifyAttendeesDialog;
    }
}
