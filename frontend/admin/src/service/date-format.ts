import {DateTimeModification} from "../model/purchase-context.ts";

/**
 * A date coming from the backend. Accepted shapes:
 *  - ISO-8601 instant, with offset or "Z", optionally followed by a zone id: "2025-01-07T11:56:43.813Z[UTC]"
 *  - wall-clock date/time, without offset: "2025-02-28 07:00", "2025-02-28T07:00", "2025-02-28".
 *    It is considered already expressed in the target time zone and is never converted
 *  - DateTimeModification ({date, time}), also wall-clock
 *  - Date, an instant
 */
export type DateValue = string | DateTimeModification | Date | null | undefined;

export type DateFormatStyle = 'date-time' | 'date' | 'time';

export interface DateFormatOptions {
    // IANA time zone used to display instants. Ignored for wall-clock values. Defaults to the browser time zone
    timeZone?: string;
    style?: DateFormatStyle;
}

export interface DateFormatPreferences {
    locale: string;
    date: Intl.DateTimeFormatOptions;
    time: Intl.DateTimeFormatOptions;
}

export const DATE_FORMAT_CHANGED_EVENT = 'alfio-date-format-changed';

// e.g. "Jun 30, 2026, 14:00"
const DEFAULT_PREFERENCES: DateFormatPreferences = {
    locale: 'en-US',
    date: { year: 'numeric', month: 'short', day: '2-digit' },
    time: { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' },
};

let preferences: DateFormatPreferences = DEFAULT_PREFERENCES;
const formatters = new Map<string, Intl.DateTimeFormat>();

export function dateFormatPreferences(): DateFormatPreferences {
    return preferences;
}

/**
 * Changes how dates are displayed in the whole admin (e.g. from an organizer setting).
 * Every <alfio-format-date> re-renders automatically.
 */
export function setDateFormatPreferences(update: Partial<DateFormatPreferences>): void {
    preferences = { ...preferences, ...update };
    formatters.clear();
    window.dispatchEvent(new CustomEvent(DATE_FORMAT_CHANGED_EVENT));
}

export function resetDateFormatPreferences(): void {
    setDateFormatPreferences(DEFAULT_PREFERENCES);
}

export function formatDate(value: DateValue, options: DateFormatOptions = {}): string {
    const parsed = parseDateValue(value);
    if (parsed == null) {
        return '';
    }
    return formatter(options.style, displayTimeZone(parsed, options.timeZone)).format(parsed.date);
}

/**
 * Formats an interval, omitting the parts shared by both ends: "Jun 30, 2026, 14:00 – 18:00"
 */
export function formatDateRange(start: DateValue, end: DateValue, options: DateFormatOptions = {}): string {
    const parsedStart = parseDateValue(start);
    const parsedEnd = parseDateValue(end);
    if (parsedStart == null || parsedEnd == null) {
        return formatDate(start, options) || formatDate(end, options);
    }
    if (parsedStart.wallClock !== parsedEnd.wallClock || parsedEnd.date < parsedStart.date) {
        // the two ends cannot be expressed in the same time zone, format them separately
        return `${formatDate(start, options)} – ${formatDate(end, options)}`;
    }
    return formatter(options.style, displayTimeZone(parsedStart, options.timeZone))
        .formatRange(parsedStart.date, parsedEnd.date);
}

/**
 * Machine-readable representation, suitable for the datetime attribute of <time>
 */
export function toIsoString(value: DateValue): string {
    const parsed = parseDateValue(value);
    if (parsed == null) {
        return '';
    }
    if (parsed.wallClock) {
        // drop the "Z": a wall-clock value has no time zone
        return parsed.date.toISOString().substring(0, 16);
    }
    return parsed.date.toISOString();
}

/**
 * Converts the value of a datetime-local input ("yyyy-MM-ddTHH:mm", wall-clock in the given time zone)
 * to an ISO-8601 instant with the offset of that zone, e.g. "2026-06-30T14:00:00+02:00".
 * Returns null for an empty value.
 */
export function zonedIsoFromDateTimeLocal(value: string, timeZone: string): string | null {
    if (value == null || value.trim() === '') {
        return null;
    }
    // interpret the wall time as UTC to approximate the instant, then read the offset of the zone there
    const wallAsUtc = new Date(`${value}:00Z`);
    if (Number.isNaN(wallAsUtc.getTime())) {
        return null;
    }
    let offset = zoneOffsetMinutes(timeZone, wallAsUtc);
    // refine once, so that the offset is the one in effect at the actual instant
    offset = zoneOffsetMinutes(timeZone, new Date(wallAsUtc.getTime() - offset * 60000));
    let sign = '+';
    if (offset < 0) {
        sign = '-';
    }
    const abs = Math.abs(offset);
    const hh = String(Math.floor(abs / 60)).padStart(2, '0');
    const mm = String(abs % 60).padStart(2, '0');
    return `${value}:00${sign}${hh}:${mm}`;
}

function zoneOffsetMinutes(timeZone: string, instant: Date): number {
    const parts = new Intl.DateTimeFormat('en-US', {
        timeZone,
        hour12: false,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
    }).formatToParts(instant);
    const get = (type: Intl.DateTimeFormatPartTypes) => Number(parts.find((part) => part.type === type)?.value);
    const asUtc = Date.UTC(get('year'), get('month') - 1, get('day'), get('hour') % 24, get('minute'), get('second'));
    return (asUtc - instant.getTime()) / 60000;
}

/**
 * Converts a date to the "yyyy-MM-ddTHH:mm" format expected by a datetime-local input, in the given time zone
 */
export function toDateTimeLocal(value: DateValue, timeZone: string): string {
    const parsed = parseDateValue(value);
    if (parsed == null) {
        return '';
    }
    const parts = new Intl.DateTimeFormat('en-GB', {
        timeZone: displayTimeZone(parsed, timeZone),
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        hourCycle: 'h23',
    }).formatToParts(parsed.date);
    const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((item) => item.type === type)?.value ?? '';
    return `${part('year')}-${part('month')}-${part('day')}T${part('hour')}:${part('minute')}`;
}

interface ParsedDate {
    date: Date;
    // wall-clock values are stored as UTC and displayed in UTC, so that they are never shifted
    wallClock: boolean;
}

const WALL_CLOCK_PATTERN = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2})(?::(\d{2}))?)?/;

function parseDateValue(value: DateValue): ParsedDate | null {
    if (value == null || value === '') {
        return null;
    }
    if (value instanceof Date) {
        return validOrNull({ date: value, wallClock: false });
    }
    if (typeof value === 'object') {
        return parseWallClock(`${value.date}T${value.time}`);
    }
    // ZonedDateTime.toString() appends the zone id, e.g. "2025-01-07T11:56:43.813Z[UTC]"
    const normalized = value.trim().replace(/\[[^\]]+]$/, '').replace(' ', 'T');
    if (/(?:Z|[+-]\d\d:?\d\d)$/i.test(normalized)) {
        return validOrNull({ date: new Date(normalized), wallClock: false });
    }
    return parseWallClock(normalized);
}

function parseWallClock(value: string): ParsedDate | null {
    const match = WALL_CLOCK_PATTERN.exec(value);
    if (match == null) {
        return null;
    }
    const [, year, month, day, hour, minute, second] = match.map(part => Number(part ?? 0));
    return validOrNull({ date: new Date(Date.UTC(year, month - 1, day, hour, minute, second)), wallClock: true });
}

function validOrNull(parsed: ParsedDate): ParsedDate | null {
    if (Number.isNaN(parsed.date.getTime())) {
        return null;
    }
    return parsed;
}

function displayTimeZone(parsed: ParsedDate, timeZone: string | undefined): string | undefined {
    if (parsed.wallClock) {
        return 'UTC';
    }
    return timeZone;
}

function formatter(style: DateFormatStyle = 'date-time', timeZone: string | undefined): Intl.DateTimeFormat {
    const key = `${style}|${timeZone ?? ''}`;
    let result = formatters.get(key);
    if (result == null) {
        result = new Intl.DateTimeFormat(preferences.locale, { ...styleOptions(style), timeZone });
        formatters.set(key, result);
    }
    return result;
}

function styleOptions(style: DateFormatStyle): Intl.DateTimeFormatOptions {
    switch (style) {
        case 'date':
            return preferences.date;
        case 'time':
            return preferences.time;
        default:
            return { ...preferences.date, ...preferences.time };
    }
}
