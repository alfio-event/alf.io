import { LitElement, PropertyValues } from 'lit';
import { customElement, property } from 'lit/decorators.js';
import { navigate } from '../admin-router';

/**
 * A route that only sends to another one, e.g. /events/{eventName} to its overview. Replaces the history entry, so
 * that Back doesn't land on the redirect again.
 */
@customElement('alfio-redirect')
export class Redirect extends LitElement {

    @property({ type: String, attribute: 'data-route' }) route = '';

    protected updated(changed: PropertyValues<this>): void {
        if (changed.has('route') && this.route !== '') {
            const route = this.route;
            // after the navigation that rendered this element is over
            setTimeout(() => navigate(route, { replace: true }));
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-redirect': Redirect;
    }
}
