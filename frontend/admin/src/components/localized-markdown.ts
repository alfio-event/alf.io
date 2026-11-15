import {css, html, LitElement, nothing, PropertyValues, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {unsafeHTML} from 'lit/directives/unsafe-html.js';
import {ContentLanguage, LocalizedContent} from '../model/purchase-context.ts';
import {UtilService} from '../service/util.ts';

/**
 * A localized markdown text, read-only (e.g. the description of an event): one tab per language,
 * named after the language, with the rendered markdown. A text in one language only is shown
 * without tabs.
 *
 *   <alfio-localized-markdown .value=${event.description} .languages=${event.contentLanguages}></alfio-localized-markdown>
 */
@customElement('alfio-localized-markdown')
export class LocalizedMarkdown extends LitElement {

    // locale -> markdown text
    @property({attribute: false})
    value: LocalizedContent = {};

    // used to name the tabs; a locale without a matching language is shown as it is
    @property({attribute: false})
    languages: ContentLanguage[] = [];

    @state()
    private rendered: Record<string, string> = {};

    static readonly styles = css`
        :host {
            display: block;
        }
        sl-tab-group {
            --track-color: var(--sl-color-gray-200);
        }
        sl-tab-group::part(base) {
            margin-top: 0;
        }
        sl-tab-panel {
            --padding: var(--sl-spacing-small) 0 0;
        }
        .markdown {
            overflow-wrap: anywhere;
        }
        .markdown img {
            max-width: 100%;
        }
        .markdown > :first-child {
            margin-top: 0;
        }
        .markdown > :last-child {
            margin-bottom: 0;
        }
    `;

    protected willUpdate(changed: PropertyValues<this>): void {
        if (changed.has('value')) {
            void this.renderTexts();
        }
    }

    render(): TemplateResult {
        const entries = Object.entries(this.value ?? {}).filter(([, text]) => text != null && text.trim() !== '');
        if (entries.length === 0) {
            return html`${nothing}`;
        }
        if (entries.length === 1) {
            return this.renderText(entries[0][0]);
        }
        return html`
            <sl-tab-group>
                ${repeat(entries, ([locale]) => locale, ([locale], index) => html`
                    <sl-tab slot="nav" panel=${locale} ?active=${index === 0}>${this.languageName(locale)}</sl-tab>
                    <sl-tab-panel name=${locale} ?active=${index === 0}>${this.renderText(locale)}</sl-tab-panel>
                `)}
            </sl-tab-group>
        `;
    }

    private renderText(locale: string): TemplateResult {
        return html`
            ${when(this.rendered[locale] != null,
                () => html`<div class="markdown">${unsafeHTML(this.rendered[locale])}</div>`,
                () => html`<sl-spinner></sl-spinner>`)}
        `;
    }

    private languageName(locale: string): string {
        const language = this.languages.find((candidate) => candidate.locale === locale);
        if (language == null) {
            return locale;
        }
        return language.displayLanguage;
    }

    private async renderTexts(): Promise<void> {
        const value = this.value ?? {};
        const rendered: Record<string, string> = {};
        await Promise.all(Object.entries(value).map(async ([locale, text]) => {
            try {
                rendered[locale] = await UtilService.renderMarkdown(text ?? '');
            } catch {
                // shown as plain text if the markdown can't be rendered
                const escaped = document.createElement('p');
                escaped.textContent = text ?? '';
                rendered[locale] = escaped.outerHTML;
            }
        }));
        // ignores the result of a previous value
        if (value === this.value) {
            this.rendered = rendered;
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-localized-markdown': LocalizedMarkdown;
    }
}
