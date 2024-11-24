import { customElement, property, query, state } from "lit/decorators.js";
import { html, LitElement, type TemplateResult } from "lit";
import { repeat } from "lit/directives/repeat.js";

import type { OfflinePaymentDialog } from "./offline-payment-dialog";
import { OrganizationConfigurationService } from "../service/configuration";
import { dispatchFeedback } from "../model/dom-events";
import type { ConfirmationDialog } from "./confirmation-dialog";

/**
 * Displays defined custom payment methods for org. Allows
 * organizers to add new payment methods.
 */
@customElement('offline-payment-config-block')
export class OfflinePaymentConfigBlock extends LitElement {
    @property({attribute: "organization", type: Number})
    organization?: number;

    @query("offline-payment-dialog")
    paymentCreationDialog?: OfflinePaymentDialog;
    @query("confirmation-dialog")
    confirmDialog?: ConfirmationDialog;

    @state()
    protected _selectedPaymentMethod: string | null = null;
    @state()
    protected _paymentConfig: CustomOfflinePayment[] = [];

    configService?: OrganizationConfigurationService;

    constructor() {
        super();
        this.updateConfigFromServer();
    }

    async updated(changedProperties: Map<string, unknown>) {
        if(changedProperties.has("organization") && this.organization) {
            this.configService = new OrganizationConfigurationService(this.organization);
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

    protected renderPaymentMethodDetails() {
        return html`
            <div>
                ${repeat(this._paymentConfig, (config) => config.paymentName, (config) => html`
                    <sl-details style="margin: 10px 0;" summary="${config.paymentName}">
                        <h3>Description</h3>
                        ${config.paymentDescription}
                        <h3>Instructions</h3>
                        ${config.paymentInstructions}
                        <br/>
                        <div style="display: flex; flex-direction: row; justify-content: flex-end;">
                            <sl-icon-button
                                style="background-color: rgb(153, 95, 13); margin-right: 5px;"
                                name="pencil-square"
                                label="Edit"
                                @click=${() => {
                                    this.paymentCreationDialog?.openDialog(config);
                                }}
                            ></sl-icon-button>
                            <sl-icon-button
                                label="Delete"
                                name="trash"
                                style="background-color: rgb(148, 35, 32);"
                                @click=${() => {
                                    this._selectedPaymentMethod = config.paymentName;
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
        await this.updateConfigFromServer();

        const updatedPaymentMethodList = this._paymentConfig.filter(method => method.paymentName !== this._selectedPaymentMethod);
        const submitResult = await this.configService?.updateConfigurationEntries({
            PAYMENT_OFFLINE: [
                {
                    id: -1,
                    key: "CUSTOM_OFFLINE_PAYMENTS",
                    value: JSON.stringify(updatedPaymentMethodList),
                }
            ]
        });

        if (submitResult?.ok) {
            dispatchFeedback({
                type: "success",
                message: "Deleted Offline Payment Method."
            }, this);
            this.paymentCreationDialog?.closeDialog();
            this._paymentConfig = this._paymentConfig.filter(method => method.paymentName !== this._selectedPaymentMethod);
            this._selectedPaymentMethod = null;
        } else {
            dispatchFeedback({
                type: "danger",
                message: "Failed to delete offline payment method."
            }, this);
        }
    }

    private async _saveCustomPaymentMethod(event: CustomEvent<{payment: CustomOfflinePayment, oldPayment?: CustomOfflinePayment}>) {
        const {payment, oldPayment} = event.detail;

        await this.updateConfigFromServer();

        if(oldPayment) {
            this._paymentConfig = this._paymentConfig.filter(config => config.paymentName !== oldPayment.paymentName);
        }

        this._paymentConfig = [...this._paymentConfig, {
            paymentName: payment.paymentName,
            paymentDescription: payment.paymentDescription,
            paymentInstructions: payment.paymentInstructions,
        }];
        const submitResult = await this.configService?.updateConfigurationEntries(
            {
                PAYMENT_OFFLINE: [
                    {
                        id: -1,
                        key: "CUSTOM_OFFLINE_PAYMENTS",
                        value: JSON.stringify(this._paymentConfig),
                    }
                ]
            }
        );

        if (submitResult?.ok) {
            dispatchFeedback({
                type: "success",
                message: `${oldPayment ? "Edited" : "Created"} Offline Payment Method.`
            }, this);
            this.paymentCreationDialog?.closeDialog();
        } else {
            dispatchFeedback({
                type: "danger",
                message: `Failed to ${oldPayment ? "edit" : "create"} offline payment method.`
            }, this);
        }
    }

    async updateConfigFromServer() {
        let curConfigStr: string | undefined;

        try {
            curConfigStr = await this.configService?.getConfigurationEntry<string>("CUSTOM_OFFLINE_PAYMENTS");
        } catch(e) {
            console.warn("Failed to get offline payments config from server", e);
        }

        if (!curConfigStr) {
            return;
        }

        try {
            this._paymentConfig = JSON.parse(curConfigStr);
        } catch (e) {
            console.error("Failed to parse offline payments JSON from DB:", e);
        }
    }
}
