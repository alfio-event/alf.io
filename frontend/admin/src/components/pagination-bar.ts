import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import { formatNumber, t } from '../service/i18n.ts';

export type AlfioPageChange = CustomEvent<{ page: number }>;

/**
 * Pagination controls with a "x of y items" summary.
 * Pages are 1-based. Fires "alfio-page-change" when the user selects a different page.
 */
@customElement('alfio-pagination-bar')
export class PaginationBar extends LitElement {
    @property({ type: Number }) page = 1;
    @property({ type: Number }) total = 0;
    @property({ type: Number, attribute: 'page-size' }) pageSize = 50;
    // plural label of the paginated items, e.g. "payments"
    @property({ type: String, attribute: 'item-label' }) itemLabel = t('admin.pagination.items');

    static readonly styles = css`
        :host {
            display: block;
        }
        nav {
            display: flex;
            align-items: center;
            justify-content: flex-end;
            gap: var(--sl-spacing-2x-small);
            padding-block: var(--sl-spacing-medium);
        }
        .summary {
            flex: 1;
            order: -1;
            text-align: left;
            color: var(--sl-color-neutral-600);
            font-size: var(--sl-font-size-small);
            margin: 0;
        }
        .ellipsis {
            color: var(--sl-color-neutral-500);
            padding-inline: var(--sl-spacing-x-small);
        }
    `;

    render(): TemplateResult {
        const lastPage = Math.max(1, Math.ceil(this.total / this.pageSize));
        const page = Math.min(this.page, lastPage);
        const displayed = Math.min(this.pageSize, Math.max(0, this.total - (page - 1) * this.pageSize));
        const visiblePages = Array.from({ length: lastPage }, (_, index) => index + 1)
            .filter((number) => number === 1 || number === lastPage || Math.abs(number - page) <= 2);
        return html`
            <nav aria-label=${t('admin.pagination.label', this.itemLabel)}>
                <span class="summary">
                    ${when(lastPage > 1, () => html`${t('admin.pagination.page-of', formatNumber(page), formatNumber(lastPage))} · `)}${t('admin.pagination.shown', formatNumber(displayed), formatNumber(this.total), this.itemLabel)}
                </span>
                <sl-button size="small" variant="default" outline ?disabled=${page === 1}
                           @click=${() => this.changePage(page - 1)}>
                    ${t('admin.pagination.previous')}
                </sl-button>
                ${visiblePages.map((number, index) => html`
                    ${when(index > 0 && number - visiblePages[index - 1] > 1,
                        () => html`<span class="ellipsis" aria-hidden="true">…</span>`)}
                    ${when(number === page,
                        () => html`<sl-button size="small" variant="primary" aria-label=${t('admin.pagination.page', number)} aria-current="page">${number}</sl-button>`,
                        () => html`<sl-button size="small" variant="default" outline aria-label=${t('admin.pagination.page', number)} @click=${() => this.changePage(number)}>${number}</sl-button>`)}
                `)}
                <sl-button size="small" variant="default" outline ?disabled=${page >= lastPage}
                           @click=${() => this.changePage(page + 1)}>
                    ${t('admin.pagination.next')}
                </sl-button>
            </nav>
        `;
    }

    private changePage(page: number): void {
        if (page === this.page) {
            return;
        }
        this.dispatchEvent(new CustomEvent('alfio-page-change', {
            detail: { page },
            bubbles: true,
            composed: true,
        }));
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-pagination-bar': PaginationBar;
    }
    interface GlobalEventHandlersEventMap {
        'alfio-page-change': AlfioPageChange;
    }
}
