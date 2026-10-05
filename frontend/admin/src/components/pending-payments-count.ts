import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {subscribePendingPayments} from '../service/pending-payments.ts';
import {badges} from '../styles.ts';

/**
 * Displays the number of pending payments for an event. Nothing is rendered while there are no pending payments,
 * unless "show-zero" is set.
 *
 * display="badge" (default): a pill with the count
 * display="summary": "N payments pending"
 */
@customElement('alfio-pending-payments-count')
export class PendingPaymentsCount extends LitElement {

    @property({ type: String, attribute: 'event-name' })
    eventName = '';

    @property({ type: String })
    display: 'badge' | 'summary' = 'badge';

    @property({ type: Boolean, attribute: 'show-zero' })
    showZero = false;

    @state()
    private count: number | null = null;

    private unsubscribe: (() => void) | null = null;

    static readonly styles = [badges, css`
        :host {
            display: inline;
        }

        .summary {
            display: inline-flex;
            align-items: center;
            gap: var(--sl-spacing-2x-small);
            color: var(--sl-color-warning-700);
            font-size: var(--sl-font-size-small);
        }

        .summary.empty {
            color: var(--sl-color-gray-600);
        }
    `];

    connectedCallback(): void {
        super.connectedCallback();
        // first connection is handled by willUpdate
        if (this.hasUpdated) {
            this.subscribe();
        }
    }

    disconnectedCallback(): void {
        super.disconnectedCallback();
        this.cleanup();
    }

    protected willUpdate(changed: PropertyValues<this>): void {
        if (changed.has('eventName')) {
            this.subscribe();
        }
    }

    private subscribe(): void {
        this.cleanup();
        this.count = null;
        // AngularJS templates might set the attribute before interpolating it
        if (this.eventName.length > 0 && !this.eventName.includes('{{')) {
            this.unsubscribe = subscribePendingPayments(this.eventName, count => this.count = count);
        }
    }

    private cleanup(): void {
        this.unsubscribe?.();
        this.unsubscribe = null;
    }

    render() {
        const count = this.count;
        if (count == null || (count === 0 && !this.showZero)) {
            return nothing;
        }
        if (this.display === 'summary') {
            return this.renderSummary(count);
        }
        return this.renderBadge(count);
    }

    private renderBadge(count: number) {
        if (count === 0) {
            return html`<sl-badge variant="neutral" pill>0</sl-badge>`;
        }
        return html`<sl-badge variant="warning" pill>${count}</sl-badge>`;
    }

    private renderSummary(count: number) {
        if (count === 0) {
            return html`<span class="summary empty">No pending payments</span>`;
        }
        return html`
            <span class="summary">
                <sl-icon name="exclamation-circle" aria-hidden="true"></sl-icon>
                <strong>${count}</strong>
                <span>${this.pendingLabel(count)}</span>
            </span>
        `;
    }

    private pendingLabel(count: number): string {
        if (count === 1) {
            return 'payment pending';
        }
        return 'payments pending';
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-pending-payments-count': PendingPaymentsCount;
    }
}
