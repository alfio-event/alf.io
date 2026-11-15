import { customElement, property, query, state } from "lit/decorators.js";
import { css, html, LitElement, type TemplateResult } from "lit";
import { repeat } from "lit/directives/repeat.js";

import { CustomPaymentMethodsService } from "../service/custom-payment-methods";
import { dispatchFeedback } from "../model/dom-events";
import { ConfirmationDialogService } from "../service/confirmation-dialog";
import { base } from "../styles";
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

    static styles = [
        base,
        css`
            .toolbar {
                display: flex;
                justify-content: flex-end;
                margin-bottom: var(--sl-spacing-small);
            }
            .payment-methods {
                display: grid;
                gap: var(--sl-spacing-small);
            }
            .payment-method-summary {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-small);
                width: 100%;
                min-width: 0;
            }
            .payment-method-name {
                flex: 1 1 auto;
                min-width: 0;
                overflow-wrap: anywhere;
                font-weight: var(--sl-font-weight-semibold);
            }
            .localization h4 {
                margin: var(--sl-spacing-medium) 0 var(--sl-spacing-2x-small);
                font-size: var(--sl-font-size-small);
                color: var(--sl-color-gray-600);
            }
            .localization p {
                margin: 0;
                overflow-wrap: anywhere;
            }
            .localization-actions {
                display: flex;
                justify-content: flex-end;
                margin-top: var(--sl-spacing-medium);
            }
        `,
    ];

    protected render(): TemplateResult {
        return html`
            <div>
                <div class="toolbar">
                    <sl-button type="button" variant="success" size="small" @click=${() => this.paymentCreationDialog?.openDialog()}>
                        <sl-icon name="plus-circle" slot="prefix"></sl-icon>
                        Add payment method
                    </sl-button>
                </div>
                ${this.renderPaymentMethodDetails()}
                <offline-payment-dialog
                    .currentMethods=${this._paymentConfig}
                    @offlinePaymentDialogSave=${this._saveCustomPaymentMethod}
                ></offline-payment-dialog>
            </div>
        `;
    }

    protected renderPaymentMethodDetails() {
        return html`
            <div class="payment-methods">
                ${repeat(this._paymentConfig, (config) => config.paymentMethodId, (config) => html`
                    <sl-details>
                        <div class="payment-method-summary" slot="summary">
                            <span class="payment-method-name">
                                ${config.localizations?.en?.paymentName || config.localizations[Object.keys(config.localizations)[0]].paymentName}
                            </span>
                            <sl-button variant="danger" size="small"
                                       @click=${(event: MouseEvent) => {
                                           event.stopPropagation();
                                           void this._confirmDeletePaymentMethod(config);
                                       }}>
                                <sl-icon slot="prefix" name="trash"></sl-icon>
                                Delete
                            </sl-button>
                        </div>
                        <sl-tab-group>
                            ${repeat(Object.keys(config.localizations), (key) => key, (key) => html`
                                <sl-tab
                                    .closable=${Object.keys(config.localizations).length > 1}
                                    @sl-close=${() => {
                                        void this._confirmDeleteLocale(config, key);
                                    }}
                                    slot="nav"
                                    panel="${key}"
                                >
                                    ${key}
                                </sl-tab>

                                <sl-tab-panel name="${key}" class="localization">
                                    <h4>Name</h4>
                                    <p>${config.localizations[key].paymentName}</p>
                                    <h4>Description</h4>
                                    <p>${config.localizations[key].paymentDescription}</p>
                                    <h4>Instructions</h4>
                                    <p>${config.localizations[key].paymentInstructions}</p>
                                    <div class="localization-actions">
                                        <sl-button variant="default" size="small"
                                                   @click=${() => {
                                                       this.paymentCreationDialog?.openDialog(config, key);
                                                   }}>
                                            <sl-icon slot="prefix" name="pencil"></sl-icon>
                                            Edit
                                        </sl-button>
                                    </div>
                                </sl-tab-panel>
                            `)}
                            <sl-tab slot="nav" @click=${(event: MouseEvent) => this._handleAddNewLocale(event, config)}>
                                <sl-tooltip content="Add translation" hoist>
                                    <sl-icon-button label="Add translation" name="plus-square"></sl-icon-button>
                                </sl-tooltip>
                            </sl-tab>
                        </sl-tab-group>
                    </sl-details>
                `)}
            </div>
        `;
    }

    private async _confirmDeletePaymentMethod(config: CustomOfflinePayment): Promise<void> {
        const confirmed = await ConfirmationDialogService.requestConfirm(
            'Delete payment method',
            'This payment method will be permanently deleted from your organization.',
            'danger');
        if (confirmed) {
            await this._handleDeletePaymentMethod(config.paymentMethodId);
        }
    }

    private async _confirmDeleteLocale(config: CustomOfflinePayment, localizationKey: string): Promise<void> {
        const confirmed = await ConfirmationDialogService.requestConfirm(
            'Delete translation',
            'This translation will be permanently deleted.',
            'danger');
        if (confirmed) {
            await this._handleDeleteLocale(config, localizationKey);
        }
    }

    private _handleAddNewLocale(event: MouseEvent, config: CustomOfflinePayment) {
        event.stopPropagation();
        this.paymentCreationDialog?.openDialog(config);
    }

    private async _handleDeleteLocale(config: CustomOfflinePayment, localizationKey: string) {
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


    private async _handleDeletePaymentMethod(paymentMethodId: string | null) {
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
                message: "Payment method deleted."
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
        if (oldPayment?.paymentMethodId) {
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

        let successMessage = "Payment method created.";
        let failureMessage = "Failed to create the payment method.";
        if (oldPayment) {
            successMessage = "Payment method updated.";
            failureMessage = "Failed to update the payment method.";
        }
        if (submitResult?.ok) {
            dispatchFeedback({
                type: "success",
                message: successMessage
            }, this);
            this.paymentCreationDialog?.closeDialog();
            await this.updateConfigFromServer();
        } else {
            dispatchFeedback({
                type: "danger",
                message: failureMessage
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
