import {customElement, query, state} from "lit/decorators.js";
import {css, html, LitElement} from "lit";
import {PurchaseContext} from "../../model/purchase-context.ts";
import {Task} from "@lit/task";
import {AdditionalFieldService} from "../../service/additional-field.ts";
import {AdditionalField, AdditionalFieldStats} from "../../model/additional-field.ts";
import {repeat} from "lit/directives/repeat.js";
import {when} from "lit/directives/when.js";
import {SlDrawer} from "@shoelace-style/shoelace";
import {renderIf} from "../../service/helpers.ts";
import {modernLayout, modernTable, textAlign} from "../../styles.ts";
import {emptyState} from "../../components/empty-state.ts";
import { t } from '../../service/i18n';


@customElement('alfio-additional-field-statistics')
export class AdditionalFieldStatistics extends LitElement {

    field: AdditionalField | null = null;
    purchaseContext: PurchaseContext | null = null;

    @state()
    active = false;

    @query("sl-drawer#drawer-statistics")
    drawer?: SlDrawer;

    static readonly styles = [textAlign, modernLayout, modernTable, css`
        sl-drawer {
            --sl-z-index-drawer: 1031;
        }

        .table {
            width: 100%;
        }
    `]

    private readonly retrievePageDataTask = new Task<ReadonlyArray<boolean>, ReadonlyArray<AdditionalFieldStats>>(this,
        async () => {
            if (this.field?.id != null) {
                return await AdditionalFieldService.loadRestrictedValuesStats(this.purchaseContext!, this.field!.id);
            }
            return [];
        },
        () => [this.active]);

    protected render(): unknown {

        return html`
            <sl-drawer label=${t('admin.additional-fields.statistics.title', this.field?.name ?? '')} id="drawer-statistics">
               ${renderIf(() => this.active, () => this.retrievePageDataTask.render({
                   initial: () => html`<div class="loading"><sl-spinner></sl-spinner></div>`,
                   pending: () => html`<div class="loading"><sl-spinner></sl-spinner></div>`,
                   error: () => html`
                       <sl-alert open variant="danger">
                           <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                           ${t('admin.additional-fields.statistics.load-failed')}
                       </sl-alert>
                   `,
                   complete: stats => when(stats.length === 0, () => emptyState(t('admin.additional-fields.statistics.none')), () => html`
                    <div class="table-responsive">
                    <table class="table table-striped">
                        <thead>
                            <tr>
                                <th>${t('admin.poll.option')}</th>
                                <th>${t('admin.additional-fields.statistics.count')}</th>
                                <th>${t('admin.promo-code.discount-type.percentage')}</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${repeat(stats, s => s.name, (s) => html`
                                <tr>
                                    <td>${s.name}</td>
                                    <td class="text-end">${s.count}</td>
                                    <td class="text-end">${s.percentage}%</td>
                                </tr>
                            `)}
                        </tbody>
                    </table>
                    </div>
            `)
               })!)}
            </sl-drawer>
        `;
    }

    public async show(request: {
        purchaseContext: PurchaseContext,
        field: AdditionalField
    }) {
        if (this.drawer != null) {
            this.field = request.field;
            this.purchaseContext = request.purchaseContext;
            this.active = true;
            await this.drawer?.show();
        }
        this.drawer?.addEventListener('sl-after-hide', async (event: Event) => {
            // nested Shoelace components emit their own sl-after-hide
            if (event.target !== event.currentTarget) {
                return;
            }
            this.active = false;
            this.dispatchEvent(new CustomEvent('alfio-drawer-closed'));
        })
        return this.drawer != null;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-additional-field-statistics': AdditionalFieldStatistics
    }
}
