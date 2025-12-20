import { html, LitElement, type TemplateResult } from "lit";
import { customElement, property, query, state } from "lit/decorators.js";
import { dialog, row } from "../styles";
import type { SlDialog } from "@shoelace-style/shoelace";

/**
 * Basic confirm/cancel helper dialog for prompting
 * a user to inform of a dangerous action with
 * potential consequences.
 */
@customElement('confirmation-dialog')
export class ConfirmationDialog extends LitElement {
    static styles = [row, dialog];

    @property({attribute: "dialog-title", type: String})
    dialogTitle?: string;

    @property({attribute: "dialog-description", type: String})
    dialogDescription?: string;

    @property({attribute: "confirm-text", type: String})
    confirmText?: string;

    @property({attribute: "cancel-text", type: String})
    cancelText?: string;

    @property({attribute: "confirm-variant", type: String})
    confirmVariant?: string;

    /**
     * Used to pass data to the callback method (such as an ID).
     * Useful for identifying what object or operation this dialog pertains to.
     */
    @state()
    dialogPayload?: unknown = null;

    @query("sl-dialog")
    dialog?: SlDialog;

    protected render(): TemplateResult {
        return html`
            <sl-dialog label=${this.dialogTitle} class="dialog">
                ${this.dialogDescription}
                <span slot="footer">
                    <sl-button variant=${this.confirmVariant} @click=${() => this.handleConfirmPressed()}>
                        ${this.confirmText}
                    </sl-button>
                    <sl-button variant="neutral" @click=${() => this.closeDialog()}>${this.cancelText}</sl-button>
                </span>
            </sl-dialog>
        `;
    }

    protected handleConfirmPressed() {
        this.closeDialog();
        this.dispatchEvent(new CustomEvent("confirmActionButtonPressed", {detail: this.dialogPayload}));
    }

    openDialog(payload?: unknown) {
        if(payload) {
            this.dialogPayload = payload;
        }
        this.dialog?.show();
    }

    closeDialog() {
        this.dialog?.hide();
    }
}

