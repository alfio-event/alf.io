import {customElement, property, state} from "lit/decorators.js";
import {unsafeHTML} from 'lit/directives/unsafe-html.js';
import {css, html, LitElement, TemplateResult} from "lit";
import {Task} from "@lit/task";
import {UtilService} from "../service/util.ts";
import {dialog, row} from "../styles.ts";

@customElement('alfio-display-commonmark-preview')
export class DisplayCommonMarkPreview extends LitElement {

    static styles = [
        row,
        dialog,
        css`
            sl-button.right::part(base) {
                justify-content: end;
            }
            sl-dialog {
                --alfio-dialog-max-width: 52rem;
            }
            .preview {
                min-height: 50vh;
                border: 1px dashed var(--sl-color-neutral-200);
                padding: 0 var(--sl-spacing-medium);
                overflow-wrap: anywhere;
            }
            .preview-loading {
                display: grid;
                place-items: center;
                min-height: 50vh;
            }
        `
    ]

    @property({ type: String, attribute: 'data-button-text' })
    buttonText?: string;

    @property({ type: String, attribute: 'data-text' })
    text?: string;

    @state()
    dialogOpen = false;

    private task?: Task<ReadonlyArray<any>, string>;

    protected render(): TemplateResult {
        return html`
            <div class="row">
                <sl-button class="right" variant="text" .disabled=${(this.text ?? '').trim().length === 0} @click=${this.openDialog} size="small"><sl-icon name="easel3" slot="prefix"></sl-icon> ${this.buttonText}</sl-button>
            </div>
            <sl-dialog ?open=${this.dialogOpen} label="Text preview" class="responsive-dialog dialog-markdown-preview"
                       @sl-after-hide=${this.onAfterHide}>
                ${this.renderText()}
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${this.closeDialog}>Close</sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    private openDialog(): void {
        this.dialogOpen = true;
        this.task = new Task<ReadonlyArray<any>, string>(this,
            async () => {
                return await UtilService.renderMarkdown(this.text!);
            }, () => []);
    }

    private closeDialog(): void {
        this.dialogOpen = false;
    }

    // closed with the X button or the Escape key: keep the state in sync, so that the preview can be opened again
    private onAfterHide = (event: Event): void => {
        if (event.target !== event.currentTarget) {
            return;
        }
        this.dialogOpen = false;
    };

    private renderText(): TemplateResult {
        if (this.task == null) {
            return html``;
        }
        return this.task.render({
            initial: () => this.renderLoading(),
            pending: () => this.renderLoading(),
            complete: (value) => html`<div class="preview">${unsafeHTML(value)}</div>`,
            error: () => html`
                <sl-alert variant="danger" open>
                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                    The preview could not be rendered. Please try again.
                </sl-alert>
            `,
        });
    }

    private renderLoading(): TemplateResult {
        return html`<div class="preview-loading"><sl-spinner></sl-spinner></div>`;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-display-commonmark-preview': DisplayCommonMarkPreview
    }
}
