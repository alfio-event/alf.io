import {customElement, property, state} from "lit/decorators.js";
import {css, html, LitElement} from "lit";
import {AlfioEvent} from "../../model/event.ts";
import {Task} from "@lit/task";
import {
    AdditionalField, AdditionalFieldTemplate, NewAdditionalFieldFromTemplate,
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
import {ConfirmationDialogService} from "../../service/confirmation-dialog.ts";
import {AlfioDialogClosed, dispatchFeedback} from "../../model/dom-events.ts";
import {LocalizedAdditionalFieldContent, renderPreview} from "./additional-field-util.ts";
interface Model {
    purchaseContextType: PurchaseContextType;
    event?: AlfioEvent;
    subscriptionDescriptor?: SubscriptionDescriptor;
    supportedLanguages: ContentLanguage[];
    dataTask: Task<ReadonlyArray<number>, ListData>;
    isSubscription: boolean;
    templates: ReadonlyArray<AdditionalFieldTemplate>
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
    @state()
    itemsCount: number = 0;

    private readonly retrievePageDataTask = new Task<ReadonlyArray<string>, Model>(this,
        async ([publicIdentifier, purchaseContextType, organizationId]) => {
            const result = await PurchaseContextService.load(publicIdentifier, purchaseContextType as PurchaseContextType, Number.parseInt(organizationId, 10));
            const isSubscription = (purchaseContextType as PurchaseContextType) === 'subscription';
            const purchaseContext = isSubscription ? result.subscriptionDescriptor! : result.eventWithOrganization!.event;
            const templates = await AdditionalFieldService.loadTemplates(purchaseContext);
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
                isSubscription,
                templates
            }
        },
        () => [this.publicIdentifier!, this.purchaseContextType!, this.organizationId!]);

    static readonly styles = [pageHeader, textColors, itemsList, cardBgColors, badges, css`
        sl-tab-group {
            height: 100%;
        }
        div.m-1 {
            margin-top: 1em;
            margin-bottom: 1em;
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

                ${this.renderCreateButton(model)}

                <div class="m-1">
                    ${this.iterateItems(model)}
                </div>

                <div class="m-1"></div>
            `
        });
    }

    private renderCreateButton(model: Model) {
        return html`
            <sl-dropdown>
                <sl-button variant="success" slot="trigger" caret>Create new</sl-button>
                <sl-menu>
                    <sl-menu-item>
                        From Template
                        <sl-menu slot="submenu">
                            ${repeat(model.templates, t => t.name, (template) => html`
                                    <sl-menu-item @click=${() => this.newFromTemplate(model, template, this.itemsCount)}>${template.description['en'].label} (${template.name})</sl-menu-item>
                                `)}
                        </sl-menu>
                    </sl-menu-item>
                    <sl-menu-item @click=${() => this.newCustom(model, this.itemsCount)}>Custom</sl-menu-item>
                </sl-menu>
            </sl-dropdown>
        `;
    }

    private iterateItems(model: Model) {
        return model.dataTask.render({
            initial: () => html`loading...`,
            complete: listData => {
                this.itemsCount = listData.items.length;
                if (listData.items.length === 0) {
                    return this.renderStandard();
                }
                const renderedItems = repeat(listData.items, (field) => field.id, (field, index) => {
                    return html`

                        ${renderIf(() => index === listData.standardIndex, this.renderStandard)}

                        <div id=${`additional-field-${field.id}`}></div>
                        <sl-card class="item bg-default">
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
                                ${this.showStatisticsButton(field, model)}
                                <div class="button-container">
                                    <sl-button type="button" variant="default" @click=${() => this.edit(field, model)}>
                                        <sl-icon name="pencil" slot="prefix"></sl-icon>
                                        Edit
                                    </sl-button>
                                    <sl-button type="button" variant="danger" @click=${() => this.delete(field, model)}>
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
                                                    ${renderPreview(d, field)}
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
        ${renderIf(() => (index < listData.items.length - 1) || field.order < 0,
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
            await AdditionalFieldService.swapFieldPosition(purchaseContext, targetId!, prevTargetId!);
        } else {
            await AdditionalFieldService.moveField(purchaseContext, targetId!, targetPosition - 1);
        }
        this.refreshCount++;
    }

    private async fieldDown(field: AdditionalField, index: number, listData: ListData, purchaseContext: PurchaseContext): Promise<void> {
        if (index < listData.items.length) {
            const nextField = listData.items[index + 1];
            if (field.order < 0 && nextField.order >= 0) {
                await AdditionalFieldService.moveField(purchaseContext, field.id!, 0);
            } else {
                await AdditionalFieldService.swapFieldPosition(purchaseContext, field.id!, nextField.id!);
            }
        } else {
            await AdditionalFieldService.moveField(purchaseContext, field.id!, 0);
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

    async delete(field: AdditionalField, model: Model): Promise<boolean> {
        try {
            const confirmation = await ConfirmationDialogService.requestConfirm(
                `Delete field ${field.name}`,
                `Are you sure to delete the field "${field.name}"? All the values in the tickets associated to this field will be removed and they cannot be recovered.`,
                'danger'
            );
            if (confirmation) {
                const response = await AdditionalFieldService.deleteField((model.event ?? model.subscriptionDescriptor)!, field.id!);
                if(response.ok) {
                    dispatchFeedback({
                        type: 'success',
                        message: `Field ${field.name} successfully deleted`
                    }, this);
                    this.refreshCount++;
                    return true;
                } else {
                    dispatchFeedback({
                        type: 'danger',
                        message: `Cannot delete field ${field.name}`
                    }, this);
                }
            }
            return false;
        } catch(e) {
            console.error("Error while deleting field", e);
            dispatchFeedback({
                type: 'danger',
                message: `Cannot delete field ${field.name}`
            }, this);
            return false;
        }
    }

    private showStatisticsButton(field: AdditionalField, model: Model) {
        return renderIf(() => field.type === 'select' || field.type === 'country', () => html`
            <div class="button-container">
                <sl-button @click=${() => this.openStatisticsDetail(model, field)}><sl-icon name="bar-chart-line" slot="prefix"></sl-icon> Statistics</sl-button>
            </div>
        `);
    }

    private async openStatisticsDetail(model: Model, field: AdditionalField): Promise<void> {
        const div = document.createElement('div');
        div.innerHTML = `
            <alfio-additional-field-statistics></alfio-additional-field-statistics>
        `;
        const component = div.querySelector('alfio-additional-field-statistics')!;
        document.body.appendChild(div);
        await customElements.whenDefined('alfio-additional-field-statistics');
        component.addEventListener('alfio-drawer-closed', async () => {
            setTimeout(() => div.remove());
        });
        await component.show({
            purchaseContext: (model.event ?? model.subscriptionDescriptor)!,
            field
        });
    }

    private async openEditDialog(model: Model,
                                 ordinal: number,
                                 field?: AdditionalField,
                                 template?: NewAdditionalFieldFromTemplate): Promise<void> {
        const div = document.createElement('div');
        div.innerHTML = `
            <alfio-additional-field-edit></alfio-additional-field-edit>
        `;
        const component = div.querySelector('alfio-additional-field-edit')!;
        document.body.appendChild(div);
        await customElements.whenDefined('alfio-additional-field-edit');
        component.addEventListener('alfio-dialog-closed', async (e) => {
            await this.editDialogClosed(e);
            setTimeout(() => div.remove());
        });
        const purchaseContext: PurchaseContext = (model.isSubscription ? model.subscriptionDescriptor : model.event)!;
        await component.open({
            field,
            template,
            purchaseContext,
            ordinal
        });
    }


    private async newFromTemplate(model: Model, template: AdditionalFieldTemplate, fieldsCount: number) {
        const ordinal = fieldsCount + 1;
        const newField: NewAdditionalFieldFromTemplate = {
            ...template,
            order: ordinal
        };
        await this.openEditDialog(model, ordinal, undefined, newField);
    }

    private async edit(additionalField: AdditionalField, model: Model) {
        await this.openEditDialog(model, additionalField.order, additionalField);
    }

    private async newCustom(model: Model, itemsCount: number) {
        await this.openEditDialog(model, itemsCount + 1);
    }

    private async editDialogClosed(e: AlfioDialogClosed) {
        this.editActive = false;
        if (e.detail.success) {
            this.refreshCount++;
            dispatchFeedback({
                type: 'success',
                message: 'Operation completed successfully'
            }, this);
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-additional-field-list': AdditionalFieldList
    }
}
