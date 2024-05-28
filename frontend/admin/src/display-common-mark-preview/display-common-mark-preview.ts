import {customElement, property} from "lit/decorators.js";
import {unsafeHTML} from 'lit/directives/unsafe-html.js';
import {html, LitElement, TemplateResult} from "lit";
import {Task} from "@lit/task";
import {UtilService} from "../service/util.ts";

@customElement('alfio-display-commonmark-preview')
export class DisplayCommonMarkPreview extends LitElement {
    @property({ type: String, attribute: 'data-button-text' })
    buttonText?: string;

    @property({ type: String, attribute: 'data-text' })
    text?: string;

    @property({ type: Boolean })
    dialogOpen = false;

    private task?: Task<ReadonlyArray<any>, string>;

    protected render(): TemplateResult {
        return html`
            <sl-button variant="neutral" @click=${this.openDialog}>${this.buttonText}</sl-button>
            <sl-dialog ?open=${this.dialogOpen} label="Preview" class="dialog-markdown-preview" id="test-id">
                <div style="height: 50vh; border: dashed 2px var(--sl-color-neutral-200); padding: 0 1rem;">
                    ${this.renderText()}
                </div>
                <sl-button slot="footer" variant="primary" @click=${this.closeDialog}>Close</sl-button>
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

    private renderText(): TemplateResult {
        return this.task?.render({
                initial: () => html`init`,
                complete: (value) => html`${unsafeHTML(value)}`
            }) ?? html`error`;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-display-commonmark-preview': DisplayCommonMarkPreview
    }
}
