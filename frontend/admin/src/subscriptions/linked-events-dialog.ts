import {html, LitElement, TemplateResult} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import type {SlDialog} from '@shoelace-style/shoelace';
import {EventSubscriptionLink} from '../model/subscription-descriptor.ts';
import {formatAmount} from '../service/reservation-format.ts';
import {badges, dialog, modernLayout, modernTable} from '../styles.ts';

/**
 * Shows the events linked to a subscription. The content is set by show(), so that every
 * opening starts from the data of the clicked subscription.
 */
@customElement('alfio-linked-events-dialog')
export class LinkedEventsDialog extends LitElement {

    @state()
    private links: EventSubscriptionLink[] | null = null;

    @query('sl-dialog')
    private dialog!: SlDialog;

    static readonly styles = [dialog, modernLayout, modernTable, badges];

    async show(links: EventSubscriptionLink[]): Promise<void> {
        this.links = links;
        await this.updateComplete;
        await this.dialog.show();
    }

    render(): TemplateResult {
        const links = this.links ?? [];
        return html`
            <sl-dialog class="responsive-dialog" label="Linked events" @sl-after-hide=${this.onAfterHide}>
                ${when(this.links == null,
                    () => html`<div class="dialog-loading"><sl-spinner></sl-spinner></div>`,
                    () => html`
                        <div class="table-responsive">
                            <table class="table">
                                <thead>
                                <tr>
                                    <th scope="col">Name</th>
                                    <th scope="col">Price per ticket</th>
                                </tr>
                                </thead>
                                <tbody>
                                ${repeat(links, (link) => link.eventId, (link) => html`
                                    <tr>
                                        <td>${link.eventDisplayName}</td>
                                        <td>${formatAmount(link.formattedPricePerTicket, link.eventCurrency)}</td>
                                    </tr>
                                `)}
                                </tbody>
                            </table>
                        </div>
                    `)}
                <div slot="footer">
                    <sl-button variant="default" size="large" @click=${() => this.dialog.hide()}>Close</sl-button>
                </div>
            </sl-dialog>
        `;
    }

    private onAfterHide = (event: Event): void => {
        // nested Shoelace components (e.g. tooltips, selects) emit their own sl-after-hide
        if (event.target !== event.currentTarget) {
            return;
        }
        this.links = null;
    };
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-linked-events-dialog': LinkedEventsDialog;
    }
}
