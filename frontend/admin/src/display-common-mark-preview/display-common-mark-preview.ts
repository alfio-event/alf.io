import {customElement, property, state} from "lit/decorators.js";
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

    @state()
    dialogOpen = false;

    private task?: Task<ReadonlyArray<any>, string>;

    protected render(): TemplateResult {
        return html`
            <sl-button variant="default" @click=${this.openDialog} size="small"><sl-icon name="easel3" slot="prefix"></sl-icon> ${this.buttonText}</sl-button>
            <sl-dialog ?open=${this.dialogOpen} label="Text Preview" style="--width: 50vw;" class="dialog-markdown-preview" >
                <div style="height: 50vh; border: dashed 1px var(--sl-color-neutral-200); padding: 0 1rem;">
                    ${this.renderText()}
                </div>
                <sl-button slot="footer" variant="default" @click=${this.closeDialog}>Close</sl-button>
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
