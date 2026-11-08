import {html, LitElement, TemplateResult} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {base, modernLayout} from '../styles.ts';

/**
 * The container of a single subscription's screens (reservations, e-mail log, additional
 * fields, reservation detail): a link back to the list (or to the reservations, when coming
 * from a reservation) and the routed content, projected through the default slot.
 */
@customElement('alfio-subscription-detail')
export class SubscriptionDetail extends LitElement {

    @property({type: Number, attribute: 'data-organization-id'})
    organizationId = 0;

    @property({type: String, attribute: 'data-subscription-id'})
    subscriptionId = '';

    // 'true' when the current route is a reservation of this subscription
    @property({type: String, attribute: 'data-back-to-reservations'})
    backToReservations = '';

    static readonly styles = [base, modernLayout];

    render(): TemplateResult {
        const backToReservations = this.backToReservations === 'true';
        let href = `#/subscriptions/${this.organizationId}/list`;
        if (backToReservations) {
            href = `#/subscriptions/${this.organizationId}/${this.subscriptionId}/reservations`;
        }
        return html`
            <sl-button variant="text" class="back-link" href=${href}>
                <sl-icon slot="prefix" name="arrow-left"></sl-icon>
                ${when(backToReservations, () => 'Back to reservations', () => 'Back to list')}
            </sl-button>
            <slot></slot>
        `;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-subscription-detail': SubscriptionDetail;
    }
}
