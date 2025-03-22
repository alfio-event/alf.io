import {customElement, query} from "lit/decorators.js";
import {html, LitElement, TemplateResult} from "lit";
import {SlDialog, SlRequestCloseEvent} from "@shoelace-style/shoelace";
import {
    AdditionalField,
    NewAdditionalFieldFromTemplate
} from "../../model/additional-field.ts";
import {ContentLanguage, PurchaseContext} from "../../model/purchase-context.ts";

@customElement('alfio-additional-field-edit')
export class AdditionalFieldEdit extends LitElement {

    @query("sl-dialog#editDialog")
    dialog: SlDialog | null = null;


    protected render(): TemplateResult {
        return html`
            <sl-dialog
                id="editDialog"
                style="--width: 50vw; --header-spacing:16px; --body-spacing: 16px; --sl-font-size-large: 1.5rem;"
                class="dialog"
                @sl-request-close=${this.preventAccidentalClose}>

                TODO insert form here

            </sl-dialog>
        `;
    }

    public async open(request: {
        field?: AdditionalField,
        template?: NewAdditionalFieldFromTemplate,
        supportedLanguages: ContentLanguage[],
        purchaseContext: PurchaseContext,
        ordinal: number
    }): Promise<boolean> {
        if (this.dialog != null) {
            console.log(request);
            await this.dialog!.show();
        }
        return this.dialog != null;
    }

    private preventAccidentalClose(e: SlRequestCloseEvent): void {
        if (e.detail.source === 'overlay') {
            e.preventDefault();
        } else {
            this.dispatchEvent(new CustomEvent('alfio-dialog-closed', { detail: { success: false } }));
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-additional-field-edit': AdditionalFieldEdit
    }
}
