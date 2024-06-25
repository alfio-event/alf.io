import { LitElement, html, css } from 'lit';
import { customElement, property } from 'lit/decorators.js'
import { when } from 'lit/directives/when.js';
import { ConfigurationService } from '../service/configuration';
import { retroCompat, textColors, row } from '../styles'

@customElement('alfio-project-banner')
export class ProjectBanner extends LitElement {

    @property({ type: String, attribute: 'data-full-banner' })
    fullBanner?: 'true' | 'false';

    @property({ type: String, attribute: 'data-alfio-version' })
    alfioVersion?: string;

    static styles = [
        retroCompat,
        textColors,
        row,
        css` :host { --alfio-row-cols: 3 }`
    ];

    render() {
        const notFullBanner = () => html`
            <div>
                <h5 class="text-muted">Powered by <a href="https://alf.io" target="_blank">Alf.io</a> v.${this.alfioVersion}</h5>
                <small><a href="https://github.com/alfio-event/alf.io/issues" target="_blank" rel="noopener">report an issue</a> or <a href="https://github.com/alfio-event/alf.io/discussions" target="_blank" rel="nofollow noopener noreferrer">ask for help</a></small>
                <div class="wMarginTop10px">
                    <sl-button variant="success" size="small" href="https://alf.io/usage-form/" target="_blank" rel="noopener">Tell us about you!</sl-button>
                </div>
            </div>
        `;

        const fullBanner = () => html`
            <div>
                <h3 class="text-center">Thank you for using Alf.io!</h3>
                <h4> We don't track usage in any way, because we respect your privacy<br>
                ...but we're very happy that you've decided to use Alf.io and we'd love to hear your story! </h4>
                <div>&nbsp;</div>
                <div class="row">
                    <div>
                        <sl-button variant="primary" href="https://alf.io/usage-form/" target="_blank" rel="noopener">
                            Tell us about you!
                        </sl-button>
                    </div>
                    <div>
                        <sl-button variant="success" href="https://opencollective.com/alfio/contribute" target="_blank" rel="noopener">
                            Contribute on OpenCollective
                        </sl-button>
                    </div>
                    <div>
                        <sl-button type="button" class="btn btn-default ml-3" @click=${this.dismiss} ng-click="$ctrl.dismiss()">Dismiss</sl-button>
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
            console.log('error while updating...');
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-project-banner': ProjectBanner
    }
}