import type { SlDialog } from "@shoelace-style/shoelace";
import { TanStackFormController } from "@tanstack/lit-form";
import { customElement, property, query, state } from "lit/decorators.js";
import { html, LitElement, type TemplateResult } from "lit";
import {Task} from '@lit/task';
import { repeat } from "lit/directives/repeat.js";
import { dialog, form, row } from "../styles";
import { classMap } from "lit/directives/class-map.js";
import { type SlSelect } from '@shoelace-style/shoelace';
import { LocalizationService } from "../service/localization";

type LocalizationFormFields = CustomOfflinePaymentLocalization & {
    localizationKey: string;
};

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
     * Identifies the localization we are currently editing
     * in the UI.
     */
    @state()
    localizationKey?: string;

    @state()
    availableLanguages: {locale: string, value: number, language: string, displayLanguage: string}[] = [];

    @state()
    editingExistingLocalization: boolean = false;

    /**
     * The current payment methods known by the system.
     * Used for name collision client-side validation.
     */
    @property({type: Array})
    currentMethods: CustomOfflinePayment[] = [];


    /**
     * Allows component to access Alf.io supported languages
     * for populating language dropdown.
     */
    localizationService?: LocalizationService = new LocalizationService();

    #form = new TanStackFormController(this, {
        defaultValues: {
            localizationKey: "",
            paymentName: "",
            paymentDescription: "",
            paymentInstructions: ""
        } as LocalizationFormFields
    });

    private _updateLanguagesTask = new Task(this, {
        task: async () => {
            let response = await this.localizationService?.getEventsSupportedLanguages();
            if(!response) { throw new Error("Failed to get languages"); }

            return response
        },
        args: () => []
    });

    protected render(): TemplateResult {
        return html`
            <div>
                <sl-dialog
                    label="${this.editObject ? "Update" : "Create"} Payment Method"
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
                    name: `localizationKey`,
                    validators: {
                        onChange: ({ value }: {value: string}) => {
                            return !value ? 'Language selection is required.' : undefined;
                        }
                    },
                },
                (field) => {
                    return html`
                    <sl-select
                        label="Translation"
                        class="${classMap({ error: this._hasError(field.state.meta) })}"
                        required
                        ?disabled=${this.editingExistingLocalization}
                        .value=${field.state.value}
                        @sl-blur=${() => field.handleBlur()}
                        @sl-change=${(event: Event) => {
                            if (event.currentTarget) {
                                const newValue = (event.currentTarget as SlSelect).value as string;
                                field.handleChange(newValue);
                            }
                        }}>
                        ${this._updateLanguagesTask.render({
                            pending: () => html`<sl-option value="" disabled>Loading available languages...</sl-option>`,
                            complete: (languages) => {
                                if(this.editObject && !this.editingExistingLocalization) {
                                    const existingLocalizations = Object.keys(this.editObject.localizations);
                                    languages = languages.filter(item => !existingLocalizations.includes(item.locale));
                                }

                                return html`
                                    ${repeat(languages, (language) => language.value, (language) => {
                                        return html`
                                        <sl-option value="${language.locale}">${language.displayLanguage}</sl-option>
                                        `;
                                    })}
                                `
                            },
                            error: (_e) => html`<sl-option value="" disabled>Failed to load available languages...</sl-option>`
                        })}
                    </sl-select>
                    `;
                }
            )}
            ${this.#form.field(
                {
                    name: `paymentName`,
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
                    name: `paymentDescription`,
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
                    name: `paymentInstructions`,
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

    async openDialog(editObject?: CustomOfflinePayment, localizationKey?: string) {
        this.editObject = editObject;
        this.localizationKey = localizationKey;

        if(editObject && localizationKey) {
            this.editingExistingLocalization = true;
        } else {
            this.editingExistingLocalization = false;
        }

        const curLocaleKey = this.localizationKey ?? '';

        if(this.dialog) {
            this.#form.api.update({
                defaultValues: this._buildInitialFormValues(curLocaleKey),
                onSubmit: async ({value}) => {
                    let existingLocalizations = this.editObject ? {...this.editObject.localizations} : {};
                    const newPaymentObj = {
                        paymentMethodId: this.editObject?.paymentMethodId ?? null,
                        localizations: {
                            ...existingLocalizations,
                        }
                    };

                    newPaymentObj.localizations[value.localizationKey] = {
                        paymentName: value.paymentName,
                        paymentDescription: value.paymentDescription,
                        paymentInstructions: value.paymentInstructions
                    };

                    this.dispatchEvent(
                        new CustomEvent("offlinePaymentDialogSave", {
                            detail: {
                                newPayment: newPaymentObj,
                                oldPayment: this.editObject
                            }
                        })
                    )
                },
                validators: {
                    onSubmitAsync: async props => {
                        if(
                            !this.editObject
                            && this.currentMethods.some(method => method.localizations.en.paymentName === props.value.paymentName)
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
        this.#form.api.reset();
        await this.dialog?.hide();
    }

    private _hasError(meta: any) {
        return (meta.isTouched && meta.errors.length > 0);
    }

    private _buildInitialFormValues(localizationKey?: string) : LocalizationFormFields {
        if(this.editObject && localizationKey) {
            return {
                localizationKey: localizationKey,
                paymentName: this.editObject.localizations[localizationKey].paymentName,
                paymentDescription: this.editObject.localizations[localizationKey].paymentDescription,
                paymentInstructions: this.editObject.localizations[localizationKey].paymentInstructions
            };
        }

        return {
            localizationKey: "",
            paymentName: "",
            paymentDescription: "",
            paymentInstructions: "",
        }
    }
}

