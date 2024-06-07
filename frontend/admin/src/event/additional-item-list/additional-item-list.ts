import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js'
import {repeat} from 'lit/directives/repeat.js';
import {AdditionalItemService, UsageCount} from "../../service/additional-item.ts";
import {Task} from "@lit/task";
import {AlfioEvent, ContentLanguage} from "../../model/event.ts";
import {AdditionalItem, AdditionalItemType, supplementPolicyDescriptions} from "../../model/additional-item.ts";
import {EventService} from "../../service/event.ts";
import {renderIf, supportedLanguages} from "../../service/helpers.ts";
import {pageHeader, textColors} from "../../styles.ts";
import {when} from "lit/directives/when.js";
import {AdditionalItemEdit} from "../additional-item-edit/additional-item-edit.ts";
import {AlfioDialogClosed} from "../../model/dom-events.ts";

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
            const [items, count] = await Promise.all([AdditionalItemService.loadAll({eventId: event.id}), AdditionalItemService.useCount(event.id)]);
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

    @query("alfio-additional-item-edit")
    itemEditComponent?: AdditionalItemEdit;

    render() {

        return this.retrieveListTask.render({
            initial: () => html`loading...`,
            complete: (model) => html`
                <div class="page-header">
                    <h3>
                        <sl-icon name=${model.icon}></sl-icon> ${model.title}
                    </h3>
                    ${ model.allowDownload ?
                        html`<sl-button href=${`/admin/api/events/${model.event.publicIdentifier}/additional-services/${this.type}/export`} target="_blank" rel="noopener">
                                <sl-icon name="download"></sl-icon> Export purchased items
                            </sl-button>` : nothing}
                </div>

                <alfio-additional-item-edit
                    .event=${model.event}
                    .supportedLanguages=${model.event.contentLanguages}
                    data-item-id=${this.editedItemId}
                    data-type=${this.type}
                    @alfio-dialog-closed=${this.editDialogClosed}></alfio-additional-item-edit>

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
        return html`${repeat(model.items, (item) => item.id, (item) => {
            return html`
                <div id=${`additional-service-${item.id}`}></div>
                <sl-card class="item">
                    <div slot="header">
                        <div class="col">${showItemTitle(item)}</div>
                        <div class="text-success"> ${`Confirmed: ${formatSoldCount(model, item.id)}`}</div>
                    </div>
                    <div slot="footer">
                        <sl-button variant="default" title="edit" @click=${() => this.edit(item)} type="button"><sl-icon name="pencil" slot="prefix"></sl-icon> edit</sl-button>
                        ${renderIf(() => countUsage(model, item.id) === 0, () => html`<sl-button title="delete" variant="danger" @click=${() => this.delete(item)} type="button"><sl-icon name="trash" slot="prefix"></sl-icon> delete</sl-button>`)}
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
                            () => html`<sl-format-number type="currency" currency=${item.currency} value=${item.finalPrice}></sl-format-number><span></span>`,
                            () => html`<span>User-defined</span>`)}
                            </div>
                            ${renderIf(() => item.type === 'SUPPLEMENT', () => html`
                                <div class="info">
                                    <strong>Policy</strong>
                                    ${supplementPolicyDescriptions[item.supplementPolicy]}
                                </div>`)}

                            ${renderIf(() => item.fixPrice && (item.supplementPolicy !== 'MANDATORY_ONE_FOR_TICKET' && item.supplementPolicy !== 'OPTIONAL_UNLIMITED_AMOUNT'),
                        () => html`
                                <div class="info">
                                    <strong>Max Qty per ${item.supplementPolicy === 'OPTIONAL_MAX_AMOUNT_PER_TICKET' ? 'ticket' : 'order'}</strong>
                                    ${item.maxQtyPerOrder}
                                </div>`)}
                        </div>

                        <sl-tab-group>
                            ${repeat(item.description, d => d.id, (d) => html`
                                <sl-tab slot="nav" panel=${d.locale}>${d.locale}</sl-tab>
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
        })}`;
    }

    private editDialogClosed(e: AlfioDialogClosed) {
        this.editedItemId = null;
        this.editActive = false;
        if (e.detail.success) {
            // TODO refresh list using task
            // TODO show notification
        }
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
