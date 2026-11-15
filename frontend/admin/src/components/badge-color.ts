import {html, TemplateResult} from 'lit';

export interface BadgeColor {
    // the name stored in CHECK_IN_COLOR_CONFIGURATION
    value: string;
    label: string;
    // the color of the swatch
    color: string;
    // the color of a border on a white background (a white badge needs a visible one)
    border: string;
}

/**
 * The badge colors of the ticket categories: the check-in app shows them on a successful scan
 */
export const BADGE_COLORS: BadgeColor[] = [
    { value: 'primary', label: 'Blue', color: 'var(--sl-color-blue-600)', border: 'var(--sl-color-blue-600)' },
    { value: 'info', label: 'Cyan', color: 'var(--sl-color-cyan-500)', border: 'var(--sl-color-cyan-500)' },
    { value: 'success', label: 'Green', color: 'var(--sl-color-green-600)', border: 'var(--sl-color-green-600)' },
    { value: 'warning', label: 'Yellow', color: 'var(--sl-color-yellow-400)', border: 'var(--sl-color-yellow-400)' },
    { value: 'danger', label: 'Red', color: 'var(--sl-color-red-600)', border: 'var(--sl-color-red-600)' },
    { value: 'purple', label: 'Purple', color: 'var(--sl-color-purple-600)', border: 'var(--sl-color-purple-600)' },
    { value: 'secondary', label: 'Gray', color: 'var(--sl-color-gray-500)', border: 'var(--sl-color-gray-500)' },
    { value: 'light', label: 'White', color: 'var(--sl-color-neutral-0)', border: 'var(--sl-color-neutral-400)' },
    { value: 'dark', label: 'Black', color: 'var(--sl-color-gray-900)', border: 'var(--sl-color-gray-900)' },
];

export function findBadgeColor(value: string | null | undefined): BadgeColor | undefined {
    return BADGE_COLORS.find(c => c.value === value);
}

/**
 * A dot of the given color. Style it with the "badge-swatch" class
 */
export function badgeSwatch(color: BadgeColor, slot?: string): TemplateResult {
    return html`<span class="badge-swatch" slot=${slot ?? ''} aria-hidden="true" style=${`background-color: ${color.color}`}></span>`;
}
