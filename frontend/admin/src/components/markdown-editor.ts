import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {Editor, Extension} from '@tiptap/core';
import {StarterKit} from '@tiptap/starter-kit';
import {Markdown} from '@tiptap/markdown';
import {Table, TableCell, TableHeader, TableRow} from '@tiptap/extension-table';
import {Plugin, PluginKey} from '@tiptap/pm/state';
import type {SlInput, SlMenuItem} from '@shoelace-style/shoelace';

export type AlfioMarkdownChange = CustomEvent<string>;

// set on the transactions that replace the whole document from the outside (the "value" property): they bypass the max-length check
const skipMaxLength = new PluginKey('alfioSkipMaxLength');

/**
 * WYSIWYG editor for markdown-capable content, based on Tiptap (ProseMirror) with the official
 * Markdown extension. The "value" property is the markdown source and every change fires
 * "value-changed" with the updated markdown, so the parent keeps owning the stored format
 * (CommonMark, rendered server-side by /admin/api/utils/render-commonmark).
 *
 * Same shape as alfio-code-editor: "label" above the frame, "invalid" paints the frame red.
 * "max-length" caps the length of the markdown source: changes that would exceed it are
 * rejected, like the maxlength of a textarea.
 */
@customElement('alfio-markdown-editor')
export class MarkdownEditor extends LitElement {
    // the markdown source of the document
    @property({type: String}) value = '';
    // form-control label, displayed above the editor
    @property({type: String}) label = '';
    // maximum length of the markdown source, 0 = no limit
    @property({type: Number, attribute: 'max-length'}) maxLength = 0;
    // the value is not valid: red border
    @property({type: Boolean, reflect: true}) invalid = false;
    @query('#editor-host') private editorHost!: HTMLDivElement | null;

    // bumped on every editor transaction, so the toolbar re-renders its active/disabled states
    @state() private toolbarVersion = 0;
    @state() private linkDialogOpen = false;
    @state() private linkUrl = '';

    private editor: Editor | null = null;
    // last markdown emitted by the editor, to tell external "value" updates from the echo of our own changes
    private lastMarkdown = '';

    static styles = css`
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
            border: 1px solid var(--sl-input-border-color);
            border-radius: var(--sl-input-border-radius);
            background-color: var(--sl-input-background-color);
            overflow: hidden;
            transition: var(--sl-transition-x-fast) border-color, var(--sl-transition-x-fast) box-shadow;
        }

        .editor-frame:focus-within {
            border-color: var(--sl-input-border-color-focus);
            box-shadow: 0 0 0 3px var(--sl-input-focus-ring-color);
        }

        :host([invalid]) .editor-frame {
            border-color: var(--sl-color-danger-600);
        }

        .toolbar {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            gap: var(--sl-spacing-3x-small);
            padding: var(--sl-spacing-2x-small);
            background-color: var(--sl-color-gray-50);
            border-bottom: 1px solid var(--sl-color-gray-200);
        }

        .toolbar-divider {
            width: 1px;
            align-self: stretch;
            margin: 0 var(--sl-spacing-2x-small);
            background-color: var(--sl-color-gray-200);
        }

        /* the active state of a toolbar toggle (sl-icon-button has no variant): filled primary icon */
        .toolbar sl-icon-button[aria-pressed="true"] {
            color: var(--sl-color-neutral-0);
        }

        .toolbar sl-icon-button[aria-pressed="true"]::part(base) {
            background-color: var(--sl-color-primary-600);
        }

        .editor-host {
            max-height: 24rem;
            overflow: auto;
        }

        /* ProseMirror content. white-space is required: ProseMirror warns when its base CSS
           (which sets it) does not reach the shadow root */
        .editor-host .ProseMirror {
            white-space: pre-wrap;
            overflow-wrap: break-word;
            outline: none;
            min-height: 5rem;
            padding: var(--sl-spacing-small) var(--sl-spacing-medium);
            font-size: var(--sl-font-size-small);
            color: var(--sl-color-neutral-800);
        }

        .editor-host .ProseMirror > :last-child {
            margin-bottom: 0;
        }

        .editor-host .ProseMirror p,
        .editor-host .ProseMirror ul,
        .editor-host .ProseMirror ol,
        .editor-host .ProseMirror blockquote,
        .editor-host .ProseMirror pre,
        .editor-host .ProseMirror table,
        .editor-host .ProseMirror h1,
        .editor-host .ProseMirror h2,
        .editor-host .ProseMirror h3 {
            margin: 0 0 var(--sl-spacing-x-small);
        }

        .editor-host .ProseMirror ul,
        .editor-host .ProseMirror ol {
            padding-inline-start: var(--sl-spacing-large);
        }

        .editor-host .ProseMirror h1,
        .editor-host .ProseMirror h2,
        .editor-host .ProseMirror h3 {
            font-weight: var(--sl-font-weight-semibold);
            line-height: 1.25;
        }

        .editor-host .ProseMirror h1 {
            font-size: 1.5em;
        }

        .editor-host .ProseMirror h2 {
            font-size: 1.3em;
        }

        .editor-host .ProseMirror h3 {
            font-size: 1.15em;
        }

        .editor-host .ProseMirror blockquote {
            padding-inline-start: var(--sl-spacing-small);
            border-inline-start: 3px solid var(--sl-color-gray-300);
            color: var(--sl-color-gray-600);
        }

        .editor-host .ProseMirror code {
            font-family: var(--sl-font-mono);
            font-size: 0.9em;
            background-color: var(--sl-color-gray-100);
            border-radius: var(--sl-border-radius-small);
            padding: 0 var(--sl-spacing-3x-small);
        }

        .editor-host .ProseMirror pre {
            background-color: var(--sl-color-gray-100);
            border-radius: var(--sl-border-radius-medium);
            padding: var(--sl-spacing-small);
        }

        .editor-host .ProseMirror pre code {
            background: none;
            padding: 0;
        }

        .editor-host .ProseMirror a {
            color: var(--sl-color-primary-600);
        }

        .editor-host .ProseMirror hr {
            border: none;
            border-top: 1px solid var(--sl-color-gray-300);
            margin: var(--sl-spacing-small) 0;
        }

        .editor-host .ProseMirror table {
            border-collapse: collapse;
            width: 100%;
        }

        .editor-host .ProseMirror th,
        .editor-host .ProseMirror td {
            border: 1px solid var(--sl-color-gray-300);
            padding: var(--sl-spacing-2x-small) var(--sl-spacing-x-small);
            text-align: start;
        }

        .editor-host .ProseMirror th {
            background-color: var(--sl-color-gray-100);
            font-weight: var(--sl-font-weight-semibold);
        }

        .editor-host .ProseMirror .selectedCell {
            background-color: var(--sl-color-primary-50);
        }

    `;

    protected firstUpdated(): void {
        this.createEditor();
    }

    disconnectedCallback(): void {
        this.editor?.destroy();
        this.editor = null;
        super.disconnectedCallback();
    }

    protected updated(changedProperties: Map<string | symbol, unknown>): void {
        if (this.editor == null) {
            return;
        }
        if (changedProperties.has('value') && this.value !== this.lastMarkdown) {
            // external update, e.g. the text loaded from the server: replace the whole document
            this.editor.chain().setMeta(skipMaxLength, true).setContent(this.value, {contentType: 'markdown'}).run();
            this.lastMarkdown = this.editor.getMarkdown();
        }
    }

    private createEditor(): void {
        if (this.editorHost == null) {
            return;
        }
        const component = this;
        const maxLengthMarkdown = Extension.create({
            name: 'alfioMaxLengthMarkdown',
            addProseMirrorPlugins() {
                return [new Plugin({
                    key: skipMaxLength,
                    filterTransaction: (tr, _state) => {
                        if (tr.getMeta(skipMaxLength) != null || component.maxLength <= 0) {
                            return true;
                        }
                        const manager = component.editor?.markdown;
                        if (manager == null) {
                            return true;
                        }
                        // the limit applies to the markdown source, not to the visible text
                        return manager.serialize(tr.doc.toJSON()).length <= component.maxLength;
                    },
                })];
            },
        });
        this.editor = new Editor({
            element: this.editorHost,
            extensions: [
                StarterKit.configure({
                    heading: {levels: [1, 2, 3]},
                    link: {openOnClick: false},
                }),
                Table.configure({resizable: false}),
                TableRow,
                TableHeader,
                TableCell,
                Markdown.configure({markedOptions: {gfm: true, breaks: false}}),
                maxLengthMarkdown,
            ],
            content: this.value,
            contentType: 'markdown',
            onUpdate: ({editor}) => {
                this.lastMarkdown = editor.getMarkdown();
                this.dispatchEvent(new CustomEvent<string>('value-changed', {
                    detail: this.lastMarkdown,
                    composed: true,
                }));
            },
            onTransaction: () => {
                this.toolbarVersion++;
            },
        });
        this.lastMarkdown = this.editor.getMarkdown();
        // render the toolbar with the real editor states (enabled buttons, active marks)
        this.requestUpdate();
    }

    private active(name: string, attributes?: Record<string, unknown>): boolean {
        return this.editor?.isActive(name, attributes) === true;
    }

    // sl-icon-button has no "variant": the active state is shown with aria-pressed, styled below
    private pressed(name: string, attributes?: Record<string, unknown>): string {
        if (this.active(name, attributes)) {
            return 'true';
        }
        return 'false';
    }

    private headingLabel(): string {
        if (this.active('heading', {level: 1})) {
            return 'H1';
        }
        if (this.active('heading', {level: 2})) {
            return 'H2';
        }
        if (this.active('heading', {level: 3})) {
            return 'H3';
        }
        return 'Paragraph';
    }

    private onHeadingSelect(e: CustomEvent): void {
        if (this.editor == null) {
            return;
        }
        const value = (e.detail as { item: SlMenuItem }).item.value;
        if (value === 'paragraph') {
            this.editor.chain().focus().setParagraph().run();
            return;
        }
        let level: 1 | 2 | 3 = 1;
        if (value === 'h2') {
            level = 2;
        } else if (value === 'h3') {
            level = 3;
        }
        this.editor.chain().focus().toggleHeading({level}).run();
    }

    private openLinkDialog(): void {
        if (this.editor == null) {
            return;
        }
        this.linkUrl = this.editor.getAttributes('link').href ?? '';
        this.linkDialogOpen = true;
    }

    private applyLink(): void {
        if (this.editor == null) {
            return;
        }
        const url = this.linkUrl.trim();
        if (url === '') {
            this.editor.chain().focus().unsetLink().run();
        } else {
            this.editor.chain().focus().setLink({href: url}).run();
        }
        this.linkDialogOpen = false;
    }

    private removeLink(): void {
        this.editor?.chain().focus().unsetLink().run();
        this.linkDialogOpen = false;
    }

    private renderToolbar(): TemplateResult {
        const editor = this.editor;
        return html`
            <div class="toolbar">
                <sl-icon-button name="arrow-counterclockwise" label="Undo"
                                ?disabled=${editor == null || !editor.can().undo()}
                                @click=${() => editor?.chain().focus().undo().run()}></sl-icon-button>
                <sl-icon-button name="arrow-clockwise" label="Redo"
                                ?disabled=${editor == null || !editor.can().redo()}
                                @click=${() => editor?.chain().focus().redo().run()}></sl-icon-button>
                <span class="toolbar-divider"></span>
                <sl-icon-button name="type-bold" label="Bold" aria-pressed=${this.pressed('bold')}
                                ?disabled=${editor == null || !editor.can().toggleBold()}
                                @click=${() => editor?.chain().focus().toggleBold().run()}></sl-icon-button>
                <sl-icon-button name="type-italic" label="Italic" aria-pressed=${this.pressed('italic')}
                                ?disabled=${editor == null || !editor.can().toggleItalic()}
                                @click=${() => editor?.chain().focus().toggleItalic().run()}></sl-icon-button>
                <sl-icon-button name="type-underline" label="Underline" aria-pressed=${this.pressed('underline')}
                                ?disabled=${editor == null || !editor.can().toggleUnderline()}
                                @click=${() => editor?.chain().focus().toggleUnderline().run()}></sl-icon-button>
                <sl-icon-button name="type-strikethrough" label="Strikethrough" aria-pressed=${this.pressed('strike')}
                                ?disabled=${editor == null || !editor.can().toggleStrike()}
                                @click=${() => editor?.chain().focus().toggleStrike().run()}></sl-icon-button>
                <sl-icon-button name="code-slash" label="Inline code" aria-pressed=${this.pressed('code')}
                                ?disabled=${editor == null || !editor.can().toggleCode()}
                                @click=${() => editor?.chain().focus().toggleCode().run()}></sl-icon-button>
                <span class="toolbar-divider"></span>
                <sl-dropdown hoist>
                    <sl-button slot="trigger" size="small" caret ?disabled=${editor == null}>${this.headingLabel()}</sl-button>
                    <sl-menu @sl-select=${this.onHeadingSelect}>
                        <sl-menu-item value="paragraph">Paragraph</sl-menu-item>
                        <sl-menu-item value="h1">Heading 1</sl-menu-item>
                        <sl-menu-item value="h2">Heading 2</sl-menu-item>
                        <sl-menu-item value="h3">Heading 3</sl-menu-item>
                    </sl-menu>
                </sl-dropdown>
                <span class="toolbar-divider"></span>
                <sl-icon-button name="list-ul" label="Bullet list" aria-pressed=${this.pressed('bulletList')}
                                ?disabled=${editor == null || !editor.can().toggleBulletList()}
                                @click=${() => editor?.chain().focus().toggleBulletList().run()}></sl-icon-button>
                <sl-icon-button name="list-ol" label="Ordered list" aria-pressed=${this.pressed('orderedList')}
                                ?disabled=${editor == null || !editor.can().toggleOrderedList()}
                                @click=${() => editor?.chain().focus().toggleOrderedList().run()}></sl-icon-button>
                <span class="toolbar-divider"></span>
                <sl-icon-button name="quote" label="Quote" aria-pressed=${this.pressed('blockquote')}
                                ?disabled=${editor == null || !editor.can().toggleBlockquote()}
                                @click=${() => editor?.chain().focus().toggleBlockquote().run()}></sl-icon-button>
                <sl-icon-button name="code-square" label="Code block" aria-pressed=${this.pressed('codeBlock')}
                                ?disabled=${editor == null || !editor.can().toggleCodeBlock()}
                                @click=${() => editor?.chain().focus().toggleCodeBlock().run()}></sl-icon-button>
                <sl-icon-button name="hr" label="Horizontal rule"
                                ?disabled=${editor == null || !editor.can().setHorizontalRule()}
                                @click=${() => editor?.chain().focus().setHorizontalRule().run()}></sl-icon-button>
                <span class="toolbar-divider"></span>
                <sl-icon-button name="link-45deg" label="Link" aria-pressed=${this.pressed('link')}
                                ?disabled=${editor == null}
                                @click=${this.openLinkDialog}></sl-icon-button>
                <sl-icon-button name="eraser" label="Clear formatting"
                                ?disabled=${editor == null || !editor.can().clearNodes()}
                                @click=${() => editor?.chain().focus().clearNodes().unsetAllMarks().run()}></sl-icon-button>
            </div>
        `;
    }

    private renderLinkDialog(): TemplateResult {
        return html`
            <sl-dialog ?open=${this.linkDialogOpen} label="Link" @sl-hide=${() => this.linkDialogOpen = false}>
                <sl-input label="URL" type="url" placeholder="https://…" .value=${this.linkUrl}
                          @sl-input=${(e: Event) => this.linkUrl = (e.target as SlInput).value}></sl-input>
                <sl-button slot="footer" variant="default" @click=${() => this.linkDialogOpen = false}>Cancel</sl-button>
                ${when(this.active('link'), () => html`
                    <sl-button slot="footer" variant="danger" @click=${this.removeLink}>Remove link</sl-button>
                `, () => nothing)}
                <sl-button slot="footer" variant="primary" @click=${this.applyLink}>Apply</sl-button>
            </sl-dialog>
        `;
    }

    render(): TemplateResult {
        return html`
            ${when(this.label !== '', () => html`<span class="control-label">${this.label}</span>`, () => nothing)}
            <div class="editor-frame">
                ${this.renderToolbar()}
                <div id="editor-host" class="editor-host"></div>
            </div>
            ${this.renderLinkDialog()}
        `;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-markdown-editor': MarkdownEditor;
    }
    interface GlobalEventHandlersEventMap {
        'value-changed': AlfioMarkdownChange;
    }
}
