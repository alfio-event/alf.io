import {customElement, query, state} from "lit/decorators.js";
import {css, html, LitElement, nothing, TemplateResult} from "lit";
import {SlDialog, SlRequestCloseEvent, SlSelect} from "@shoelace-style/shoelace";
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
import {asNumber, asString, notifyChange, renderIf} from "../../service/helpers.ts";
import {repeat} from "lit/directives/repeat.js";
import {
    cardBgColors,
    dialog as dialogStyling,
    form,
    itemsList,
    listGroup,
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
import {when} from "lit/directives/when.js";
import {
    BoundField,
    fieldError,
    focusFirstError,
    FormMountController,
    INVALID_FORM_MESSAGE,
    requiredValue,
    setServerError,
    submitValidated,
    validatedInput,
    validateWith
} from "../../components/form-validation.ts";
import { t } from '../../service/i18n';

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

    @state()
    private saving = false;

    @state()
    private submitError: string | null = null;

    static readonly styles = [row, dialogStyling, form, textColors, cardBgColors, textAlign, itemsList, listGroup, css`
        .block {
            display: block;
        }

        #editDialog {
            --alfio-dialog-max-width: 75rem;
        }

        .section-header {
            font-weight: bold;
            color: var(--sl-color-neutral-900);
            font-size: var(--sl-font-size-large);
        }

        div.row section {
            margin-top: var(--sl-spacing-medium);
        }

        div.row section:nth-of-type(1) {
            margin-top: 0;
        }

        div.section-title {
            padding-top: var(--sl-spacing-medium);
            text-transform: uppercase;
            color: var(--sl-input-help-text-color);
            margin-bottom: var(--sl-spacing-2x-small);
            font-size: var(--sl-font-size-medium);
            font-weight: 600;
        }

        section div.section-subtitle {
            font-size: var(--sl-input-help-text-font-size-medium);
            color: var(--sl-input-help-text-color);
            margin-top: var(--sl-spacing-3x-small);
        }

        div.field-constraints {
            margin-top: var(--sl-spacing-medium);
            padding: 0 var(--sl-spacing-medium) var(--sl-spacing-medium);
            background: var(--sl-color-gray-50);
            border-radius: var(--sl-border-radius-large);
            border: 1px dashed var(--sl-color-gray-300);
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
        } as AdditionalFieldForm,
        onSubmit: async ({value}) => {
            await this.save(value);
        },
    });

    constructor() {
        super();
        new FormMountController(this, this.#form.api);
    }

    private get submitted(): boolean {
        return this.#form.api.state.submissionAttempts > 0;
    }


    protected render(): TemplateResult {
        return html`
            <sl-dialog
                id="editDialog"
                class="responsive-dialog"
                label=${this.dialogTitle}
                @sl-request-close=${this.preventAccidentalClose}>

                    ${renderIf(() => this.displayForm, () => this.renderForm())}

            </sl-dialog>
        `;
    }

    private renderForm(): TemplateResult {
        const contentLanguages = this.purchaseContext?.contentLanguages ?? [];
        return html`
            <form id="form" novalidate @submit=${this.submit}>
                <div class="row custom" style="--alfio-custom-row-cols-layout: 2fr 1fr">
                    <div class="col">
                        <section>
                            <div class="section-header">${t('admin.additional-field-dialog.definition')}</div>
                            <div class="section-subtitle">${t('admin.additional-field-dialog.definition.help')}</div>
                            ${(this.renderFieldTypeSelector())}
                            ${this.renderConstraints()}
                        </section>
                        <sl-divider></sl-divider>
                        <section>
                            <div class="section-header">${t('admin.additional-field-dialog.labels')}</div>
                            <div class="section-subtitle">${t('admin.additional-field-dialog.labels.help')}</div>
                            <sl-tab-group>
                                ${contentLanguages.map((d, index) => {
                                    return html`
                                            <sl-tab slot="nav" panel=${d.locale} ?active=${index === 0}>${d.displayLanguage}${renderIf(() => index === 0, () => html`<sl-badge variant="primary" pill class="ms-1">${t('admin.additional-field-dialog.primary')}</sl-badge>`)}</sl-tab>
                                            <sl-tab-panel name=${d.locale} ?active=${index === 0}>
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
                            <div class="section-header">${t('admin.additional-field-dialog.reference')}</div>
                            <div class="section-subtitle">${t('admin.additional-field-dialog.reference.help')}</div>
                            ${(this.renderNameField())}
                        </section>
                        <sl-divider></sl-divider>
                        <section>
                            <div class="section-header">${t('admin.additional-field-dialog.behavior')}</div>
                            <div class="section-subtitle">${t('admin.additional-field-dialog.behavior.help')}</div>
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
                                <div class="section-header">${t('admin.additional-field-dialog.visibility')}</div>
                                <div class="section-subtitle">${t('admin.additional-field-dialog.visibility.help')}</div>
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
                            <div class="section-title">${t('admin.additional-field-dialog.preview')}</div>

                            ${this.buildPreview(contentLanguages[0])}

                            <sl-alert variant="primary" open class="mt-3">
                                <sl-icon slot="icon" name="info-circle"></sl-icon>
                                <strong>${t('admin.additional-field-dialog.preview.help')}</strong>
                            </sl-alert>
                        </div>

                    </div>
                </div>

                ${when(this.submitError != null, () => html`
                    <sl-alert variant="danger" open closable class="mt-3" @sl-after-hide=${() => { this.submitError = null; }}>
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        ${this.submitError}
                    </sl-alert>
                `)}
                <!-- the footer stays inside the form, because Save submits it -->
                <div>
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => this.close(false)}>${t('admin.common.cancel')}</sl-button>
                        <div></div>
                        <sl-button variant="warning" type="submit" size="large" ?loading=${this.saving} ?disabled=${this.saving}>${t('admin.common.save')}</sl-button>
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
                    <strong>${t('admin.additional-field-dialog.preview.missing')}</strong><br />
                    ${t('admin.additional-field-dialog.preview.configure')}
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
            <sl-switch .value=${field.state.value} help-text=${t('admin.additional-field-dialog.editable.help')} checked=${field.state.value || nothing} @sl-change=${(e: InputEvent) => notifyChange(e, field, _ => (e.currentTarget as HTMLInputElement).checked)} class="block mt-2 mb-2">
                ${t('admin.additional-field-dialog.editable')}
            </sl-switch>
        `);
    }

    private renderDisplayAtCheckInField() {
        return this.#form.field({
            name: 'displayAtCheckIn'
        }, (field) => html`
            <sl-switch help-text=${t('admin.additional-field-dialog.check-in.help')} checked=${field.state.value || nothing} @sl-change=${(e: InputEvent) => notifyChange(e, field, _ => (e.currentTarget as HTMLInputElement).checked)} class="block mt-2 mb-2">
                ${t('admin.additional-field-dialog.check-in')}
            </sl-switch>
        `);
    }

    private renderRequiredCheckbox() {
        return this.#form.field({
            name: 'required'
        }, (field) => html`
            <sl-switch size="medium" .value=${field.state.value} help-text=${t('admin.additional-field-dialog.required.help')} checked=${field.state.value || nothing} @sl-change=${(e: InputEvent) => notifyChange(e, field, _ => (e.currentTarget as HTMLInputElement).checked)} class="block mt-2 mb-2">
                ${t('admin.additional-field-dialog.required')}
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
                <sl-select label=${t('admin.additional-field-dialog.type')} required hoist .value=${field.state.value} @sl-change=${(e: InputEvent) => selectionChangeHandler(e)}>
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
            validators: validateWith(value => this.validateFieldName(value)),
        }, field => validatedInput(field, this.submitted, {
            label: t('admin.additional-field-dialog.name'),
            placeholder: t('admin.common.name'),
            maxlength: 64,
            helpText: t('admin.additional-field-dialog.name.help'),
            readonly: this.editField,
        }));
    }

    // the name of an existing field can't be changed
    private validateFieldName(value: unknown): string | undefined {
        if (this.editField) {
            return undefined;
        }
        return validateName(value);
    }

    private renderLanguageFields(cl: ContentLanguage, primary: boolean) {
        // the first label suggests the internal name, until the user edits it
        const generateName = (value: string) => {
            if (primary && !this.editField) {
                const currentFieldName = this.#form.api.getFieldValue('name');
                const currentFieldNameUntouched = this.#form.api.getFieldMeta('name')!.isPristine;
                if (currentFieldNameUntouched || currentFieldName?.length === 0) {
                    let generatedValue = value.toLowerCase().replaceAll(/\W/g,"_").substring(0, 64);
                    if (generatedValue.endsWith("_")) {
                        generatedValue = generatedValue.substring(0, generatedValue.length - 1);
                    }
                    this.#form.api.setFieldValue('name', generatedValue, {dontUpdateMeta: true});
                }
            }
        };

        return html`
            ${this.#form.field({
                name: `description.${cl.locale}.description.label`,
                validators: validateWith(value => requiredValue(value)),
            }, field => validatedInput(withChangeListener(field, generateName), this.submitted, {
                label: t('admin.additional-item.title'),
                placeholder: t('admin.additional-field-dialog.title.placeholder'),
            }))}
            ${renderIf(() => supportsPlaceholder(this.#form.api.state.values.type),
                () => html`
                    ${this.#form.field({name: `description.${cl.locale}.description.placeholder`}, field => validatedInput(field as BoundField<string>, this.submitted, {
                        label: t('admin.additional-field-dialog.placeholder'),
                        placeholder: t('admin.additional-field-dialog.placeholder.placeholder'),
                        optional: true,
                    }))}
                `)}
            `;
    }

    private renderValues() {
        if (!supportsRestrictedValues(this.#form.api.state.values.type)) {
            return nothing;
        }

        return html`
            <section>
                <div class="section-header">${t('admin.additional-field-dialog.options')}</div>
                <div class="section-subtitle">${t('admin.additional-field-dialog.options.help')}</div>
                <div class="field-constraints">
                    ${this.#form.field({
                        name: 'selectableOptions',
                    }, (selectableOptionsField) => {
                        const contentLanguages = this.purchaseContext?.contentLanguages ?? [];
                        const selectableOptions = selectableOptionsField.state.value ?? [];
                        return html`
                            ${repeat(selectableOptions, (_, index) => index, (option, index) => {
                                return this.renderSelectableOptionCard(index,
                                    selectableOptions.length - 1,
                                    contentLanguages,
                                    () => selectableOptionsField.removeValue(index),
                                    (newIndex) => selectableOptionsField.moveValue(index, newIndex),
                                    option.toBePersisted
                                );
                            })}
                            <sl-button class="mt-2" variant="text" @click=${() => {selectableOptionsField.pushValue({fieldName: '', description: {}, toBePersisted: true})}}>
                                <sl-icon slot="prefix" name="plus-circle"></sl-icon>
                                ${t('admin.poll-edit.add-option')}
                            </sl-button>
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
                                       moveField: (newIndex: number) => void,
                                       toBePersisted: boolean) {
        // the first description suggests the value, until the user edits it
        const generateValue = (value: string, languageIdx: number) => {
            if (languageIdx === 0 && (!this.editField || toBePersisted)) {
                const currentFieldName = this.#form.api.getFieldValue(`selectableOptions[${optionIndex}].fieldName`);
                const currentFieldNameUntouched = this.#form.api.getFieldMeta(`selectableOptions[${optionIndex}].fieldName`)?.isPristine ?? true;
                if (currentFieldNameUntouched || currentFieldName?.length === 0) {
                    this.#form.api.setFieldValue(`selectableOptions[${optionIndex}].fieldName`, value.toLowerCase().replaceAll(/\W/g,"_").substring(0, 64), {dontUpdateMeta: true});
                }
            }
        };
        return html`
            <sl-card class="item bg-default mt-3">
                <div slot="header">
                    <div class="col option-title">${t('admin.additional-field-dialog.option', optionIndex + 1)}</div>
                    <div class="col">
                        ${renderIf(() => this.#form.api.getFieldValue(`selectableOptions[${optionIndex}].toBePersisted`),
                            () => html`<sl-button variant="danger" size="small" @click=${() => removeField()}><sl-icon slot="prefix" name="trash"></sl-icon>${t('admin.common.delete')}</sl-button>`)}
                    </div>
                </div>
                <div slot="footer">
                    ${renderIf(() => optionIndex > 0,
                        () => html`
                            <sl-button type="button" variant="default" size="small" @click=${() => moveField(optionIndex - 1)}>
                                <sl-icon name="arrow-up" slot="prefix"></sl-icon>
                                ${t('admin.common.move-up')}
                            </sl-button>
                        `)}
                    ${renderIf(() => optionIndex < maxIndex,
                        () => html`
                        <sl-button type="button" variant="default" size="small" @click=${() => moveField(optionIndex + 1)}>
                            <sl-icon name="arrow-down" slot="prefix"></sl-icon>
                            ${t('admin.common.move-down')}
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
                                        validators: validateWith(value => this.ifSelectableOptions(() => requiredValue(value))),
                                    }, field => validatedInput(withChangeListener(field as BoundField<string>, value => generateValue(value, languageIndex)), this.submitted, {
                                        label: t('admin.additional-field-dialog.option.description', d.displayLanguage),
                                        placeholder: t('admin.additional-field-dialog.option.description.placeholder', d.displayLanguage),
                                    }))}
                                </div>
                            </div>
                        `;
                    })}
                    <sl-divider></sl-divider>
                    <div class="row custom">
                        <div class="col text-end selectable-option-header">${t('admin.additional-field-dialog.value')}</div>
                        <div class="col">
                            ${this.#form.field({
                                name: `selectableOptions[${optionIndex}].fieldName`,
                                validators: validateWith(value => this.ifSelectableOptions(() => this.validateOptionValue(value, optionIndex))),
                            }, field => validatedInput(field, this.submitted, {
                                label: t('admin.additional-field-dialog.value'),
                                placeholder: t('admin.additional-field-dialog.value.placeholder'),
                                maxlength: 64,
                                helpText: t('admin.additional-field-dialog.value.help'),
                                readonly: this.editField && !toBePersisted,
                            }))}
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
                    validators: validateWith(value => this.validateCategories(value)),
                }, (field) => {

                    const selectionChanged = (e: InputEvent) => {
                        const target = e.currentTarget as HTMLInputElement;
                        const checked = target.checked;
                        this.allCategories = checked;
                        if (checked) {
                            field.handleChange([]);
                        }
                    }

                    return html`
                        <ul class="list-group">
                            <li class="list-group-item">
                                <sl-switch help-text=${t('admin.additional-field-dialog.all-categories.help')} class="block mt-2 mb-2"
                                           .checked=${this.allCategories}
                                           @sl-change=${(e: InputEvent) => selectionChanged(e)}>${t('admin.promo-code.all-categories.switch')}</sl-switch>
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

    private renderCategories(event: AlfioEvent, field: BoundField<number[] | undefined>) {
        const error = fieldError(field.state.meta, this.submitted);

        const selectionChanged = (e: InputEvent) => {
            const target = e.currentTarget as SlSelect;
            const values  = [];
            for(const option of target.selectedOptions) {
                values.push(Number.parseInt(option.value));
            }
            field.handleChange(values);
        };

        return html`
            <sl-select label=${t('admin.categories.title')} required hoist value=${field.state.value?.join(' ') ?? ''} class=${classMap({ error: error != null, 'no-mt': true })} help-text=${error ?? ''} multiple @sl-change=${selectionChanged} @sl-blur=${() => field.handleBlur()}>
                ${event.ticketCategories.map((tc) => {
                    return html`<sl-option value="${tc.id}">${tc.name}</sl-option>`;
                })}
            </sl-select>
        `;
    }

    private renderAdditionalItemSelector() {
        const selectionChange = (e: InputEvent, field: { handleChange: (m: any) => void }) => {
            const additionalService = (e.currentTarget as HTMLInputElement).checked;
            if (additionalService) {
                field.handleChange('ADDITIONAL_SERVICE');
            } else {
                field.handleChange('ATTENDEE');
                this.#form.api.setFieldValue('additionalServiceId', undefined);
            }
        }

        const findTitle = (item: AdditionalItem) => item.title[0].value ?? '';
        return html`
            <li class="list-group-item">
                ${this.#form.field({
                    name: 'context'
                }, (field) => html`
                        <sl-switch help-text=${t('admin.additional-field-dialog.option-only.help')} class="block mt-2 mb-2" .checked=${field.state.value === 'ADDITIONAL_SERVICE'} @sl-change=${(e: InputEvent) => selectionChange(e, field)}>
                            ${t('admin.additional-field-dialog.option-only')}
                        </sl-switch>
                    `)}

                ${renderIf(() => this.#form.api.state.values.context === 'ADDITIONAL_SERVICE', () => html`
                    ${this.#form.field({
                        name: 'additionalServiceId',
                        validators: {
                            ...validateWith(value => this.validateAdditionalService(value)),
                            onChangeListenTo: ['context'],
                        },
                    }, (field) => {
                        const error = fieldError(field.state.meta, this.submitted);
                        return html`
                            <sl-select label=${t('admin.additional-field-dialog.additional-option')} required hoist class=${classMap({ error: error != null, "no-mt block": true })}
                                       help-text=${error ?? ''}
                                       .value=${asString(field.state.value) ?? ''}
                                       @sl-change=${(e: InputEvent) => notifyChange(e, field, asNumber)}
                                       @sl-blur=${() => field.handleBlur()}>
                                ${repeat(this.additionalItems, item => html`<sl-option value=${String(item.id)}>${findTitle(item)}</sl-option>`)}
                            </sl-select>
                        `;
                    })}
                `)}
            </li>

        `;
    }

    private renderConstraints() {
        const fieldType = this.#form.api.state.values.type;
        if (supportsMinMaxLength(fieldType)) {
            return html`
                <div class="field-constraints">

                    <div class="section-title">${t('admin.additional-field-dialog.field-options')}</div>

                    <div class="row">
                        <div class="col">
                            ${this.#form.field({
                                    name: 'minLength',
                                    validators: {
                                        ...validateWith(value => this.ifMinMaxLength(() => optionalCount(value))),
                                        onChangeListenTo: ['type'],
                                    },
                                },
                                field => validatedInput(field as unknown as BoundField<string>, this.submitted, {label: lengthLabel(fieldType, 'min'), optional: true}))}
                        </div>
                        <div class="col">
                            ${this.#form.field({
                                    name: 'maxLength',
                                    validators: {
                                        ...validateWith(value => this.ifMinMaxLength(() => this.validateMaxLength(value))),
                                        onChangeListenTo: ['type', 'minLength'],
                                    },
                                },
                                field => validatedInput(field as unknown as BoundField<string>, this.submitted, {label: lengthLabel(fieldType, 'max'), optional: true}))}
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
            if (request.field == null) {
                this.dialogTitle = t('admin.additional-field-dialog.new');
            } else {
                this.dialogTitle = t('admin.additional-field-dialog.edit', request.field.name);
            }
            this.editField = request.field != null;
            this.existingFieldName = request.field?.name;
            this.purchaseContext = request.purchaseContext;
            this.submitError = null;
            this.preview = undefined;
            const defaultValues = this.buildDefaultValues(request.purchaseContext, request.ordinal, request.field, request.template);
            this.allCategories = (defaultValues.categoryIds?.length ?? 0) === 0;
            this.#form.api.reset(defaultValues);
            this.additionalItems = [];
            if (request.purchaseContext.type === 'event' && !this.editField) {
                this.additionalItems = await AdditionalItemService.loadAll({eventId: (request.purchaseContext as AlfioEvent).id});
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

        let context: AdditionalFieldForm['context'] = 'ATTENDEE';
        if (purchaseContext.type === 'subscription') {
            context = 'SUBSCRIPTION';
        }
        return {
            name: template?.name ?? '',
            order: template?.order ?? ordinal,
            type: template?.type ?? 'input:text',
            description: this.parseDescriptions(purchaseContext, template),
            editable: true,
            displayAtCheckIn: false,
            required: false,
            context,
            selectableOptions: this.parseRestrictedValues(purchaseContext, template)
        };
    }

    private parseRestrictedValues(purchaseContext: PurchaseContext,
                                  template?: NewAdditionalFieldFromTemplate): SelectableOption[]  {
        if (template == null) {
            return [];
        }

        return (template.restrictedValues ?? []).map(v => {
            const description: {[lang: string]: string} = {};
            purchaseContext.contentLanguages.forEach(({locale}) => {
                const fd = template.description[locale];
                description[locale] = fd?.restrictedValues?.[v] ?? '';
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


    private submit = async (e: Event): Promise<void> => {
        e.preventDefault();
        e.stopImmediatePropagation();
        if (this.saving) {
            return;
        }
        this.submitError = null;
        if (!await submitValidated(this.#form.api)) {
            this.submitError = INVALID_FORM_MESSAGE;
            await this.updateComplete;
            focusFirstError(this.renderRoot);
        }
    };

    private ifSelectableOptions(validate: () => string | undefined): string | undefined {
        if (!supportsRestrictedValues(this.#form.api.state.values.type)) {
            return undefined;
        }
        return validate();
    }

    private ifMinMaxLength(validate: () => string | undefined): string | undefined {
        if (!supportsMinMaxLength(this.#form.api.state.values.type)) {
            return undefined;
        }
        return validate();
    }

    // the values of the existing options can't be changed
    private validateOptionValue(value: unknown, optionIndex: number): string | undefined {
        if (this.editField && !this.#form.api.getFieldValue(`selectableOptions[${optionIndex}].toBePersisted`)) {
            return undefined;
        }
        const invalid = validateName(value);
        if (invalid != null) {
            return invalid;
        }
        const duplicate = this.#form.api.state.values.selectableOptions
            .some((option, index) => index !== optionIndex && option.fieldName === String(value));
        if (duplicate) {
            return t('admin.additional-field-dialog.error.duplicate-value');
        }
        return undefined;
    }

    private validateMaxLength(value: unknown): string | undefined {
        const invalid = optionalCount(value);
        if (invalid != null) {
            return invalid;
        }
        const min = this.#form.api.state.values.minLength;
        if (isBlank(value) || isBlank(min) || optionalCount(min) != null) {
            return undefined;
        }
        if (Number(value) < Number(min)) {
            return t('admin.additional-field-dialog.error.max');
        }
        return undefined;
    }

    private validateCategories(value: unknown): string | undefined {
        if (this.purchaseContext?.type !== 'event' || this.allCategories) {
            return undefined;
        }
        if (!Array.isArray(value) || value.length === 0) {
            return t('admin.promo-code.error.categories');
        }
        return undefined;
    }

    private validateAdditionalService(value: unknown): string | undefined {
        if (this.#form.api.state.values.context !== 'ADDITIONAL_SERVICE') {
            return undefined;
        }
        if (value == null || Number.isNaN(value)) {
            return t('admin.additional-field-dialog.error.option');
        }
        return undefined;
    }

    private async save(additionalFieldForm: AdditionalFieldForm) {
        this.saving = true;
        try {
            if (additionalFieldForm.id == null) {
                await this.createNew(additionalFieldForm);
            } else {
                await this.updateExisting(additionalFieldForm);
            }
        } finally {
            this.saving = false;
        }
    }


    private async updateExisting(additionalFieldForm: AdditionalFieldForm) {
        const additionalField = this.getAdditionalFieldFromForm(additionalFieldForm);
        const response = await AdditionalFieldService.saveField(this.purchaseContext!, additionalField);
        if (response.ok) {
            await this.close(true);
        } else {
            this.submitError = t('admin.additional-item.save-failed');
        }
    }

    private async createNew(additionalFieldForm: AdditionalFieldForm) {
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
            if (nameError == null) {
                this.submitError = t('admin.additional-item.save-failed');
                return;
            }
            let message = t('admin.additional-field-dialog.error.name');
            if (nameError === 'duplicate') {
                message = t('admin.additional-field-dialog.error.name-taken');
            }
            setServerError(this.#form.api, 'name', message);
            this.submitError = INVALID_FORM_MESSAGE;
            await this.updateComplete;
            focusFirstError(this.renderRoot);
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
    }}

function validateName(value: unknown): string | undefined {
    const missing = requiredValue(value);
    if (missing != null) {
        return missing;
    }
    if (!/^[a-z0-9][a-z0-9_]*$/i.test(String(value))) {
        return t('admin.additional-field-dialog.error.name');
    }
    return undefined;
}

function isBlank(value: unknown): boolean {
    return value == null || String(value).trim() === '';
}

// empty, or a whole number not lower than zero
function optionalCount(value: unknown): string | undefined {
    if (isBlank(value)) {
        return undefined;
    }
    const count = Number(String(value).trim());
    if (!Number.isInteger(count) || count < 0) {
        return t('admin.validation.integer-or-empty');
    }
    return undefined;
}

/**
 * The field, with an action run on every change before the value is stored (e.g. to suggest the value of another field)
 */
function withChangeListener(field: BoundField<string>, onChange: (value: string) => void): BoundField<string> {
    return {
        state: field.state,
        handleBlur: () => field.handleBlur(),
        handleChange: (value: string) => {
            onChange(value);
            field.handleChange(value);
        },
    };
}

// a date of birth is limited by age, the other fields by length
function lengthLabel(fieldType: AdditionalFieldType, bound: 'min' | 'max'): string {
    if (fieldType === 'input:dateOfBirth') {
        if (bound === 'min') {
            return t('admin.additional-field-dialog.min-age');
        }
        return t('admin.additional-field-dialog.max-age');
    }
    if (bound === 'min') {
        return t('admin.additional-fields.min-length');
    }
    return t('admin.additional-fields.max-length');
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-additional-field-edit': AdditionalFieldEdit
    }
}
