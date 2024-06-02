import {html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js'
import {repeat} from 'lit/directives/repeat.js';
import {classMap} from 'lit/directives/class-map.js';
import {AdditionalItemService, UsageCount} from "../../service/additional-item.ts";
import {Task} from "@lit/task";
import {AlfioEvent, ContentLanguage} from "../../model/event.ts";
import {AdditionalItem, AdditionalItemType} from "../../model/additional-item.ts";
import {EventService} from "../../service/event.ts";
import {renderIf, supportedLanguages} from "../../service/helpers.ts";
import {pageHeader} from "../../styles.ts";
import {when} from "lit/directives/when.js";
import {SlCloseEvent} from "@shoelace-style/shoelace";
import {AdditionalItemEdit} from "../additional-item-edit/additional-item-edit.ts";

interface Model {
    items: Array<AdditionalItem>;
    event: AlfioEvent;
    title: string,
    icon: string,
    type: AdditionalItemType,
    supportedLanguages: ContentLanguage[],
    usageCount: UsageCount,
    allowDownload: boolean
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
    @property({ type: Number })
    editedItemId: number | null = null;
    @state()
    editActive: boolean = false;


    private retrieveListTask = new Task<ReadonlyArray<string>, Model>(this,
        async ([publicIdentifier]) => {
            const event = (await EventService.load(publicIdentifier)).event;
            const [items, count] = await Promise.all([AdditionalItemService.loadAll(event.id), AdditionalItemService.useCount(event.id)]);
            return {
                items: items.filter(i => i.type === this.type),
                event,
                title: this.pageTitle ?? '',
                icon: this.icon ?? '',
                type: this.type!,
                supportedLanguages: supportedLanguages(),
                usageCount: count,
                allowDownload: Object.values(count).some(p => Object.values(p).reduce((pv: number, cv: number) => pv + cv) > 0),
            };
        },
        () => [this.publicIdentifier!]);

    static styles = [pageHeader];

    @query("alfio-additional-item-edit")
    itemEditComponent?: AdditionalItemEdit;

    render() {

        return this.retrieveListTask.render({
            initial: () => html`loading...`,
            complete: (model) => html`
                <div class="page-header">
                    <h3>
                        <sl-icon name=${model.icon}></sl-icon> ${model.title}
                        ${ model.allowDownload ?
                            html`<sl-button href=${`/admin/api/events/${model.event.publicIdentifier}/additional-services/${this.type}/export`} target="_blank" rel="noopener">
                                    <sl-icon name="download"></sl-icon> Export purchased items
                                </sl-button>` : nothing}
                    </h3>
                </div>

                <alfio-additional-item-edit
                    .event=${model.event}
                    .supportedLanguages=${model.event.contentLanguages}
                    data-item-id=${this.editedItemId}
                    data-type=${this.type}
                    @sl-after-hide=${this.editDialogClosed}></alfio-additional-item-edit>

                ${this.iterateItems(model)}

                ${this.generateFooter(model)}

                `
        });
    }

    async addNew(): Promise<void> {
        this.editedItemId = null;
        if (this.itemEditComponent != null) {
            this.editActive = await this.itemEditComponent.open();
        }
    }

    async edit(item: AdditionalItem): Promise<void> {
        this.editedItemId = item.id;
        if (this.itemEditComponent != null) {
            this.editActive = await this.itemEditComponent.open();
        }
    }

    delete(item: AdditionalItem): void {
        console.log('delete item', item.id);
    }

    private generateFooter(model: Model): TemplateResult {
        const warning = () => html`
            <div class="alert alert-warning">
                <p><span class="fa fa-warning"></span> Cannot add <span>${model.type === 'DONATION' ? 'donations' : 'additional options'}</span> to an event marked as "free of charge".</p>
                <p>Please change this setting, add a default price > 0, specify currency and Taxes</p>
            </div>`;
        const footer = () => html`
            <div class="row">
                <div class="col-xs-12" style="font-size: 20px">
                    <sl-button type="button" variant="success" @click=${this.addNew} size="large">
                        <sl-icon name="plus-circle" slot="prefix"></sl-icon>
                        Add new
                    </sl-button>
                </div>
            </div>`;
        return when(model.event.freeOfCharge, warning, () => renderIf(() => !this.editActive, footer));
    }

    private iterateItems(model: Model): TemplateResult {
        return html`${repeat(model.items, (item) => item.id, (item, index) => {
            return html`
                <div class="panel panel-default">
                    <div data-ng-if="item.id" id="additional-service-${item.id}"></div>
                    <div class="panel-heading">
                        <div class="panel-title">
                            <div class="row">
                                <div class="col-xs-9">
                                    <h4>
                                        ${showItemTitle(item)}
                                        <span style="margin-left: 20px;" class="text-success"> ${`Confirmed: ${formatSoldCount(model, item.id)}`}</span>
                                    </h4>
                                </div>
                                <div class="col-xs-3">
                                    <div class="pull-right">
                                        <sl-button variant="default" title="edit" @click=${() => this.edit(item)} type="button"><sl-icon name="edit" slot="prefix"></sl-icon> edit</sl-button>
                                        ${renderIf(() => countUsage(model, item.id) === 0, () => html`<sl-button title="delete" @click=${() => this.delete(item)} type="button"><sl-icon name="trash" slot="prefix"></sl-icon> delete</sl-button>`)}
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="panel-body">
                        <div class="row">
                            <div class="col-xs-12 col-md-5">
                                <div class="row">
                                    <div class="col-sm-4"><strong>Inception</strong></div>
                                    <div class="col-sm-8">
                                        <sl-format-date date=${item.inception.date + 'T' + item.inception.time} month="long" day="numeric" year="numeric" hour="numeric" minute="numeric"></sl-format-date>
                                    </div>
                                </div>
                                <div class="row">
                                    <div class="col-sm-4"><strong>Expiration</strong></div>
                                    <sl-format-date date=${item.expiration.date + 'T' + item.expiration.time} month="long" day="numeric" year="numeric" hour="numeric" minute="numeric"></sl-format-date>
                                </div>
                                <div class="row">
                                    <div class="col-sm-4"><strong>Price</strong></div>
                                    <div class="col-sm-8">
                                        ${when(item.fixPrice,
                                            () => html`<sl-format-number type="currency" currency=${item.currency} value=${item.finalPrice}></sl-format-number><span></span>`,
                                            () => html`<span>User-defined</span>`)}
                                    </div>
                                </div>
                                <div class="row">
                                    <div class="col-sm-4"><strong>Type</strong></div>
                                    <div class="col-sm-8">${item.type}</div>
                                </div>
                                ${renderIf(() => item.type === 'SUPPLEMENT', () => html`<div class="row">
                                    <div class="col-sm-4"><strong>Policy</strong></div>
                                    <div class="col-sm-8">${item.supplementPolicy}</div>
                                </div>`)}

                                ${renderIf(() => item.fixPrice && (item.supplementPolicy !== 'MANDATORY_ONE_FOR_TICKET' && item.supplementPolicy !== 'OPTIONAL_UNLIMITED_AMOUNT'),
                                    () => html`<div class="row">
                                        <div class="col-sm-4"><strong>Max Qty per ${item.supplementPolicy === 'OPTIONAL_MAX_AMOUNT_PER_TICKET' ? 'ticket' : 'order'}</strong></div>
                                        <div class="col-sm-8">${item.maxQtyPerOrder}</div>
                                    </div>`)}
                            </div>
                            <div class="hidden-xs col-md-7">
                                ${repeat(item.description, d => d.id, (d) => html`
                                    <p class=${classMap({'text-muted': true, 'text-danger': d.value === ''})} title=${d.locale}>${d.locale} <alfio-display-commonmark-preview data-button-text="View description" data-text=${d.value}></alfio-display-commonmark-preview></p>
                                `)}
                            </div>
                        </div>
                    </div>
                </div>
                ${renderIf(() => index + 1 < model.items.length, () => html`<sl-divider></sl-divider>`)}
            `
        })}`;
    }

    private editDialogClosed(e: SlCloseEvent) {
        console.log(e.detail);
        this.editedItemId = null;
        this.editActive = false;
        // TODO refresh list using task
    }
}

function countUsage(model: Model, itemId: number): number {
    if (model.usageCount[itemId] != null) {
        const detail = model.usageCount[itemId];
        const acquired = detail['ACQUIRED'] ?? 0;
        const checkedIn = detail['CHECKED_IN'] ?? 0;
        const toBePaid = detail['TO_BE_PAID'] ?? 0;
        return acquired + checkedIn + toBePaid;
    }
    return 0;
}

function formatSoldCount(model: Model, itemId: number): string {
    if (model.usageCount[itemId] != null) {
        const detail = model.usageCount[itemId];
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
