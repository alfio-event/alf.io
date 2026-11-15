import {customElement, property, state} from "lit/decorators.js";
import {css, html, LitElement, TemplateResult} from "lit";
import {when} from "lit/directives/when.js";
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
import {badges, detailList as detailListStyles, modernLayout, purchaseContextListPage, textColors} from "../../styles.ts";
import {taskContent} from "../../components/task-content.ts";
import {sectionCard} from "../../components/section-card.ts";
import {detailList} from "../../components/detail-list.ts";
import {localizedTitle} from "../../service/reservation-format.ts";
import {ConfirmationDialogService} from "../../service/confirmation-dialog.ts";
import {AlfioDialogClosed, dispatchFeedback} from "../../model/dom-events.ts";
import {LocalizedAdditionalFieldContent, renderPreview} from "./additional-field-util.ts";
import {AdditionalItem} from "../../model/additional-item.ts";
import {AdditionalItemService} from "../../service/additional-item.ts";

interface Model {
    purchaseContextType: PurchaseContextType;
    event?: AlfioEvent;
    subscriptionDescriptor?: SubscriptionDescriptor;
    supportedLanguages: ContentLanguage[];
    dataTask: Task<ReadonlyArray<number>, ListData>;
    isSubscription: boolean;
    templates: ReadonlyArray<AdditionalFieldTemplate>
    additionalItems: ReadonlyArray<AdditionalItem>
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
            if (!this.ready) {
                // AngularJS has not interpolated the attributes yet: the next run will have them
                return new Promise<Model>(() => {});
            }
            const result = await PurchaseContextService.load(publicIdentifier, purchaseContextType as PurchaseContextType, Number.parseInt(organizationId, 10));
            const isSubscription = (purchaseContextType as PurchaseContextType) === 'subscription';
            let purchaseContext: PurchaseContext;
            if (isSubscription) {
                purchaseContext = result.subscriptionDescriptor!;
            } else {
                purchaseContext = result.eventWithOrganization!.event;
            }
            const templates = await AdditionalFieldService.loadTemplates(purchaseContext);
            const supportedLanguages = purchaseContext.contentLanguages;
            const additionalItems: AdditionalItem[] = [];
            if ((purchaseContextType as PurchaseContextType) === 'event') {
                additionalItems.push(...(await AdditionalItemService.loadAll({eventId: result.eventWithOrganization!.event.id})));
            }
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
                templates,
                additionalItems
            }
        },
        () => [this.publicIdentifier!, this.purchaseContextType!, this.organizationId!]);

    /**
     * False while AngularJS has not interpolated the attributes yet (e.g. data-organization-id="{{...}}"):
     * the next run will have the real values. Number.parseInt of such a value is NaN, which the server rejects.
     */
    private get ready(): boolean {
        if (this.publicIdentifier == null || this.publicIdentifier === '' || this.publicIdentifier.includes('{{')) {
            return false;
        }
        if (this.purchaseContextType == null || this.purchaseContextType.includes('{{')) {
            return false;
        }
        if (this.purchaseContextType === 'subscription'
            && (this.organizationId == null || Number.isNaN(Number.parseInt(this.organizationId, 10)))) {
            return false;
        }
        return true;
    }

    static readonly styles = [modernLayout, purchaseContextListPage, detailListStyles, badges, textColors, css`
        .field-body {
            display: grid;
            gap: var(--sl-spacing-large);
        }

        @media (min-width: 768px) {
            .field-body {
                grid-template-columns: minmax(0, 1fr) minmax(0, 1.3fr);
            }
        }

        .preview-label {
            color: var(--sl-color-gray-600);
            font-size: var(--sl-font-size-small);
            font-weight: var(--sl-font-weight-semibold);
        }

        .field-filters {
            margin-top: var(--sl-spacing-medium);
        }

        .field-filters sl-badge {
            margin-inline-start: var(--sl-spacing-2x-small);
        }

        .standard-fields {
            display: flex;
            flex-direction: column;
        }
    `];

    protected render(): unknown {
        return taskContent(this.retrievePageDataTask, this.retrievePageDataTask.value ?? null, 'Failed to load the page data.',
            (model) => html`
                <div class="container">
                    <div class="page-title-row secondary">
                        <h1>${pageTitle(model)}</h1>
                    </div>
                    <hr class="page-separator" />
                    <p class="page-description text-muted">${pageDescription(model)}</p>
                    <div class="filter-toolbar" style="justify-content: flex-end;">
                        <div class="filter-right">${this.renderCreateButton(model)}</div>
                    </div>
                    ${this.iterateItems(model)}
                </div>
            `);
    }

    private itemsCount(model: Model): number {
        return model.dataTask.value?.items.length ?? 0;
    }

    private renderCreateButton(model: Model) {
        return html`
            <sl-dropdown hoist placement="bottom-end">
                <sl-button variant="success" size="large" slot="trigger" caret>
                    <sl-icon name="plus-circle" slot="prefix"></sl-icon>
                    Add new
                </sl-button>
                <sl-menu>
                    <sl-menu-item>
                        From template
                        <sl-menu slot="submenu">
                            ${repeat(model.templates, t => t.name, (template) => html`
                                    <sl-menu-item @click=${() => this.newFromTemplate(model, template, this.itemsCount(model))}>${template.description['en'].label} (${template.name})</sl-menu-item>
                                `)}
                        </sl-menu>
                    </sl-menu-item>
                    <sl-menu-item @click=${() => this.newCustom(model, this.itemsCount(model))}>Custom</sl-menu-item>
                </sl-menu>
            </sl-dropdown>
        `;
    }

    private iterateItems(model: Model): TemplateResult {
        return taskContent(model.dataTask, model.dataTask.value ?? null, 'Failed to load the additional fields.', listData => {
            if (listData.items.length === 0) {
                return renderStandard();
            }
            const renderedItems = repeat(listData.items, (field) => field.id, (field, index) => html`
                ${renderIf(() => index === listData.standardIndex, renderStandard)}

                <div id=${`additional-field-${field.id}`}></div>
                ${sectionCard({
                    icon: 'input-cursor-text',
                    title: field.name,
                    badge: fieldBadges(field),
                    className: 'field-card',
                    actions: html`
                        ${this.showMoveUpDownButtons(index, field, listData, model)}
                        ${this.showStatisticsButton(field, model)}
                        <sl-button type="button" variant="default" size="small" @click=${() => this.edit(field, model)}>
                            <sl-icon name="pencil" slot="prefix"></sl-icon>
                            Edit
                        </sl-button>
                        <sl-button type="button" variant="danger" size="small" @click=${() => this.delete(field, model)}>
                            <sl-icon name="trash" slot="prefix"></sl-icon>
                            Delete
                        </sl-button>
                    `,
                }, html`
                    <div class="field-body">
                        ${detailList([
                            { label: 'Type', value: renderAdditionalFieldType(field.type) },
                            supportsMinMaxLength(field.type) && { label: 'Min length', value: field.minLength },
                            supportsMinMaxLength(field.type) && { label: 'Max length', value: field.maxLength },
                        ])}
                        <div>
                            <span class="preview-label">Preview</span>
                            <sl-tab-group>
                                ${repeat(this.sortContentLanguages(field, model), d => d.localeLabel, d => html`
                                    <sl-tab slot="nav" panel=${d.locale}>${d.localeLabel}</sl-tab>
                                    <sl-tab-panel name=${d.locale}>
                                        ${renderPreview(d, field)}
                                    </sl-tab-panel>
                                `)}
                            </sl-tab-group>
                        </div>
                    </div>
                    ${this.displayFilters(field, model)}
                `)}
            `);
            if (listData.standardIndex === -1) {
                // standard fields are set to be at the end
                return html`
                    ${renderedItems}
                    ${renderStandard()}
                `;
            }
            return html`${renderedItems}`;
        });
    }

    private displayFilters(field: AdditionalField, model: Model) {

        const eventCategories = model.event?.ticketCategories ?? [];
        const selectedCategories = field.categoryIds?.length ?? 0;
        const additionalItem = model.additionalItems.find(ai => ai.id === field.additionalServiceId);

        if (selectedCategories === 0 && additionalItem == null) {
            return html``;
        }

        return html`
            <sl-alert open variant="primary" class="field-filters">
                <sl-icon slot="icon" name="info-circle"></sl-icon>
                ${renderIf(() => selectedCategories > 0, () => html`
                    <div>
                        Collected only for the following categories: ${repeat(eventCategories.filter(c => field.categoryIds!.includes(c.id)), c => c.id, category => html`<sl-badge variant="neutral" pill>${category.name}</sl-badge>`)}
                    </div>
                `)}
                ${renderIf(() => additionalItem != null, () => html`
                    <div>
                        Collected only if the additional option <sl-badge variant="neutral" pill>${additionalItem!.title.map(t => t.value).join(" / ")}</sl-badge> was selected
                    </div>
                `)}
            </sl-alert>
        `;
    }

    private showMoveUpDownButtons(index: number, field: AdditionalField, listData: ListData, model: Model) {
        return html`
        ${renderIf(() => index > 0 || field.order >= listData.standardIndex,
            () => html`
                <sl-button type="button" variant="default" size="small" @click=${() => this.fieldUp(field, index, listData, (model.event ?? model.subscriptionDescriptor)!)}>
                    <sl-icon name="arrow-up" slot="prefix"></sl-icon>
                    Move up
                </sl-button>
            `)}
        ${renderIf(() => (index < listData.items.length - 1) || field.order < 0,
            () => html`
                <sl-button type="button" variant="default" size="small" @click=${() => this.fieldDown(field, index, listData, (model.event ?? model.subscriptionDescriptor)!)}>
                    <sl-icon name="arrow-down" slot="prefix"></sl-icon>
                    Move down
                </sl-button>
            `)}
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
            <sl-button variant="default" size="small" @click=${() => this.openStatisticsDetail(model, field)}><sl-icon name="bar-chart-line" slot="prefix"></sl-icon>Statistics</sl-button>
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
        let purchaseContext: PurchaseContext = model.event!;
        if (model.isSubscription) {
            purchaseContext = model.subscriptionDescriptor!;
        }
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

function pageTitle(model: Model): TemplateResult {
    if (model.isSubscription) {
        const title = Object.values(model.subscriptionDescriptor?.title ?? {})[0];
        return html`Subscription owner's data to collect${when(title, () => html` for <i>${title}</i>`)}`;
    }
    return html`Attendees' data to collect for <i>${localizedTitle(model.event!)}</i>`;
}

function pageDescription(model: Model): string {
    if (model.isSubscription) {
        return 'The following data will be collected.';
    }
    return 'The following data will be collected. Full name, email and language are collected by default.';
}

function fieldBadges(field: AdditionalField): TemplateResult {
    return html`
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
            <sl-tooltip content="This information will be shown in the check-in app upon successful scan">
                <sl-badge variant="success" pill>shown at check-in</sl-badge>
            </sl-tooltip>
        `)}
    `;
}

function renderStandard(): TemplateResult {
    return sectionCard({icon: 'person-vcard', title: 'Standard fields'}, html`
        <div class="standard-fields">
            <div>First name</div>
            <sl-divider></sl-divider>
            <div>Last name</div>
            <sl-divider></sl-divider>
            <div>Email address</div>
        </div>
    `);
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-additional-field-list': AdditionalFieldList
    }
}
