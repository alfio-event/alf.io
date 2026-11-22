import {LocalizedContent} from './purchase-context.ts';

export type PollStatus = 'DRAFT' | 'OPEN' | 'CLOSED';

export interface PollOption {
    id: number | null;
    title: LocalizedContent;
    description: LocalizedContent;
}

/**
 * A poll of an event, as returned by the poll admin API (PollModification)
 */
export interface Poll {
    id: number | null;
    title: LocalizedContent;
    description: LocalizedContent;
    order: number;
    options: PollOption[];
    accessRestricted: boolean;
    status: PollStatus;
}

export interface PollParticipant {
    id: number;
    firstName: string;
    lastName: string;
    emailAddress: string;
    categoryName: string;
}

export interface PollOptionStatistics {
    votes: number;
    optionId: number;
    // already formatted by the backend, e.g. "33.33"
    percentage: string;
}

export interface PollStatistics {
    totalVotes: number;
    allowedParticipants: number;
    participationPercentage: string;
    optionStatistics: PollOptionStatistics[];
}

/**
 * The text of a localized content in the given (first) locale, or the first available one
 */
export function firstLocalized(content: LocalizedContent, firstLocale: string): string {
    return content[firstLocale] ?? Object.values(content)[0] ?? '';
}

/**
 * A backend-computed percentage without the trailing ".00" (e.g. "50.00" -> "50")
 */
export function formatPercentage(percentage: string): string {
    if (percentage.endsWith('.00')) {
        return percentage.substring(0, percentage.length - 3);
    }
    return percentage;
}

/**
 * Whether every selected language of a localized content has a non-blank text
 */
export function isCompleteLocalized(content: LocalizedContent | null | undefined): boolean {
    if (content == null || Object.keys(content).length === 0) {
        return false;
    }
    return Object.values(content).every((text) => text != null && text.trim() !== '');
}
