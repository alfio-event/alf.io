import {html, TemplateResult} from 'lit';
import { t } from '../service/i18n';

/**
 * A link to another site (documentation, a provider's dashboard), opened in a new tab. The icon tells that it
 * leaves the admin. Use with the "modernLayout" styles.
 */
export function externalLink(href: string, label: string | TemplateResult): TemplateResult {
    return html`<a class="external-link" href=${href} target="_blank" rel="noopener noreferrer">${label}<sl-icon
            name="box-arrow-up-right" label=${t('admin.common.opens-in-new-tab')}></sl-icon></a>`;
}
