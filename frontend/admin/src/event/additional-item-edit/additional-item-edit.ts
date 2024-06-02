import {html, LitElement, PropertyValues, TemplateResult} from "lit";
import {customElement, property, query} from "lit/decorators.js";
import {
    AdditionalItemTaxType,
    AdditionalItemType,
    isMandatoryPercentage,
    SupplementPolicy,
    supplementPolicyDescriptions,
    taxTypeDescriptions
} from "../../model/additional-item.ts";
import {SlDialog, SlRequestCloseEvent} from "@shoelace-style/shoelace";
import {AlfioEvent, ContentLanguage} from "../../model/event.ts";
import {repeat} from "lit/directives/repeat.js";
import {TanStackFormController} from "@tanstack/lit-form";
import {dialog, pageHeader, row} from "../../styles.ts";
import {extractDateTime, notifyChange} from "../../service/helpers.ts";

@customElement('alfio-additional-item-edit')
export class AdditionalItemEdit extends LitElement {

    static styles = [pageHeader, row, dialog];

    @property({ type: Number, attribute: 'data-item-id' })
    itemId?: number;

    @property({ type: Array<ContentLanguage>, attribute: 'data-languages'})
    supportedLanguages?: ContentLanguage[];

    @property()
    event?: AlfioEvent;

    @property({ type: String, attribute: 'data-type' })
    type?: AdditionalItemType;

    @query("sl-dialog#editDialog")
    dialog?: SlDialog;

    #form = new TanStackFormController(this, {
        defaultValues: {
            descriptions: [] as DescriptionForm[],
            availabilityAndPrices: {} as AvailabilityAndPricesForm
        }
    });

    protected updated(_changedProperties: PropertyValues) {
        super.updated(_changedProperties);
        const hasSupportedLanguages = _changedProperties.has('supportedLanguages');
        const hasEvent = _changedProperties.has('event');
        if (hasSupportedLanguages || hasEvent) {
            const currentState = this.#form.api.state;
            this.#form.api.update({
                defaultValues: {
                    availabilityAndPrices: hasEvent ? this.buildAvailabilityAndPricesFromEvent(this.event!) : currentState.values.availabilityAndPrices,
                    descriptions: (this.supportedLanguages ?? []).map(sl => {
                        return {
                            locale: sl.locale,
                            title: '',
                            description: '',
                        };
                    })
                }
            });
        }
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
            maxQtyPerOrder: 1
        }
    }

    protected render(): TemplateResult {
        return html`
            <sl-dialog label=${(this.itemId ?? 0) > 0 ? `Edit Additional Item` : 'New Additional Item'} id="editDialog" style="--width: 70vw; --header-spacing:16px; --body-spacing: 16px; --sl-font-size-large: 1.5rem;" class="dialog" @sl-request-close=${this.preventAccidentalClose}>
                <form id="form" @submit=${(e: Event) => {e.preventDefault()}}>
                    <div class="row">
                        ${this.#form.field({name: 'descriptions'}, (descriptionsField) => this.renderDescription(descriptionsField.state.value))}
                    </div>
                    <h3>Availability and prices</h3>
                    <div>
                        ${this.#form.field({name: 'availabilityAndPrices'}, (field) => this.renderAvailabilityAndPrices(field.state.value))}
                    </div>
                    <sl-button slot="footer" variant="primary" @click=${this.close}>Close</sl-button>
                </form>
            </sl-dialog>
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
                        onChange: ({ value }: { value: string }) => {
                            return value && value.length < 3
                                ? 'Not long enough'
                                : undefined
                        },
                    }},
                    (field) => {
                        return html`
                            <sl-input placeholder="Title" label="Title" .value=${field.state.value} @sl-change=${(e: InputEvent) => notifyChange(e, field)}></sl-input>
                        `
                    })}
                    ${this.#form.field({name: `descriptions[${index}].description`, validators: {
                                onChange: ({ value }: { value: string }) => {
                                    return value && value.length < 3
                                        ? 'Not long enough'
                                        : undefined
                                },
                            }},
                        (field) => {
                            return html`
                                <sl-textarea .value=${field.state.value} rows="2" @sl-change=${(e: InputEvent) => notifyChange(e, field)}>
                                    <div slot="label">
                                        Description <alfio-display-commonmark-preview data-button-text="preview" .text=${field.state.value}></alfio-display-commonmark-preview>
                                    </div>
                                    <div slot="help-text">TODO</div>
                                </sl-textarea>
                            `
                    })}
                </div>
            `;
        })}`;
    }

    private renderAvailabilityAndPrices(formValue: AvailabilityAndPricesForm): TemplateResult {
        return html`
            <div>
                ${this.#form.field({name: `availabilityAndPrices.supplementPolicy`},
                    (field) => {
                        return html`
                            <sl-select label="Additional Item Policy" value=${field.state.value} @sl-change=${(e: InputEvent) => notifyChange(e, field)}>
                                ${repeat(Object.keys(supplementPolicyDescriptions), k => k, (k) => html`
                                    <sl-option value=${k}>${supplementPolicyDescriptions[k]}</sl-option>
                                `)}
                            </sl-select>`
                    })
                }
            </div>
            <div class="row">
                <div class="col">
                    ${this.#form.field({name: `availabilityAndPrices.inception`, validators: {
                                onChange: ({ value }: { value: string }) => {
                                    return value && value.length < 3
                                        ? 'Not long enough'
                                        : undefined
                                },
                            }},
                        (field) => {
                            return html`<sl-input value=${extractDateTime(field.state.value)} type="datetime-local" label="Valid from"  @sl-change=${(e: InputEvent) => notifyChange(e, field)}></sl-input>`
                        })
                    }
                </div>
                <div class="col">
                    ${this.#form.field({name: `availabilityAndPrices.expiration`, validators: {
                                onChange: ({ value }: { value: string }) => {
                                    return value && value.length < 3
                                        ? 'Not long enough'
                                        : undefined
                                },
                            }},
                        (field) => {
                            return html`<sl-input .value=${extractDateTime(field.state.value)} type="datetime-local" label="Valid to" @sl-change=${(e: InputEvent) => notifyChange(e, field)}></sl-input>`
                        })
                    }
                </div>
            </div>
            <div class="row">
                <div class="col">
                    ${this.#form.field({name: `availabilityAndPrices.price`},
                        (field) => {
                            return html`
                                <sl-input .value=${field.state.value} label="Price" @sl-change=${(e: InputEvent) => notifyChange(e, field)}>
                                    <div slot="suffix">${isMandatoryPercentage(formValue.supplementPolicy) ? '%' : this.event?.currency}</div>
                                </sl-input>`
                        })
                    }
                </div>
                <div class="col">
                    ${this.#form.field({name: `availabilityAndPrices.vatType`},
                        (field) => {
                            return html`
                                <sl-select label="Tax Policy" value=${field.state.value}  @sl-change=${(e: InputEvent) => notifyChange(e, field)}>
                                    ${repeat(Object.keys(taxTypeDescriptions), k => k, (k) => html`
                                        <sl-option value=${k}>${taxTypeDescriptions[k]}</sl-option>
                                    `)}
                                </sl-select>`
                        })
                    }
                </div>
            </div>
            ${this.handleContextBasedFields(formValue)}
        `;
    }

    private handleContextBasedFields(formValue: AvailabilityAndPricesForm): TemplateResult {

        switch (formValue.supplementPolicy) {
            case "MANDATORY_PERCENTAGE_RESERVATION":
            case "MANDATORY_PERCENTAGE_FOR_TICKET":
                return html`
                    <div class="row">
                        <div class="col">
                            ${this.#form.field({name: `availabilityAndPrices.minPrice`},
                                (field) => {
                                    return html`
                                        <sl-input .value=${field.state.value} label="Min price" @sl-change=${(e: InputEvent) => notifyChange(e, field)}>
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
                                        <sl-input .value=${field.state.value} label="Max price" @sl-change=${(e: InputEvent) => notifyChange(e, field)}>
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
                    <div>
                        ${this.#form.field({name: `availabilityAndPrices.maxQtyPerOrder`},
                            (field) => {
                                return html`
                                        <sl-input .value=${field.state.value} label=${`Max quantity per ${formValue.supplementPolicy === 'OPTIONAL_MAX_AMOUNT_PER_RESERVATION' ? 'order' : 'ticket'}`} @sl-change=${(e: InputEvent) => notifyChange(e, field)}></sl-input>
                                    `
                            })
                        }
                    </div>
                `;
            default:
                return html`<!-- ${formValue.supplementPolicy} does not need additional input fields -->`;
        }
    }

    public async open(): Promise<boolean> {
        if (this.dialog != null) {
            await this.dialog?.show();
        }
        return this.dialog != null;
    }

    public async close(): Promise<boolean> {
        if (this.dialog != null) {
            await this.dialog.hide();
        }
        return this.dialog != null;
    }

    private preventAccidentalClose(e: SlRequestCloseEvent): void {
        if (e.detail.source === 'overlay') {
            e.preventDefault();
        }
    }
}

interface DescriptionForm {
    locale: string;
    title: string;
    description: string;
}

interface AvailabilityAndPricesForm {
    price: number;
    fixPrice: boolean;
    availableQuantity?: number;
    maxQtyPerOrder?: number;
    inception: string;
    expiration: string;
    vat: number | null;
    vatType: AdditionalItemTaxType;
    supplementPolicy: SupplementPolicy;
    availableItems: number | null;
    minPrice: number | null;
    maxPrice: number | null;
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-additional-item-edit': AdditionalItemEdit
    }
}
