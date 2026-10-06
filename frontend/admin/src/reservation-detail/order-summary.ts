import {css, html, TemplateResult} from 'lit';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {OrderSummary, SummaryRow} from '../model/reservation-detail.ts';
import {humanize} from '../service/helpers.ts';

/* styles of orderSummaryTable(). Use with "modernTable" and "reservationTable" */
export const orderSummary = css`
    .order-summary .quantity {
        text-align: center;
    }
    .order-summary th.amount {
        text-align: right;
    }
    .order-summary .summary-row td {
        font-weight: var(--sl-font-weight-semibold);
    }
    .order-summary .total-row td {
        border-top: 2px solid var(--sl-color-gray-300);
        font-size: var(--sl-font-size-large);
        font-weight: var(--sl-font-weight-bold);
    }
`;

/**
 * Items, taxes and total of a reservation. Amounts are already formatted by the backend.
 */
export function orderSummaryTable(summary: OrderSummary, currencyCode: string): TemplateResult {
    const showTaxes = !summary.vatExempt;
    return html`
        <div class="table-responsive order-summary">
            <table class="table">
                <thead>
                    <tr>
                        <th>Item</th>
                        <th class="hide-small">Type</th>
                        <th class="quantity">Qty</th>
                        <th class="amount">Price</th>
                        <th class="amount">Subtotal (${currencyCode})</th>
                    </tr>
                </thead>
                <tbody>
                    ${repeat(summary.summary, (row, index) => `${row.type}-${index}`, (row) => renderRow(row))}
                    ${when(showTaxes && summary.vatStatus === 'NOT_INCLUDED', () => taxesRow('Taxes', summary))}
                    <tr class="total-row">
                        <td colspan="4">Total</td>
                        <td class="amount">${summary.totalPrice}</td>
                    </tr>
                    ${when(showTaxes && summary.vatStatus === 'INCLUDED', () => taxesRow('Taxes (included)', summary))}
                </tbody>
            </table>
        </div>
    `;
}

function renderRow(row: SummaryRow): TemplateResult {
    if (row.type === 'TAX_DETAIL') {
        return html`
            <tr class="summary-row">
                <td colspan="4">Taxes</td>
                <td class="amount">${row.subTotal}</td>
            </tr>
        `;
    }
    return html`
        <tr>
            <td>${row.name}</td>
            <td class="hide-small">${humanize(row.type)}</td>
            <td class="quantity">${row.amount}</td>
            <td class="amount">${row.price}</td>
            <td class="amount">${row.subTotal}</td>
        </tr>
    `;
}

function taxesRow(label: string, summary: OrderSummary): TemplateResult {
    return html`
        <tr class="summary-row">
            <td>${label}</td>
            <td class="hide-small"></td>
            <td class="quantity">${summary.vatPercentage} %</td>
            <td></td>
            <td class="amount">${summary.totalVAT}</td>
        </tr>
    `;
}
