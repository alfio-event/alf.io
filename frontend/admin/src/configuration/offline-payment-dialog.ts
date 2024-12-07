import type { SlDialog } from "@shoelace-style/shoelace";
import { TanStackFormController } from "@tanstack/lit-form";
import { html, LitElement, type TemplateResult } from "lit";
import { customElement, property, query, state } from "lit/decorators.js";
import { dialog, form, row } from "../styles";
import { classMap } from "lit/directives/class-map.js";

/**
 * Defines offline payment method form to be filled out
 * by organizers.
 */
@customElement('offline-payment-dialog')
export class OfflinePaymentDialog extends LitElement {
    static styles = [row, dialog, form];

    @query("sl-dialog#offlinePaymentDialog")
    dialog?: SlDialog;

    /**
     * Used to edit the properties of an existing payment
     * method object instead of creating a new one.
     */
    @state()
    editObject?: CustomOfflinePayment;

    /**
     * The current payment methods known by the system.
     * Used for name colision client-side validation.
     */
    @property({type: Array})
    currentMethods: CustomOfflinePayment[] = [];


    #form = new TanStackFormController(this, {
        defaultValues: {
            payment: {} as CustomOfflinePayment
        }
    });

    protected render(): TemplateResult {
        return html`
            <div>
                <sl-dialog
                    label="New Payment Method"
                    id="offlinePaymentDialog"
                    style="--width: 50vw; --sl-font-size-large: 1.5rem;"
                    class="dialog"
                >
                    ${this.renderPaymentMethodForm()}
                </sl-dialog>
            </div>
        `;
    }

    renderPaymentMethodForm(): TemplateResult {
        return html`<form
            id="form"
            @submit=${
                async (e: Event) => {
                    e.preventDefault();
                    e.stopImmediatePropagation();
                    await this.#form.api.handleSubmit();
                }
            }
        >
            ${this.#form.field(
                {
                    name: `payment.paymentName`,
                    validators: {
                        onChange: ({ value }: {value: string}) => {
                            return value.length < 3 ? 'Name is too short.' : undefined;
                        }
                    },
                },
                (field) => {
                    return html`
                    <div>
                        <sl-input
                            id="paymentMethodName"
                            class="${classMap({ error: this._hasError(field.state.meta) })}"
                            label="Payment Method Name"
                            required
                            .value=${field.state.value}
                            @sl-blur=${() => field.handleBlur()}
                            @sl-change=${(event: InputEvent) => {
                                if (event.currentTarget) {
                                    const newValue = (event.currentTarget as HTMLInputElement).value;
                                    field.handleChange(newValue);
                                }
                            }}
                        >
                        </sl-input>
                    </div>`
                },
            )}
            ${this.#form.field(
                {
                    name: `payment.paymentDescription`,
                    validators: {
                    onChange: ({ value }: {value: string}) =>
                        value.length < 3 ? 'Description is too short' : undefined,
                    },
                },
                (field) => {
                    return html`
                    <div>
                        <sl-textarea
                            label="Payment Method Description"
                            class="${classMap({ error: this._hasError(field.state.meta) })}"
                            required
                            rows="2"
                            .value=${field.state.value}
                            @sl-blur=${() => field.handleBlur()}
                            @sl-change=${(event: InputEvent) => {
                                if (event.currentTarget) {
                                    const newValue = (event.currentTarget as HTMLInputElement).value;
                                    field.handleChange(newValue);
                                }
                            }}
                        >
                            <div slot="help-text">
                                <alfio-display-commonmark-preview
                                    data-button-text="preview"
                                    .text=${field.state.value}
                                >
                                </alfio-display-commonmark-preview>
                            </div>
                        </sl-textarea>
                    </div>`
                },
            )}
            ${this.#form.field(
                {
                    name: `payment.paymentInstructions`,
                    validators: {
                    onChange: ({ value }: {value: string}) =>
                        value.length < 3 ? 'Instructions are too short.' : undefined,
                    },
                },
                (field) => {
                    return html`
                    <div>
                        <sl-textarea
                            label="Payment Method Instructions"
                            class="${classMap({ error: this._hasError(field.state.meta) })}"
                            required
                            rows="2"
                            .value=${field.state.value}
                            @sl-blur=${() => field.handleBlur()}
                            @sl-change=${(event: InputEvent) => {
                                if (event.currentTarget) {
                                    const newValue = (event.currentTarget as HTMLInputElement).value;
                                    field.handleChange(newValue);
                                }
                            }}
                        >
                            <div slot="help-text">
                                <alfio-display-commonmark-preview
                                    data-button-text="preview"
                                    .text=${field.state.value}
                                >
                                </alfio-display-commonmark-preview>
                            </div>
                        </sl-textarea>
                    </div>`
                },
            )}
            <div slot="footer">
                <sl-divider></sl-divider>
                <div class="row" style="--alfio-row-cols: 3">
                    <sl-button variant="default" size="large" @click=${() => this.closeDialog()}>Close</sl-button>
                    <div></div>
                    <sl-button
                        variant="success"
                        type="submit"
                        size="large"
                        .disabled=${!this.#form.api.state.canSubmit}
                    >
                        Save
                    </sl-button>
                </div>
            </div>
        </form>
        `;
    }

    async openDialog(editObject?: CustomOfflinePayment) {
        this.editObject = editObject;

        if(this.dialog) {
            this.#form.api.update({
                defaultValues: {
                    payment: this._buildInitialFormValues(),
                },
                onSubmit: async (formResult) => {
                    this.dispatchEvent(
                        new CustomEvent("offlinePaymentDialogSave", {
                            detail: {
                                payment: formResult.value.payment,
                                oldPayment: this.editObject
                            }
                        })
                    )
                },
                validators: {
                    onSubmitAsync: async props => {
                        if(
                            !this.editObject
                            && this.currentMethods.some(method => method.paymentName === props.value.payment.paymentName)
                        ) {
                            return 'Chosen payment method name matches one already created for this organization. Please choose a different name';
                        }

                        return undefined;
                    }
                }
            });
            await this.dialog.show();
        }
    }

    async closeDialog() {
        await this.dialog?.hide();
        this.#form.api.reset();
    }

    private _hasError(meta: any) {
        return (meta.isTouched && meta.errors.length > 0);
    }

    private _buildInitialFormValues() : CustomOfflinePayment {
        if(this.editObject) {
            return {
                paymentName: this.editObject.paymentName,
                paymentDescription: this.editObject.paymentDescription,
                paymentInstructions: this.editObject.paymentInstructions
            };
        }

        return {
            paymentName: "",
            paymentDescription: "",
            paymentInstructions: "",
        }
    }
}

