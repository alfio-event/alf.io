import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import type {SlSelect, SlSwitch} from '@shoelace-style/shoelace';
import {SettingsLevel, TranslationsOverride} from '../model/configuration.ts';
import {ContentLanguage} from '../model/purchase-context.ts';
import {SettingsService} from '../service/settings.ts';
import {TemplateService} from '../service/template.ts';
import {errorMessage} from '../service/helpers.ts';
import {dispatchFeedback} from '../model/dom-events.ts';
import {sectionCard} from '../components/section-card.ts';
import {taskContent} from '../components/task-content.ts';
import {emptyState} from '../components/empty-state.ts';
import {ListSearchController} from '../components/list-search.ts';
import {AlfioPageChange} from '../components/pagination-bar.ts';
import '../components/pagination-bar.ts';
import {badges, base, form, modernLayout, modernTable, textColors} from '../styles.ts';
import {TranslationDialog, TranslationSavedDetail} from './translation-dialog.ts';
import './translation-dialog.ts';

const PAGE_SIZE = 50;

interface TranslationsData {
    languages: ContentLanguage[];
    // the customizations of the levels above (system, organization), merged
    inherited: TranslationsOverride;
}

interface LanguageBundle {
    locale: string;
    // the texts of the language, with the customizations of the levels above
    texts: Record<string, string>;
    // the English texts, which explain what each key is about
    english: Record<string, string>;
}

interface TranslationRow {
    key: string;
    english: string;
    value: string;
    standard: string;
    customized: boolean;
}

/**
 * The texts of the public pages and of the e-mails, which can be customized for each language at a level
 * (system, organization or event). A customization applies to the level and to the levels below it.
 */
@customElement('alfio-translations-editor')
export class TranslationsEditor extends LitElement {

    @property({attribute: false}) level!: SettingsLevel;

    @state() private locale = 'en';
    @state() private search = '';
    @state() private onlyCustomized = false;
    @state() private page = 1;
    // the customizations of this level
    @state() private customized: TranslationsOverride | null = null;
    @state() private removing: string | null = null;
    @state() private lastData: TranslationsData | null = null;
    @state() private lastBundle: LanguageBundle | null = null;
    @query('alfio-translation-dialog') private dialog!: TranslationDialog;

    private readonly searchController = new ListSearchController(this, (search) => {
        this.search = search;
        this.page = 1;
    });

    private readonly loadTask = new Task(this, {
        task: async ([level]): Promise<TranslationsData> => {
            const [languages, customized, inherited] = await Promise.all([
                TemplateService.allLanguages(),
                SettingsService.loadTranslationsOverride(level),
                loadInherited(level),
            ]);
            this.customized = customized;
            return {languages, inherited};
        },
        args: () => [this.level] as const,
        onComplete: (data) => {
            this.lastData = data;
        },
    });

    private readonly bundleTask = new Task(this, {
        task: async ([locale, data]): Promise<LanguageBundle | null> => {
            if (data == null) {
                return null;
            }
            const [texts, english] = await Promise.all([
                SettingsService.loadBundle(locale),
                SettingsService.loadBundle('en'),
            ]);
            return {
                locale,
                texts: withCustomizations(texts, data.inherited[locale]),
                english: withCustomizations(english, data.inherited['en']),
            };
        },
        args: () => [this.locale, this.lastData] as const,
        onComplete: (bundle) => {
            if (bundle != null) {
                this.lastBundle = bundle;
            }
        },
    });

    static readonly styles = [
        base,
        textColors,
        badges,
        form,
        modernTable,
        modernLayout,
        css`
            :host {
                display: block;
            }
            .filter-toolbar {
                align-items: center;
            }
            .language-select {
                flex: 0 1 14rem;
                margin-top: 0;
            }
            .translation-key {
                display: block;
                margin-top: var(--sl-spacing-3x-small);
                color: var(--sl-color-gray-500);
                font-family: var(--sl-font-mono);
                font-size: var(--sl-font-size-x-small);
                overflow-wrap: anywhere;
            }
            .translation-text {
                overflow-wrap: anywhere;
            }
            .translation-value sl-badge {
                margin-inline-start: var(--sl-spacing-x-small);
            }
            td.translation-english,
            td.translation-value {
                width: 45%;
            }
            .actions-cell {
                white-space: nowrap;
                text-align: right;
            }
            .actions-cell sl-button + sl-button {
                margin-inline-start: var(--sl-spacing-2x-small);
            }
            @media (max-width: 700px) {
                .actions-cell {
                    white-space: normal;
                }
                .actions-cell sl-button {
                    margin: var(--sl-spacing-3x-small) 0;
                }
            }
        `,
    ];

    render(): TemplateResult {
        return taskContent(this.loadTask, this.lastData, 'Failed to load the translations. Please try again.',
            (data) => this.renderContent(data));
    }

    private renderContent(data: TranslationsData): TemplateResult {
        return html`
            <p class="page-description text-muted">
                Customize the texts of the public pages and of the e-mails. A customization also applies to the levels below this one.
            </p>
            <div class="filter-toolbar">
                ${this.searchController.render('translations')}
                <sl-select class="language-select label-hidden" label="Language" hoist .value=${this.locale}
                           @sl-change=${(e: Event) => this.selectLanguage(String((e.target as SlSelect).value))}>
                    <!-- the icon shows what the select is for; its label is for the screen readers only, so the controls align -->
                    <sl-icon slot="prefix" name="translate"></sl-icon>
                    ${repeat(data.languages, (language) => language.locale, (language) => html`
                        <sl-option value=${language.locale}>${language.displayLanguage}</sl-option>
                    `)}
                </sl-select>
                <sl-switch id="only-customized" ?checked=${this.onlyCustomized}
                           @sl-change=${(e: Event) => { this.onlyCustomized = (e.target as SlSwitch).checked; this.page = 1; }}>
                    Only customized
                </sl-switch>
            </div>
            ${taskContent(this.bundleTask, this.currentBundle(), 'Failed to load the texts of the language. Please try again.',
                (bundle) => this.renderList(data, bundle))}
            <alfio-translation-dialog @alfio-translation-saved=${this.onTranslationSaved}></alfio-translation-dialog>
        `;
    }

    private renderList(data: TranslationsData, bundle: LanguageBundle): TemplateResult {
        const rows = this.rows(bundle);
        const lastPage = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
        const page = Math.min(this.page, lastPage);
        const visible = rows.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
        const languageName = this.languageName(data, bundle.locale);
        return sectionCard({
            icon: 'translate',
            title: `${languageName} texts`,
            badge: html`<sl-badge variant="primary" pill>${rows.length}</sl-badge>`,
            className: 'list-card',
        }, html`
            ${when(rows.length === 0, () => emptyState(this.emptyMessage()), () => html`
                <div class="table-responsive">
                    <table class="table table-striped">
                        <thead>
                            <tr>
                                <th>English</th>
                                <th>${languageName}</th>
                                <th><span class="sr-only">Actions</span></th>
                            </tr>
                        </thead>
                        <tbody>
                            ${repeat(visible, (row) => row.key, (row) => this.renderRow(row, languageName))}
                        </tbody>
                    </table>
                </div>
                <alfio-pagination-bar page=${page} total=${rows.length} page-size=${PAGE_SIZE} item-label="texts"
                                      @alfio-page-change=${(e: AlfioPageChange) => { this.page = e.detail.page; }}></alfio-pagination-bar>
            `)}
        `);
    }

    private renderRow(row: TranslationRow, languageName: string): TemplateResult {
        return html`
            <tr class="translation-row" data-key=${row.key}>
                <td class="translation-english">
                    <span class="translation-text">${row.english}</span>
                    <span class="translation-key">${row.key}</span>
                </td>
                <td class="translation-value">
                    <span class="translation-text">${row.value}</span>
                    ${when(row.customized, () => html`<sl-badge variant="primary" pill>Customized</sl-badge>`)}
                </td>
                <td class="actions-cell">
                    <sl-button class="edit-translation" size="small" variant="default"
                               @click=${() => void this.dialog.show({...row, languageName})}>
                        <sl-icon slot="prefix" name="pencil"></sl-icon>
                        Edit
                    </sl-button>
                    ${when(row.customized, () => html`
                        <sl-button class="reset-translation" size="small" variant="danger" outline
                                   ?loading=${this.removing === row.key} ?disabled=${this.removing != null}
                                   @click=${() => void this.reset(row.key)}>
                            <sl-icon slot="prefix" name="trash"></sl-icon>
                            Reset
                        </sl-button>
                    `)}
                </td>
            </tr>
        `;
    }

    // ---------- data ----------

    private currentBundle(): LanguageBundle | null {
        // while another language is loading, the list of the previous one stays visible
        return this.bundleTask.value ?? this.lastBundle;
    }

    private rows(bundle: LanguageBundle): TranslationRow[] {
        const customized = this.customized?.[bundle.locale] ?? {};
        const search = this.search.trim().toLowerCase();
        return Object.keys(bundle.texts).sort()
            .map((key) => {
                const standard = displayText(bundle.texts[key]);
                const isCustomized = customized[key] !== undefined;
                let value = standard;
                if (isCustomized) {
                    value = displayText(customized[key]);
                }
                return {key, english: displayText(bundle.english[key] ?? ''), value, standard, customized: isCustomized};
            })
            .filter((row) => !this.onlyCustomized || row.customized)
            .filter((row) => search === ''
                || row.key.toLowerCase().includes(search)
                || row.value.toLowerCase().includes(search)
                || row.english.toLowerCase().includes(search));
    }

    private languageName(data: TranslationsData, locale: string): string {
        return data.languages.find((language) => language.locale === locale)?.displayLanguage ?? locale;
    }

    private emptyMessage(): string {
        if (this.onlyCustomized && this.search.trim() === '') {
            return 'No text of this language has been customized';
        }
        return 'No text matches the filter';
    }

    private selectLanguage(locale: string): void {
        this.locale = locale;
        this.page = 1;
    }

    // ---------- changes ----------

    private onTranslationSaved = async (event: CustomEvent<TranslationSavedDetail>): Promise<void> => {
        const locale = this.locale;
        const translations = copyOf(this.customized);
        translations[locale] = {...(translations[locale] ?? {}), [event.detail.key]: storedText(event.detail.value)};
        try {
            await SettingsService.saveTranslationsOverride(this.level, translations);
            this.customized = translations;
            this.dialog.close();
            dispatchFeedback({type: 'success', message: 'The translation has been saved'}, this);
        } catch (e) {
            this.dialog.showError(errorMessage(e, 'Failed to save the translation. Please try again.'));
        }
    };

    private async reset(key: string): Promise<void> {
        const locale = this.locale;
        const translations = copyOf(this.customized);
        const texts = {...(translations[locale] ?? {})};
        delete texts[key];
        translations[locale] = texts;
        this.removing = key;
        try {
            await SettingsService.saveTranslationsOverride(this.level, translations);
            this.customized = translations;
            dispatchFeedback({type: 'success', message: 'The standard translation applies again'}, this);
        } catch (e) {
            dispatchFeedback({type: 'danger', message: errorMessage(e, 'Failed to reset the translation. Please try again.')}, this);
        } finally {
            this.removing = null;
        }
    }
}

/**
 * The customizations of the levels above the given one, the closest level winning
 */
async function loadInherited(level: SettingsLevel): Promise<TranslationsOverride> {
    if (level.type === 'SYSTEM') {
        return {};
    }
    const system = await SettingsService.loadTranslationsOverride({type: 'SYSTEM'}).catch(() => ({} as TranslationsOverride));
    if (level.type === 'ORGANIZATION') {
        return system;
    }
    const organization = await SettingsService.loadTranslationsOverride({
        type: 'ORGANIZATION', organizationId: level.organizationId, organizationName: '',
    }).catch(() => ({} as TranslationsOverride));
    const merged = copyOf(system);
    for (const [locale, texts] of Object.entries(organization)) {
        merged[locale] = {...(merged[locale] ?? {}), ...texts};
    }
    return merged;
}

function withCustomizations(texts: Record<string, string>, customizations: Record<string, string> | undefined): Record<string, string> {
    return {...texts, ...(customizations ?? {})};
}

function copyOf(translations: TranslationsOverride | null): TranslationsOverride {
    return structuredClone(translations ?? {});
}

/**
 * The texts are stored in the MessageFormat syntax: placeholders may be written {{0}} and quotes are doubled
 */
function displayText(value: string): string {
    return value.replace(/{+(\d+)}+/g, '{$1}').replace(/''/g, "'");
}

function storedText(value: string): string {
    return value.replace(/{+(\d+)}+/g, '{$1}').replace(/([^'])'([^'])/g, "$1''$2");
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-translations-editor': TranslationsEditor;
    }
}
