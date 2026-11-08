import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {dropZone, form, textColors} from '../styles.ts';
import {formatBytesBinary, matchesAccept} from '../service/helpers.ts';

export type FileUploadChangeEvent = CustomEvent<{ file: File | null }>;

@customElement('alfio-file-upload')
export class FileUpload extends LitElement {

    static styles = [form, textColors, dropZone, css`
        :host {
            display: block;
        }

        /* when the host is stretched by a parent grid/flex row (e.g. beside the image
           preview in alfio-purchase-context-image), the dashed box fills it instead of
           keeping its content height; with an auto-height host, 100% resolves to auto */
        .drop-zone {
            height: 100%;
            box-sizing: border-box;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
        }

        .file-info {
            display: flex;
            align-items: center;
            gap: var(--sl-spacing-small);
            padding: var(--sl-spacing-medium) var(--sl-spacing-large);
            border: 1px solid var(--sl-input-border-color);
            border-radius: var(--sl-input-border-radius);
            background-color: var(--sl-color-neutral-5);
        }

        .file-info-icon {
            color: var(--sl-color-primary-500);
        }

        .file-details {
            flex: 1;
            min-width: 0;
        }

        .file-name {
            display: block;
            font-weight: 600;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        .file-size {
            display: block;
            font-size: var(--sl-font-size-small);
            color: var(--sl-color-gray-500);
        }

        .error-message {
            margin-top: var(--sl-spacing-small);
        }
    `];

    @property({type: String})
    public accept: string = '';

    @property({type: String})
    public label: string = 'Choose a file or drag it here';

    @property({type: Number})
    public maxSize: number = 0;

    @query('#file-input')
    private fileInput?: HTMLInputElement;

    @state()
    private selectedFile: File | null = null;

    @state()
    private error: TemplateResult | string = '';

    @state()
    private isDragOver: boolean = false;

    protected render(): TemplateResult {
        return html`
            ${when(this.selectedFile,
                () => this.renderFileInfo(),
                () => this.renderDropZone())}
            <input
                id="file-input"
                type="file"
                .accept=${this.accept}
                style="display: none"
                @change=${this.onInputChange}
            />
            ${when(this.error,
                () => html`
                    <sl-alert variant="danger" class="error-message" open>
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        ${this.error}
                    </sl-alert>
                `)}
        `;
    }

    private renderDropZone(): TemplateResult {
        return html`
            <div
                class="drop-zone ${this.isDragOver ? 'drag-over' : ''}"
                role="button"
                tabindex="0"
                aria-label="Choose a file"
                @click=${() => this.fileInput?.click()}
                @keydown=${this.onDropZoneKeydown}
                @dragover=${this.onDragOver}
                @dragleave=${this.onDragLeave}
                @drop=${this.onDrop}
            >
                <div class="drop-zone-icon">
                    <sl-icon name="cloud-upload"></sl-icon>
                </div>
                <p>${this.label}</p>
                ${when(this.accept,
                    () => html`<p class="drop-zone-prompt">Accepted: ${this.accept}</p>`)}
                ${when(this.maxSize > 0,
                    () => html`<p class="drop-zone-prompt">Max size: ${formatBytesBinary(this.maxSize)}</p>`)}
            </div>
        `;
    }

    private renderFileInfo(): TemplateResult | typeof nothing {
        if (!this.selectedFile) {
            return nothing;
        }
        return html`
            <div class="file-info">
                <sl-icon class="file-info-icon" name="file-earmark-text"></sl-icon>
                <div class="file-details">
                    <span class="file-name">${this.selectedFile.name}</span>
                    <span class="file-size"><sl-format-bytes value=${this.selectedFile.size}></sl-format-bytes></span>
                </div>
                <sl-icon-button
                    name="x-circle"
                    label="Remove file"
                    @click=${this.clearFile}
                ></sl-icon-button>
            </div>
        `;
    }

    private onInputChange = (e: Event): void => {
        const input = e.target as HTMLInputElement;
        const file = input.files?.[0] ?? null;
        this.processFile(file);
    };

    private onDropZoneKeydown = (e: KeyboardEvent): void => {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            this.fileInput?.click();
        }
    };

    private onDragOver = (e: DragEvent): void => {
        e.preventDefault();
        this.isDragOver = true;
    };

    private onDragLeave = (e: DragEvent): void => {
        e.preventDefault();
        const relatedTarget = e.relatedTarget as Node;
        const host = this.shadowRoot?.host;

        if (host?.contains(relatedTarget)) return;

        this.isDragOver = false;
    };

    private onDrop = (e: DragEvent): void => {
        e.preventDefault();
        this.isDragOver = false;
        const file = e.dataTransfer?.files[0] ?? null;
        this.processFile(file);
    };

    private processFile(file: File | null): void {
        this.error = '';
        if (!file) {
            this.selectedFile = null;
            this.dispatchChange(null);
            return;
        }

        if (this.accept && !matchesAccept(file, this.accept)) {
            this.error = `File type "${file.type || '(unknown)'}" is not accepted. Expected: ${this.accept}`;
            return;
        }

        if (this.maxSize > 0 && file.size > this.maxSize) {
            this.error = html`File exceeds maximum size of ${formatBytesBinary(this.maxSize)}`;
            return;
        }

        this.selectedFile = file;
        this.dispatchChange(file);
    }

    private clearFile = (): void => {
        if (this.fileInput) {
            this.fileInput.value = '';
        }
        this.selectedFile = null;
        this.error = '';
        this.dispatchChange(null);
    };

    private dispatchChange(file: File | null): void {
        this.dispatchEvent(new CustomEvent<{ file: File | null }>('change', {
            detail: {file},
            bubbles: false,
            composed: true,
        }));
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-file-upload': FileUpload;
    }
}
