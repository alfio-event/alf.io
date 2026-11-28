import {css, html, LitElement, nothing, TemplateResult} from "lit";
import {customElement, query, state} from "lit/decorators.js";
import {
    AdditionalItem,
    AdditionalItemTaxType,
    AdditionalItemType,
    isMandatoryPercentage,
    SupplementPolicy,
    supplementPolicyDescriptions,
    taxTypeDescriptions
} from "../../model/additional-item.ts";
import {SlDialog, SlRequestCloseEvent, SlSelect, SlTextarea} from "@shoelace-style/shoelace";
import {AlfioEvent} from "../../model/event.ts";
import {repeat} from "lit/directives/repeat.js";
import {when} from "lit/directives/when.js";
import {TanStackFormController} from "@tanstack/lit-form";
import {dialog, form, modernLayout, row, textColors} from "../../styles.ts";
import {extractDateTime, toDateTimeModification} from "../../service/helpers.ts";
import {classMap} from "lit/directives/class-map.js";
import {AdditionalItemService} from "../../service/additional-item.ts";
import {ContentLanguage} from "../../model/purchase-context.ts";
import {
    fieldError,
    focusFirstError,
    FormMountController,
    INVALID_FORM_MESSAGE,
    requiredValue,
    setServerError,
    submitValidated,
    TextField,
    validatedInput,
    ValidatedInputOptions,
    validateWith
} from "../../components/form-validation.ts";
import { t } from '../../service/i18n';

@customElement('alfio-additional-item-edit')
export class AdditionalItemEdit extends LitElement {

    static styles = [modernLayout, row, dialog, form, textColors, css`
        #editDialog {
            --alfio-dialog-max-width: 60rem;
        }

        .language-heading {
            margin: 0;
            padding-bottom: var(--sl-spacing-2x-small);
            border-bottom: 1px solid var(--sl-color-gray-200);
            font-size: var(--sl-font-size-medium);
            color: var(--sl-color-gray-700);
        }
    `];

    private editedItem: AdditionalItem | null = null;
    private supportedLanguages: ContentLanguage[] = [];
    private event: AlfioEvent | null = null;
    private type: AdditionalItemType | null = null;
    private ordinal = 0;

    @query("sl-dialog#editDialog")
    dialog?: SlDialog;

    @state()
    private saving = false;

    @state()
    private submitError: string | null = null;

    #form = new TanStackFormController(this, {
        defaultValues: {
            descriptions: [] as DescriptionForm[],
            availabilityAndPrices: {} as AvailabilityAndPricesForm,
        } as FormData,
        onSubmit: async ({value}) => {
            await this.save(value);
        },
    });

    constructor() {
        super();
        new FormMountController(this, this.#form.api);
    }

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


        let availabilityAndPrices = currentState.values.availabilityAndPrices;
        if (this.event != null) {
            availabilityAndPrices = this.buildAvailabilityAndPricesFromEvent(this.event);
        }
        return {
            availabilityAndPrices,
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
            expiration: extractDateTime(event.begin),
            maxQtyPerOrder: null,
            availableQuantity: null
        }
    }

    private buildAvailabilityAndPricesFromItem(item: AdditionalItem): AvailabilityAndPricesForm {
        // -1 means "unlimited"
        let availableItems: number | null = null;
        if (item.availableItems !== -1) {
            availableItems = item.availableItems;
        }
        let availableQuantity: number | null = null;
        if (item.availableQuantity !== -1) {
            availableQuantity = item.availableQuantity ?? null;
        }
        let maxQtyPerOrder: number | null = null;
        if (item.maxQtyPerOrder !== -1) {
            maxQtyPerOrder = item.maxQtyPerOrder ?? null;
        }
        return {
            availableItems,
            minPrice: item.minPrice,
            maxPrice: item.maxPrice,
            price: item.price,
            fixPrice: item.fixPrice,
            vat: item.vat,
            vatType: item.vatType,
            supplementPolicy: item.supplementPolicy,
            inception: `${item.inception.date}T${item.inception.time}`,
            expiration: `${item.expiration.date}T${item.expiration.time}`,
            maxQtyPerOrder,
            availableQuantity
        }
    }

    private get dialogTitle(): string {
        const editing = this.editedItem != null;
        if (this.type === 'SUPPLEMENT') {
            if (editing) {
                return t('admin.additional-item.edit-option');
            }
            return t('admin.additional-item.new-option');
        }
        if (editing) {
            return t('admin.additional-item.edit-donation');
        }
        return t('admin.additional-item.new-donation');
    }

    private get submitted(): boolean {
        return this.#form.api.state.submissionAttempts > 0;
    }

    protected render(): TemplateResult {
        return html`
            <sl-dialog label=${this.dialogTitle}
                       id="editDialog"
                       class="responsive-dialog"
                       @sl-request-close=${this.preventAccidentalClose}>

                ${this.renderForm()}

            </sl-dialog>
        `;
    }

    private renderForm(): TemplateResult {
        const values = this.#form.api.state.values;
        return html`
            <form id="form" class="dialog-form form-stack" novalidate @submit=${this.submit}>
                <div class="section-card">
                    <div class="dialog-section-header"><sl-icon name="card-text"></sl-icon>${t('admin.additional-item.descriptions')}</div>
                    <div class="section-body row">
                        ${this.renderDescriptions(values.descriptions ?? [])}
                    </div>
                </div>
                <div class="section-card">
                    <div class="dialog-section-header"><sl-icon name="calendar-event"></sl-icon>${t('admin.additional-item.availability')}</div>
                    <div class="section-body">
                        ${this.renderAvailabilityAndPrices(values.availabilityAndPrices)}
                    </div>
                </div>
                ${when(this.submitError != null, () => html`
                    <sl-alert variant="danger" open closable @sl-after-hide=${() => { this.submitError = null; }}>
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

    private renderDescriptions(descriptions: DescriptionForm[]): TemplateResult {
        return html`${repeat(descriptions, (_, index) => index, (description, index) => html`
            <div class="col">
                <h4 class="language-heading">${this.supportedLanguages?.find(cl => cl.locale === description.locale)?.displayLanguage}</h4>
                ${this.#form.field({name: `descriptions[${index}].title`, validators: validateWith(value => requiredValue(value))},
                    field => validatedInput(field, this.submitted, {label: t('admin.additional-item.title')}))}
                ${this.#form.field({name: `descriptions[${index}].description`, validators: validateWith(value => requiredValue(value))},
                    field => {
                        const error = fieldError(field.state.meta, this.submitted);
                        return html`
                            <sl-textarea label=${t('admin.common.description')} required rows="2" class=${classMap({ error: error != null })}
                                         .value=${field.state.value}
                                         @sl-input=${(e: Event) => field.handleChange((e.target as SlTextarea).value)}
                                         @sl-blur=${() => field.handleBlur()}>
                                <div slot="help-text">
                                    ${when(error != null, () => html`<div>${error}</div>`)}
                                    <alfio-display-commonmark-preview data-button-text=${t('admin.additional-item.preview')} .text=${field.state.value}></alfio-display-commonmark-preview>
                                </div>
                            </sl-textarea>
                        `;
                    })}
            </div>
        `)}`;
    }

    private renderAvailabilityAndPrices(formValue: AvailabilityAndPricesForm): TemplateResult {
        return html`
            ${when(this.type === 'SUPPLEMENT', () => html`
                ${this.#form.field({name: 'availabilityAndPrices.supplementPolicy'}, field => html`
                    <sl-select required hoist label=${t('admin.additional-item.policy')} .value=${field.state.value}
                               @sl-change=${(e: Event) => field.handleChange((e.target as SlSelect).value as SupplementPolicy)}>
                        ${repeat(Object.keys(supplementPolicyDescriptions), k => k, (k) => html`
                            <sl-option value=${k}>${supplementPolicyDescriptions[k]}</sl-option>
                        `)}
                    </sl-select>
                `)}
            `)}
            <div class="row">
                <div class="col">
                    ${this.#form.field({name: 'availabilityAndPrices.inception', validators: validateWith(value => requiredValue(value))},
                        field => validatedInput(field, this.submitted, {label: t('admin.category-dialog.valid-from'), type: 'datetime-local'}))}
                </div>
                <div class="col">
                    ${this.#form.field({
                            name: 'availabilityAndPrices.expiration',
                            validators: {
                                ...validateWith(value => this.validateExpiration(value)),
                                onChangeListenTo: ['availabilityAndPrices.inception'],
                            },
                        },
                        field => validatedInput(field, this.submitted, {label: t('admin.additional-item.valid-to'), type: 'datetime-local'}))}
                </div>
            </div>
            ${when(this.type === 'DONATION', () => html`
                ${this.#form.field({name: 'availabilityAndPrices.fixPrice'}, field => html`
                    <sl-select required hoist label=${t('admin.additional-item.price-policy')} .value=${String(field.state.value)}
                               @sl-change=${(e: Event) => field.handleChange((e.target as SlSelect).value === 'true')}>
                        <sl-option value="true">${t('admin.additional-item.price-policy.fixed')}</sl-option>
                        <sl-option value="false">${t('admin.additional-items.price.user-defined')}</sl-option>
                    </sl-select>
                `)}
                ${when(formValue.fixPrice, () => this.renderMaxQuantityPerOrder(formValue))}
            `)}
            <div class="row" style="--alfio-row-cols: ${priceColumns(formValue)}">
                ${when(formValue.fixPrice, () => html`
                    <div class="col">
                        ${this.#form.field({
                                name: 'availabilityAndPrices.price',
                                validators: {
                                    ...validateWith(value => this.validatePrice(value)),
                                    onChangeListenTo: ['availabilityAndPrices.fixPrice'],
                                },
                            },
                            field => this.numberInput(field, {label: t('admin.category-dialog.price'), suffix: this.priceUnit(formValue)}))}
                    </div>
                `)}
                <div class="col">
                    ${this.#form.field({name: 'availabilityAndPrices.vatType'}, field => html`
                        <sl-select required hoist label=${t('admin.additional-item.tax-policy')} .value=${field.state.value}
                                   @sl-change=${(e: Event) => field.handleChange((e.target as SlSelect).value as AdditionalItemTaxType)}>
                            ${repeat(Object.keys(taxTypeDescriptions), k => k, (k) => html`
                                <sl-option value=${k}>${taxTypeDescriptions[k]}</sl-option>
                            `)}
                        </sl-select>
                    `)}
                </div>
            </div>

            ${when(this.type === 'SUPPLEMENT', () => this.renderPolicyFields(formValue))}
        `;
    }

    private renderPolicyFields(formValue: AvailabilityAndPricesForm): TemplateResult | typeof nothing {
        switch (formValue.supplementPolicy) {
            case "MANDATORY_ONE_FOR_TICKET":
                return nothing;
            case "MANDATORY_PERCENTAGE_RESERVATION":
            case "MANDATORY_PERCENTAGE_FOR_TICKET":
                return html`
                    <div class="row">
                        <div class="col">
                            ${this.#form.field({
                                    name: 'availabilityAndPrices.minPrice',
                                    validators: {
                                        ...validateWith(value => this.validatePriceLimit(value)),
                                        onChangeListenTo: ['availabilityAndPrices.supplementPolicy'],
                                    },
                                },
                                field => this.numberInput(field, {label: t('admin.additional-item.min-price'), suffix: this.event?.currency, optional: true}))}
                        </div>
                        <div class="col">
                            ${this.#form.field({
                                    name: 'availabilityAndPrices.maxPrice',
                                    validators: {
                                        ...validateWith(value => this.validateMaxPrice(value)),
                                        onChangeListenTo: ['availabilityAndPrices.supplementPolicy', 'availabilityAndPrices.minPrice'],
                                    },
                                },
                                field => this.numberInput(field, {label: t('admin.additional-item.max-price'), suffix: this.event?.currency, optional: true}))}
                        </div>
                    </div>
                `;
            case "OPTIONAL_MAX_AMOUNT_PER_RESERVATION":
            case "OPTIONAL_MAX_AMOUNT_PER_TICKET":
                return html`
                    <div class=${classMap({ row: this.event?.supportsAdditionalItemsQuantity ?? false })}>
                        <div class="col">
                            ${this.renderMaxQuantityPerOrder(formValue)}
                        </div>
                        ${this.renderAvailableQuantity()}
                    </div>
                `;
            default:
                return this.renderAvailableQuantity();
        }
    }

    private renderMaxQuantityPerOrder(formValue: AvailabilityAndPricesForm): TemplateResult {
        return html`
            ${this.#form.field({
                    name: 'availabilityAndPrices.maxQtyPerOrder',
                    validators: {
                        ...validateWith(value => this.validateMaxQuantityPerOrder(value)),
                        onChangeListenTo: ['availabilityAndPrices.supplementPolicy', 'availabilityAndPrices.fixPrice'],
                    },
                },
                field => this.numberInput(field, {label: this.maxQuantityLabel(formValue), optional: true, helpText: t('admin.additional-item.no-limit-help')}))}
        `;
    }

    private renderAvailableQuantity(): TemplateResult | typeof nothing {
        if (!this.event?.supportsAdditionalItemsQuantity) {
            return nothing;
        }
        return html`
            <div class="col">
                ${this.#form.field({
                        name: 'availabilityAndPrices.availableQuantity',
                        validators: validateWith(value => optionalCount(value)),
                    },
                    field => this.numberInput(field, {label: t('admin.additional-item.total-quantity'), optional: true, helpText: t('admin.additional-item.no-limit-help')}))}
            </div>
        `;
    }

    /**
     * A number field, edited as text like every sl-input
     */
    private numberInput(field: unknown, options: ValidatedInputOptions): TemplateResult {
        return validatedInput(field as TextField, this.submitted, options);
    }

    private maxQuantityLabel(formValue: AvailabilityAndPricesForm): string {
        if (this.type === 'DONATION' || formValue.supplementPolicy === 'OPTIONAL_MAX_AMOUNT_PER_RESERVATION') {
            return t('admin.additional-items.max-per-order');
        }
        return t('admin.additional-items.max-per-ticket');
    }

    private priceUnit(formValue: AvailabilityAndPricesForm): string | undefined {
        if (isMandatoryPercentage(formValue.supplementPolicy)) {
            return '%';
        }
        return this.event?.currency;
    }

    private get availabilityAndPrices(): AvailabilityAndPricesForm {
        return this.#form.api.state.values.availabilityAndPrices;
    }

    private validateExpiration(value: unknown): string | undefined {
        const missing = requiredValue(value);
        if (missing != null) {
            return missing;
        }
        const inception = extractDateTime(this.availabilityAndPrices.inception);
        // datetime-local values (yyyy-MM-ddTHH:mm) sort as strings
        if (inception !== '' && extractDateTime(String(value)) < inception) {
            return t('admin.promo-code.error.end');
        }
        return undefined;
    }

    private validatePrice(value: unknown): string | undefined {
        if (!this.availabilityAndPrices.fixPrice) {
            return undefined;
        }
        const missing = requiredValue(value);
        if (missing != null) {
            return missing;
        }
        return optionalAmount(value);
    }

    private validatePriceLimit(value: unknown): string | undefined {
        if (this.type !== 'SUPPLEMENT' || !isMandatoryPercentage(this.availabilityAndPrices.supplementPolicy)) {
            return undefined;
        }
        return optionalAmount(value);
    }

    private validateMaxPrice(value: unknown): string | undefined {
        const invalid = this.validatePriceLimit(value);
        if (invalid != null || this.type !== 'SUPPLEMENT' || !isMandatoryPercentage(this.availabilityAndPrices.supplementPolicy)) {
            return invalid;
        }
        const minPrice = this.availabilityAndPrices.minPrice;
        if (isBlank(value) || isBlank(minPrice) || optionalAmount(minPrice) != null) {
            return undefined;
        }
        if (Number(value) < Number(minPrice)) {
            return t('admin.additional-item.error.max-price');
        }
        return undefined;
    }

    // the quantity per order applies to a donation with a fixed price, and to the additional options with a maximum amount
    private validateMaxQuantityPerOrder(value: unknown): string | undefined {
        const values = this.availabilityAndPrices;
        let applies = values.supplementPolicy === 'OPTIONAL_MAX_AMOUNT_PER_RESERVATION'
            || values.supplementPolicy === 'OPTIONAL_MAX_AMOUNT_PER_TICKET';
        if (this.type === 'DONATION') {
            applies = values.fixPrice;
        }
        if (!applies || isBlank(value)) {
            return undefined;
        }
        const quantity = Number(String(value).trim());
        if (!Number.isInteger(quantity) || quantity < 1) {
            return t('admin.validation.positive-integer-or-empty');
        }
        return undefined;
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
            this.ordinal = request.ordinal;
            this.submitError = null;
            this.#form.api.reset(this.buildDefaultValues(this.#form.api.state));
            await this.dialog.show();
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

    private buildServerPayload(value: FormData): Partial<AdditionalItem> {
        const availabilityAndPrices = value.availabilityAndPrices;
        return {
            id: this.editedItem?.id,
            maxQtyPerOrder: numberOrUnlimited(availabilityAndPrices.maxQtyPerOrder),
            price: availabilityAndPrices.price,
            availableQuantity: numberOrUnlimited(availabilityAndPrices.availableQuantity),
            inception: toDateTimeModification(availabilityAndPrices.inception),
            expiration: toDateTimeModification(availabilityAndPrices.expiration),
            title: value.descriptions.map(df => { return { locale: df.locale, type: 'TITLE', value: df.title, id: df.titleId }}),
            description: value.descriptions.map(df => { return { locale: df.locale, type: 'DESCRIPTION', value: df.description, id: df.descriptionId }}),
            supplementPolicy: availabilityAndPrices.supplementPolicy,
            vatType: availabilityAndPrices.vatType,
            vat: availabilityAndPrices.vat,
            type: this.type ?? undefined,
            fixPrice: availabilityAndPrices.fixPrice,
            minPrice: numberOrNull(availabilityAndPrices.minPrice),
            maxPrice: numberOrNull(availabilityAndPrices.maxPrice),
            ordinal: this.ordinal
        };
    }

    /**
     * Called by the form once all the fields are valid. The server checks the values again: its errors are displayed
     * on their fields
     */
    private async save(value: FormData): Promise<void> {
        const additionalItemRequest = this.buildServerPayload(value);
        this.saving = true;
        try {
            const result = await AdditionalItemService.validateAdditionalItem(additionalItemRequest);
            if (!result.success) {
                result.validationErrors.forEach(error => this.showServerError(error.fieldName, value));
                this.submitError = INVALID_FORM_MESSAGE;
                await this.updateComplete;
                focusFirstError(this.renderRoot);
                return;
            }
            const update = await AdditionalItemService.updateAdditionalItem(additionalItemRequest, this.event!.id);
            if (update.ok) {
                await this.close(true);
            } else {
                this.submitError = t('admin.additional-item.save-failed');
            }
        } finally {
            this.saving = false;
        }
    }

    private showServerError(fieldName: string, value: FormData): void {
        const api = this.#form.api;
        switch (fieldName) {
            case 'title':
            case 'description':
                value.descriptions.forEach((_, index) => setServerError(api, `descriptions[${index}].${fieldName}`, t('admin.additional-item.error.text')));
                break;
            case 'price':
                setServerError(api, 'availabilityAndPrices.price', t('admin.additional-item.error.amount'));
                break;
            case 'inception':
            case 'expiration':
                setServerError(api, `availabilityAndPrices.${fieldName}`, t('admin.additional-item.error.validity'));
                break;
            default:
                this.submitError = t('admin.additional-item.save-failed-check');
        }
    }
}

function isBlank(value: unknown): boolean {
    return value == null || String(value).trim() === '';
}

// empty, or an amount not lower than zero
function optionalAmount(value: unknown): string | undefined {
    if (isBlank(value)) {
        return undefined;
    }
    const amount = Number(String(value).trim());
    if (!Number.isFinite(amount) || amount < 0) {
        return t('admin.additional-item.error.amount');
    }
    return undefined;
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

function numberOrNull(value: number | string | null): number | null {
    if (isBlank(value)) {
        return null;
    }
    return Number(value);
}

// -1 means "unlimited"
function numberOrUnlimited(value: number | string | null): number {
    return numberOrNull(value) ?? -1;
}

function priceColumns(formValue: AvailabilityAndPricesForm): number {
    if (formValue.fixPrice) {
        return 2;
    }
    return 1;
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
