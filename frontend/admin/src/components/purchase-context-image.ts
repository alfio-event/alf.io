import {css, html, LitElement, nothing, PropertyValues, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {repeat} from 'lit/directives/repeat.js';
import {form, textColors} from '../styles.ts';
import {errorMessage} from '../service/helpers.ts';
import {transformImageFile, uploadImage} from '../service/image.ts';
import './file-upload.ts';
import type {FileUploadChangeEvent} from './file-upload.ts';

export interface ImageChangeDetail {
    // null when the image was removed
    fileBlobId: string | null;
    // data URL of the newly uploaded image, null when removed
    previewUrl: string | null;
    fileName?: string;
    fileType?: string;
}

/**
 * Image of a purchase context (event, subscription): the drop box on the left is the shared
 * <alfio-file-upload>, the preview of the current image is on the right. The picked file is
 * transformed (SVG converted to PNG, resized to max 450px) and uploaded exactly as the
 * AngularJS create-event flow does, then an "image-change" event is dispatched with the new
 * file blob id.
 *
 * Plain custom element on purpose: it is also used inside AngularJS templates, where the
 * controller sets the "current-image" attribute and listens for the bubbling "image-change"
 * event (or reads the "fileBlobId" property).
 */
@customElement('alfio-purchase-context-image')
export class PurchaseContextImage extends LitElement {

    // URL of the currently saved image (e.g. /file/{fileBlobId}), shown until a new one is uploaded
    @property({type: String, attribute: 'current-image'})
    currentImage: string | null = null;

    @property({type: String})
    accept: string = 'image/*';

    @property({type: Number, attribute: 'max-size'})
    maxSize: number = 1048576;

    @property({type: String})
    label: string = 'Drop your image here or click to select';

    // set after a successful upload
    @property({attribute: false})
    fileBlobId: string | null = null;

    @state()
    private previewUrl: string | null = null;

    @state()
    private uploading: boolean = false;

    @state()
    private error: string = '';

    // bumped after each pick, so the file-upload goes back to its drop zone
    @state()
    private uploadResetCount: number = 0;

    static styles = [form, textColors, css`
        :host {
            display: block;
        }

        .image-upload {
            display: grid;
            grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
            gap: var(--sl-spacing-medium);
            align-items: stretch;
        }

        .preview-box {
            position: relative;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            gap: var(--sl-spacing-small);
            min-height: 12rem;
            padding: var(--sl-spacing-medium);
            border: 1px solid var(--sl-input-border-color);
            border-radius: var(--sl-input-border-radius);
            background-color: var(--sl-color-neutral-5);
        }

        .preview-box img {
            max-width: 100%;
            max-height: 15rem;
            object-fit: contain;
        }

        .preview-placeholder {
            display: grid;
            place-items: center;
            gap: var(--sl-spacing-x-small);
            color: var(--sl-color-gray-400);
            font-size: var(--sl-font-size-small);
        }

        .preview-placeholder sl-icon {
            font-size: var(--sl-font-size-3x-large);
        }

        .preview-spinner {
            position: absolute;
            top: var(--sl-spacing-small);
            right: var(--sl-spacing-small);
        }

        .error-message {
            margin-top: var(--sl-spacing-small);
        }

        @media (max-width: 640px) {
            .image-upload {
                grid-template-columns: 1fr;
            }
        }
    `];

    protected willUpdate(changed: PropertyValues<this>): void {
        if (changed.has('currentImage')) {
            // the parent changed the saved image (load, reset): drop any local preview
            this.previewUrl = null;
        }
    }

    protected render(): TemplateResult {
        const preview = this.previewUrl ?? (this.currentImage != null && this.currentImage !== '' ? this.currentImage : null);
        return html`
            <div class="image-upload">
                ${repeat([this.uploadResetCount], (count) => count, () => html`
                    <alfio-file-upload
                        accept=${this.accept}
                        max-size=${this.maxSize}
                        label=${this.label}
                        @change=${this.onFileSelected}></alfio-file-upload>
                `)}
                <div class="preview-box">
                    ${when(preview != null,
                        () => html`
                            <img src=${preview} alt="Image preview">
                            <sl-button variant="danger" size="small" class="remove-image" @click=${this.removeImage}>
                                <sl-icon slot="prefix" name="trash"></sl-icon>
                                Remove
                            </sl-button>
                        `,
                        () => html`
                            <div class="preview-placeholder">
                                <sl-icon name="image" aria-label="No image"></sl-icon>
                                <span>No image selected</span>
                            </div>
                        `)}
                    ${when(this.uploading, () => html`<sl-spinner class="preview-spinner"></sl-spinner>`, () => nothing)}
                </div>
            </div>
            ${when(this.error,
                () => html`
                    <sl-alert variant="danger" class="error-message" open>
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        ${this.error}
                    </sl-alert>
                `)}
        `;
    }

    private onFileSelected = async (event: FileUploadChangeEvent): Promise<void> => {
        const file = event.detail.file;
        if (file == null) {
            // the selection was cleared inside alfio-file-upload
            return;
        }
        this.error = '';
        this.uploading = true;
        try {
            const transformed = await transformImageFile(file);
            const fileBlobId = await uploadImage(transformed);
            this.previewUrl = transformed.dataUrl;
            this.fileBlobId = fileBlobId;
            this.dispatchChange({
                fileBlobId,
                previewUrl: transformed.dataUrl,
                fileName: transformed.name,
                fileType: transformed.type,
            });
        } catch (error) {
            this.error = errorMessage(error, 'Failed to upload the image');
        } finally {
            this.uploading = false;
            this.uploadResetCount++;
        }
    };

    private removeImage = (): void => {
        this.previewUrl = null;
        this.fileBlobId = null;
        this.error = '';
        this.uploadResetCount++;
        this.dispatchChange({fileBlobId: null, previewUrl: null});
    };

    private dispatchChange(detail: ImageChangeDetail): void {
        this.dispatchEvent(new CustomEvent<ImageChangeDetail>('image-change', {
            detail,
            bubbles: true,
            composed: true,
        }));
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-purchase-context-image': PurchaseContextImage;
    }
}
