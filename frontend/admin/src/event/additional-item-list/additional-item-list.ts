import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js'
import {repeat} from 'lit/directives/repeat.js';
import {AdditionalItemService, UsageCount} from "../../service/additional-item.ts";
import {Task} from "@lit/task";
import {AlfioEvent} from "../../model/event.ts";
import {
    AdditionalItem,
    AdditionalItemLocalizedContent,
    AdditionalItemType,
    isMandatory,
    isMandatoryPercentage,
    supplementPolicyDescriptions
} from "../../model/additional-item.ts";
import {EventService} from "../../service/event.ts";
import {renderIf, supportedLanguages} from "../../service/helpers.ts";
import {badges, detailList as detailListStyles, modernLayout, purchaseContextListPage, textColors} from "../../styles.ts";
import {taskContent} from "../../components/task-content.ts";
import {sectionCard} from "../../components/section-card.ts";
import {emptyState} from "../../components/empty-state.ts";
import {detailList} from "../../components/detail-list.ts";
import {localizedTitle} from "../../service/reservation-format.ts";
import {when} from "lit/directives/when.js";
import {AlfioDialogClosed, dispatchFeedback} from "../../model/dom-events.ts";
import {ConfirmationDialogService} from "../../service/confirmation-dialog.ts";
import {ContentLanguage} from "../../model/purchase-context.ts";
import "../../components/format-date.ts";
import { t, tHtml, formatNumber } from '../../service/i18n';

interface Model {
    event: AlfioEvent;
    title: string;
    icon: string;
    type: AdditionalItemType;
    supportedLanguages: ContentLanguage[];
    dataTask: Task<ReadonlyArray<number>, ListData>;
}

interface ListData {
    items: Array<AdditionalItem>;
    usageCount: UsageCount;
    allowDownload: boolean;
}

interface EnhancedAdditionalItemLocalizedContent extends AdditionalItemLocalizedContent {
    localeLabel: string;
}

@customElement('alfio-additional-item-list')
export class AdditionalItemList extends LitElement {

    @property({ type: String, attribute: 'data-public-identifier' })
    publicIdentifier?: string;
    @property({ type: String, attribute: 'data-type' })
    type?: AdditionalItemType;
    @property({ type: String, attribute: 'data-title' })
    pageTitle?: string;
    @property({ type: String, attribute: 'data-icon' })
    icon?: string;
    @state()
    editActive: boolean = false;
    @state()
    refreshCount: number = 0;

    private readonly retrievePageDataTask = new Task<ReadonlyArray<string>, Model>(this,
        async ([publicIdentifier]) => {
            const event = (await EventService.load(publicIdentifier)).event;
            const dataTask = new Task<ReadonlyArray<number>, ListData>(this, async (_) => {
                const [items, count] = await Promise.all([AdditionalItemService.loadAll({eventId: event.id}), AdditionalItemService.useCount(event.id)]);
                return {
                    items: items.filter(i => i.type === this.type),
                    usageCount: count,
                    allowDownload: Object.values(count).some(p => Object.values(p).reduce((pv: number, cv: number) => pv + cv, 0) > 0),
                }
            }, () => [this.refreshCount]);
            return {
                event,
                title: this.pageTitle ?? '',
                icon: this.icon ?? '',
                type: this.type!,
                supportedLanguages: supportedLanguages(),
                dataTask
            };
        },
        () => [this.publicIdentifier!]);

    static readonly styles = [modernLayout, purchaseContextListPage, detailListStyles, badges, textColors, css`
        .item-body {
            display: grid;
            gap: var(--sl-spacing-large);
        }

        @media (min-width: 768px) {
            .item-body {
                grid-template-columns: minmax(0, 1fr) minmax(0, 1.3fr);
            }
        }

        .panel-content {
            display: flex;
            flex-direction: column;
            gap: var(--sl-spacing-x-small);
        }

        .description-text {
            padding-left: var(--sl-spacing-x-small);
            overflow-wrap: anywhere;
        }
    `];

    render() {
        return taskContent(this.retrievePageDataTask, this.retrievePageDataTask.value ?? null, t('admin.load-error.event'),
            model => html`
                <div class="container">
                    <div class="page-title-row secondary">
                        <h1>${tHtml('admin.additional-items.title', model.title, html`<i>${localizedTitle(model.event)}</i>`)}</h1>
                    </div>
                    <hr class="page-separator" />
                    ${this.iterateItems(model)}
                </div>
            `);
    }

    async addNew(model: Model, count: number): Promise<void> {
        this.editActive = true;
        let ordinal = 0;
        if (model.event.supportsAdditionalServicesOrdinal) {
            ordinal = count + 1;
        }
        await this.openEditDialog(model, null, ordinal);
    }

    async edit(item: AdditionalItem, model: Model): Promise<void> {
        this.editActive = true;
        await this.openEditDialog(model, item, item.ordinal);
    }

    private async openEditDialog(model: Model, item: AdditionalItem | null, ordinal: number): Promise<void> {
        const div = document.createElement('div');
        div.innerHTML = `
          <alfio-additional-item-edit></alfio-additional-item-edit>
        `;
        const itemEditComponent = div.querySelector('alfio-additional-item-edit')!;
        document.body.appendChild(div);
        await customElements.whenDefined('alfio-additional-item-edit');
        itemEditComponent.addEventListener('alfio-dialog-closed', async (e) => {
            await this.editDialogClosed(e);
            setTimeout(() => div.remove());
        });
        await itemEditComponent.open({
            supportedLanguages: model.event.contentLanguages,
            event: model.event,
            type: this.type!,
            editedItem: item,
            ordinal: ordinal
        });

    }

    async delete(item: AdditionalItem, model: Model): Promise<boolean> {
        try {
            const confirmation = await ConfirmationDialogService.requestConfirm(
                t('admin.additional-items.delete.title'),
                t('admin.additional-items.delete.text', item.title.map(title => title.value).join("/")),
                'danger'
                );
            if (confirmation) {
                const response = await AdditionalItemService.deleteAdditionalItem(item.id, model.event.id);
                if(response.ok) {
                    dispatchFeedback({
                        type: 'success',
                        message: t('admin.additional-items.deleted')
                    }, this);
                    this.triggerListRefresh();
                    return true;
                } else {
                    dispatchFeedback({
                        type: 'danger',
                        message: t('admin.additional-items.delete-failed')
                    }, this);
                }
            }
            return false;
        } catch {
            return false;
        }
    }

    private renderToolbar(model: Model, listData: ListData): TemplateResult {
        if (model.event.freeOfCharge) {
            let freeEvent = t('admin.additional-items.free-event.options');
            if (model.type === 'DONATION') {
                freeEvent = t('admin.additional-items.free-event.donations');
            }
            return html`
                <sl-alert open variant="warning">
                    <sl-icon name="exclamation-triangle" slot="icon"></sl-icon>
                    <p>${freeEvent}</p>
                    <p>${t('admin.additional-items.free-event.help')}</p>
                </sl-alert>
            `;
        }
        return html`
            <div class="filter-toolbar actions-only">
                <div class="filter-right">
                    ${when(listData.allowDownload, () => html`
                        <sl-button variant="default" size="large" href=${`/admin/api/events/${model.event.publicIdentifier}/additional-services/${this.type}/export`} target="_blank" rel="noopener">
                            <sl-icon name="download" slot="prefix"></sl-icon>
                            ${t('admin.additional-items.export')}
                        </sl-button>
                    `)}
                    ${when(!this.editActive, () => html`
                        <sl-button type="button" variant="success" size="large" @click=${() => this.addNew(model, listData.items.length)}>
                            <sl-icon name="plus-circle" slot="prefix"></sl-icon>
                            ${t('admin.common.add-new-entity')}
                        </sl-button>
                    `)}
                </div>
            </div>
        `;
    }

    private sortContentLanguages(item: AdditionalItem, model: Model): EnhancedAdditionalItemLocalizedContent[] {
        return model.event.contentLanguages
            .filter(cl => {
                return item.description.some(d => d.locale === cl.locale)
            }).map(cl => {
                return {
                    localeLabel: cl.displayLanguage,
                    ...item.description.find(d => d.locale === cl.locale)!
                };
            });
    }

    private iterateItems(model: Model): TemplateResult {
        return taskContent(model.dataTask, model.dataTask.value ?? null, t('admin.additional-items.load-failed'), listData => html`
            ${this.renderToolbar(model, listData)}
            ${when(listData.items.length === 0, () => html`
                <section class="section-card">${emptyState(emptyMessage(model.type))}</section>
            `)}
            ${repeat(listData.items, (item) => item.id, (item, index) => html`
                <div id=${`additional-service-${item.id}`}></div>
                ${sectionCard({
                    icon: model.icon,
                    title: itemTitle(item),
                    badge: missingTitleBadge(item),
                    className: 'item-card',
                    actions: html`
                        ${this.renderMoveButtons(listData, index, item, model)}
                        <sl-button variant="default" size="small" title=${t('admin.common.edit')} @click=${() => this.edit(item, model)} type="button"><sl-icon name="pencil" slot="prefix"></sl-icon>${t('admin.common.edit')}</sl-button>
                        ${this.renderDelete(listData, item, model)}
                    `,
                }, html`
                    <div class="item-body">
                        ${detailList([
                            { label: t('admin.statistics.confirmed'), value: html`<span class="text-success">${formatSoldCount(listData, item.id)}</span>` },
                            { label: t('admin.additional-items.inception'), value: html`<alfio-format-date .date=${item.inception} time-zone=${model.event.timeZone}></alfio-format-date>` },
                            { label: t('admin.additional-items.expiration'), value: html`<alfio-format-date .date=${item.expiration} time-zone=${model.event.timeZone}></alfio-format-date>` },
                            { label: t('admin.category-dialog.price'), value: this.showItemPrice(item) },
                            item.type === 'SUPPLEMENT' && { label: t('admin.additional-items.policy'), value: supplementPolicyDescriptions[item.supplementPolicy] },
                            showMaxQuantity(item) && { label: maxQuantityLabel(item), value: item.maxQtyPerOrder },
                        ])}
                        <sl-tab-group>
                            ${repeat(this.sortContentLanguages(item, model), d => d.locale, (d, index) => html`
                                <sl-tab slot="nav" panel=${d.locale} ?active=${index === 0}>${d.localeLabel}</sl-tab>
                                <sl-tab-panel name=${d.locale} ?active=${index === 0}>
                                    <div class="panel-content">
                                        <alfio-display-commonmark-preview data-button-text=${t('admin.common.preview')} data-text=${d.value}></alfio-display-commonmark-preview>
                                        <div class="description-text">${d.value}</div>
                                    </div>
                                </sl-tab-panel>
                            `)}
                        </sl-tab-group>
                    </div>
                `)}
            `)}
        `);
    }

    private renderMoveButtons(listData: ListData, index: number, item: AdditionalItem, model: Model): TemplateResult {
        return renderIf(() => model.event.supportsAdditionalServicesOrdinal && listData.items.length > 1, () => html`
            ${renderIf(() => index > 0 && item.ordinal !== 0, () => html`
                <sl-button variant="default" size="small" title=${t('admin.common.move-up')} @click=${() => this.moveItem(model.event.id, listData, item, index, 'up')} type="button"><sl-icon name="arrow-up" slot="prefix"></sl-icon>${t('admin.common.move-up')}</sl-button>
            `)}
            ${renderIf(() => index < listData.items.length - 1 && item.ordinal !== 0, () => html`
                <sl-button variant="default" size="small" title=${t('admin.common.move-down')} @click=${() => this.moveItem(model.event.id, listData, item, index, 'down')} type="button"><sl-icon name="arrow-down" slot="prefix"></sl-icon>${t('admin.common.move-down')}</sl-button>
            `)}
        `);
    }

    private async moveItem(eventId: number,
                           listData: ListData,
                           item: AdditionalItem,
                           index: number,
                           direction: 'up' | 'down'): Promise<void> {
        const items = listData.items;
        let toSwap: AdditionalItem;
        if (direction === 'up') {
            // swap the item with the preceding one
            toSwap = items[index - 1];
        } else {
            toSwap = items[index + 1];
        }
        const response = await AdditionalItemService.swapItems(eventId, item.id, toSwap.id);
        if (response.ok) {
            dispatchFeedback({
                type: 'success',
                message: t('admin.common.operation-completed')
            }, this);
            this.triggerListRefresh();
        } else {
            dispatchFeedback({
                type: 'danger',
                message: t('admin.additional-items.swap-failed')
            }, this);
        }
    }

    private showItemPrice(item: AdditionalItem): TemplateResult {
        if (item.fixPrice) {
            return this.showItemFixPrice(item);
        }
        return html`<span>${t('admin.additional-items.price.user-defined')}</span>`;
    }

    private showItemFixPrice(item: AdditionalItem): TemplateResult {
        if (isMandatoryPercentage(item.supplementPolicy)) {
            return html`<div>${item.price}%${
                renderIf(() => item.minPrice != null, () => html`, ${t('admin.additional-items.price.min')} <sl-format-number type="currency" currency=${item.currency} value=${item.minPrice}></sl-format-number><span></span>`)
            }${
                renderIf(() => item.maxPrice != null, () => html`, ${t('admin.additional-items.price.max')} <sl-format-number type="currency" currency=${item.currency} value=${item.maxPrice}></sl-format-number><span></span>`)
            }</div>`;
        }
        return html`<sl-format-number type="currency" currency=${item.currency} value=${item.finalPrice}></sl-format-number><span></span>`;
    }

    private async editDialogClosed(e: AlfioDialogClosed) {
        this.editActive = false;
        if (e.detail.success) {
            this.triggerListRefresh();
            dispatchFeedback({
                type: 'success',
                message: t('admin.common.operation-completed')
            }, this);
        }
    }

    private triggerListRefresh(): void {
        this.refreshCount++;
    }

    private renderDelete(listData: ListData, item: AdditionalItem, model: Model) {
        return renderIf(() => countUsage(listData, item.id) === 0, () => html`
            <sl-button title=${t('admin.common.delete')} variant="danger" size="small" @click=${() => this.delete(item, model)} type="button"><sl-icon name="trash" slot="prefix"></sl-icon>${t('admin.common.delete')}</sl-button>
        `);
    }
}

function countUsage(listData: ListData, itemId: number): number {
    if (listData.usageCount[itemId] != null) {
        const detail = listData.usageCount[itemId];
        const acquired = detail['ACQUIRED'] ?? 0;
        const checkedIn = detail['CHECKED_IN'] ?? 0;
        const toBePaid = detail['TO_BE_PAID'] ?? 0;
        return acquired + checkedIn + toBePaid;
    }
    return 0;
}

function formatSoldCount(listData: ListData, itemId: number): string {
    if (listData.usageCount[itemId] != null) {
        const detail = listData.usageCount[itemId];
        const acquired = detail['ACQUIRED'] ?? 0;
        const checkedIn = detail['CHECKED_IN'] ?? 0;
        const toBePaid = detail['TO_BE_PAID'] ?? 0;
        const totalSold = acquired + checkedIn + toBePaid;
        if (checkedIn > 0 || toBePaid > 0) {
            return t('admin.additional-items.sold-detail', formatNumber(totalSold), formatNumber(acquired), formatNumber(checkedIn), formatNumber(toBePaid));
        }
        return formatNumber(totalSold);
    }
    return '0';
}

function itemTitle(item: AdditionalItem): string {
    return item.title.map(title => {
        if (title.value === '') {
            return t('admin.additional-items.missing-title', title.locale);
        }
        return title.value;
    }).join(' / ');
}

function missingTitleBadge(item: AdditionalItem): TemplateResult | undefined {
    const missing = item.title.filter(title => title.value === '').map(title => title.locale);
    if (missing.length === 0) {
        return undefined;
    }
    return html`<sl-badge variant="danger" pill>${t('admin.additional-items.title-missing', missing.join(', '))}</sl-badge>`;
}

function showMaxQuantity(item: AdditionalItem): boolean {
    return item.fixPrice && (item.type === 'DONATION' || (!isMandatory(item.supplementPolicy) && item.supplementPolicy !== 'OPTIONAL_UNLIMITED_AMOUNT'));
}

function maxQuantityLabel(item: AdditionalItem): string {
    if (item.supplementPolicy === 'OPTIONAL_MAX_AMOUNT_PER_TICKET') {
        return t('admin.additional-items.max-per-ticket');
    }
    return t('admin.additional-items.max-per-order');
}

function emptyMessage(type: string): string {
    if (type === 'DONATION') {
        return t('admin.additional-items.none.donations');
    }
    return t('admin.additional-items.none.options');
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-additional-item-list': AdditionalItemList
    }
}
