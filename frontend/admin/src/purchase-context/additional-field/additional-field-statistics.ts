import {customElement, query, state} from "lit/decorators.js";
import {css, html, LitElement} from "lit";
import {PurchaseContext} from "../../model/purchase-context.ts";
import {Task} from "@lit/task";
import {AdditionalFieldService} from "../../service/additional-field.ts";
import {AdditionalField, AdditionalFieldStats} from "../../model/additional-field.ts";
import {repeat} from "lit/directives/repeat.js";
import {SlDrawer} from "@shoelace-style/shoelace";
import {renderIf} from "../../service/helpers.ts";
import {textAlign} from "../../styles.ts";


@customElement('alfio-additional-field-statistics')
export class AdditionalFieldStatistics extends LitElement {

    field: AdditionalField | null = null;
    purchaseContext: PurchaseContext | null = null;

    @state()
    active = false;

    @query("sl-drawer#drawer-statistics")
    drawer?: SlDrawer;

    static readonly styles = [textAlign, css`
        sl-drawer {
            --sl-z-index-drawer: 1031;
        }
        table {
            width: 100%;
        }
        table.table-striped th, table.table-striped tr:nth-child(even) {
            background-color: #f2f2f2;
        }

        .table-striped tr {
            height: 2rem;
        }

        .table-striped td, .table-striped th {
            padding: 12px 15px;
        }

        table.table-striped th {
            font-weight: bold;
            text-align: center;
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
            <sl-drawer label=${`Statistics for ${this.field?.name}`} id="drawer-statistics">
               ${renderIf(() => this.active, () => this.retrievePageDataTask.render({
                   initial: () => html`<sl-spinner></sl-spinner>`,
                   complete: stats => html`
                    <table class="table table-striped">
                        <thead>
                            <tr>
                                <th>Option</th>
                                <th>Count</th>
                                <th>Percentage</th>
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
            `
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
        this.drawer?.addEventListener('sl-after-hide', async () => {
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
