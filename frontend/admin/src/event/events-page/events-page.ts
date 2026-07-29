import {css, html, LitElement} from 'lit';
import {customElement} from 'lit/decorators.js';
import {retroCompat, panelStyles, spacing} from '../../styles.ts';

@customElement('alfio-events-page')
export class EventsPage extends LitElement {

    static readonly styles = [
        retroCompat,
        panelStyles,
        spacing,
        css`
            :host {
                display: block;
                font-family: "Helvetica Neue", Helvetica, Arial, sans-serif;
                font-size: 14px;
                line-height: 1.42857143;
                -webkit-font-smoothing: antialiased;
            }

            .container {
                width: 100%;
                margin-right: auto;
                margin-left: auto;
                max-width: 100%;
            }

            @media (min-width: 992px) {
                .container {
                    width: 970px;
                    padding-right: 15px;
                    padding-left: 15px;
                }
            }

            @media (min-width: 1200px) {
                .container {
                    width: 1170px;
                }
            }

            h1 {
                font-family: "Source Sans Pro", "Helvetica Neue", Helvetica, Arial, sans-serif;
                font-size: 41px;
                font-weight: 500;
                line-height: 1.1;
                margin-top: 22px;
                margin-bottom: 11px;
                color: #333;
            }

            hr {
                height: 0;
                margin: 22px 0;
                border: 0;
                border-top: 1px solid #eee;
            }
        `
    ];

    render() {
        return html`
            <div class="container">
                <h1>Events</h1>
                <hr />
                <alfio-export-reservations-button></alfio-export-reservations-button>
                <alfio-active-events-list></alfio-active-events-list>
                <alfio-expired-events-list></alfio-expired-events-list>
            </div>
        `;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-events-page': EventsPage;
    }
}
