import {slColor} from '../../components/chart.ts';

/**
 * The states of the tickets of an event, shown by the ticket status chart and by the bar of each
 * category: the same state has the same name and color in both.
 */
// availableDynamic: available to every category without a limit; availableFixed: kept by the categories with a limit
export type AllocationKey = 'checkedIn' | 'sold' | 'pending' | 'availableFixed' | 'availableDynamic' | 'notAssigned';

interface AllocationDefinition {
    label: string;
    // Shoelace color token, and the color used when the token isn't defined
    token: string;
    fallback: string;
}

// Sold and checked in are the strongest colors: they are what the organizer looks for. Pending is calm, as the
// reservations in progress are normal. Only "Not on sale" asks for attention, because it needs an action
const DEFINITIONS: Record<AllocationKey, AllocationDefinition> = {
    checkedIn: {label: 'Checked in', token: '--sl-color-success-700', fallback: '#15803d'},
    sold: {label: 'Sold', token: '--sl-color-success-500', fallback: '#22c55e'},
    pending: {label: 'Pending', token: '--sl-color-sky-400', fallback: '#38bdf8'},
    availableDynamic: {label: 'Available', token: '--sl-color-gray-300', fallback: '#d1d5db'},
    availableFixed: {label: 'Kept for categories with a limit', token: '--sl-color-gray-200', fallback: '#e5e7eb'},
    notAssigned: {label: 'Not on sale', token: '--sl-color-warning-500', fallback: '#f59e0b'},
};

/**
 * The color of a state, e.g. for another chart that shows the same states
 */
export function allocationColor(key: AllocationKey): string {
    return slColor(DEFINITIONS[key].token, DEFINITIONS[key].fallback);
}

export interface AllocationSegment {
    key: AllocationKey;
    label: string;
    value: number;
    color: string;
}

/**
 * The segments with a value, in the order of the definitions. The colors are read when called,
 * once the Shoelace theme is loaded.
 *
 * @param labels a different label for some states, e.g. "Available" within a category
 */
export function allocationSegments(values: Partial<Record<AllocationKey, number>>,
                                   labels: Partial<Record<AllocationKey, string>> = {}): AllocationSegment[] {
    const keys = Object.keys(DEFINITIONS) as AllocationKey[];
    return keys
        .filter((key) => (values[key] ?? 0) > 0)
        .map((key) => ({
            key,
            label: labels[key] ?? DEFINITIONS[key].label,
            value: values[key] ?? 0,
            color: allocationColor(key),
        }));
}
