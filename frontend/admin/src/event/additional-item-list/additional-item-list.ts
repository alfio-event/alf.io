import {css, html, LitElement, nothing, TemplateResult} from 'lit';
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
import {cardBgColors, itemsList, pageHeader, textColors} from "../../styles.ts";
import {when} from "lit/directives/when.js";
import {AlfioDialogClosed, dispatchFeedback} from "../../model/dom-events.ts";
import {ConfirmationDialogService} from "../../service/confirmation-dialog.ts";
import {ContentLanguage} from "../../model/purchase-context.ts";

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

    static readonly styles = [pageHeader, cardBgColors, textColors, itemsList, css`

        sl-tab-group {
            height: 100%;
        }

        .panel-content {
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            height: 80px;
        }

        .ps {
            padding-left: 0.5rem;
        }

        .page-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .pb-2 {
            padding-bottom: 2rem;
        }

        .download-container {
            display: flex;
            align-items: center;
            justify-content: end;
        }
    `];

    render() {
        return this.retrievePageDataTask.render({
            initial: () => html`loading...`,
            complete: (model) => html`

                <div class="page-header">
                    <h3>
                        <sl-icon name=${model.icon}></sl-icon> ${model.title}
                    </h3>
                </div>

                ${this.iterateItems(model)}

                <div class="pb-2"></div>
                `
        });
    }

    async addNew(model: Model, count: number): Promise<void> {
        this.editActive = true;
        const ordinal = model.event.supportsAdditionalServicesOrdinal ? count + 1 : 0;
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
            setTimeout(() => document.body.removeChild(div));
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
                "Delete additional option?",
                `Do you want to delete Additional option "${item.title.map(t => t.value).join("/")}"?`,
                'danger'
                );
            if (confirmation) {
                const response = await AdditionalItemService.deleteAdditionalItem(item.id, model.event.id);
                if(response.ok) {
                    dispatchFeedback({
                        type: 'success',
                        message: 'Additional Option successfully deleted'
                    }, this);
                    this.triggerListRefresh();
                    return true;
                } else {
                    dispatchFeedback({
                        type: 'danger',
                        message: 'Cannot delete additional option'
                    }, this);
                }
            }
            return false;
        } catch(e) {
            return false;
        }
    }

    private generateFooter(model: Model, listData: ListData): TemplateResult {
        const warning = () => html`
            <div class="alert alert-warning">
                <p><span class="fa fa-warning"></span> Cannot add <span>${model.type === 'DONATION' ? 'donations' : 'additional options'}</span> to an event marked as "free of charge".</p>
                <p>Please change this setting, add a default price > 0, specify currency and Taxes</p>
            </div>`;
        const footer = () => html`
            <div class="row">
                <div class="col-xs-12" style="font-size: 20px">
                    <sl-button type="button" variant="success" @click=${() => this.addNew(model, listData.items.length)} size="large">
                        <sl-icon name="plus-circle" slot="prefix"></sl-icon>
                        Add new
                    </sl-button>
                </div>
            </div>`;
        return when(model.event.freeOfCharge, warning, () => renderIf(() => !this.editActive, footer));
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

    private iterateItems(model: Model) {

        return model.dataTask.render({
            initial: () => html`loading...`,
            complete: listData => html`
                ${this.displayDownloadButton(listData, model)}
                ${repeat(listData.items, (item) => item.id, (item, index) => {
                    return html`
                    <div id=${`additional-service-${item.id}`}></div>
                    <sl-card class="item bg-default">
                        <div slot="header">
                            <div class="col">${showItemTitle(item)}</div>
                            <div class="text-success"> ${`Confirmed: ${formatSoldCount(listData, item.id)}`}</div>
                        </div>
                        <div slot="footer" class="${model.event.supportsAdditionalServicesOrdinal && listData.items.length > 1 ? 'multiple': ''}">
                            ${this.renderMoveButtons(listData, index, item, model)}
                            <div class="button-container">
                                <sl-button variant="default" title="edit" @click=${() => this.edit(item, model)} type="button"><sl-icon name="pencil" slot="prefix"></sl-icon> edit</sl-button>
                                ${this.renderDelete(listData, item, model)}
                            </div>
                        </div>
                        <div class="body">
                            <div class="info-container">
                                <div class="info">
                                    <strong>Inception</strong>
                                    <sl-format-date date=${item.inception.date + 'T' + item.inception.time} month="long" day="numeric" year="numeric" hour="numeric" minute="numeric"></sl-format-date>
                                </div>
                                <div class="info">
                                    <strong>Expiration</strong>
                                    <sl-format-date date=${item.expiration.date + 'T' + item.expiration.time} month="long" day="numeric" year="numeric" hour="numeric" minute="numeric"></sl-format-date>
                                </div>
                                <div class="info">
                                    <strong>Price</strong>
                                    ${when(item.fixPrice,
                                        () => this.showItemFixPrice(item),
                                        () => html`<span>User-defined</span>`)}
                                </div>
                                ${renderIf(() => item.type === 'SUPPLEMENT', () => html`
                                    <div class="info">
                                        <strong>Policy</strong>
                                        ${supplementPolicyDescriptions[item.supplementPolicy]}
                                    </div>`)}

                                ${renderIf(() => item.fixPrice && (item.type === 'DONATION' || (!isMandatory(item.supplementPolicy) && item.supplementPolicy !== 'OPTIONAL_UNLIMITED_AMOUNT')),
                        () => html`
                                    <div class="info">
                                        <strong>Max Qty per ${item.supplementPolicy === 'OPTIONAL_MAX_AMOUNT_PER_TICKET' ? 'ticket' : 'order'}</strong>
                                        ${item.maxQtyPerOrder}
                                    </div>`)}
                            </div>

                            <sl-tab-group>
                                ${repeat(this.sortContentLanguages(item, model), d => d.locale, (d) => html`
                                    <sl-tab slot="nav" panel=${d.locale}>${d.localeLabel}</sl-tab>
                                    <sl-tab-panel name=${d.locale}>
                                        <div class="panel-content">
                                            <alfio-display-commonmark-preview data-button-text="Preview" data-text=${d.value}></alfio-display-commonmark-preview>
                                            <div class="ps">${d.value}</div>
                                        </div>
                                    </sl-tab-panel>

                                `)}

                            </sl-tab-group>
                        </div>
                    </sl-card>
                `
                })}
                ${this.generateFooter(model, listData)}
            `
        })
    }

    private displayDownloadButton(listData: ListData, model: Model) {
        if (listData.allowDownload) {
            return html`
                <div class="download-container pb-2">
                    <sl-button href=${`/admin/api/events/${model.event.publicIdentifier}/additional-services/${this.type}/export`} target="_blank" rel="noopener">
                                <sl-icon name="download"></sl-icon> Export purchased items
                    </sl-button>
                </div>`;
        }
        return nothing;
    }

    private renderMoveButtons(listData: ListData, index: number, item: AdditionalItem, model: Model) {
        return renderIf(() => model.event.supportsAdditionalServicesOrdinal && listData.items.length > 1, () => html`
            <div class="button-container">
                ${renderIf(() => index > 0 && item.ordinal !== 0, () => html`
                    <sl-button variant="default" title="move up" @click=${() => this.moveItem(model.event.id, listData, item, index, 'up')} type="button"><sl-icon name="arrow-up" slot="prefix"></sl-icon> move up</sl-button>
                `)}
                ${renderIf(() => index < listData.items.length - 1 && item.ordinal !== 0, () => html`
                    <sl-button variant="default" title="move down" @click=${() => this.moveItem(model.event.id, listData, item, index, 'down')} type="button"><sl-icon name="arrow-down" slot="prefix"></sl-icon> move down</sl-button>
                `)}
            </div>
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
                message: 'Operation completed successfully'
            }, this);
            this.triggerListRefresh();
        } else {
            dispatchFeedback({
                type: 'danger',
                message: 'Error while swapping items'
            }, this);
        }
    }

    private showItemFixPrice(item: AdditionalItem): TemplateResult {
        if (isMandatoryPercentage(item.supplementPolicy)) {
            return html`<div>${item.price}%${
                renderIf(() => item.minPrice != null, () => html`, min. <sl-format-number type="currency" currency=${item.currency} value=${item.minPrice}></sl-format-number><span></span>`)
            }${
                renderIf(() => item.maxPrice != null, () => html`, max <sl-format-number type="currency" currency=${item.currency} value=${item.maxPrice}></sl-format-number><span></span>`)
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
                message: 'Operation completed successfully'
            }, this);
        }
    }

    private triggerListRefresh(): void {
        this.refreshCount++;
    }

    private renderDelete(listData: ListData, item: AdditionalItem, model: Model) {
        return renderIf(() => countUsage(listData, item.id) === 0, () => html`
            <sl-button title="delete" variant="danger" @click=${() => this.delete(item, model)} type="button"><sl-icon name="trash" slot="prefix"></sl-icon> delete</sl-button>
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
        return totalSold + (checkedIn > 0 || toBePaid > 0 ? ` (of which ${acquired} Acquired, ${checkedIn} Checked in, ${toBePaid} To be paid on site)` : '');
    }
    return '0';
}

function showItemTitle(item: AdditionalItem): TemplateResult {
    return html`${repeat(item.title, title => title.id, (title, index) => {
        return html`
                <span .title=${title.locale}>
                    <span class=${title.value === '' ? 'text-danger' : ''}>${ title.value !== '' ? title.value : `!! missing ${title.locale} !!` }</span>
                    ${when(index + 1 < item.title.length, () => html`<span> / </span>`, () => nothing)}
                </span>`
    })}`;
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-additional-item-list': AdditionalItemList
    }
}
