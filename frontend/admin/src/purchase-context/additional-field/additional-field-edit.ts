import {customElement, query, state} from "lit/decorators.js";
import {html, LitElement, nothing, TemplateResult} from "lit";
import {SlDialog, SlRequestCloseEvent} from "@shoelace-style/shoelace";
import {
    AdditionalField,
    additionalFieldTypesWithDescription,
    DescriptionRequest,
    NewAdditionalFieldFromTemplate,
    PurchaseContextFieldDescriptionContainer,
    RestrictedValueRequest,
    supportsMinMaxLength,
    supportsPlaceholder,
    supportsRestrictedValues
} from "../../model/additional-field.ts";
import {PurchaseContext} from "../../model/purchase-context.ts";
import {TanStackFormController} from "@tanstack/lit-form";
import {notifyChange, renderIf} from "../../service/helpers.ts";
import {repeat} from "lit/directives/repeat.js";
import {dialog as dialogStyling, form, pageHeader, row, textColors} from "../../styles.ts";
import {AlfioEvent, TicketCategory} from "../../model/event.ts";
import {AdditionalFieldService} from "../../service/additional-field.ts";

@customElement('alfio-additional-field-edit')
export class AdditionalFieldEdit extends LitElement {

    @query("sl-dialog#editDialog")
    dialog?: SlDialog;

    @state()
    dialogTitle?: string

    @state()
    displayForm: boolean = false;

    @state()
    private purchaseContext?: PurchaseContext;


    static styles = [pageHeader, row, dialogStyling, form, textColors];

    #form = new TanStackFormController(this, {
        defaultValues: {
            id: undefined,
            name: '',
            type: 'input:text',
            context: 'ATTENDEE',
            editable: true,
            required: false,
            displayAtCheckIn: false,
            description: {},
            order: 1,
            restrictedValues: [],
            maxLength: undefined,
            minLength: undefined,
            additionalServiceId: undefined,
            disabledValues: undefined,
            categoryIds: undefined,
        } as AdditionalField
    })


    protected render(): TemplateResult {
        return html`
            <sl-dialog
                id="editDialog"
                style="--width: 50vw; --header-spacing:16px; --body-spacing: 16px; --sl-font-size-large: 1.5rem;"
                class="dialog"
                label=${this.dialogTitle}
                @sl-request-close=${this.preventAccidentalClose}>

                ${renderIf(() => this.displayForm, () => this.renderForm())}

            </sl-dialog>
        `;
    }

    private renderForm(): TemplateResult {
        return html`
            <form id="form" @submit="${async (e: Event) => {e.preventDefault(); e.stopImmediatePropagation(); await this.#form.api.handleSubmit();}}">
                ${this.renderHeader()}
                <sl-divider></sl-divider>
                <h3>Field Configuration</h3>
                ${this.renderFieldConfiguration()}
                ${this.renderValues()}
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

    private renderHeader(): TemplateResult {
        return html`
            <div class="row">
                    <div class="col">
                         ${this.#form.field({
                            name: 'name',
                            validators: {
                                onChange: ({ value }) => {
                                    if (!value || value.length === 0) {
                                        return 'Required field';
                                    }
                                    if (!/^[a-z0-9][a-z0-9-_]*$/i.test(value)) {
                                        return 'Name contains invalid characters';
                                    }
                                    return undefined;
                                }
                            }
                        }, (field) => html`
                                             <sl-input placeholder="Name" label="Field Name" required .value=${field.state.value} @sl-change=${(e: InputEvent) => notifyChange(e, field)}></sl-input>
                        `)}
                                         ${this.#form.field({
                            name: 'type'
                        }, (field) => html`
                                             <sl-select label="Field Type" required .value=${field.state.value} @sl-change=${(e: InputEvent) => notifyChange(e, field)}>
                                                 ${repeat(Object.entries(additionalFieldTypesWithDescription), ([k]) => k, ([value, description]) => html`
                                                     <sl-option value=${value}>${description}</sl-option>
                                                 `)}
                                             </sl-select>
                                         `)}
                                    </div>
                                    <div class="col">
                                        ${this.#form.field({
                            name: 'required'
                        }, (field) => html`
                                            <sl-checkbox .value=${field.state.value} checked=${field.state.value || nothing} @sl-change=${(e: InputEvent) => notifyChange(e, field)}>
                                                Required
                                            </sl-checkbox>
                                        `)}
                                        ${this.#form.field({
                            name: 'displayAtCheckIn'
                        }, (field) => html`
                                <sl-checkbox .value=${field.state.value} checked=${field.state.value || nothing} @sl-change=${(e: InputEvent) => notifyChange(e, field)}>
                                    Shown at Check-in
                                    <span slot="help-text">This information will be shown in the check-in app upon successful scan</span>
                                </sl-checkbox>
                            `)}
                        ${this.#form.field({
                            name: 'editable'
                        }, (field) => html`
                            <sl-checkbox .value=${field.state.value} checked=${field.state.value || nothing} @sl-change=${(e: InputEvent) => notifyChange(e, field)}>
                                Editable
                                <span slot="help-text">Whether Information can be modified after set</span>
                            </sl-checkbox>
                        `)}
                    </div>
                </div>
        `;
    }

    private renderFieldConfiguration() {
        const contentLanguages = this.purchaseContext?.contentLanguages ?? [];
        return html`
            <div class="row">
                <div class="col">
                    ${this.renderCollectFor()}
                </div>
                <div class="col">
                    ${this.renderConstraints()}
                </div>
            </div>
            <sl-divider></sl-divider>
            <h3>Localized messages</h3>
            <div class="row">
                ${repeat(contentLanguages, cl => cl.locale, (cl) => {
                    return html`
                        <div class="col">
                            <div class="border-bottom">
                                <h4>${cl.displayLanguage}</h4>
                            </div>
                            ${this.#form.field({name: `description.${cl.locale}.description.label`}, (field) => {
                                return html`
                                    <sl-input label="Label" required .value=${field.state.value ?? nothing} @sl-change=${(e: InputEvent) => notifyChange(e, field)}></sl-input>
                                `
                            })}
                            ${renderIf(() => supportsPlaceholder(this.#form.api.state.values.type),
                                () => html`
                                    ${this.#form.field({name: `description.${cl.locale}.description.placeholder`}, (field) => html`
                                        <sl-input label="Placeholder" .value=${field.state.value ?? nothing} @sl-change=${(e: InputEvent) => notifyChange(e, field)}></sl-input>
                                    `)}
                                `)}
                        </div>
                    `;
                })}
            </div>
        `;
    }

    private renderValues() {
        if (!supportsRestrictedValues(this.#form.api.state.values.type)) {
            return nothing;
        }
        const restrictedValues = this.#form.api.state.values.restrictedValues ?? [];
        return html`
            <h3>Values</h3>
            <sl-button variant="success" @click={}>Add New</sl-button>

            ${repeat(restrictedValues, rv => html`
                <div>${rv}</div>
            `)}
        `;
    }

    private async close(success: boolean):Promise<boolean> {
        if (this.dialog != null) {
            await this.dialog.hide();
        }
        this.dispatchEvent(new CustomEvent('alfio-dialog-closed', { detail: { success } }));
        return this.dialog != null;
    }



    private renderCollectFor() {
        if (this.purchaseContext?.type === 'event') {
            const event = this.purchaseContext as AlfioEvent;
            return html`
                <h3>Collect for</h3>
                ${this.#form.field({ name: 'categoryIds' }, (field) => {

                    const selectionChanged = (e: InputEvent, category?: TicketCategory) => {
                        const target = e.currentTarget as HTMLInputElement;
                        const checked = target.checked;

                        if (category == null) {
                            if (checked) {
                                field.handleChange([]);
                            }
                            return;
                        }

                        let values = [...(field.state.value ?? [])];
                        if (checked) {
                            values.push(category.id);
                        } else {
                            values = values.filter(i => i !== category.id);
                        }
                        field.handleChange(values);
                    }

                    return html`
                        <sl-checkbox checked=${!field.state.value || field.state.value.length === 0 || nothing} @sl-change=${(e: InputEvent) => selectionChanged(e, undefined)}>
                            All Ticket categories
                        </sl-checkbox>
                        <br />
                        ${repeat(event.ticketCategories, tc => tc.id, (tc) => {
                            return html`
                                <sl-checkbox checked=${field.state.value?.includes(tc.id) || nothing} @sl-change=${(e: InputEvent) => selectionChanged(e, tc)}>
                                    ${tc.name}
                                </sl-checkbox>
                                <br />
                            `;
                        })}
                    `;
                })}
            `;
        }
        return html``;
    }

    private renderConstraints() {
        const fieldType = this.#form.api.state.values.type;
        if (supportsMinMaxLength(fieldType)) {
            return html`
                ${this.#form.field({name: 'minLength'},
                    (field) => html`
                         <sl-input label=${fieldType === 'input:dateOfBirth' ? "Min age (years)" : "Min length"} .value=${field.state.value ?? nothing} @sl-change=${(e: InputEvent) => notifyChange(e, field)}></sl-input>
                    `)}

                ${this.#form.field({name: 'maxLength'},
                    (field) => html`
                         <sl-input label=${fieldType === 'input:dateOfBirth' ? "Max age (years)" : "Max length"} .value=${field.state.value ?? nothing} @sl-change=${(e: InputEvent) => notifyChange(e, field)}></sl-input>
                    `)}
            `;
        }
        return nothing;
    }

    public async open(request: {
        field?: AdditionalField,
        template?: NewAdditionalFieldFromTemplate,
        purchaseContext: PurchaseContext,
        ordinal: number
    }): Promise<boolean> {

        if (this.dialog != null) {
            this.dialogTitle = request.field != null ? `Edit ${request.field.name}` : `Add attendee data`;
            this.purchaseContext = request.purchaseContext;
            this.#form.api.update({
                defaultValues: this.buildDefaultValues(request.purchaseContext, request.ordinal, request.field, request.template),
                onSubmit: async (state) => {
                    await this.save(state.value);
                }
            });
            this.displayForm = true;
            await this.dialog!.show();
        }
        return this.dialog != null;
    }

    private preventAccidentalClose(e: SlRequestCloseEvent): void {
        if (e.detail.source === 'overlay') {
            e.preventDefault();
        } else {
            this.displayForm = false;
            this.dispatchEvent(new CustomEvent('alfio-dialog-closed', { detail: { success: false } }));
        }
    }

    private buildDefaultValues(purchaseContext: PurchaseContext,
                               ordinal: number,
                               field?: AdditionalField,
                               template?: NewAdditionalFieldFromTemplate): AdditionalField {
        if (field != null) {
            return {
                ...field
            };
        }

        return {
            name: template?.name ?? '',
            order: template?.order ?? ordinal,
            type: template?.type ?? 'input:text',
            description: this.parseDescriptions(purchaseContext, template),
            editable: true,
            displayAtCheckIn: false,
            required: false,
            context: purchaseContext.type === 'subscription' ? 'SUBSCRIPTION' : 'ATTENDEE'
        };
    }

    private parseDescriptions(purchaseContext: PurchaseContext,
                              template?: NewAdditionalFieldFromTemplate): {[p: string]: PurchaseContextFieldDescriptionContainer} {

        const descriptions: {[p: string]: PurchaseContextFieldDescriptionContainer} = {};
        purchaseContext.contentLanguages.forEach((lang) => {
            const fromTemplate = template?.description[lang.locale];
            descriptions[lang.locale] = {
                locale: lang.locale,
                description: {
                    label: fromTemplate?.label ?? '',
                    placeholder: fromTemplate?.placeholder,
                    restrictedValues: fromTemplate?.restrictedValues,
                },
                fieldName: template?.name ?? ''
            };
        });
        return descriptions;
    }


    private async save(value: AdditionalField) {
        let updateResult: Response;
        if (value.id != null) {
            updateResult = await AdditionalFieldService.saveField(this.purchaseContext!, value);
        } else {
            const descriptionRequest: {[locale: string]: DescriptionRequest} = {};
            const restrictedValues: RestrictedValueRequest[] = [];
            Object.entries(value.description).forEach(([key, value]) => {
               descriptionRequest[key] = {
                   label: value.description.label,
                   placeholder: value.description.placeholder ?? ''
               }
               if (value.description.restrictedValues != null) {
                   restrictedValues.push({
                       value: value.description.restrictedValues[key],
                       enabled: true
                   });
               }
            });
            updateResult = await AdditionalFieldService.createNewField(this.purchaseContext!, {
                type: value.type,
                name: value.name,
                order: value.order,
                categoryIds: value.categoryIds ?? [],
                displayAtCheckIn: value.displayAtCheckIn,
                forAdditionalService: undefined, // TODO
                maxLength: value.maxLength,
                minLength: value.minLength,
                readOnly: !value.editable,
                required: value.required,
                description: descriptionRequest,
                restrictedValues: restrictedValues,
                userDefinedOrder: false
            });
        }

        if (updateResult.ok) {
            await this.close(true);
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-additional-field-edit': AdditionalFieldEdit
    }
}
