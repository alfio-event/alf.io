import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import type {SlDialog, SlRequestCloseEvent, SlTextarea} from '@shoelace-style/shoelace';
import {TanStackFormController} from '@tanstack/lit-form';
import {classMap} from 'lit/directives/class-map.js';
import {
    BoundField,
    fieldError,
    focusFirstError,
    FormMountController,
    INVALID_FORM_MESSAGE,
    requiredValue,
    submitValidated,
    validateWith,
} from '../components/form-validation.ts';
import {base, detailList, dialog, form, modernLayout, row, textColors} from '../styles.ts';

/**
 * The translation to edit
 */
export interface TranslationToEdit {
    key: string;
    languageName: string;
    // the English text, to explain what the translation is about
    english: string;
    // the translation that applies when it isn't customized at this level
    standard: string;
    // the translation shown now
    value: string;
}

export interface TranslationSavedDetail {
    key: string;
    value: string;
}

export const TRANSLATION_SAVED_EVENT = 'alfio-translation-saved';

/**
 * Edits one translation. Fires "alfio-translation-saved" with the new text: the host saves it, then closes the
 * dialog with close(), or shows an error with showError()
 */
@customElement('alfio-translation-dialog')
export class TranslationDialog extends LitElement {

    @state() private translation: TranslationToEdit | null = null;
    @state() private saving = false;
    @state() private invalid = false;
    @state() private error: string | null = null;
    @query('sl-dialog') private dialog!: SlDialog;

    readonly #form = new TanStackFormController(this, {
        defaultValues: {value: ''},
        onSubmit: async ({value}) => {
            const translation = this.translation;
            if (translation == null) {
                return;
            }
            this.saving = true;
            this.dispatchEvent(new CustomEvent<TranslationSavedDetail>(TRANSLATION_SAVED_EVENT, {
                detail: {key: translation.key, value: value.value},
                bubbles: true,
                composed: true,
            }));
        },
    });

    static readonly styles = [
        base,
        textColors,
        form,
        row,
        detailList,
        modernLayout,
        dialog,
        css`
            sl-dialog {
                --alfio-dialog-max-width: 44rem;
            }
            .detail-list {
                margin-bottom: var(--sl-spacing-medium);
            }
        `,
    ];

    constructor() {
        super();
        new FormMountController(this, this.#form.api);
    }

    async show(translation: TranslationToEdit): Promise<void> {
        this.translation = translation;
        this.saving = false;
        this.invalid = false;
        this.error = null;
        this.#form.api.reset({value: translation.value});
        await this.updateComplete;
        await this.dialog.show();
    }

    close(): void {
        this.saving = false;
        void this.dialog.hide();
    }

    showError(message: string): void {
        this.saving = false;
        this.error = message;
    }

    render(): TemplateResult {
        return html`
            <sl-dialog class="responsive-dialog" label="Edit translation"
                       @sl-request-close=${this.onRequestClose}
                       @sl-after-hide=${this.onAfterHide}>
                ${when(this.translation, (translation) => this.renderForm(translation))}
            </sl-dialog>
        `;
    }

    private renderForm(translation: TranslationToEdit): TemplateResult {
        const submitted = this.#form.api.state.submissionAttempts > 0;
        return html`
            <form id="translation-form" novalidate @submit=${this.submit}>
                <dl class="detail-list">
                    <dt>Key</dt>
                    <dd class="monospace">${translation.key}</dd>
                    <dt>English</dt>
                    <dd>${translation.english}</dd>
                    <dt>Standard ${translation.languageName} text</dt>
                    <dd>${translation.standard}</dd>
                </dl>
                ${this.#form.field({name: 'value', validators: validateWith(requiredValue)}, (field: BoundField<string>) => {
                    const error = fieldError(field.state.meta, submitted);
                    return html`
                        <sl-textarea id="translation-value" label="${translation.languageName} text" rows="4" resize="auto" required
                                     class=${classMap({error: error != null})}
                                     help-text=${error ?? 'Placeholders such as {0} are replaced by the actual values.'}
                                     .value=${field.state.value}
                                     @sl-input=${(e: Event) => field.handleChange((e.target as SlTextarea).value)}
                                     @sl-blur=${() => field.handleBlur()}></sl-textarea>
                    `;
                })}
                ${when(this.error, () => html`
                    <sl-alert variant="danger" open>
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        ${this.error}
                    </sl-alert>
                `)}
            </form>
            <div slot="footer">
                <sl-divider></sl-divider>
                ${when(this.invalid, () => html`<p class="text-danger">${INVALID_FORM_MESSAGE}</p>`)}
                <div class="row" style="--alfio-row-cols: 3">
                    <sl-button variant="default" size="large" @click=${() => this.close()}>Cancel</sl-button>
                    <div></div>
                    <sl-button id="save-translation" variant="warning" size="large" ?loading=${this.saving} ?disabled=${this.saving}
                               @click=${this.submit}>Save</sl-button>
                </div>
            </div>
        `;
    }

    private submit = async (event: Event): Promise<void> => {
        event.preventDefault();
        if (this.saving) {
            return;
        }
        this.error = null;
        const valid = await submitValidated(this.#form.api);
        this.invalid = !valid;
        if (!valid) {
            await this.updateComplete;
            focusFirstError(this.renderRoot);
        }
    };

    // a click outside the dialog would discard the text: only the close button and Cancel close it
    private onRequestClose = (event: SlRequestCloseEvent): void => {
        if (event.detail.source === 'overlay') {
            event.preventDefault();
        }
    };

    private onAfterHide = (event: Event): void => {
        // nested Shoelace components (e.g. tooltips) emit their own sl-after-hide
        if (event.target !== event.currentTarget) {
            return;
        }
        this.translation = null;
    };
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-translation-dialog': TranslationDialog;
    }
    interface GlobalEventHandlersEventMap {
        'alfio-translation-saved': CustomEvent<TranslationSavedDetail>;
    }
}
