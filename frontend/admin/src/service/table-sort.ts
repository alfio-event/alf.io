import {html, TemplateResult} from 'lit';
import {when} from 'lit/directives/when.js';

export type SortDirection = 'asc' | 'desc';

export interface SortState<K extends string> {
    sortKey: K;
    sortDirection: SortDirection;
}

export type SortValue = string | number | null | undefined;

/**
 * Returns the sort state after the user has selected the given key:
 * a new key is sorted ascending, the current one toggles its direction.
 */
export function nextSort<K extends string>(current: SortState<K>, sortKey: K): SortState<K> {
    if (current.sortKey === sortKey && current.sortDirection === 'asc') {
        return { sortKey, sortDirection: 'desc' };
    }
    return { sortKey, sortDirection: 'asc' };
}

/**
 * Returns a sorted copy of the given items. Numbers are compared numerically, everything else as text.
 * Missing values come first in ascending order.
 */
export function sortItems<T, K extends string>(items: T[],
                                               sort: SortState<K>,
                                               valueOf: (item: T, key: K) => SortValue): T[] {
    const multiplier = sort.sortDirection === 'asc' ? 1 : -1;
    return [...items].sort((a, b) => compareValues(valueOf(a, sort.sortKey), valueOf(b, sort.sortKey)) * multiplier);
}

function compareValues(left: SortValue, right: SortValue): number {
    if (typeof left === 'number' && typeof right === 'number') {
        return left - right;
    }
    return String(left ?? '').localeCompare(String(right ?? ''), undefined, { numeric: true, sensitivity: 'base' });
}

function ariaSort(active: boolean, direction: SortDirection): 'ascending' | 'descending' | 'none' {
    if (!active) {
        return 'none';
    }
    if (direction === 'asc') {
        return 'ascending';
    }
    return 'descending';
}

function sortIcon(direction: SortDirection): string {
    if (direction === 'asc') {
        return 'caret-up-fill';
    }
    return 'caret-down-fill';
}

/**
 * Renders a keyboard-accessible sortable table header. Use with the "sortableTable" styles.
 */
export function sortableHeader<K extends string>(label: string,
                                                 sortKey: K,
                                                 sort: SortState<K>,
                                                 onSort: (sortKey: K) => void,
                                                 className = ''): TemplateResult {
    const active = sort.sortKey === sortKey;
    return html`
        <th class="sortable-header ${className}" aria-sort=${ariaSort(active, sort.sortDirection)}>
            <button type="button" class="sort-button" @click=${() => onSort(sortKey)}>
                ${label}${when(active, () => html`<sl-icon class="sort-icon" name=${sortIcon(sort.sortDirection)}></sl-icon>`)}
            </button>
        </th>
    `;
}
