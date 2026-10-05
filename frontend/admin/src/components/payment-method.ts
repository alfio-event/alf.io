import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {paymentMethodIcon} from '../service/reservation-format.ts';

/**
 * Displays a payment method, preceded by an icon for the well-known ones
 */
@customElement('alfio-payment-method')
export class PaymentMethod extends LitElement {
    @property({ type: String }) method = '';

    static readonly styles = css`
        :host {
            display: inline-flex;
            align-items: center;
            gap: var(--sl-spacing-2x-small);
            font-size: var(--sl-font-size-small);
            color: var(--sl-color-gray-600);
        }
        sl-icon {
            color: var(--sl-color-gray-500);
        }
    `;

    render(): TemplateResult | typeof nothing {
        if (!this.method) {
            return nothing;
        }
        const icon = paymentMethodIcon(this.method);
        return html`${when(icon, () => html`<sl-icon name=${icon!}></sl-icon>`)}${this.method}`;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-payment-method': PaymentMethod;
    }
}
