import type { SlDialog, SlRequestCloseEvent } from "@shoelace-style/shoelace";
import { TanStackFormController } from "@tanstack/lit-form";
import { customElement, property, query, state } from "lit/decorators.js";
import { css, html, LitElement, nothing, type TemplateResult } from "lit";
import { when } from "lit/directives/when.js";
import {Task} from '@lit/task';
import { repeat } from "lit/directives/repeat.js";
import { dialog, form, modernLayout, row } from "../styles";
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
    static styles = [
        row,
        dialog,
        form,
        modernLayout,
        css`
            sl-dialog {
                --alfio-dialog-max-width: 48rem;
            }
        `,
    ];

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
                    label=${this.dialogLabel()}
                    id="offlinePaymentDialog"
                    class="responsive-dialog"
                    @sl-request-close=${this.onRequestClose}
                    @sl-after-hide=${this.onAfterHide}
                >
                    ${this.renderPaymentMethodForm()}
                </sl-dialog>
            </div>
        `;
    }

    private dialogLabel(): string {
        if (this.editObject) {
            return 'Edit payment method';
        }
        return 'Add payment method';
    }

    // a click outside the dialog would discard what the user typed: only the close button and Cancel close it
    private onRequestClose = (event: SlRequestCloseEvent): void => {
        if (event.target !== event.currentTarget) {
            return;
        }
        if (event.detail.source === 'overlay') {
            event.preventDefault();
        }
    };

    private onAfterHide = (event: Event): void => {
        // nested Shoelace components (e.g. selects, the preview dialog) emit their own sl-after-hide
        if (event.target !== event.currentTarget) {
            return;
        }
        this.#form.api.reset();
    };

    renderPaymentMethodForm(): TemplateResult {
        const formErrors = this.#form.api.state.errors.filter(error => error != null && error !== '');
        return html`<form
            id="form"
            class="form-stack"
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
                            if (!value) {
                                return 'Language selection is required.';
                            }
                            return undefined;
                        }
                    },
                },
                (field) => {
                    return html`
                    <sl-select
                        label="Language"
                        class="${classMap({ error: this._hasError(field.state.meta) })}"
                        required
                        hoist
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
                            if (value.length < 3) {
                                return 'Name is too short.';
                            }
                            return undefined;
                        }
                    },
                },
                (field) => {
                    return html`
                    <div>
                        <sl-input
                            id="paymentMethodName"
                            class="${classMap({ error: this._hasError(field.state.meta) })}"
                            label="Name"
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
                    onChange: ({ value }: {value: string}) => {
                        if (value.length < 3) {
                            return 'Description is too short.';
                        }
                        return undefined;
                    },
                    },
                },
                (field) => {
                    return html`
                    <div>
                        <sl-textarea
                            label="Description"
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
                                    data-button-text="Preview"
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
                    onChange: ({ value }: {value: string}) => {
                        if (value.length < 3) {
                            return 'Instructions are too short.';
                        }
                        return undefined;
                    },
                    },
                },
                (field) => {
                    return html`
                    <div>
                        <sl-textarea
                            label="Instructions"
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
                                    data-button-text="Preview"
                                    .text=${field.state.value}
                                >
                                </alfio-display-commonmark-preview>
                            </div>
                        </sl-textarea>
                    </div>`
                },
            )}
            ${when(formErrors.length > 0, () => html`
                <sl-alert variant="danger" open>
                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                    ${formErrors.join(' ')}
                </sl-alert>
            `, () => nothing)}
            <!-- the footer stays inside the form, so that Save submits it -->
            <div class="footer">
                <sl-divider></sl-divider>
                <div class="row" style="--alfio-row-cols: 3">
                    <sl-button variant="default" size="large" @click=${() => this.closeDialog()}>Cancel</sl-button>
                    <div></div>
                    <sl-button
                        variant="warning"
                        type="submit"
                        size="large"
                        ?loading=${this.#form.api.state.isSubmitting}
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
                    let existingLocalizations: Record<string, CustomOfflinePaymentLocalization> = {};
                    if (this.editObject) {
                        existingLocalizations = {...this.editObject.localizations};
                    }
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

