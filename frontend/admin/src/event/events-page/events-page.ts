import {css, html, LitElement} from 'lit';
import {customElement} from 'lit/decorators.js';
import {retroCompat} from '../../styles.ts';

@customElement('alfio-events-page')
export class EventsPage extends LitElement {

    static readonly styles = [retroCompat, css`
        .container {
            width: 100%;
            margin-inline: auto;
            padding-inline: var(--sl-spacing-medium);
        }

        @media (min-width: 992px) {
            .container {
                max-width: 970px;
            }
        }

        @media (min-width: 1200px) {
            .container {
                max-width: 1170px;
            }
        `
    ];

    render() {
        return html`
            <div class="container">
                <alfio-events-list></alfio-events-list>
            </div>
        `;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-events-page': EventsPage;
    }
}
