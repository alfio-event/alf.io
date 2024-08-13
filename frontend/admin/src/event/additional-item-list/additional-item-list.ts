import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js'
import {repeat} from 'lit/directives/repeat.js';
import {AdditionalItemService, UsageCount} from "../../service/additional-item.ts";
import {Task} from "@lit/task";
import {msg, localized, str} from '@lit/localize';
import {AlfioEvent, ContentLanguage} from "../../model/event.ts";
import {
    AdditionalItem,
    AdditionalItemType, isMandatory,
    isMandatoryPercentage,
    supplementPolicyDescriptions
} from "../../model/additional-item.ts";
import {EventService} from "../../service/event.ts";
import {renderIf, supportedLanguages} from "../../service/helpers.ts";
import {pageHeader, textColors} from "../../styles.ts";
import {when} from "lit/directives/when.js";
import {AlfioDialogClosed, dispatchFeedback} from "../../model/dom-events.ts";
import {ConfirmationDialogService} from "../../service/confirmation-dialog.ts";

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

@customElement('alfio-additional-item-list')
@localized()
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
    allowDownload: boolean = false;
    @state()
    refreshCount: number = 0;

    private retrievePageDataTask = new Task<ReadonlyArray<string>, Model>(this,
        async ([publicIdentifier]) => {
            const event = (await EventService.load(publicIdentifier)).event;
            const dataTask = new Task<ReadonlyArray<number>, ListData>(this, async ([]) => {
                const [items, count] = await Promise.all([AdditionalItemService.loadAll({eventId: event.id}), AdditionalItemService.useCount(event.id)]);
                return {
                    items: items.filter(i => i.type === this.type),
                    usageCount: count,
                    allowDownload: Object.values(count).some(p => Object.values(p).reduce((pv: number, cv: number) => pv + cv) > 0),
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

    static styles = [pageHeader, textColors, css`
        .item {
            width: 100%;
            margin-bottom: 1rem;
        }
        .item [slot='header'] {
            display: flex;
            align-items: center;
            justify-content: space-between;
        }
        .item [slot='footer'] {
            display: flex;
            align-items: center;
            justify-content: end;
            gap: 1em;
        }

        .item .body {
            display: grid;
            row-gap: 0.5rem;
        }

        .item .body .info-container {
            display: grid;
            row-gap: 0.5rem;
        }

        .item .body .info-container .info {
            display: grid;
            grid-template-columns: 0.5fr 1.3fr;
            grid-auto-rows: auto;
            column-gap: 3rem;
        }


        @media only screen and (min-width: 768px) {
            .item > .body {
                grid-template-columns: 1fr 1.3fr;
                grid-auto-rows: auto;
                column-gap: 3rem;
            }
        }

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
    `];

    render() {
        return this.retrievePageDataTask.render({
            initial: () => html`loading...`,
            complete: (model) => html`

                <div class="page-header">
                    <h3>
                        <sl-icon name=${model.icon}></sl-icon> ${model.title}
                    </h3>
                    ${ this.allowDownload ?
                        html`<sl-button href=${`/admin/api/events/${model.event.publicIdentifier}/additional-services/${this.type}/export`} target="_blank" rel="noopener">
                                <sl-icon name="download"></sl-icon> ${msg('Export purchased items')}
                            </sl-button>` : nothing}
                </div>

                ${this.iterateItems(model)}

                ${this.generateFooter(model)}

                `
        });
    }

    async addNew(model: Model): Promise<void> {
        this.editActive = true;
        await this.openEditDialog(model, null);
    }

    async edit(item: AdditionalItem, model: Model): Promise<void> {
        this.editActive = true;
        await this.openEditDialog(model, item);
    }

    private async openEditDialog(model: Model, item: AdditionalItem | null): Promise<void> {
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
            editedItem: item
        });

    }

    async delete(item: AdditionalItem, model: Model): Promise<boolean> {
        try {
            const confirmation = await ConfirmationDialogService.requestConfirm(
                msg("Delete additional option?"),
                msg(str`Do you want to delete Additional option "${item.title.map(t => t.value).join("/")}"?`),
                'danger'
                );
            if (confirmation) {
                const response = await AdditionalItemService.deleteAdditionalItem(item.id, model.event.id);
                if(response.ok) {
                    dispatchFeedback({
                        type: 'success',
                        message: msg('Additional Option successfully deleted')
                    }, this);
                    this.triggerListRefresh();
                } else {
                    dispatchFeedback({
                        type: 'danger',
                        message: msg('Cannot delete additional option')
                    }, this);
                }
            }
            return false;
        } catch(e) {
            return false;
        }
    }

    private generateFooter(model: Model): TemplateResult {
        const warning = () => html`
            <div class="alert alert-warning">
                <p><span class="fa fa-warning"></span> ${msg(html`Cannot add <span>${model.type === 'DONATION' ? 'donations' : 'additional options'}</span> to an event marked as "free of charge".`)}</p>
                <p>${msg('Please change this setting, add a default price > 0, specify currency and Taxes')}</p>
            </div>`;
        const footer = () => html`
            <div class="row">
                <div class="col-xs-12" style="font-size: 20px">
                    <sl-button type="button" variant="success" @click=${() => this.addNew(model)} size="large">
                        <sl-icon name="plus-circle" slot="prefix"></sl-icon>
                        ${msg('Add new')}
                    </sl-button>
                </div>
            </div>`;
        return when(model.event.freeOfCharge, warning, () => renderIf(() => !this.editActive, footer));
    }

    private iterateItems(model: Model) {
        return model.dataTask.render({
            initial: () => html`loading...`,
            complete: listData => html`${repeat(listData.items, (item) => item.id, (item) => {
                return html`
                <div id=${`additional-service-${item.id}`}></div>
                <sl-card class="item">
                    <div slot="header">
                        <div class="col">${showItemTitle(item)}</div>
                        <div class="text-success"> ${`Confirmed: ${formatSoldCount(listData, item.id)}`}</div>
                    </div>
                    <div slot="footer">
                        <sl-button variant="default" title="edit" @click=${() => this.edit(item, model)} type="button"><sl-icon name="pencil" slot="prefix"></sl-icon> edit</sl-button>
                        ${renderIf(() => countUsage(listData, item.id) === 0, () => html`<sl-button title="delete" variant="danger" @click=${() => this.delete(item, model)} type="button"><sl-icon name="trash" slot="prefix"></sl-icon> delete</sl-button>`)}
                    </div>
                    <div class="body">
                        <div class="info-container">
                            <div class="info">
                                <strong>${msg('Inception')}</strong>
                                <sl-format-date date=${item.inception.date + 'T' + item.inception.time} month="long" day="numeric" year="numeric" hour="numeric" minute="numeric"></sl-format-date>
                            </div>
                            <div class="info">
                                <strong>${msg('Expiration')}</strong>
                                <sl-format-date date=${item.expiration.date + 'T' + item.expiration.time} month="long" day="numeric" year="numeric" hour="numeric" minute="numeric"></sl-format-date>
                            </div>
                            <div class="info">
                                <strong>${msg('Price')}</strong>
                                ${when(item.fixPrice,
                                    () => this.showItemFixPrice(item),
                                    () => html`<span>${msg('User-defined')}</span>`)}
                            </div>
                            ${renderIf(() => item.type === 'SUPPLEMENT', () => html`
                                <div class="info">
                                    <strong>${msg('Policy')}</strong>
                                    ${supplementPolicyDescriptions[item.supplementPolicy]}
                                </div>`)}

                            ${renderIf(() => item.fixPrice && (item.type === 'DONATION' || (!isMandatory(item.supplementPolicy) && item.supplementPolicy !== 'OPTIONAL_UNLIMITED_AMOUNT')),
                    () => html`
                                <div class="info">
                                    <strong>${msg(str`Max Qty per ${item.supplementPolicy === 'OPTIONAL_MAX_AMOUNT_PER_TICKET' ? 'ticket' : 'order'}`)}</strong>
                                    ${item.maxQtyPerOrder}
                                </div>`)}
                        </div>

                        <sl-tab-group>
                            ${repeat(item.description, d => d.id, (d) => html`
                                <sl-tab slot="nav" panel=${d.locale}>${d.locale}</sl-tab>
                                <sl-tab-panel name=${d.locale}>
                                    <div class="panel-content">
                                        <alfio-display-commonmark-preview data-button-text="${msg('Preview')}" data-text=${d.value}></alfio-display-commonmark-preview>
                                        <div class="ps">${d.value}</div>
                                    </div>
                                </sl-tab-panel>

                            `)}

                        </sl-tab-group>
                    </div>
                </sl-card>
            `
            })}`
        })
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
