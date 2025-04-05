import { customElement, property, query, state } from "lit/decorators.js";
import { html, LitElement, type TemplateResult } from "lit";
import { repeat } from "lit/directives/repeat.js";

import { CustomPaymentMethodsService } from "../service/custom-payment-methods";
import { dispatchFeedback } from "../model/dom-events";
import type { ConfirmationDialog } from "./confirmation-dialog";
import type { OfflinePaymentDialog } from "./offline-payment-dialog";

/**
 * Displays defined custom payment methods for org. Allows
 * organizers to add new payment methods.
 */
@customElement('offline-payment-config-block')
export class OfflinePaymentConfigBlock extends LitElement {
    @property({attribute: "organization", type: Number})
    organization: number = -1;

    @query("offline-payment-dialog")
    paymentCreationDialog?: OfflinePaymentDialog;
    @query("confirmation-dialog")
    confirmDialog?: ConfirmationDialog;

    @state()
    protected _selectedPaymentMethod: string | null = null;
    @state()
    protected _paymentConfig: CustomOfflinePayment[] = [];

    paymentMethodService?: CustomPaymentMethodsService;

    constructor() {
        super();
        this.updateConfigFromServer();
    }

    async updated(changedProperties: Map<string, unknown>) {
        if(changedProperties.has("organization") && this.organization) {
            this.paymentMethodService = new CustomPaymentMethodsService();
            await this.updateConfigFromServer();
        }
    }

    protected render(): TemplateResult {
        return html`
            <div>
                <sl-button type="button" variant="success" @click=${() => this.paymentCreationDialog?.openDialog()} size="medium">
                    <sl-icon name="plus-circle" slot="prefix"></sl-icon>
                    New Payment Method
                </sl-button>
                ${this.renderPaymentMethodDetails()}
                <offline-payment-dialog
                    .currentMethods=${this._paymentConfig}
                    @offlinePaymentDialogSave=${this._saveCustomPaymentMethod}
                />
            </div>
        `;
    }

    // TODO: Support multiple localizations instead of defaulting to 'en'
    protected renderPaymentMethodDetails() {
        return html`
            <div>
                ${repeat(this._paymentConfig, (config) => config.localizations.en.paymentName, (config) => html`
                    <sl-details style="margin: 10px 0;" summary="${config.localizations.en.paymentName}">
                        <h3>Description</h3>
                        ${config.localizations.en.paymentDescription}
                        <h3>Instructions</h3>
                        ${config.localizations.en.paymentInstructions}
                        <br/>
                        <div style="display: flex; flex-direction: row; justify-content: flex-end;">
                            <sl-icon-button
                                style="color: #E8E0E0; background-color: rgb(153, 95, 13); margin-right: 5px;"
                                name="pencil-square"
                                label="Edit"
                                @click=${() => {
                                    this.paymentCreationDialog?.openDialog(config);
                                }}
                            ></sl-icon-button>
                            <sl-icon-button
                                label="Delete"
                                name="trash"
                                style="color: #E8E0E0; background-color: rgb(148, 35, 32);"
                                @click=${() => {
                                    this._selectedPaymentMethod = config.paymentMethodId;
                                    this.confirmDialog?.openDialog()
                                }}
                            ></sl-icon-button>
                        </div>
                    </sl-details>
                `)}

                <confirmation-dialog
                    dialog-title="Delete Payment Method?"
                    dialog-description="This payment method will be permanently deleted from your organization."
                    confirm-text="Delete"
                    cancel-text="Cancel"
                    confirm-variant="danger"
                    @confirmActionButtonPressed=${this._handleDeletePaymentMethod}
                ></confirmation-dialog>
            </div>
        `;
    }


    private async _handleDeletePaymentMethod() {
        if(!this._selectedPaymentMethod) {
            return;
        }

        const submitResult = await this.paymentMethodService?.deletePaymentMethod(
            this.organization,
            this._selectedPaymentMethod
        );

        if (submitResult?.ok) {
            dispatchFeedback({
                type: "success",
                message: "Deleted Offline Payment Method."
            }, this);
            this.paymentCreationDialog?.closeDialog();
            this._paymentConfig = this._paymentConfig.filter(method => method.paymentMethodId !== this._selectedPaymentMethod);
            this._selectedPaymentMethod = null;
            await this.updateConfigFromServer();
        } else {
            dispatchFeedback({
                type: "danger",
                message: "Failed to delete offline payment method."
            }, this);
        }
    }

    private async _saveCustomPaymentMethod(event: CustomEvent<{newPayment: CustomOfflinePayment, oldPayment?: CustomOfflinePayment}>) {
        const {newPayment, oldPayment} = event.detail;

        let submitResult;
        if (oldPayment && oldPayment.paymentMethodId) {
            submitResult = await this.paymentMethodService?.updatePaymentMethod(
                this.organization,
                oldPayment.paymentMethodId,
                newPayment
            );
        } else {
            submitResult = await this.paymentMethodService?.createPaymentMethod(
                this.organization,
                newPayment
            );
        }

        if (submitResult?.ok) {
            dispatchFeedback({
                type: "success",
                message: `${oldPayment ? "Edited" : "Created"} Offline Payment Method.`
            }, this);
            this.paymentCreationDialog?.closeDialog();
            await this.updateConfigFromServer();
        } else {
            dispatchFeedback({
                type: "danger",
                message: `Failed to ${oldPayment ? "edit" : "create"} offline payment method.`
            }, this);
        }
    }

    async updateConfigFromServer() {
        const result = await this.paymentMethodService?.getPaymentMethodsForOrganization(this.organization);
        if(!result) {
            return;
        }

        this._paymentConfig = result
    }
}
