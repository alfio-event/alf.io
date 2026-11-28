import { LitElement, html, css } from 'lit';
import { customElement, property } from 'lit/decorators.js'
import { when } from 'lit/directives/when.js';
import { ConfigurationService } from '../service/configuration';
import { retroCompat, textColors, row, spacing } from '../styles'
import { t, tHtml } from '../service/i18n';

@customElement('alfio-project-banner')
export class ProjectBanner extends LitElement {

    @property({ type: String, attribute: 'data-full-banner' })
    fullBanner?: 'true' | 'false';

    @property({ type: String, attribute: 'data-alfio-version' })
    alfioVersion?: string;

    static readonly styles = [
        retroCompat,
        textColors,
        row,
        spacing,
        css`
            :host {
                --alfio-row-cols: 3;
            }

            .banner-actions {
                margin-top: var(--sl-spacing-large);
            }
        `
    ];

    render() {
        const notFullBanner = () => html`
            <div>
                <h5 class="text-muted">${tHtml('admin.banner.powered-by', html`<a href="https://alf.io" target="_blank">Alf.io</a>`, this.alfioVersion)}</h5>
                <small>${tHtml('admin.banner.report-or-ask',
                    html`<a href="https://github.com/alfio-event/alf.io/issues" target="_blank" rel="noopener">${t('admin.banner.report-an-issue')}</a>`,
                    html`<a href="https://github.com/alfio-event/alf.io/discussions" target="_blank" rel="nofollow noopener noreferrer">${t('admin.banner.ask-for-help')}</a>`)}</small>
                <div class="wMarginTop10px">
                    <sl-button variant="primary" size="small" href="https://alf.io/usage-form/" target="_blank" rel="noopener">${t('admin.banner.tell-us')}</sl-button>
                </div>
            </div>
        `;

        const fullBanner = () => html`
            <div>
                <h3 class="text-center">${t('admin.banner.thank-you')}</h3>
                <h4>${t('admin.banner.no-tracking')}<br>
                ${t('admin.banner.your-story')}</h4>
                <div class="row banner-actions">
                    <div>
                        <sl-button variant="primary" href="https://alf.io/usage-form/" target="_blank" rel="noopener">
                            ${t('admin.banner.tell-us')}
                        </sl-button>
                    </div>
                    <div>
                        <sl-button variant="default" href="https://opencollective.com/alfio/contribute" target="_blank" rel="noopener">
                            ${t('admin.banner.contribute')}
                        </sl-button>
                    </div>
                    <div>
                        <sl-button type="button" variant="default" @click=${this.dismiss}>${t('admin.common.dismiss')}</sl-button>
                    </div>
                </div>
            </div>
        `;

        return html`${when(this.fullBanner === 'true', fullBanner, notFullBanner)}`;
    }

    async dismiss() {
        try {
            await ConfigurationService.update({ key: 'SHOW_PROJECT_BANNER', value: 'false' });
            this.fullBanner = 'false';
            window.location.reload();
        } catch (e) {
            console.log('error while updating...', e);
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-project-banner': ProjectBanner
    }
}
