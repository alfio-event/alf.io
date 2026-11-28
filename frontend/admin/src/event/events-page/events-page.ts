import {html, LitElement} from 'lit';
import {customElement} from 'lit/decorators.js';
import {base, modernLayout, retroCompat} from '../../styles.ts';
import { t } from '../../service/i18n';

@customElement('alfio-events-page')
export class EventsPage extends LitElement {

    static readonly styles = [base, retroCompat, modernLayout];

    render() {
        return html`
            <div class="container">
                <div class="page-title-row">
                    <h1>${t('admin.menu.events')}</h1>

                </div>
                <hr class="page-separator">
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
