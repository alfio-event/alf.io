import {customElement, property, state} from "lit/decorators.js";
import {css, html, LitElement} from "lit";
import {AlfioEvent} from "../../model/event.ts";
import {Task} from "@lit/task";
import {
    AdditionalField,
    PurchaseContextFieldDescriptionContainer,
    renderAdditionalFieldType,
    supportsMinMaxLength
} from "../../model/additional-field.ts";
import {ContentLanguage, PurchaseContext, PurchaseContextType} from "../../model/purchase-context.ts";
import {SubscriptionDescriptor} from "../../model/subscription-descriptor.ts";
import {PurchaseContextService} from "../../service/purchase-context.ts";
import {AdditionalFieldService} from "../../service/additional-field.ts";
import {repeat} from "lit/directives/repeat.js";
import {renderIf} from "../../service/helpers.ts";
import {badges, cardBgColors, itemsList, pageHeader, textColors} from "../../styles.ts";

interface Model {
    purchaseContextType: PurchaseContextType;
    event?: AlfioEvent;
    subscriptionDescriptor?: SubscriptionDescriptor;
    supportedLanguages: ContentLanguage[];
    dataTask: Task<ReadonlyArray<number>, ListData>;
    isSubscription: boolean;
}

interface ListData {
    items: ReadonlyArray<AdditionalField>;
    standardIndex: number;
}

interface LocalizedAdditionalFieldContent {
    locale: string,
    localeLabel: string,
    description: PurchaseContextFieldDescriptionContainer
}

@customElement('alfio-additional-field-list')
export class AdditionalFieldList extends LitElement {
    @property({ type: String, attribute: 'data-public-identifier' })
    publicIdentifier?: string;
    @property({ type: String, attribute: 'data-purchase-context-type' })
    purchaseContextType?: PurchaseContextType;
    @property({ type: String, attribute: 'data-organization-id' })
    organizationId?: string;
    @state()
    editActive: boolean = false;
    @state()
    refreshCount: number = 0;

    private readonly retrievePageDataTask = new Task<ReadonlyArray<string>, Model>(this,
        async ([publicIdentifier, purchaseContextType, organizationId]) => {
            const result = await PurchaseContextService.load(publicIdentifier, purchaseContextType as PurchaseContextType, parseInt(organizationId, 10));
            const isSubscription = (purchaseContextType as PurchaseContextType) === 'subscription';
            const purchaseContext = isSubscription ? result.subscriptionDescriptor! : result.eventWithOrganization!.event;
            const supportedLanguages = purchaseContext.contentLanguages;
            const dataTask = new Task<ReadonlyArray<number>, ListData>(this, async (_) => {
                const items = await AdditionalFieldService.loadAllByPurchaseContext(purchaseContext);
                let standardIndex = items.findIndex(i => i.order >= 0);
                return {
                    items,
                    standardIndex
                };
            }, () => [this.refreshCount]);
            return {
                purchaseContextType: purchaseContextType as PurchaseContextType,
                event: result.eventWithOrganization?.event,
                subscriptionDescriptor: result.subscriptionDescriptor,
                supportedLanguages,
                dataTask,
                isSubscription
            }
        },
        () => [this.publicIdentifier!, this.purchaseContextType!, this.organizationId!]);

    static readonly styles = [pageHeader, textColors, itemsList, cardBgColors, badges, css`
        sl-tab-group {
            height: 100%;
        }
    `];

    protected render(): unknown {
        return this.retrievePageDataTask.render({
            initial: () => html`loading...`,
            complete: (model) => html`
                <div class="page-header">
                    <h3>
                        <sl-icon name="info-circle"></sl-icon> ${model.isSubscription ? `Subscription owner's` : `Attendees'`} data to collect
                    </h3>
                    <h5 class="text-muted">
                        The following data will be collected ${model.isSubscription ? '': `(full name, e-mail and language are collected by default)`}
                    </h5>
                </div>

                ${this.iterateItems(model)}

                <div class="pb-2"></div>
            `
        });
    }

    private iterateItems(model: Model) {
        return model.dataTask.render({
            initial: () => html`loading...`,
            complete: listData => {
                if (listData.items.length === 0) {
                    return this.renderStandard();
                }
                const renderedItems = repeat(listData.items, (field) => field.id, (field, index) => {
                    return html`

                        ${renderIf(() => index === listData.standardIndex, this.renderStandard)}

                        <div id=${`additional-field-${field.id}`}></div>
                        <sl-card class="item">
                            <div slot="header">
                                <div class="col"><strong>${field.name}</strong></div>
                                <div class="col">
                                    ${renderIf(() => field.required, () => html`
                                        <sl-tooltip content="This information is required">
                                            <sl-badge variant="warning" pill>required</sl-badge>
                                        </sl-tooltip>
                                    `)}
                                    ${renderIf(() => !field.editable, () => html`
                                        <sl-tooltip content="Information cannot be modified after set">
                                            <sl-badge variant="neutral" pill>read-only</sl-badge>
                                        </sl-tooltip>
                                    `)}
                                    ${renderIf(() => field.displayAtCheckIn, () => html`
                                        <sl-tooltip
                                            content="This information will be shown in the check-in app upon successful scan">
                                            <sl-badge variant="success" pill>shown at check-in</sl-badge>
                                        </sl-tooltip>
                                    `)}
                                </div>
                            </div>
                            <div slot="footer" class="multiple">
                                <div class="button-container">
                                    ${this.showMoveUpDownButtons(index, field, listData, model)}
                                </div>
                                <div class="button-container">
                                    <sl-button type="button" variant="default" @click=${() => console.log('todo')}>
                                        <sl-icon name="pencil" slot="prefix"></sl-icon>
                                        Edit
                                    </sl-button>
                                    <sl-button type="button" variant="danger" @click=${() => console.log('todo')}>
                                        <sl-icon name="trash" slot="prefix"></sl-icon>
                                        Delete
                                    </sl-button>
                                </div>
                            </div>
                            <div class="body">
                                <div class="info-container">
                                    <div class="info">
                                        <strong>Type</strong>
                                        <span>${renderAdditionalFieldType(field.type)}</span>
                                    </div>
                                    ${renderIf(() => supportsMinMaxLength(field.type), () => html`
                                        <div class="info">
                                            <strong>Min length</strong>
                                            <span>${field.minLength}</span>
                                        </div>
                                        <div class="info">
                                            <strong>Max length</strong>
                                            <span>${field.maxLength}</span>
                                        </div>
                                    `)}
                                </div>
                                <div>
                                <strong>Preview</strong>
                                <sl-tab-group>
                                    ${repeat(this.sortContentLanguages(field, model), d => d.localeLabel, d => html`
                                        <sl-tab slot="nav" panel=${d.locale}>${d.localeLabel}</sl-tab>
                                        <sl-tab-panel name=${d.locale}>
                                            <div class="info-container">
                                                ${this.renderPreview(d, field)}
                                            </div>
                                        </sl-tab-panel>
                                    `)}
                                </sl-tab-group>
                                </div>
                            </div>
                        </sl-card>
                    `;
                });
                if (listData.standardIndex === -1) {
                    // standard fields are set to be at the end
                    return html`
                        ${renderedItems}
                        ${this.renderStandard()}
                    `
                }
                return renderedItems;
            }
        })
    }

    private showMoveUpDownButtons(index: number, field: AdditionalField, listData: ListData, model: Model) {
        return html`
        ${renderIf(() => index > 0 || field.order >= listData.standardIndex,
            () => html`
                <sl-button type="button" variant="default" @click=${() => this.fieldUp(field, index, listData, (model.event ?? model.subscriptionDescriptor)!)}>
                    <sl-icon name="arrow-up" slot="prefix"></sl-icon>
                    Move up
                </sl-button>
            `)}
        ${renderIf(() => (index < listData.items.length) || field.order < 0,
            () => html`
                <sl-button type="button" variant="default" @click=${() => this.fieldDown(field, index, listData, (model.event ?? model.subscriptionDescriptor)!)}>
                    <sl-icon name="arrow-down" slot="prefix"></sl-icon>
                    Move down
                </sl-button>
            `)}
        `;
    }

    private renderStandard() {
        return html`
            <sl-card class="item bg-primary">
                <div slot="header">
                    Standard Fields
                </div>
                <div>
                    <div>First Name</div>
                    <sl-divider></sl-divider>
                    <div>Last Name</div>
                    <sl-divider></sl-divider>
                    <div>Email Address</div>
                </div>
            </sl-card>
        `;
    }

    private async fieldUp(field: AdditionalField, index: number, listData: ListData, purchaseContext: PurchaseContext): Promise<void> {
        const targetId = field.id;
        const targetPosition = field.order ?? 0;
        if (index > 0) {
            const prevTargetId = listData.items[index - 1].id;
            await AdditionalFieldService.swapFieldPosition(purchaseContext, targetId, prevTargetId);
        } else {
            await AdditionalFieldService.moveField(purchaseContext, targetId, targetPosition - 1);
        }
        this.refreshCount++;
    }

    private async fieldDown(field: AdditionalField, index: number, listData: ListData, purchaseContext: PurchaseContext): Promise<void> {
        if (index < listData.items.length) {
            const nextField = listData.items[index + 1];
            if (field.order < 0 && nextField.order >= 0) {
                await AdditionalFieldService.moveField(purchaseContext, field.id, 0);
            } else {
                await AdditionalFieldService.swapFieldPosition(purchaseContext, field.id, nextField.id);
            }
        } else {
            await AdditionalFieldService.moveField(purchaseContext, field.id, 0);
        }
        this.refreshCount++;
    }

    private sortContentLanguages(item: AdditionalField, model: Model): LocalizedAdditionalFieldContent[] {
        return model.supportedLanguages
            .filter(cl => {
                return item.description[cl.locale] != null;
            }).map(cl => {
                return {
                    locale: cl.locale,
                    localeLabel: cl.displayLanguage,
                    description: item.description[cl.locale]
                };
            });
    }

    private renderPreview(fieldContent: LocalizedAdditionalFieldContent, field: AdditionalField) {
        const localizedConfiguration = fieldContent.description.description;
        switch(field.type) {
            case "checkbox":
                return html`
                    ${repeat(Object.entries(localizedConfiguration.restrictedValues ?? {}), ([k]) => k, ([value, label]) => html`
                        <sl-checkbox value=${value}>${label}</sl-checkbox>
                    `)}

                `;
            case "radio":
                return html`
                    <sl-radio-group label=${localizedConfiguration.label} name=${field.name}>
                        ${repeat(Object.entries(localizedConfiguration.restrictedValues ?? {}), ([k]) => k, ([value, label]) => html`
                            <sl-radio value=${value}> ${label}</sl-radio>
                        `)}
                    </sl-radio-group>
                `;
            case "country":
                return html`
                    <label>${localizedConfiguration.label}</label>
                    <sl-select hoist>
                        <sl-option value="C1">Country 1</sl-option>
                        <sl-option value="C2">Country 2</sl-option>
                        <sl-option value="C3">Country 3</sl-option>
                    </sl-select>
                `;
            case "select":

                return html`
                    <label>${localizedConfiguration.label}</label>
                    <sl-select hoist>
                        ${repeat(Object.entries(localizedConfiguration.restrictedValues ?? {}), ([k]) => k, ([value, label]) => html`
                            <sl-option value=${value}>${label}</sl-option>
                        `)}
                    </sl-select>
                `;

            case "textarea":
                return html`
                    <sl-textarea label=${localizedConfiguration.label} placeholder=${localizedConfiguration.placeholder ?? ''}></sl-textarea>
                `;

        }

        let inputType;

        if (field.type === 'input:dateOfBirth') {
            inputType = 'date';
        } else if (field.type === 'vat:eu') {
            inputType = 'text';
        } else {
            inputType = field.type.substring(field.type.indexOf(':') + 1);
        }

        return html`
            <sl-input type=${inputType} label=${localizedConfiguration.label} placeholder=${localizedConfiguration.placeholder ?? ''}>
        `;
    }


}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-additional-field-list': AdditionalFieldList
    }
}
