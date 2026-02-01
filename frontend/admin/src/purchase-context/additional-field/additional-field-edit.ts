import {customElement, query, state} from "lit/decorators.js";
import {css, html, LitElement, nothing, TemplateResult} from "lit";
import {SlDialog, SlRequestCloseEvent} from "@shoelace-style/shoelace";
import {
    AdditionalField, AdditionalFieldType,
    additionalFieldTypesWithDescription,
    DescriptionRequest,
    NewAdditionalFieldFromTemplate,
    PurchaseContextFieldDescriptionContainer,
    RestrictedValueRequest,
    supportsMinMaxLength,
    supportsPlaceholder,
    supportsRestrictedValues
} from "../../model/additional-field.ts";
import {ContentLanguage, PurchaseContext} from "../../model/purchase-context.ts";
import {TanStackFormController} from "@tanstack/lit-form";
import {notifyChange, renderIf} from "../../service/helpers.ts";
import {repeat} from "lit/directives/repeat.js";
import {
    cardBgColors,
    dialog as dialogStyling,
    form,
    itemsList,
    listGroup,
    pageHeader,
    retroCompat,
    row,
    textAlign,
    textColors
} from "../../styles.ts";
import {AlfioEvent} from "../../model/event.ts";
import {AdditionalFieldService} from "../../service/additional-field.ts";
import {AdditionalItem} from "../../model/additional-item.ts";
import {AdditionalItemService} from "../../service/additional-item.ts";
import {renderPreview} from "./additional-field-util.ts";
import {classMap} from "lit/directives/class-map.js";
import {dispatchFeedback} from "../../model/dom-events.ts";

interface SelectableOption {
    fieldName: string;
    description: {[lang: string]: string};
    toBePersisted: boolean;
}

interface AdditionalFieldForm extends AdditionalField {
    selectableOptions: SelectableOption[];
}

@customElement('alfio-additional-field-edit')
export class AdditionalFieldEdit extends LitElement {

    @query("sl-dialog#editDialog")
    dialog?: SlDialog;

    @state()
    dialogTitle?: string;

    @state()
    editField: boolean = false;

    @state()
    displayForm: boolean = false;

    @state()
    additionalItems: AdditionalItem[] = [];

    @state()
    private purchaseContext?: PurchaseContext;

    @state()
    allCategories: boolean = true;

    @state()
    preview?: AdditionalField;

    @state()
    existingFieldName?: string;

    @state()
    unsubscribeFn?: () => void;

    static readonly styles = [pageHeader, row, dialogStyling, form, textColors, cardBgColors, textAlign, itemsList, listGroup, css`
        .block {
            display: block;
        }

        .section-header {
            font-weight: bold;
            color: black;
            font-size: calc(var(--sl-font-size-medium) * 1.1);
        }

        div.row section {
            margin-top: 16px;
        }

        div.row section:nth-of-type(1) {
            margin-top: 0;
        }

        div.section-title {
            padding-top: 16px;
            text-transform: uppercase;
            color: var(--sl-input-help-text-color);
            margin-bottom: 4px;
            font-size: var(--sl-font-size-medium);
            font-weight: 600;
        }

        section div.section-subtitle {
            font-size: var(--sl-input-help-text-font-size-medium);
            color: var(--sl-input-help-text-color);
            margin-top: 2px;
        }

        div.field-constraints {
            margin-top: 16px;
            padding: 0 16px 16px 16px;
            background: #fafafa;
            border-radius: 8px;
            border: 1px dashed #d1d5db;
        }

        .selectable-option-header {
            display: flex;
            justify-content: center;
            align-items: center;
            font-weight: 600;
            color: var(--sl-input-help-text-color);
        }

        .option-title {
            color: var(--sl-input-help-text-color);
            font-weight: 600;
        }

        sl-card.preview-container sl-input,
        sl-card.preview-container sl-textarea,
        sl-card.preview-container sl-select {
            margin-top: 0;
        }

        div.live-preview-container {
            height: 100%;
        }

        div.live-preview {
            position: sticky;
            top: -1px;
        }
        .no-mt {
            margin-top: 0;
        }
        sl-tab.has-error:not([active])::part(base) {
            color: var(--sl-color-danger-600);
        }
    `, retroCompat];

    readonly #form = new TanStackFormController(this, {
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
            selectableOptions: []
        } as AdditionalFieldForm
    });


    protected render(): TemplateResult {
        return html`
            <sl-dialog
                id="editDialog"
                style="--width: 70vw; --header-spacing:16px; --body-spacing: 16px; --sl-font-size-large: 1.5rem;"
                class="dialog"
                label=${this.dialogTitle}
                @sl-request-close=${this.preventAccidentalClose}>

                    ${renderIf(() => this.displayForm, () => this.renderForm())}

            </sl-dialog>
        `;
    }

    private renderForm(): TemplateResult {
        const contentLanguages = this.purchaseContext?.contentLanguages ?? [];
        return html`
            <form id="form" @submit="${async (e: Event) => {e.preventDefault(); e.stopImmediatePropagation(); await this.#form.api.handleSubmit();}}">
                <div class="row custom" style="--alfio-custom-row-cols-layout: 2fr 1fr">
                    <div class="col">
                        <section>
                            <div class="section-header">Field Definition</div>
                            <div class="section-subtitle">What kind of data will you collect?</div>
                            ${(this.renderFieldTypeSelector())}
                            ${this.renderConstraints()}
                        </section>
                        <sl-divider></sl-divider>
                        <section>
                            <div class="section-header">Display Labels</div>
                            <div class="section-subtitle">What attendees will see in the form</div>
                            <sl-tab-group>
                                ${contentLanguages.map((d, index) => {
                                    return html`
                                            <sl-tab slot="nav" panel=${d.locale}>${d.displayLanguage}${renderIf(() => index === 0, () => html`<sl-badge variant="primary" pill class="ms-1">Primary</sl-badge>`)}</sl-tab>
                                            <sl-tab-panel name=${d.locale}>
                                                <div class="info-container">
                                                    ${this.renderLanguageFields(d, index === 0)}
                                                </div>
                                            </sl-tab-panel>
                                        `;
                                })}
                            </sl-tab-group>
                        </section>
                        <sl-divider></sl-divider>
                        ${this.renderValues()}
                        <section>
                            <div class="section-header">Field Reference</div>
                            <div class="section-subtitle">How field will be referenced in export/import Spreadsheets</div>
                            ${(this.renderNameField())}
                        </section>
                        <sl-divider></sl-divider>
                        <section>
                            <div class="section-header">Field Behavior</div>
                            <div class="section-subtitle">How the field works</div>
                            <ul class="list-group">
                                <li class="list-group-item">
                                    ${this.renderRequiredCheckbox()}
                                </li>
                                <li class="list-group-item">
                                    ${this.renderEditableField()}
                                </li>
                                <li class="list-group-item">
                                    ${this.renderDisplayAtCheckInField()}
                                </li>
                            </ul>
                        </section>
                        ${renderIf(() => this.purchaseContext?.type === 'event', () => html`
                            <sl-divider></sl-divider>
                            <section>
                                <div class="section-header">Visibility Rules</div>
                                <div class="section-subtitle">Control when this field appears</div>
                                <ul class="list-group">
                                    <li class="list-group-item">
                                        ${this.renderCollectFor()}
                                    </li>
                                    <li class="list-group-item"></li>
                                </ul>
                            </section>
                        `)}
                    </div>
                    <div class="col live-preview-container">

                        <div class="live-preview">
                            <div class="section-title">Live Preview</div>

                            ${this.buildPreview(contentLanguages[0])}

                            <sl-alert variant="primary" open class="mt-3">
                                <sl-icon slot="icon" name="info-circle"></sl-icon>
                                <strong>Preview updates as you configure the field</strong>
                            </sl-alert>
                        </div>

                    </div>
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

    private buildPreview(cl: ContentLanguage) {
        const descriptionForContentLanguage = this.#form.api.getFieldValue('description')[cl.language];
        if (this.preview == null) {
            return html`
                <sl-alert variant="warning" open class="mt-3">
                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                    <strong>Not enough information to build a preview</strong><br />
                    Please configure the field
                </sl-alert>
            `;
        } else {
            return html`
                <sl-card class="item bg-default mt-3 preview-container">
                    ${renderPreview({
                        locale: cl.locale,
                        localeLabel: cl.displayLanguage,
                        description: descriptionForContentLanguage
                    }, this.preview)}
                </sl-card>
            `;
        }
    }

    private renderEditableField() {
        return this.#form.field({
            name: 'editable'
        }, (field) => html`
            <sl-switch .value=${field.state.value} help-text="Attendee can modify after initial submission" checked=${field.state.value || nothing} @sl-change=${(e: InputEvent) => notifyChange(e, field, _ => (e.currentTarget as HTMLInputElement).checked)} class="block mt-2 mb-2">
                Editable by attendee
            </sl-switch>
        `);
    }

    private renderDisplayAtCheckInField() {
        return this.#form.field({
            name: 'displayAtCheckIn'
        }, (field) => html`
            <sl-switch help-text="Displayed in check-in app upon successful scan" checked=${field.state.value || nothing} @sl-change=${(e: InputEvent) => notifyChange(e, field, _ => (e.currentTarget as HTMLInputElement).checked)} class="block mt-2 mb-2">
                Show at check-in
            </sl-switch>
        `);
    }

    private renderRequiredCheckbox() {
        return this.#form.field({
            name: 'required'
        }, (field) => html`
            <sl-switch size="medium" .value=${field.state.value} checked=${field.state.value || nothing} @sl-change=${(e: InputEvent) => notifyChange(e, field, _ => (e.currentTarget as HTMLInputElement).checked)} class="block mt-2 mb-2">
                Required field
            </sl-switch>
        `);
    }

    private renderFieldTypeSelector() {

        return this.#form.field({
            name: 'type'
        }, (field) => {
            const selectionChangeHandler = (e: InputEvent) => {
                const value = (e.currentTarget as HTMLInputElement).value as AdditionalFieldType;
                if (supportsRestrictedValues(value) && this.#form.api.getFieldValue("selectableOptions").length === 0) {
                    // add first selectable option
                    this.#form.api.setFieldValue("selectableOptions", [{fieldName: '', description: {}, toBePersisted: true}]);
                }
                notifyChange(e, field);
            }
            return html`
                <sl-select label="Field Type" required .value=${field.state.value} @sl-change=${(e: InputEvent) => selectionChangeHandler(e)} class=${classMap({ error: this.hasError(field.state.meta) })}>
                    ${repeat(Object.entries(additionalFieldTypesWithDescription), ([k]) => k, ([value, description]) => html`
                        <sl-option value=${value}>${description}</sl-option>
                    `)}
                </sl-select>
            `;
        });
    }

    private renderNameField() {
        return this.#form.field({
            name: 'name',
            validators: {
                onChange: ({value}) => {
                    return this.validateName(value);
                }
            }
        }, (field) => html`
             <sl-input placeholder="Name" label="Internal Field Name" maxlength="64" help-text="Letters, numbers, and underscores only." required class=${classMap({ error: this.hasError(field.state.meta) })} .value=${field.state.value} @sl-change=${(e: InputEvent) => notifyChange(e, field)} .disabled=${this.editField}></sl-input>
        `);
    }

    private validateName(value: string) {
        if (!value || value.length === 0) {
            return 'Required field';
        }
        if (!/^[a-z0-9][a-z0-9_]*$/i.test(value)) {
            return 'Name contains invalid characters';
        }
        return undefined;
    }

    private renderLanguageFields(cl: ContentLanguage, primary: boolean) {

        const descriptionChanged = (e: InputEvent, field: any) => {
            if (primary && !this.editField) {
                const value = (e.currentTarget as HTMLInputElement).value;
                const currentFieldName = this.#form.api.getFieldValue(`name`);
                const currentFieldNameUntouched = this.#form.api.getFieldMeta(`name`)!.isPristine;
                if (currentFieldNameUntouched || currentFieldName?.length === 0) {
                    let generatedValue = value.toLowerCase().replaceAll(/\W/g,"_").substring(0, 64);
                    if (generatedValue.endsWith("_")) {
                        generatedValue = generatedValue.substring(0, generatedValue.length - 1);
                    }
                    this.#form.api.setFieldValue(`name`, generatedValue, {dontUpdateMeta: true});
                }
            }
            notifyChange(e, field);
        };

        return html`
            ${this.#form.field({
                name: `description.${cl.locale}.description.label`,
                validators: {
                    onChange: ({value}) => {
                        if (value.trim().length === 0) {
                            return 'error.required';
                        }
                        return undefined;
                    }
                }
            }, (field) => {
                return html`
                        <sl-input label="Title" placeholder="The question attendees will see" required class=${classMap({ error: this.hasError(field.state.meta) })} .value=${field.state.value ?? nothing} @sl-change=${(e: InputEvent) => descriptionChanged(e, field)}></sl-input>
                    `
            })}
            ${renderIf(() => supportsPlaceholder(this.#form.api.state.values.type),
                () => html`
                    ${this.#form.field({name: `description.${cl.locale}.description.placeholder`}, (field) => html`
                        <sl-input label="Placeholder" .value=${field.state.value ?? nothing} placeholder="Optional hint inside the field" @sl-change=${(e: InputEvent) => notifyChange(e, field)}></sl-input>
                    `)}
                `)}
            `;
    }

    private renderValues() {
        if (!supportsRestrictedValues(this.#form.api.state.values.type)) {
            return nothing;
        }

        return html`
            <section>
                <div class="section-header">Selectable Options</div>
                <div class="section-subtitle">Define the options attendees can choose from</div>
                <div class="field-constraints">
                    ${this.#form.field({
                        name: 'selectableOptions',
                    }, (selectableOptionsField) => {
                        const contentLanguages = this.purchaseContext?.contentLanguages ?? [];
                        const selectableOptions = selectableOptionsField.state.value ?? [];
                        return html`
                            ${repeat(selectableOptions, (_, index) => index, (_, index) => {
                                return this.renderSelectableOptionCard(index,
                                    selectableOptions.length - 1,
                                    contentLanguages,
                                    () => selectableOptionsField.removeValue(index),
                                    (newIndex) => selectableOptionsField.moveValue(index, newIndex)
                                );
                            })}
                            <sl-button class="mt-2" variant="success" @click=${() => {selectableOptionsField.pushValue({fieldName: '', description: {}, toBePersisted: true})}}>Add option</sl-button>
                        `;
                    })}
                </div>
            </section>
        `;
    }

    private renderSelectableOptionCard(optionIndex: number,
                                       maxIndex: number,
                                       contentLanguages: ContentLanguage[],
                                       removeField: () => void,
                                       moveField: (newIndex: number) => void) {
        const descriptionChanged = (e: InputEvent, languageIdx: number, field: any) => {
            if (languageIdx === 0 && !this.editField) {
                // only process it when it's the first language
                const value = (e.currentTarget as HTMLInputElement).value;
                const currentFieldName = this.#form.api.getFieldValue(`selectableOptions[${optionIndex}].fieldName`);
                const currentFieldNameTouched = this.#form.api.getFieldMeta(`selectableOptions[${optionIndex}].fieldName`)!.isPristine;
                if (currentFieldNameTouched || currentFieldName?.length === 0) {
                    this.#form.api.setFieldValue(`selectableOptions[${optionIndex}].fieldName`, value.toLowerCase().replaceAll(/\W/g,"_").substring(0, 64), {dontUpdateMeta: true});
                }
            }
            notifyChange(e, field);
        };
        return html`
            <sl-card class="item bg-default mt-3">
                <div slot="header">
                    <div class="col option-title">Option ${optionIndex + 1}</div>
                    <div class="col">
                        ${renderIf(() => this.#form.api.getFieldValue(`selectableOptions[${optionIndex}].toBePersisted`),
                            () => html`<sl-button variant="danger" outline @click=${() => removeField()}><sl-icon slot="prefix" name="trash"></sl-icon> delete</sl-button>`)}
                    </div>
                </div>
                <div slot="footer">
                    ${renderIf(() => optionIndex > 0,
                        () => html`
                            <sl-button type="button" variant="default" @click=${() => moveField(optionIndex - 1)}>
                                <sl-icon name="arrow-up" slot="prefix"></sl-icon>
                                Move up
                            </sl-button>
                        `)}
                    ${renderIf(() => optionIndex < maxIndex,
                        () => html`
                        <sl-button type="button" variant="default" @click=${() => moveField(optionIndex + 1)}>
                            <sl-icon name="arrow-down" slot="prefix"></sl-icon>
                            Move down
                        </sl-button>
                    `)}
                </div>
                <div class="item-body" style="--alfio-custom-row-cols-layout: 1fr 10fr">
                    ${contentLanguages.map((d, languageIndex) => {
                        return html`
                            <div class="row custom">
                                <div class="col text-end selectable-option-header pt-2">${d.displayLanguage}</div>
                                <div class="col">
                                    ${this.#form.field({
                                        name: `selectableOptions[${optionIndex}].description.${d.locale}`,
                                        validators: {
                                            onChange: ({value}) => {
                                                if (value.trim().length === 0) {
                                                    return 'error.required';
                                                }
                                                return undefined;
                                            }
                                        }
                                    }, (field) => html`
                                        <sl-input type="text" placeholder=${`enter description (${d.displayLanguage})`}
                                                  required .value=${field.state.value ?? ''}
                                                  class=${classMap({ error: this.hasError(field.state.meta) })}
                                                  @sl-change=${(e: InputEvent) => descriptionChanged(e, languageIndex, field)}></sl-input>
                                    `)}
                                </div>
                            </div>
                        `;
                    })}
                    <sl-divider></sl-divider>
                    <div class="row custom">
                        <div class="col text-end selectable-option-header">Value</div>
                        <div class="col">
                            ${this.#form.field({
                                name: `selectableOptions[${optionIndex}].fieldName`,
                                validators: {
                                    onChange: ({value}) => {
                                        return this.validateName(value);
                                    }
                                }
                            }, (field) => html`
                                <sl-input type="text" placeholder="Value will be autogenerated from description"
                                          required .value=${field.state.value}
                                          class=${classMap({ error: this.hasError(field.state.meta) })}
                                          maxlength="64"
                                          help-text="Used in data exports. Letters, numbers, and underscores only."
                                          @sl-change=${(e: InputEvent) => notifyChange(e, field)}
                                          .readonly=${this.editField}></sl-input>
                            `)}
                        </div>
                    </div>
            </sl-card>
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
                ${this.#form.field({
                    name: 'categoryIds',
                    validators: {
                        onChange: ({value}) => {
                            if (!this.allCategories && (value?.length ?? 0) === 0) {
                                return 'error.required';
                            }
                            return undefined;
                        }
                    }
                }, (field) => {

                    const selectionChanged = (e: InputEvent) => {
                        const target = e.currentTarget as HTMLInputElement;
                        const checked = target.checked;
                        if (checked) {
                            field.handleChange([]);
                        }
                        this.allCategories = checked;
                    }

                    return html`
                        <ul class="list-group">
                            <li class="list-group-item">
                                <sl-switch help-text="Or select specific categories" class="block mt-2 mb-2"
                                           .checked=${this.allCategories}
                                           @sl-change=${(e: InputEvent) => selectionChanged(e)}>Apply to all ticket categories</sl-switch>
                                ${renderIf(() => !this.allCategories, () => html`
                                    ${this.renderCategories(event, field)}
                                `)}
                            </li>
                            ${renderIf(() => this.additionalItems.length > 0, () => this.renderAdditionalItemSelector())}
                        </ul>
                    `;
                })}
            `;
        }
        return html``;
    }

    private renderCategories(event: AlfioEvent, field: {state: {meta: any, value?: number[]}, handleChange: (values: any) => void}) {

        const selectionChanged = (e: InputEvent) => {
            const target = e.currentTarget as HTMLSelectElement;
            const values  = [];
            for(const option of target.options) {
                if (option.selected) {
                    values.push(Number.parseInt(option.value));
                }
            }
            field.handleChange(values);
        };

        return html`
            <sl-select label="Select Categories" required class=${classMap({ error: this.hasError(field.state.meta), 'no-mt': true })} multiple .value=${field.state.value?.join(' ') ?? ''} @sl-change=${selectionChanged}>
                ${event.ticketCategories.map((tc) => {
                    return html`<sl-option value="${tc.id}">${tc.name}</sl-option>`;
                })}
            </sl-select>
        `;
    }

    private renderAdditionalItemSelector() {
        const selectionChange = (e: InputEvent, field: { handleChange: (m: any) => void }) => {
            const additionalService = (e.currentTarget as HTMLInputElement).checked;
            field.handleChange(additionalService ? 'ADDITIONAL_SERVICE' : 'ATTENDEE');
            if (!additionalService) {
                this.#form.api.setFieldValue('additionalServiceId', undefined);
            }
        }

        const findTitle = (item: AdditionalItem) => item.title[0].value ?? '';
        return html`
            <li class="list-group-item">
                ${this.#form.field({
                    name: 'context'
                }, (field) => html`
                        <sl-switch help-text="Field appears when attendee selects an Additional Item" class="block mt-2 mb-2" .checked=${field.state.value === 'ADDITIONAL_SERVICE'} @sl-change=${(e: InputEvent) => selectionChange(e, field)}>
                            Show only for specific Additional Item
                        </sl-switch>
                    `)}

                ${renderIf(() => this.#form.api.state.values.context === 'ADDITIONAL_SERVICE', () => html`
                    ${this.#form.field({
                        name: 'additionalServiceId',
                        validators: {
                            onChange: ({value}) => {
                                if (value == null) {
                                    return 'error.required';
                                }
                                return undefined;
                            }
                        }
                    }, (field) => html`
                        <sl-select label="Additional Item" required class=${classMap({ error: this.hasError(field.state.meta), "no-mt block": true })} .value=${field.state.value} @sl-change=${(e: InputEvent) => notifyChange(e, field)}>
                            ${repeat(this.additionalItems, item => html`<sl-option .value=${item.id}>${findTitle(item)} ${item.id}</sl-option>`)}
                        </sl-select>
                    `)}
                `)}
            </li>

        `;
    }

    private renderConstraints() {
        const fieldType = this.#form.api.state.values.type;
        if (supportsMinMaxLength(fieldType)) {
            return html`
                <div class="field-constraints">

                    <div class="section-title">Field Options</div>

                    <div class="row">
                        <div class="col">
                            ${this.#form.field({name: 'minLength'},
                                (field) => html`
                                     <sl-input label=${fieldType === 'input:dateOfBirth' ? "Min age (years)" : "Min length"} .value=${field.state.value ?? nothing} @sl-change=${(e: InputEvent) => notifyChange(e, field)}></sl-input>
                                `)}
                        </div>
                        <div class="col">
                            ${this.#form.field({name: 'maxLength'},
                                (field) => html`
                                     <sl-input label=${fieldType === 'input:dateOfBirth' ? "Max age (years)" : "Max length"} .value=${field.state.value ?? nothing} @sl-change=${(e: InputEvent) => notifyChange(e, field)}></sl-input>
                                `)}
                        </div>
                    </div>

                </div>
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
            this.dialogTitle = request.field == null ? `Add attendee data field` : `Edit ${request.field.name} field`;
            this.editField = request.field != null;
            this.existingFieldName = request.field?.name;
            this.purchaseContext = request.purchaseContext;
            this.#form.api.update({
                defaultValues: this.buildDefaultValues(request.purchaseContext, request.ordinal, request.field, request.template),
                onSubmit: async (state) => {
                    await this.save(state.value);
                }
            });
            if (request.purchaseContext.type === 'event' && !this.editField) {
                this.additionalItems.push(...await AdditionalItemService.loadAll({eventId: (request.purchaseContext as AlfioEvent).id}));
            }
            this.displayForm = true;
            await this.dialog.show();
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
                               template?: NewAdditionalFieldFromTemplate): AdditionalFieldForm {

        if (field != null) {
            return {
                ...field,
                selectableOptions: (field.restrictedValues ?? []).map(v => {
                    const description: {[lang: string]: string} = {};
                    Object.entries(field.description).forEach(([lang, fd]) => {
                        const restrictedValues = fd.description.restrictedValues;
                        if (restrictedValues == null) {
                            description[lang] = '';
                        } else {
                            description[lang] = fd.description.restrictedValues![v] ?? '';
                        }
                    });
                    return {
                        fieldName: v,
                        description,
                        toBePersisted: false
                    };
                }),
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
            context: purchaseContext.type === 'subscription' ? 'SUBSCRIPTION' : 'ATTENDEE',
            selectableOptions: this.parseRestrictedValues(template)
        };
    }

    private parseRestrictedValues(template?: NewAdditionalFieldFromTemplate): SelectableOption[]  {
        if (template == null) {
            return [];
        }

        return (template.restrictedValues ?? []).map(v => {
            const description: {[lang: string]: string} = {};
            Object.entries(template.description).forEach(([lang, fd]) => {
                const restrictedValues = fd.restrictedValues;
                if (restrictedValues == null) {
                    description[lang] = '';
                } else {
                    description[lang] = fd.restrictedValues![v] ?? '';
                }
            });
            return {
                fieldName: v,
                description,
                toBePersisted: true
            };
        });
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


    private async save(additionalFieldForm: AdditionalFieldForm) {
        if (additionalFieldForm.id == null) {
            const descriptionRequest: { [locale: string]: DescriptionRequest } = {};
            const selectableOptions: SelectableOption[] = [];
            if (supportsRestrictedValues(additionalFieldForm.type) && additionalFieldForm.selectableOptions != null) {
                selectableOptions.push(...additionalFieldForm.selectableOptions);
            }
            const restrictedValues: RestrictedValueRequest[] = selectableOptions.map(option => ({
                value: option.fieldName,
                enabled: true
            }));
            Object.entries(additionalFieldForm.description).forEach(([key, value]) => {
                descriptionRequest[key] = {
                    label: value.description.label,
                    placeholder: value.description.placeholder ?? ''
                }
                if (selectableOptions.length > 0) {
                    descriptionRequest[key].restrictedValues = {};
                    selectableOptions.forEach(option => {
                        descriptionRequest[key].restrictedValues![option.fieldName] = option.description[key];
                    });
                }
            });
            const updateResult = await AdditionalFieldService.createNewField(this.purchaseContext!, {
                type: additionalFieldForm.type,
                name: additionalFieldForm.name,
                order: additionalFieldForm.order,
                categoryIds: additionalFieldForm.categoryIds ?? [],
                displayAtCheckIn: additionalFieldForm.displayAtCheckIn,
                forAdditionalService: this.additionalItems.find(item => item.id === additionalFieldForm.additionalServiceId),
                maxLength: additionalFieldForm.maxLength,
                minLength: additionalFieldForm.minLength,
                readOnly: !additionalFieldForm.editable,
                required: additionalFieldForm.required,
                description: descriptionRequest,
                restrictedValues: restrictedValues,
                userDefinedOrder: false
            });
            if (updateResult.success) {
                await this.close(true);
            } else {
                const nameError = updateResult.validationErrors.find(e => e.fieldName === 'name')?.code;

                if (nameError != null) {
                    this.#form.api.getFieldMeta('name')?.errors?.push(nameError);
                }

                let feedback: string;
                if (nameError === 'duplicate') {
                    feedback = `Field with internal name ${this.#form.api.getFieldValue('name')} already exists`;
                } else if (nameError == null) {
                    feedback = "Error while saving Additional Field";
                } else {
                    feedback = "Internal Name is not formatted correctly";
                }

                dispatchFeedback({
                    type: "danger",
                    message: feedback
                }, this);

            }
        } else {
            const additionalField = this.getAdditionalFieldFromForm(additionalFieldForm);
            const response = await AdditionalFieldService.saveField(this.purchaseContext!, additionalField);
            if (response.ok) {
                await this.close(true);
            } else {
                dispatchFeedback({
                    type: "danger",
                    message: "Unexpected error. Please retry."
                }, this);
            }
        }
    }


    private getAdditionalFieldFromForm(additionalFieldForm: AdditionalFieldForm) {
        const {selectableOptions, ...additionalField} = {...additionalFieldForm};
        additionalField.restrictedValues = selectableOptions.map(option => option.fieldName);
        selectableOptions.forEach(option => {
            Object.entries(option.description).forEach(([lang, text]) => {
                const restrictedValuesField: {[k:string] : string} = {};
                Object.entries(additionalField.description[lang].description.restrictedValues ?? {})
                    .filter(([k,_]) => additionalField.restrictedValues?.includes(k) ?? false)
                    .forEach(([k,v]) => {
                        restrictedValuesField[k] = v;
                    });
                restrictedValuesField[option.fieldName] = text;
                additionalField.description[lang].description.restrictedValues = restrictedValuesField;
            })
        });
        return {
            ...additionalField,
            name: this.existingFieldName ?? additionalField.name
        };
    }

    connectedCallback() {
        super.connectedCallback();
        this.unsubscribeFn = this.#form.api.store.subscribe(formState => {
            if (!this.displayForm) {
                return;
            }
            const locale = this.purchaseContext?.contentLanguages?.at(0)?.locale;
            if (locale == null) {
                return;
            }
            const currentValue = formState.currentVal.values;
            if (currentValue.description[locale] != null && currentValue.description[locale].description.label.length > 0) {
                this.preview = this.getAdditionalFieldFromForm(currentValue);
            }
        });
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        if (this.unsubscribeFn) {
            this.unsubscribeFn();
        }
    }

    updated() {
        this.shadowRoot?.querySelectorAll('sl-tab').forEach(tab => {
            const panel = this.shadowRoot?.querySelector(`sl-tab-panel[name="${tab.panel}"]`);
            tab.classList.toggle('has-error', panel?.querySelector('.error') !== null);
        });
    }

    private hasError(meta: any) {
        return meta.isTouched && meta.errors.length > 0;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-additional-field-edit': AdditionalFieldEdit
    }
}
