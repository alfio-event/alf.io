import {html, LitElement, TemplateResult} from "lit";
import {customElement, query} from "lit/decorators.js";
import {
    AdditionalItem,
    AdditionalItemTaxType,
    AdditionalItemType,
    isMandatoryPercentage,
    SupplementPolicy,
    supplementPolicyDescriptions,
    taxTypeDescriptions
} from "../../model/additional-item.ts";
import {SlDialog, SlRequestCloseEvent} from "@shoelace-style/shoelace";
import {AlfioEvent} from "../../model/event.ts";
import {repeat} from "lit/directives/repeat.js";
import {TanStackFormController} from "@tanstack/lit-form";
import {dialog, form, pageHeader, row, textColors} from "../../styles.ts";
import {extractDateTime, notifyChange, renderIf, toDateTimeModification} from "../../service/helpers.ts";
import {classMap} from "lit/directives/class-map.js";
import {AdditionalItemService} from "../../service/additional-item.ts";
import {ContentLanguage} from "../../model/purchase-context.ts";

@customElement('alfio-additional-item-edit')
export class AdditionalItemEdit extends LitElement {

    static styles = [pageHeader, row, dialog, form, textColors];

    private editedItem: AdditionalItem | null = null;
    private supportedLanguages: ContentLanguage[] = [];
    private event: AlfioEvent | null = null;
    private type: AdditionalItemType | null = null;

    @query("sl-dialog#editDialog")
    dialog?: SlDialog;

    displayForm: boolean = false;

    private validationErrors?: { [k: string]: string};

    #form = new TanStackFormController(this, {
        defaultValues: {
            descriptions: [] as DescriptionForm[],
            availabilityAndPrices: {} as AvailabilityAndPricesForm,
        } as FormData
    });

    private buildDefaultValues(currentState: { values: FormData }): FormData {
        if (this.editedItem != null) {
            const item = this.editedItem;
            return {
                availabilityAndPrices: this.buildAvailabilityAndPricesFromItem(item),
                descriptions: item.title.map(((title) => {
                    const description = item.description.find(d => d.locale === title.locale)!;
                    return {
                        locale: title.locale,
                        title: title.value,
                        description: description.value,
                        titleId: title.id,
                        descriptionId: description.id
                    }
                }))
            }
        }


        return {
            availabilityAndPrices: this.event != null ? this.buildAvailabilityAndPricesFromEvent(this.event) : currentState.values.availabilityAndPrices,
            descriptions: (this.supportedLanguages ?? []).map(sl => {
                return {
                    locale: sl.locale,
                    title: '',
                    description: '',
                    titleId: null,
                    descriptionId: null
                };
            })
        };
    }

    private buildAvailabilityAndPricesFromEvent(event: AlfioEvent): AvailabilityAndPricesForm {
        const now = new Date();
        return {
            availableItems: 0,
            minPrice: null,
            maxPrice: null,
            price: 0,
            fixPrice: true,
            vat: event.vatPercentage,
            vatType: "INHERITED",
            supplementPolicy: "OPTIONAL_UNLIMITED_AMOUNT",
            inception: `${now.getFullYear()}-${(now.getMonth()+1).toString().padStart(2, '0')}-${now.getDate().toString().padStart(2, '0')}T${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`,
            expiration: event.begin,
            maxQtyPerOrder: null,
            availableQuantity: null
        }
    }

    private buildAvailabilityAndPricesFromItem(item: AdditionalItem): AvailabilityAndPricesForm {
        return {
            availableItems: item.availableItems !== -1 ? item.availableItems : null,
            minPrice: item.minPrice,
            maxPrice: item.maxPrice,
            price: item.price,
            fixPrice: item.fixPrice,
            vat: item.vat,
            vatType: item.vatType,
            supplementPolicy: item.supplementPolicy,
            inception: `${item.inception.date}T${item.inception.time}`,
            expiration: `${item.expiration.date}T${item.expiration.time}`,
            maxQtyPerOrder: item.maxQtyPerOrder ?? null,
            availableQuantity: item.availableQuantity !== -1 ? item.availableQuantity ?? null : null
        }
    }

    protected render(): TemplateResult {
        return html`
            <sl-dialog label="${this.editedItem != null ? `Edit ` : 'New '}${this.type === 'SUPPLEMENT' ? 'Additional Item' : 'Donation Option'}"
                       id="editDialog"
                       style="--width: 50vw; --header-spacing:16px; --body-spacing: 16px; --sl-font-size-large: 1.5rem;"
                       class="dialog"
                       @sl-request-close=${this.preventAccidentalClose}>

                ${this.renderForm()}

            </sl-dialog>
        `;
    }

    private renderForm(): TemplateResult {
        return html`
            <form id="form" @submit=${async (e: Event) => { e.preventDefault(); e.stopImmediatePropagation(); await this.#form.api.handleSubmit();}}>
                <h3 style="margin-bottom: 0">Descriptions</h3>
                <div class="row">
                    ${this.#form.field({name: 'descriptions'}, (descriptionsField) => this.renderDescription(descriptionsField.state.value))}
                </div>
                <h3>Availability and prices</h3>
                <div>
                    ${this.#form.field({name: 'availabilityAndPrices'}, (field) => this.renderAvailabilityAndPrices(field.state.value))}
                </div>
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => this.close(false)}>Close</sl-button>
                        <div></div>
                        <sl-button variant="success" type="submit" size="large" .disabled=${!this.#form.api.state.canSubmit}>Save</sl-button>
                    </div>
                </div>
            </form>
        `;
    }

    private renderDescription(descriptions: DescriptionForm[]): TemplateResult {
        return html`${repeat(descriptions, (_, index) => index, (description, index) => {
            return html`
                <div class="col">
                    <div class="border-bottom">
                        <h4>${this.supportedLanguages?.find(cl => cl.locale === description.locale)?.displayLanguage}</h4>
                    </div>

                    ${
                        // title
                        this.#form.field({name: `descriptions[${index}].title`, validators: {
                            onChange: ({ value }) => {
                                return value && value.length < 3
                                    ? 'error.title'
                                    : undefined
                            },
                        }},
                        (field) => {
                            return html`
                                <sl-input placeholder="Title" label="Title" required .value=${field.state.value} class="${classMap({ error: this.hasError(field.state.meta, field.name) })}" @sl-change=${(e: InputEvent) => notifyChange(e, field)}></sl-input>
                            `
                    })}
                    ${this.#form.field({name: `descriptions[${index}].description`, validators: {
                                onChange: ({ value }) => {
                                    return value && value.length < 3
                                        ? 'error.description'
                                        : undefined
                                },
                            }},
                        (field) => {
                            return html`
                                <sl-textarea label="Description" required .value=${field.state.value} class="${classMap({ error: this.hasError(field.state.meta, field.name) })}" rows="2" @sl-input=${(e: InputEvent) => notifyChange(e, field)}>
                                    <div slot="help-text">
                                        <alfio-display-commonmark-preview data-button-text="preview" .text=${field.state.value}></alfio-display-commonmark-preview>
                                    </div>
                                </sl-textarea>
                            `
                    })}
                </div>
            `;
        })}`;
    }

    private hasError(meta: any, name: string) {
        return (meta.isTouched && meta.errors.length > 0) || this.validationError(name) != null;
    }

    private renderAvailabilityAndPrices(formValue: AvailabilityAndPricesForm): TemplateResult {
        return html`
            ${renderIf(() => this.type === 'SUPPLEMENT', () => html`
                <div>
                    ${this.#form.field({name: `availabilityAndPrices.supplementPolicy`},
                        (field) => {
                            return html`
                                <sl-select required label="Additional Item Policy" value=${field.state.value} @sl-input=${(e: InputEvent) => notifyChange(e, field)}>
                                    ${repeat(Object.keys(supplementPolicyDescriptions), k => k, (k) => html`
                                        <sl-option value=${k}>${supplementPolicyDescriptions[k]}</sl-option>
                                    `)}
                                </sl-select>`
                        })
                    }
                </div>
            `)}
            <div class="row">
                <div class="col">
                    ${this.#form.field({name: `availabilityAndPrices.inception`, validators: {
                                onChange: ({ value }) => {
                                    return value && value.length < 3
                                        ? 'Not long enough'
                                        : undefined
                                },
                            }},
                        (field) => {
                            return html`
                                <sl-input required class="${classMap({ error: this.hasError(field.state.meta, field.name) })}" value=${extractDateTime(field.state.value)} type="datetime-local" label="Valid from"  @sl-input=${(e: InputEvent) => notifyChange(e, field)}>
                                    <div slot="help-text"><div class="error-text text-danger">Please check the validity interval</div></div>
                                </sl-input>`
                        })
                    }
                </div>
                <div class="col">
                    ${this.#form.field({name: `availabilityAndPrices.expiration`, validators: {
                                onChange: ({ value }) => {
                                    return value && value.length < 3
                                        ? 'Not long enough'
                                        : undefined
                                },
                            }},
                        (field) => {
                            return html`
                                <sl-input required class="${classMap({ error: this.hasError(field.state.meta, field.name) })}" .value=${extractDateTime(field.state.value)} type="datetime-local" label="Valid to" @sl-input=${(e: InputEvent) => notifyChange(e, field)}>
                                    <div slot="help-text"><div class="error-text text-danger">Please check the validity interval</div></div>
                                </sl-input>`
                        })
                    }
                </div>
            </div>
            ${renderIf(() => this.type === 'DONATION', () => html`
                <div>
                    ${this.#form.field({name: `availabilityAndPrices.fixPrice`},
                        (field) => {
                            return html`
                                <sl-select required label="Price Policy" value=${field.state.value} @sl-input=${(e: InputEvent) => notifyChange(e, field, v => 'true' === v)}>
                                    <sl-option value="true">Fixed</sl-option>
                                    <sl-option value="false">User-defined</sl-option>
                                </sl-select>`
                        })
                    }
                </div>
                ${renderIf(() => formValue.fixPrice, () => this.addMaxQuantityPerOrder(formValue))}
            `)}
            <div class="row" style="--alfio-row-cols: ${formValue.fixPrice ? '2': '1'}">
                ${renderIf(() => formValue.fixPrice, () => html`
                    <div class="col">
                        ${this.#form.field({name: `availabilityAndPrices.price`},
                            (field) => {
                                return html`
                                    <sl-input required .value=${field.state.value} label="Price" @sl-input=${(e: InputEvent) => notifyChange(e, field)}>
                                        <div slot="suffix">${isMandatoryPercentage(formValue.supplementPolicy) ? '%' : this.event?.currency}</div>
                                    </sl-input>`
                            })
                        }
                    </div>
                `)}
                    <div class="col">
                        ${this.#form.field({name: `availabilityAndPrices.vatType`},
                            (field) => {
                                return html`
                                    <sl-select required label="Tax Policy" value=${field.state.value}  @sl-input=${(e: InputEvent) => notifyChange(e, field)}>
                                        ${repeat(Object.keys(taxTypeDescriptions), k => k, (k) => html`
                                            <sl-option value=${k}>${taxTypeDescriptions[k]}</sl-option>
                                        `)}
                                    </sl-select>`
                            })
                        }
                    </div>
            </div>

            ${renderIf(() => this.type === 'SUPPLEMENT', () => this.handleContextBasedFields(formValue))}
        `;
    }

    private handleContextBasedFields(formValue: AvailabilityAndPricesForm): TemplateResult {
        switch (formValue.supplementPolicy) {
            case "MANDATORY_ONE_FOR_TICKET":
                return html``;
            case "MANDATORY_PERCENTAGE_RESERVATION":
            case "MANDATORY_PERCENTAGE_FOR_TICKET":
                return html`
                    <div class="row">
                        <div class="col">
                            ${this.#form.field({name: `availabilityAndPrices.minPrice`},
                                (field) => {
                                    return html`
                                        <sl-input .value=${field.state.value} label="Min price" @sl-input=${(e: InputEvent) => notifyChange(e, field)}>
                                            <div slot="suffix">${this.event?.currency}</div>
                                        </sl-input>
                                    `
                                })
                            }
                        </div>
                        <div class="col">
                            ${this.#form.field({name: `availabilityAndPrices.maxPrice`},
                                (field) => {
                                    return html`
                                        <sl-input .value=${field.state.value} label="Max price" @sl-input=${(e: InputEvent) => notifyChange(e, field)}>
                                            <div slot="suffix">${this.event?.currency}</div>
                                        </sl-input>
                                    `
                                })
                            }
                        </div>
                    </div>
                `;
            case "OPTIONAL_MAX_AMOUNT_PER_RESERVATION":
            case "OPTIONAL_MAX_AMOUNT_PER_TICKET":
                return html`
                    <div class="${classMap({ row: this.event?.supportsAdditionalItemsQuantity ?? false })}">
                        <div class="col">
                            ${this.addMaxQuantityPerOrder(formValue)}
                        </div>
                        ${this.addAvailableQuantity()}
                    </div>
                `;
            default:
                return html`
                    ${this.addAvailableQuantity()}
                    <!-- ${formValue.supplementPolicy} does not need additional input fields -->
                `;
        }
    }

    private addMaxQuantityPerOrder(formValue: AvailabilityAndPricesForm) {
        return html`
            ${this.#form.field({name: `availabilityAndPrices.maxQtyPerOrder`},
            (field) => {
                return html`
                   <sl-input required .value=${field.state.value} label=${`Max quantity per ${this.type === 'DONATION' || formValue.supplementPolicy === 'OPTIONAL_MAX_AMOUNT_PER_RESERVATION' ? 'order' : 'ticket'}`} @sl-input=${(e: InputEvent) => notifyChange(e, field)}></sl-input>
                `
            })}
        `
    }

    private addAvailableQuantity() {
        if (this.event?.supportsAdditionalItemsQuantity) {
            return html`
                <div class="col">
                    ${this.#form.field({name: `availabilityAndPrices.availableQuantity`},
                        (field) => {
                            return html`
                                <sl-input .value=${field.state.value ?? null} label="Total available quantity" @sl-input=${(e: InputEvent) => notifyChange(e, field)}></sl-input>
                            `
                        })
                    }
                </div>
            `
        }
        return html``;
    }

    public async open(request: {
        editedItem: AdditionalItem | null,
        supportedLanguages: ContentLanguage[],
        event: AlfioEvent,
        type: AdditionalItemType,
        ordinal: number
    }): Promise<boolean> {
        if (this.dialog != null) {
            this.editedItem = request.editedItem;
            this.supportedLanguages = request.supportedLanguages;
            this.event = request.event;
            this.type = request.type;
            this.#form.api.update({
                defaultValues: this.buildDefaultValues(this.#form.api.state),
                onSubmit: async (values) => {
                    await this.save(values.value, request.ordinal);
                },
                validators: {
                    onSubmitAsync: async props => {
                        const errors: { [k:string]: string} = {};
                        const additionalItemRequest: Partial<AdditionalItem> = this.buildServerPayload(props.value, request.ordinal);
                        const result = await AdditionalItemService.validateAdditionalItem(additionalItemRequest);
                        if (!result.success) {
                            result.validationErrors.forEach(error => {
                                errors[error.fieldName] = error.code;
                            });
                            this.validationErrors = errors;
                            return 'form contains errors';
                        } else {
                            this.validationErrors = {};
                            return undefined;
                        }
                    }
                }
            });
            this.displayForm = true;
            await this.dialog?.show();
        }
        return this.dialog != null;
    }

    public async close(success: boolean): Promise<boolean> {
        if (this.dialog != null) {
            await this.dialog.hide();
        }
        this.dispatchEvent(new CustomEvent('alfio-dialog-closed', { detail: { success } }));
        return this.dialog != null;
    }

    private preventAccidentalClose(e: SlRequestCloseEvent): void {
        if (e.detail.source === 'overlay') {
            e.preventDefault();
        } else {
            this.dispatchEvent(new CustomEvent('alfio-dialog-closed', { detail: { success: false } }));
        }
    }

    private buildServerPayload(value: FormData, ordinal: number): Partial<AdditionalItem> {
        return {
            id: this.editedItem?.id,
            maxQtyPerOrder: value.availabilityAndPrices.maxQtyPerOrder ?? -1,
            price: value.availabilityAndPrices.price,
            availableQuantity: value.availabilityAndPrices.availableQuantity ?? -1,
            inception: toDateTimeModification(value.availabilityAndPrices.inception),
            expiration: toDateTimeModification(value.availabilityAndPrices.expiration),
            title: value.descriptions.map(df => { return { locale: df.locale, type: 'TITLE', value: df.title, id: df.titleId }}),
            description: value.descriptions.map(df => { return { locale: df.locale, type: 'DESCRIPTION', value: df.description, id: df.descriptionId }}),
            supplementPolicy: value.availabilityAndPrices.supplementPolicy,
            vatType: value.availabilityAndPrices.vatType,
            vat: value.availabilityAndPrices.vat,
            type: this.type ?? undefined,
            fixPrice: value.availabilityAndPrices.fixPrice,
            minPrice: value.availabilityAndPrices.minPrice,
            maxPrice: value.availabilityAndPrices.maxPrice,
            ordinal
        };
    }

    private async save(value: FormData, ordinal: number) {
        const additionalItemRequest: Partial<AdditionalItem> = this.buildServerPayload(value, ordinal);
        const update = await AdditionalItemService.updateAdditionalItem(additionalItemRequest, this.event!.id);
        if (update.ok) {
            await this.close(true);
        }
    }

    private validationError(fieldName: string): string | undefined {
        const validationErrors = this.validationErrors;
        if (validationErrors != null) {
            const remoteFieldName = fieldName.substring(fieldName.lastIndexOf('.') + 1, fieldName.length);
            return validationErrors[remoteFieldName];
        }
        return undefined;
    }
}

interface DescriptionForm {
    locale: string;
    title: string;
    titleId: number | null;
    description: string;
    descriptionId: number | null;
}

interface AvailabilityAndPricesForm {
    price: number;
    fixPrice: boolean;
    availableQuantity: number | null;
    maxQtyPerOrder: number | null;
    inception: string;
    expiration: string;
    vat: number | null;
    vatType: AdditionalItemTaxType;
    supplementPolicy: SupplementPolicy;
    availableItems: number | null;
    minPrice: number | null;
    maxPrice: number | null;
}

interface FormData {
    descriptions: DescriptionForm[];
    availabilityAndPrices: AvailabilityAndPricesForm
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-additional-item-edit': AdditionalItemEdit
    }
}
