import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import type {SlDialog, SlRequestCloseEvent} from '@shoelace-style/shoelace';
import {css as cssLanguage} from '@codemirror/lang-css';
import {base, dialog, form, row, textColors} from '../styles.ts';
import '../components/code-editor.ts';
import { t } from '../service/i18n';

export const CSS_SETTING_CHANGE_EVENT = 'alfio-css-setting-change';

// lines of the CSS shown in the summary
const PREVIEW_LINES = 6;

/**
 * A CSS setting: the card shows a short, read-only extract of the CSS, and "Edit" opens a large dialog with a
 * CSS editor. The dialog changes only the draft of the settings: the CSS is saved with the other settings.
 * Fires "alfio-css-setting-change" with the new CSS when the dialog is applied.
 */
@customElement('alfio-css-setting')
export class CssSetting extends LitElement {
    @property({type: String}) label = '';
    @property({type: String}) value = '';
    // what applies when the CSS is empty, e.g. "Same as the organization (a value is set)"
    @property({type: String, attribute: 'not-set-text'}) notSetText = '';
    @property({type: String, attribute: 'help-text'}) helpText = '';

    @state() private open = false;
    @state() private draft = '';
    @query('sl-dialog') private dialog!: SlDialog;

    static readonly styles = [
        base,
        textColors,
        form,
        row,
        dialog,
        css`
            :host {
                display: block;
            }
            .css-label {
                display: block;
                margin-bottom: var(--sl-spacing-x-small);
                font-weight: var(--sl-font-weight-bold);
            }
            .css-summary {
                border: 1px solid var(--sl-input-border-color);
                border-radius: var(--sl-input-border-radius);
                background: var(--sl-color-gray-50);
            }
            .css-preview {
                margin: 0;
                padding: var(--sl-spacing-small) var(--sl-spacing-medium);
                font-family: var(--sl-font-mono);
                font-size: var(--sl-font-size-x-small);
                line-height: 1.5;
                color: var(--sl-color-neutral-700);
                white-space: pre;
                overflow-x: auto;
            }
            .css-empty {
                margin: 0;
                padding: var(--sl-spacing-small) var(--sl-spacing-medium);
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-small);
            }
            .css-summary-footer {
                display: flex;
                flex-wrap: wrap;
                align-items: center;
                justify-content: space-between;
                gap: var(--sl-spacing-x-small);
                padding: var(--sl-spacing-x-small) var(--sl-spacing-medium);
                border-top: 1px solid var(--sl-color-gray-200);
                background: var(--sl-color-neutral-0);
                border-radius: 0 0 var(--sl-input-border-radius) var(--sl-input-border-radius);
                font-size: var(--sl-font-size-small);
                color: var(--sl-color-gray-600);
            }
            .help-text {
                margin-top: var(--sl-spacing-2x-small);
                font-size: var(--sl-font-size-x-small);
                color: var(--sl-color-gray-600);
            }
            sl-dialog {
                --alfio-dialog-max-width: 72rem;
            }
            .footer-note {
                margin: 0 0 var(--sl-spacing-small);
                text-align: right;
            }
            alfio-code-editor {
                /* as much room as the viewport allows, leaving space for the header and the footer of the dialog */
                --alfio-code-editor-height: max(16rem, calc(100vh - 20rem));
            }
        `,
    ];

    render(): TemplateResult {
        const text = this.value.trim();
        const lines = this.value.split('\n');
        return html`
            <span class="css-label">${this.label}</span>
            <div class="css-summary">
                ${when(text !== '', () => html`
                    <pre class="css-preview">${lines.slice(0, PREVIEW_LINES).join('\n')}${when(lines.length > PREVIEW_LINES, () => '\n…')}</pre>
                `, () => html`
                    <p class="css-empty">${this.notSetText || t('admin.css-setting.none')}</p>
                `)}
                <div class="css-summary-footer">
                    <span>${when(text !== '', () => lineCount(lines.length), () => nothing)}</span>
                    <sl-button class="edit-css" variant="default" size="small" @click=${() => this.show()}>
                        <sl-icon slot="prefix" name="pencil"></sl-icon>
                        ${t('admin.common.edit')}
                    </sl-button>
                </div>
            </div>
            ${when(this.helpText !== '', () => html`<div class="help-text">${this.helpText}</div>`)}
            <sl-dialog class="responsive-dialog" label=${this.label}
                       @sl-request-close=${this.onRequestClose}
                       @sl-after-hide=${this.onAfterHide}>
                ${when(this.open, () => html`
                    <alfio-code-editor .language=${cssLanguage()} .value=${this.draft}
                                       help-text=${t('admin.css-setting.help')}
                                       @value-changed=${(e: CustomEvent<string>) => { this.draft = e.detail; }}></alfio-code-editor>
                    <div slot="footer">
                        <sl-divider></sl-divider>
                        <p class="text-muted footer-note">${t('admin.css-setting.footer')}</p>
                        <div class="row" style="--alfio-row-cols: 3">
                            <sl-button variant="default" size="large" @click=${() => void this.dialog.hide()}>${t('admin.common.cancel')}</sl-button>
                            <div></div>
                            <sl-button class="apply-css" variant="warning" size="large" @click=${() => this.apply()}>${t('admin.common.apply')}</sl-button>
                        </div>
                    </div>
                `)}
            </sl-dialog>
        `;
    }

    private async show(): Promise<void> {
        this.draft = this.value;
        this.open = true;
        await this.updateComplete;
        await this.dialog.show();
    }

    private apply(): void {
        this.dispatchEvent(new CustomEvent<string>(CSS_SETTING_CHANGE_EVENT, {
            detail: this.draft,
            bubbles: true,
            composed: true,
        }));
        void this.dialog.hide();
    }

    // a click outside the dialog would discard the CSS being edited: only the close button and Cancel close it
    private onRequestClose = (event: SlRequestCloseEvent): void => {
        if (event.target === event.currentTarget && event.detail.source === 'overlay') {
            event.preventDefault();
        }
    };

    private onAfterHide = (event: Event): void => {
        if (event.target !== event.currentTarget) {
            return;
        }
        this.open = false;
    };
}

function lineCount(count: number): string {
    return t('admin.css-setting.lines', { count });
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-css-setting': CssSetting;
    }
    interface GlobalEventHandlersEventMap {
        'alfio-css-setting-change': CustomEvent<string>;
    }
}
