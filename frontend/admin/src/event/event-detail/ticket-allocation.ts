import {slColor} from '../../components/chart.ts';

/**
 * The states of the tickets of an event, shown by the ticket status chart and by the bar of each
 * category: the same state has the same name and color in both.
 */
export type AllocationKey = 'checkedIn' | 'sold' | 'pending' | 'availableFixed' | 'availableDynamic' | 'notAssigned';

interface AllocationDefinition {
    label: string;
    // Shoelace color token, and the color used when the token isn't defined
    token: string;
    fallback: string;
}

const DEFINITIONS: Record<AllocationKey, AllocationDefinition> = {
    checkedIn: {label: 'Checked in', token: '--sl-color-success-600', fallback: '#16a34a'},
    sold: {label: 'Sold', token: '--sl-color-success-300', fallback: '#86efac'},
    pending: {label: 'Pending', token: '--sl-color-warning-400', fallback: '#fbbf24'},
    availableFixed: {label: 'Available in fixed categories', token: '--sl-color-sky-300', fallback: '#7dd3fc'},
    availableDynamic: {label: 'Available in dynamic categories', token: '--sl-color-gray-300', fallback: '#d1d5db'},
    notAssigned: {label: 'Not assigned to a category', token: '--sl-color-gray-500', fallback: '#6b7280'},
};

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
            color: slColor(DEFINITIONS[key].token, DEFINITIONS[key].fallback),
        }));
}
