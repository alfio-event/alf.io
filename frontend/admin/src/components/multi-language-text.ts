import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {repeat} from 'lit/directives/repeat.js';
import {unsafeHTML} from 'lit/directives/unsafe-html.js';
import type {SlInput, SlTextarea} from '@shoelace-style/shoelace';
import {ContentLanguage, LocalizedContent} from '../model/purchase-context.ts';
import {sectionCard} from './section-card.ts';
import {badges, form, modernLayout, textColors} from '../styles.ts';
import {UtilService} from '../service/util.ts';

export interface MultiLanguageTextChange {
    value: LocalizedContent;
}

/**
 * Fired only when the set of languages changes (a translation is added or removed), so the
 * container can apply the same languages to the other multi-language fields of the page
 */
export interface MultiLanguageTextLocalesChange {
    locales: string[];
}

/**
 * A localized text (title, description) with one block per selected language.
 *
 * By default it renders as a section card (icon + title + translation count in the header);
 * with "card" off it renders as a bare field group, for embedding in dialogs or other cards.
 * Each language block has a header with the language name, a remove button (when there is
 * more than one) and, for markdown fields, an inline Edit/Preview toggle: the preview is
 * rendered in place, no dialog. "Add translation" is a dropdown menu of the remaining
 * languages, placed below the last block as for any "add another" action.
 *
 * Controlled component: the parent passes the current map in "value" (its keys are the
 * selected languages) and applies the "change" event's detail.value. When the selection is
 * empty (creating something new), the component picks a default language — the first
 * "contextLanguages" entry when there is a purchase context, else the browser language if
 * supported, else English — and emits it via "change"/"locales-change". When the set of
 * languages changes, "locales-change" carries the new locale list, so the container can sync
 * the other multi-language fields of the page. Plain custom element on purpose: AngularJS
 * templates can use it too, setting the "value" property and listening for the bubbling
 * "change"/"locales-change" events.
 */
@customElement('alfio-multi-language-text')
export class MultiLanguageText extends LitElement {

    // section card title / embedded heading / field label
    @property({type: String})
    label: string = '';

    @property({type: String})
    icon: string = 'card-heading';

    // render as a section card (default) or as a bare field group for embedding
    @property({type: Boolean})
    card: boolean = true;

    // the text supports markdown: show the inline Edit/Preview toggle
    @property({type: Boolean})
    markdown: boolean = false;

    @property({type: Number, attribute: 'max-length'})
    maxLength: number = 0;

    // single-line input instead of a textarea (e.g. for a title)
    @property({type: Boolean})
    multiline: boolean = true;

    @property({type: Number})
    rows: number = 4;

    // validation message of the whole field, shown below the language blocks
    @property({type: String})
    error: string = '';

    // all languages the instance supports
    @property({attribute: false})
    languages: ContentLanguage[] = [];

    // languages of the purchase context the text belongs to, when there is one (event, subscription)
    @property({attribute: false})
    contextLanguages?: ContentLanguage[];

    // current translations; the keys are the selected languages
    @property({attribute: false})
    value: LocalizedContent = {};

    // which language block is showing the preview instead of the editor
    @state()
    private previewByLocale: Record<string, boolean> = {};

    // rendered markdown of the blocks in preview, keyed by locale
    @state()
    private renderedByLocale: Record<string, string> = {};

    static styles = [modernLayout, form, textColors, badges, css`
        :host {
            display: block;
        }

        .mlt-label {
            margin: 0 0 var(--sl-spacing-small);
        }

        .language-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: var(--sl-spacing-small);
            border-bottom: 1px solid var(--sl-color-gray-200);
            padding-bottom: var(--sl-spacing-x-small);
            margin-bottom: var(--sl-spacing-small);
        }

        .language-name {
            font-weight: var(--sl-font-weight-semibold);
        }

        .language-name .locale {
            color: var(--sl-color-gray-500);
            font-size: var(--sl-font-size-small);
            font-weight: var(--sl-font-weight-normal);
            margin-inline-start: var(--sl-spacing-2x-small);
        }

        .language-actions {
            display: flex;
            align-items: center;
            gap: var(--sl-spacing-x-small);
        }

        .markdown-preview {
            min-height: 6rem;
            max-height: 24rem;
            overflow: auto;
            border: 1px dashed var(--sl-color-gray-300);
            border-radius: var(--sl-input-border-radius);
            padding: var(--sl-spacing-small) var(--sl-spacing-medium);
            background-color: var(--sl-color-neutral-5);
        }

        .markdown-preview img {
            max-width: 100%;
        }

        .markdown-preview > :first-child {
            margin-top: 0;
        }

        .markdown-preview > :last-child {
            margin-bottom: 0;
        }

        .add-translation::part(base) {
            padding-inline: 0;
        }

        .form-error-text {
            color: var(--sl-color-danger-600);
            font-size: var(--sl-font-size-small);
            margin-top: var(--sl-spacing-x-small);
        }
    `];

    protected firstUpdated(): void {
        // empty selection = creating something new: pick the default language and tell the parent
        if (Object.keys(this.value).length === 0) {
            const fallback = this.defaultLocale();
            if (fallback != null) {
                const next = {[fallback]: ''};
                this.dispatchChange(next);
                this.dispatchLocalesChange(next);
            }
        }
    }

    /**
     * The languages to show: the selected ones, or the default one while the selection is
     * still empty (i.e. when creating something new)
     */
    private get displayedLocales(): string[] {
        const locales = Object.keys(this.value);
        if (locales.length > 0) {
            return locales;
        }
        const fallback = this.defaultLocale();
        if (fallback != null) {
            return [fallback];
        }
        return [];
    }

    /**
     * The default language for a new text: the first language of the purchase context when
     * there is one, else the browser language if the application supports it, else English
     */
    private defaultLocale(): string | null {
        const contextLanguages = this.contextLanguages ?? [];
        if (contextLanguages.length > 0) {
            return contextLanguages[0].locale;
        }
        const browser = this.browserLocale();
        if (browser != null) {
            return browser;
        }
        if (this.languages.some((l) => l.locale === 'en')) {
            return 'en';
        }
        return this.languages[0]?.locale ?? 'en';
    }

    /**
     * The browser language, if the application supports it: matched by exact locale first,
     * then by language part (it-IT matches it)
     */
    private browserLocale(): string | null {
        const browserLanguages = navigator.languages?.length ? navigator.languages : [navigator.language];
        for (const browserLanguage of browserLanguages) {
            if (browserLanguage == null) {
                continue;
            }
            const match = this.languages.find((l) => l.locale === browserLanguage)
                ?? this.languages.find((l) => l.locale.split('-')[0] === browserLanguage.split('-')[0]);
            if (match != null) {
                return match.locale;
            }
        }
        return null;
    }

    protected render(): TemplateResult {
        const locales = this.displayedLocales;
        let badge: TemplateResult | undefined;
        if (locales.length > 1) {
            badge = html`<sl-badge variant="primary" pill>${locales.length} languages</sl-badge>`;
        }
        const body = html`
            <div class="form-stack">
                ${repeat(locales, (locale) => locale, (locale) => this.renderLocale(locale))}
                ${this.renderAddTranslation()}
            </div>
            ${when(this.error !== '', () => html`<div class="form-error-text">${this.error}</div>`, () => nothing)}
        `;
        if (this.card) {
            return sectionCard({icon: this.icon, title: this.label, badge}, body);
        }
        return html`
            ${when(this.label !== '', () => html`<h3 class="mlt-label">${this.label}</h3>`, () => nothing)}
            ${body}
        `;
    }

    private renderLocale(locale: string): TemplateResult {
        const language = this.languages.find((l) => l.locale === locale);
        const displayLanguage = language?.displayLanguage ?? locale;
        const text = this.value[locale] ?? '';
        const localeCount = this.displayedLocales.length;
        const previewing = this.previewByLocale[locale] === true;
        let editVariant: 'default' | 'primary' = 'default';
        let previewVariant: 'default' | 'primary' = 'default';
        if (previewing) {
            previewVariant = 'primary';
        } else {
            editVariant = 'primary';
        }
        return html`
            <div class="language-block">
                <div class="language-header">
                    <span class="language-name">${displayLanguage}<span class="locale">${locale}</span></span>
                    <span class="language-actions">
                        ${when(this.markdown, () => html`
                            <sl-button-group>
                                <sl-button size="small" variant=${editVariant} @click=${() => this.setPreview(locale, false)}>
                                    <sl-icon slot="prefix" name="pencil"></sl-icon>Edit
                                </sl-button>
                                <sl-button size="small" variant=${previewVariant} ?disabled=${text.trim() === ''} @click=${() => this.setPreview(locale, true)}>
                                    <sl-icon slot="prefix" name="eye"></sl-icon>Preview
                                </sl-button>
                            </sl-button-group>
                        `, () => nothing)}
                        ${when(localeCount > 1, () => html`
                            <sl-icon-button name="trash" label=${`Remove ${displayLanguage}`}
                                            @click=${() => this.removeLocale(locale)}></sl-icon-button>
                        `, () => nothing)}
                    </span>
                </div>
                ${when(previewing,
                    () => html`<div class="markdown-preview">${unsafeHTML(this.renderedByLocale[locale] ?? '')}</div>`,
                    () => this.renderEditor(locale, text, (newText) => this.updateLocale(locale, newText)))}
            </div>
        `;
    }

    /**
     * The editor of one language block. This is the single place to replace with a WYSIWYG
     * markdown editor: the replacement only needs to call onInput with the new text, the
     * component keeps owning the value and the "change" event.
     */
    protected renderEditor(locale: string, text: string, onInput: (text: string) => void): TemplateResult {
        let maxLengthAttr: number | typeof nothing = nothing;
        if (this.maxLength > 0) {
            maxLengthAttr = this.maxLength;
        }
        if (!this.multiline) {
            return html`
                <sl-input class="editor" id=${`editor-${locale}`} label=${this.label} type="text"
                          maxlength=${maxLengthAttr}
                          .value=${text}
                          @sl-input=${(e: Event) => onInput((e.target as SlInput).value)}>
                    ${this.renderHelpText()}
                </sl-input>
            `;
        }
        return html`
            <sl-textarea class="editor" id=${`editor-${locale}`} label=${this.label} rows=${this.rows} resize="auto"
                         maxlength=${maxLengthAttr}
                         .value=${text}
                         @sl-input=${(e: Event) => onInput((e.target as SlTextarea).value)}>
                ${this.renderHelpText()}
            </sl-textarea>
        `;
    }

    private renderHelpText(): TemplateResult | typeof nothing {
        return when(this.markdown, () => html`
            <div slot="help-text">
                <sl-button variant="text" size="small" href="https://commonmark.org/help/" target="_blank">
                    <sl-icon name="markdown" slot="prefix"></sl-icon>How to format text
                </sl-button>
            </div>
        `, () => nothing);
    }

    private renderAddTranslation(): TemplateResult | typeof nothing {
        const locales = this.displayedLocales;
        let available = this.languages.filter((language) => !locales.includes(language.locale));
        // suggest the browser language first, if it is not selected yet
        const browser = this.browserLocale();
        if (browser != null) {
            const match = available.find((l) => l.locale === browser);
            if (match != null) {
                available = [match, ...available.filter((l) => l !== match)];
            }
        }
        return when(available.length > 0, () => html`
            <sl-dropdown hoist>
                <sl-button slot="trigger" variant="text" size="small" caret class="add-translation">
                    <sl-icon slot="prefix" name="plus-circle"></sl-icon>Add translation
                </sl-button>
                <sl-menu>
                    ${repeat(available, (language) => language.locale, (language) => html`
                        <sl-menu-item @click=${() => this.addLocale(language.locale)}>
                            <sl-icon slot="prefix" name="translate"></sl-icon>${language.displayLanguage}
                        </sl-menu-item>
                    `)}
                </sl-menu>
            </sl-dropdown>
        `, () => nothing);
    }

    private setPreview(locale: string, preview: boolean): void {
        if (!preview) {
            this.previewByLocale = {...this.previewByLocale, [locale]: false};
            return;
        }
        this.previewByLocale = {...this.previewByLocale, [locale]: true};
        UtilService.renderMarkdown(this.value[locale] ?? '')
            .then((rendered) => {
                this.renderedByLocale = {...this.renderedByLocale, [locale]: rendered};
            })
            .catch(() => {
                this.renderedByLocale = {...this.renderedByLocale, [locale]: ''};
            });
    }

    private updateLocale(locale: string, text: string): void {
        this.dispatchChange({...this.value, [locale]: text});
    }

    private addLocale(locale: string): void {
        if (locale === '' || this.displayedLocales.includes(locale)) {
            return;
        }
        const next = {...this.value, [locale]: ''};
        this.dispatchChange(next);
        this.dispatchLocalesChange(next);
    }

    private removeLocale(locale: string): void {
        if (this.displayedLocales.length <= 1) {
            return;
        }
        const next = {...this.value};
        delete next[locale];
        const preview = {...this.previewByLocale};
        delete preview[locale];
        this.previewByLocale = preview;
        this.dispatchChange(next);
        this.dispatchLocalesChange(next);
    }

    private dispatchChange(value: LocalizedContent): void {
        this.dispatchEvent(new CustomEvent<MultiLanguageTextChange>('change', {
            detail: {value},
            bubbles: true,
            composed: true,
        }));
    }

    private dispatchLocalesChange(value: LocalizedContent): void {
        this.dispatchEvent(new CustomEvent<MultiLanguageTextLocalesChange>('locales-change', {
            detail: {locales: Object.keys(value)},
            bubbles: true,
            composed: true,
        }));
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-multi-language-text': MultiLanguageText;
    }
}
