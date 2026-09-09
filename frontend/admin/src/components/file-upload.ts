import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {form, textColors} from '../styles.ts';

export type FileUploadChangeEvent = CustomEvent<{ file: File | null }>;

@customElement('alfio-file-upload')
export class FileUpload extends LitElement {

    static styles = [form, textColors, css`
        :host {
            display: block;
        }

        .drop-zone {
            border: 2px dashed var(--sl-input-border-color);
            border-radius: var(--sl-input-border-radius);
            padding: var(--sl-spacing-3x-large) var(--sl-spacing-large);
            text-align: center;
            cursor: pointer;
            transition: border-color 0.15s ease, background-color 0.15s ease;
            background-color: var(--sl-color-neutral-5);
            outline: none;
        }

        .drop-zone:focus-visible {
            border-color: var(--sl-color-primary-500);
            background-color: var(--sl-color-primary-100);
            box-shadow: 0 0 0 3px var(--sl-color-primary-200);
        }

        .drop-zone:hover {
            border-color: var(--sl-color-primary-300);
            background-color: var(--sl-color-primary-50);
        }

        .drop-zone.drag-over {
            border-color: var(--sl-color-primary-500);
            background-color: var(--sl-color-primary-100);
        }

        .drop-zone-icon {
            color: var(--sl-color-primary-400);
            margin-bottom: var(--sl-spacing-small);
        }

        .drop-zone-icon sl-icon {
            --sl-icon-size: var(--sl-font-size-4x-large);
        }

        .drop-zone-prompt {
            margin: var(--sl-spacing-small) 0 0;
            color: var(--sl-color-gray-600);
            font-size: var(--sl-font-size-small);
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
    private error: string = '';

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
                    () => html`<p class="drop-zone-prompt">Max size: ${this.formatBytes(this.maxSize)}</p>`)}
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
                    <span class="file-size">${this.formatBytes(this.selectedFile.size)}</span>
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

        if (this.accept && !this.acceptMatch(file)) {
            this.error = `File type "${file.type || '(unknown)'}" is not accepted. Expected: ${this.accept}`;
            return;
        }

        if (this.maxSize > 0 && file.size > this.maxSize) {
            this.error = `File exceeds maximum size of ${this.formatBytes(this.maxSize)}`;
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

    private acceptMatch(file: File): boolean {
        const acceptList = this.accept.split(',').map(s => s.trim().toLowerCase());
        const fileType = file.type.toLowerCase();
        const fileName = file.name.toLowerCase();

        for (const pattern of acceptList) {
            if (pattern === fileType) return true;
            if (pattern.endsWith('/*') && fileType.startsWith(pattern.slice(0, -1))) return true;
            if (pattern.startsWith('.') && fileName.endsWith(pattern)) return true;
        }
        return false;
    }

    private dispatchChange(file: File | null): void {
        this.dispatchEvent(new CustomEvent<{ file: File | null }>('change', {
            detail: {file},
            bubbles: false,
            composed: true,
        }));
    }

    private formatBytes(bytes: number): string {
        if (bytes === 0) return '0 Bytes';
        const units = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(1024));
        const size = bytes / Math.pow(1024, i);
        return `${size.toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-file-upload': FileUpload;
    }
}
