import {LitElement, html, nothing, TemplateResult} from 'lit';
import { customElement, property } from 'lit/decorators.js'
import { repeat } from 'lit/directives/repeat.js';
import {AdditionalItemService, UsageCount} from "../../service/additional-item.ts";
import {Task} from "@lit/task";
import {AlfioEvent, ContentLanguage} from "../../model/event.ts";
import {AdditionalItem, AdditionalItemType} from "../../model/additional-item.ts";
import {EventService} from "../../service/event.ts";
import {renderIf, supportedLanguages} from "../../service/helpers.ts";
import {pageHeader} from "../../styles.ts";
import {when} from "lit/directives/when.js";

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

    render() {

        return this.retrieveListTask.render({
            initial: () => html`loading...`,
            complete: (model) => html`
                <div class="page-header">
                    <h3>
                        <sl-icon name=${model.icon}></sl-icon> ${model.title}
                        ${ model.allowDownload ?
                            html`<a class="btn btn-default pull-right" href="/admin/api/events/${model.event.publicIdentifier}/additional-services/${this.type}}/export" target="_blank" rel="noopener">
                                <i class="fa fa-download"></i> Export purchased items
                            </a>` : nothing}
                    </h3>
                </div>
                ${this.iterateItems(model)}

                ${this.generateFooter(model)}

                `
        });
    }

    addNew(): void {
        console.log('create new');
    }

    edit(item: AdditionalItem): void {
        console.log('edit item', item.id);
    }

    private generateFooter(model: Model): TemplateResult {
        const warning = () => html`
            <div class="alert alert-warning" data-ng-if="ctrl.eventIsFreeOfCharge">
                <p><span class="fa fa-warning"></span> Cannot add <span>${model.type === 'DONATION' ? 'donations' : 'additional options'}</span> to an event marked as "free of charge".</p>
                <p>Please change this setting, add a default price > 0, specify currency and Taxes</p>
            </div>`;
        const footer = () => html`
            <div class="row">
                <div class="col-xs-12">
                    <button type="button" class="btn btn-success" @click=${this.addNew} data-ng-if="!ctrl.editActive"><i class="fa fa-plus"></i> Add new</button></div>
            </div>`;
        return when(model.event.freeOfCharge, warning, footer);
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
                                        <button class="btn btn-sm btn-default" title="edit" @click=${() => this.edit(item)} type="button"><i class="fa fa-edit"></i> edit</button>
                                        ${renderIf(() => countUsage(model, item.id) > 0, () => html`<button class="btn btn-sm btn-default" ng-if="!(ctrl.additionalServiceUseCount[item.id] > 0)" title="delete" data-ng-click="ctrl.delete(item)" type="button"><i class="fa fa-trash"></i> delete</button>`)}
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
                                <p class="text-muted" data-ng-repeat="pair in item.zippedTitleAndDescriptions" title="{{pair[1].locale}}" ng-class="{'text-danger': pair[1].value === ''}">{{pair[1].locale}} <display-commonmark-preview button-text="View description" text="pair[1].value"></display-commonmark-preview></p>
                            </div>
                        </div>
                    </div>
                </div>
                ${renderIf(() => index + 1 < model.items.length, () => html`<sl-divider></sl-divider>`)}
            `
        })}`;
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
