import {html, TemplateResult} from 'lit';
import {repeat} from 'lit/directives/repeat.js';

/**
 * The steps to set up an integration (e.g. where to find the API keys), collapsed below the description of its
 * section card, so that the card starts with its fields. Use with the "modernLayout" styles.
 *
 * @param summary what the steps achieve, e.g. "How to get the Client ID and Secret"
 * @param steps one sentence each, in order
 */
export function setupGuide(summary: string, steps: TemplateResult[]): TemplateResult {
    return html`
        <sl-details class="setup-guide" summary=${summary}>
            <ol>
                ${repeat(steps, (_step, index) => index, (step) => html`<li>${step}</li>`)}
            </ol>
        </sl-details>
    `;
}
