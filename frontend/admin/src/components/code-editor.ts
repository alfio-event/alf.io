import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, query} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {EditorState} from '@codemirror/state';
import {EditorView, keymap} from '@codemirror/view';
import {indentMore} from '@codemirror/commands';
import {basicSetup} from 'codemirror';
import {javascript} from '@codemirror/lang-javascript';
import {LanguageSupport} from '@codemirror/language';

export type AlfioCodeChange = CustomEvent<string>;

/**
 * JavaScript editor based on CodeMirror, for the scripts of the extension editor.
 * The "value" property provides the content; every change fires "value-changed" with the new content.
 * "language" replaces the default JavaScript support, e.g. to customize the completions; it is read when the editor is created.
 */
@customElement('alfio-code-editor')
export class CodeEditor extends LitElement {
    @property({ type: String }) value = '';
    // form-control label, displayed above the editor
    @property({ type: String }) label = '';
    // hint displayed below the editor
    @property({ type: String, attribute: 'help-text' }) helpText = '';
    // the value is not valid: red border, and the help text is the error message
    @property({ type: Boolean, reflect: true }) invalid = false;
    @property({ attribute: false }) language: LanguageSupport = javascript();
    @query('#editor-host') private editorHost!: HTMLDivElement | null;

    private view: EditorView | null = null;
    // last content of the document, to tell external changes from keystrokes
    private docText = '';

    static readonly styles = css`
        :host {
            display: block;
        }

        .control-label {
            display: block;
            margin-bottom: var(--sl-spacing-x-small);
            font-weight: var(--sl-font-weight-bold);
            line-height: var(--sl-line-height-normal);
        }

        .editor-frame {
            /* pages where the script is the main content set a taller editor */
            height: var(--alfio-code-editor-height, 24rem);
            border: 1px solid var(--sl-input-border-color);
            border-radius: var(--sl-input-border-radius);
            overflow: hidden;
            transition: var(--sl-transition-x-fast) border-color, var(--sl-transition-x-fast) box-shadow;
        }

        .editor-frame:focus-within {
            border-color: var(--sl-input-border-color-focus);
            box-shadow: 0 0 0 3px var(--sl-input-focus-ring-color);
        }

        .help-text {
            margin-top: var(--sl-spacing-2x-small);
            font-size: var(--sl-font-size-x-small);
            color: var(--sl-color-gray-600);
        }

        :host([invalid]) .editor-frame {
            border-color: var(--sl-color-danger-600);
        }

        :host([invalid]) .help-text {
            color: var(--sl-color-danger-600);
        }
    `;

    updated(changedProperties: Map<string | symbol, unknown>): void {
        if (this.editorHost == null) {
            return;
        }
        if (this.view == null) {
            this.createEditor();
        } else if (changedProperties.has('value') && this.value !== this.docText) {
            // external update, e.g. the script loaded from the server or edited by the host page.
            // Only the changed range is replaced, so that the cursor, the scroll position and the undo history survive
            this.view.dispatch({changes: changedRange(this.docText, this.value)});
            this.docText = this.value;
        }
    }

    private createEditor(): void {
        const state = EditorState.create({
            doc: this.value,
            extensions: [
                theme,
                this.language,
                basicSetup,
                // keep Tab in the editor (indent) instead of moving the focus
                keymap.of([{key: 'Tab', run: indentMore, preventDefault: true}]),
                EditorView.updateListener.of((update) => {
                    if (update.docChanged && this.view != null) {
                        this.docText = this.view.state.doc.toString();
                        this.dispatchEvent(new CustomEvent<string>('value-changed', {
                            detail: this.docText,
                            composed: true,
                        }));
                    }
                }),
            ],
        });
        this.docText = this.value;
        this.view = new EditorView({state, parent: this.editorHost!});
    }

    render(): TemplateResult {
        return html`
            ${when(this.label !== '', () => html`<span class="control-label">${this.label}</span>`)}
            <div id="editor-host" class="editor-frame"></div>
            ${when(this.helpText !== '', () => html`<div class="help-text">${this.helpText}</div>`)}
        `;
    }
}

/**
 * The smallest change turning "before" into "after": the text between their common prefix and suffix
 */
function changedRange(before: string, after: string): { from: number, to: number, insert: string } {
    let prefix = 0;
    const maxPrefix = Math.min(before.length, after.length);
    while (prefix < maxPrefix && before[prefix] === after[prefix]) {
        prefix++;
    }
    let suffix = 0;
    const maxSuffix = maxPrefix - prefix;
    while (suffix < maxSuffix && before[before.length - 1 - suffix] === after[after.length - 1 - suffix]) {
        suffix++;
    }
    return {from: prefix, to: before.length - suffix, insert: after.slice(prefix, after.length - suffix)};
}

const theme = EditorView.theme({
    '&': {
        fontSize: 'var(--sl-font-size-small)',
        color: 'var(--sl-color-neutral-800)',
        height: '100%',
    },
    '.cm-scroller': {
        fontFamily: 'var(--sl-font-mono)',
        lineHeight: '1.5',
        paddingBottom: '10rem', // lets the last line be reached, like a textarea
    },
    '.cm-content': {
        caretColor: 'var(--sl-color-neutral-800)',
    },
    '.cm-gutters': {
        backgroundColor: 'var(--sl-color-gray-50)',
        color: 'var(--sl-color-gray-500)',
        border: 'none',
        borderRight: '1px solid var(--sl-color-gray-200)',
    },
    // the active line is painted over the selection layer: a light tint would wash the selection out on the
    // cursor line, a translucent darker one keeps it visible
    '.cm-activeLine': {
        backgroundColor: 'color-mix(in srgb, var(--sl-color-primary-600) 6%, transparent)',
    },
    '.cm-activeLineGutter': {
        backgroundColor: 'var(--sl-color-primary-50)',
    },
    '.cm-cursor, .cm-dropCursor': {
        borderLeftColor: 'var(--sl-color-neutral-800)',
    },
    // same selectors as CodeMirror's base theme, otherwise its default colors win
    '.cm-selectionLayer .cm-selectionBackground': {
        backgroundColor: 'var(--sl-color-primary-100)',
    },
    '&.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground': {
        backgroundColor: 'var(--sl-color-primary-200)',
    },
});

declare global {
    interface HTMLElementTagNameMap {
        'alfio-code-editor': CodeEditor;
    }
    interface GlobalEventHandlersEventMap {
        'value-changed': AlfioCodeChange;
    }
}