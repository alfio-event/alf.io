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
    @query("#paymentMethodDeleteConfirmDialog")
    paymentMethodDeleteConfirmDialog?: ConfirmationDialog;
    @query("#localizationDeleteConfirmDialog")
    localizationDeleteConfirmDialog?: ConfirmationDialog;

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

    protected renderPaymentMethodDetails() {
        return html`
            <div>
                ${repeat(this._paymentConfig, (config) => config.paymentMethodId, (config) => html`
                    <sl-details style="margin: 10px 0;">
                        <div style="display: flex; align-items: center;" slot="summary">
                            <sl-tooltip content="Delete">
                                <sl-icon-button
                                    label="Delete"
                                    name="trash"
                                    style="color: rgb(148, 35, 32);"
                                    @click=${(event: MouseEvent) => {
                                        event.stopPropagation();
                                        this.paymentMethodDeleteConfirmDialog?.openDialog(config.paymentMethodId)
                                    }}
                                ></sl-icon-button>
                            </sl-tooltip>
                            <span style="font-size: 14pt;">
                                ${config.localizations?.en?.paymentName || config.localizations[Object.keys(config.localizations)[0]].paymentName}
                            </span>
                        </div>
                        <sl-tab-group>
                            ${repeat([...Object.keys(config.localizations)], (key) => key, (key) => html`
                                <sl-tab
                                    .closable=${Object.keys(config.localizations).length > 1}
                                    @sl-close=${() => {
                                        this.localizationDeleteConfirmDialog?.openDialog({"config": config, "localizationKey": key})
                                    }}
                                    slot="nav"
                                    panel="${key}"
                                >
                                    ${key}
                                </sl-tab>

                                <sl-tab-panel name="${key}">
                                    <h3>Name</h3>
                                    ${config.localizations[key].paymentName}
                                    <h3>Description</h3>
                                    ${config.localizations[key].paymentDescription}
                                    <h3>Instructions</h3>
                                    ${config.localizations[key].paymentInstructions}
                                    <br/>
                                    <div style="display: flex; flex-direction: row; justify-content: flex-end;">
                                        <sl-tooltip content="Change Translation">
                                            <sl-icon-button
                                                style="color: #E8E0E0; background-color: rgb(153, 95, 13); margin-right: 5px;"
                                                name="pencil-square"
                                                label="Edit"
                                                @click=${() => {
                                                    this.paymentCreationDialog?.openDialog(config, key);
                                                }}
                                            ></sl-icon-button>
                                        </sl-tooltip
                                    </div>
                                </sl-tab-panel>
                            `)}
                            <sl-tab slot="nav" @click=${(event: MouseEvent) => this._handleAddNewLocale(event, config)}>
                                <sl-tooltip content="Add New Translation" hoist>
                                    <sl-icon-button label="New Locale" name="plus-square"></sl-icon-button>
                                </sl-tooltip>
                            </sl-tab>
                        </sl-tab-group>
                    </sl-details>
                `)}

                <confirmation-dialog
                    id="paymentMethodDeleteConfirmDialog"
                    dialog-title="Delete Payment Method?"
                    dialog-description="This payment method will be permanently deleted from your organization."
                    confirm-text="Delete"
                    cancel-text="Cancel"
                    confirm-variant="danger"
                    @confirmActionButtonPressed=${this._handleDeletePaymentMethod}
                ></confirmation-dialog>
                <confirmation-dialog
                    id="localizationDeleteConfirmDialog"
                    dialog-title="Delete Translation?"
                    dialog-description="This translation will be permanently deleted."
                    confirm-text="Delete"
                    cancel-text="Cancel"
                    confirm-variant="danger"
                    @confirmActionButtonPressed=${this._handleDeleteLocale}
                ></confirmation-dialog>
            </div>
        `;
    }

    private _handleAddNewLocale(event: MouseEvent, config: CustomOfflinePayment) {
        event.stopPropagation();
        this.paymentCreationDialog?.openDialog(config);
    }

    private async _handleDeleteLocale(event: CustomEvent) {
        const {config, localizationKey} = event.detail;

        if(!config.paymentMethodId) return;

        delete config.localizations[localizationKey];

        const submitResult = await this.paymentMethodService?.updatePaymentMethod(
            this.organization,
            config.paymentMethodId,
            config
        );

        if (submitResult?.ok) {
            dispatchFeedback({
                type: "success",
                message: "Removed localization from payment method."
            }, this);
            await this.updateConfigFromServer();
        } else {
            dispatchFeedback({
                type: "danger",
                message: "Failed to remove localization."
            }, this);
        }
    }


    private async _handleDeletePaymentMethod(event: CustomEvent) {
        const paymentMethodId = event.detail;
        if(!paymentMethodId) {
            return;
        }

        const submitResult = await this.paymentMethodService?.deletePaymentMethod(
            this.organization,
            paymentMethodId
        );

        if (submitResult?.ok) {
            dispatchFeedback({
                type: "success",
                message: "Deleted Offline Payment Method."
            }, this);
            this.paymentCreationDialog?.closeDialog();
            this._paymentConfig = this._paymentConfig.filter(
                method => method.paymentMethodId !== paymentMethodId
            );
            await this.updateConfigFromServer();
        } else {
            const errorMsg = (await submitResult?.text());
            dispatchFeedback({
                type: "danger",
                message: errorMsg || "Failed to delete offline payment method."
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
