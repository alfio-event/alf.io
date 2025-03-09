import {customElement, property, state} from "lit/decorators.js";
import {html, LitElement} from "lit";
import {AlfioEvent} from "../../model/event.ts";
import {Task} from "@lit/task";
import {AdditionalField, renderAdditionalFieldType, supportsMinMaxLength} from "../../model/additional-field.ts";
import {ContentLanguage, PurchaseContext, PurchaseContextType} from "../../model/purchase-context.ts";
import {SubscriptionDescriptor} from "../../model/subscription-descriptor.ts";
import {PurchaseContextService} from "../../service/purchase-context.ts";
import {AdditionalFieldService} from "../../service/additional-field.ts";
import {repeat} from "lit/directives/repeat.js";
import {renderIf} from "../../service/helpers.ts";
import {cardBgColors, itemsList, pageHeader, textColors} from "../../styles.ts";

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

    static readonly styles = [pageHeader, textColors, itemsList, cardBgColors];

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
                                <div class="col">${field.name}</div>
                                <div class="col">
                                    <sl-button type="button" variant="default" @click=${() => console.log('todo')}>
                                        <sl-icon name="pencil" slot="prefix"></sl-icon>
                                        Edit
                                    </sl-button>
                                    ${renderIf(() => index > 0 || field.order >= listData.standardIndex, () => html`
                                        <sl-button type="button" variant="default" @click=${() => this.fieldUp(field, index, listData, (model.event ?? model.subscriptionDescriptor)!)}>
                                            <sl-icon name="arrow-up" slot="prefix"></sl-icon>
                                            Move up
                                        </sl-button>
                                    `)}
                                    ${renderIf(() => (index < listData.items.length) || field.order < 0, () => html`
                                        <sl-button type="button" variant="default" @click=${() => this.fieldDown(field, index, listData, (model.event ?? model.subscriptionDescriptor)!)}>
                                            <sl-icon name="arrow-down" slot="prefix"></sl-icon>
                                            Move down
                                        </sl-button>
                                    `)}
                                    <sl-button type="button" variant="danger" @click=${() => console.log('todo')}>
                                        <sl-icon name="trash" slot="prefix"></sl-icon>
                                        Delete
                                    </sl-button>
                                </div>
                            </div>
                            <!-- TODO footer -->
                            <div class="body">
                                <div class="info-container">
                                    <div class="info">
                                        <strong>Field name</strong>
                                        <span>${field.name}</span>
                                    </div>
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
                                    <div class="row">
                                        <div class="col-sm-4"><strong>Label</strong></div>
                                        <div class="col-sm-8">
                                            <span data-ng-repeat="lang in $ctrl.allLanguages | selectedLanguages:$ctrl.selectedLocales"><span title="{{lang.displayLanguage}}">{{field.description[lang.locale].description.label}}</span><span data-ng-if="!$last"> / </span></span>
                                        </div>
                                    </div>
                                    <div class="row">
                                        <div class="col-sm-4"><strong>Placeholder</strong></div>
                                        <div class="col-sm-8">
                                        <span data-ng-repeat="lang in $ctrl.allLanguages | selectedLanguages:$ctrl.selectedLocales">
                                            <span title="{{lang.displayLanguage}}">{{field.description[lang.locale].description.placeholder}}</span><span data-ng-if="!$last"> / </span>
                                        </span>
                                        </div>
                                    </div>
                                    <div class="row">
                                        <div class="col-sm-4"><strong>Required</strong></div>
                                        <div class="col-sm-8">{{field.required}}</div>
                                    </div>
                                    <div class="row" data-ng-if="field.readOnly">
                                        <div class="col-sm-4"><strong>Read Only</strong></div>
                                        <div class="col-sm-8">{{field.readOnly}}</div>
                                    </div>
                                    <div class="row">
                                        <div class="col-sm-4" uib-tooltip="When enabled, this information will be visible to the check-in staff.">
                                            <strong>Show at Check-in</strong>
                                            <i class="fa fa-info-circle" ></i>
                                        </div>
                                        <div class="col-sm-8">{{field.displayAtCheckIn ? 'yes' : 'no'}}</div>
                                    </div>
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
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-additional-field-list': AdditionalFieldList
    }
}
