import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import type {SlRadioGroup, SlSwitch} from '@shoelace-style/shoelace';
import {AuditEntry} from '../model/reservation-detail.ts';
import {formatDate} from '../service/date-format.ts';
import {humanize} from '../service/helpers.ts';
import {formatAmount} from '../service/reservation-format.ts';
import {base, detailList, modernLayout, retroCompat, textColors} from '../styles.ts';
import {emptyState} from '../components/empty-state.ts';
import '../components/format-date.ts';

type Category = 'reservation' | 'payment' | 'attendees' | 'check-in' | 'documents';
type Tone = 'success' | 'danger' | 'warning' | 'primary' | 'neutral';

interface Who {
    // "Anna, Bob and Carl"
    names: string;
    many: boolean;
}

interface ActivityType {
    // "who" are the attendees, for operations on tickets
    describe: (who: Who) => string;
    icon: string;
    tone: Tone;
    category: Category;
    // the operation is always performed by the customer
    byCustomer?: boolean;
}

const ACTIVITY_TYPES: Record<string, ActivityType> = {
    RESERVATION_CREATE: { describe: () => 'Reservation created', icon: 'cart-plus', tone: 'primary', category: 'reservation' },
    RESERVATION_COMPLETE: { describe: () => 'Reservation completed', icon: 'check2-circle', tone: 'success', category: 'reservation' },
    CANCEL_RESERVATION_EXPIRED: { describe: () => 'Reservation expired', icon: 'hourglass-bottom', tone: 'neutral', category: 'reservation' },
    CANCEL_RESERVATION: { describe: () => 'Reservation cancelled', icon: 'x-circle', tone: 'danger', category: 'reservation' },
    TERMS_CONDITION_ACCEPTED: { describe: () => 'Terms and conditions accepted', icon: 'file-earmark-check', tone: 'neutral', category: 'reservation', byCustomer: true },
    PRIVACY_POLICY_ACCEPTED: { describe: () => 'Privacy policy accepted', icon: 'shield-check', tone: 'neutral', category: 'reservation', byCustomer: true },
    WARNING_IGNORED: { describe: () => 'The customer continued despite a warning', icon: 'exclamation-circle', tone: 'warning', category: 'reservation', byCustomer: true },
    DYNAMIC_DISCOUNT_CODE_CREATED: { describe: () => 'Discount code created', icon: 'percent', tone: 'neutral', category: 'reservation' },
    SUBSCRIPTION_ACQUIRED: { describe: () => 'Subscription purchased', icon: 'card-checklist', tone: 'success', category: 'reservation' },
    UPDATE_EVENT: { describe: () => 'Event updated', icon: 'pencil', tone: 'neutral', category: 'reservation' },

    INIT_PAYMENT: { describe: () => 'Payment started', icon: 'credit-card', tone: 'neutral', category: 'payment' },
    RESET_PAYMENT: { describe: () => 'Payment restarted', icon: 'arrow-repeat', tone: 'neutral', category: 'payment' },
    PAYMENT_CONFIRMED: { describe: () => 'Payment received', icon: 'cash-coin', tone: 'success', category: 'payment' },
    PAYMENT_ALREADY_CONFIRMED: { describe: () => 'Payment notification received again', icon: 'info-circle', tone: 'neutral', category: 'payment' },
    RESERVATION_OFFLINE_PAYMENT_CONFIRMED: { describe: () => 'Payment confirmed', icon: 'cash-coin', tone: 'success', category: 'payment' },
    AUTOMATIC_PAYMENT_CONFIRMATION: { describe: () => 'Payment confirmed automatically', icon: 'cash-coin', tone: 'success', category: 'payment' },
    AUTOMATIC_PAYMENT_CONFIRMATION_FAILED: { describe: () => 'The payment could not be confirmed automatically', icon: 'exclamation-triangle', tone: 'danger', category: 'payment' },
    PAYMENT_FAILED: { describe: () => 'Payment failed', icon: 'exclamation-triangle', tone: 'danger', category: 'payment' },
    MATCHING_PAYMENT_FOUND: { describe: () => 'A matching payment has been found', icon: 'arrow-left-right', tone: 'primary', category: 'payment' },
    MATCHING_PAYMENT_DISCARDED: { describe: () => 'The matching payment has been rejected', icon: 'x-octagon', tone: 'warning', category: 'payment' },
    REFUND: { describe: () => 'Refund issued', icon: 'arrow-counterclockwise', tone: 'warning', category: 'payment' },
    REFUND_ATTEMPT_FAILED: { describe: () => 'Refund failed', icon: 'exclamation-triangle', tone: 'danger', category: 'payment' },

    UPDATE_TICKET: { describe: (w) => `Details of ${w.names} updated`, icon: 'pencil', tone: 'neutral', category: 'attendees' },
    UPDATE_TICKET_METADATA: { describe: (w) => `Additional information of ${w.names} updated`, icon: 'pencil', tone: 'neutral', category: 'attendees' },
    TICKET_HOLDER_CHANGED: { describe: (w) => `${ticketOf(w)} assigned to someone else`, icon: 'person-check', tone: 'primary', category: 'attendees' },
    CANCEL_TICKET: { describe: (w) => `${ticketOf(w)} cancelled`, icon: 'x-circle', tone: 'danger', category: 'attendees' },
    UPDATE_TICKET_CATEGORY: { describe: (w) => `Ticket category of ${w.names} changed`, icon: 'ticket-perforated', tone: 'neutral', category: 'attendees' },
    TAG_TICKET: { describe: (w) => `Label added to ${ticketOf(w, false)}`, icon: 'tag', tone: 'neutral', category: 'attendees' },
    UNTAG_TICKET: { describe: (w) => `Label removed from ${ticketOf(w, false)}`, icon: 'tag', tone: 'neutral', category: 'attendees' },
    GROUP_MEMBER_ACQUIRED: { describe: (w) => `${ticketOf(w)} linked to a group member`, icon: 'people', tone: 'neutral', category: 'attendees' },

    CHECK_IN: { describe: (w) => `${w.names} checked in`, icon: 'door-open', tone: 'success', category: 'check-in' },
    MANUAL_CHECK_IN: { describe: (w) => `${w.names} checked in manually`, icon: 'door-open', tone: 'success', category: 'check-in' },
    REVERT_CHECK_IN: { describe: (w) => `Check-in of ${w.names} cancelled`, icon: 'arrow-counterclockwise', tone: 'warning', category: 'check-in' },
    BADGE_SCAN: { describe: (w) => `Badge of ${w.names} scanned`, icon: 'upc-scan', tone: 'primary', category: 'check-in' },

    BILLING_DOCUMENT_GENERATED: { describe: () => 'Invoice or receipt generated', icon: 'file-earmark-text', tone: 'primary', category: 'documents' },
    BILLING_DOCUMENT_INVALIDATED: { describe: () => 'Invoice or receipt invalidated', icon: 'file-earmark-x', tone: 'danger', category: 'documents' },
    BILLING_DOCUMENT_RESTORED: { describe: () => 'Invoice or receipt restored', icon: 'file-earmark-check', tone: 'success', category: 'documents' },
    CREDIT_NOTE_ISSUED: { describe: () => 'Credit note issued', icon: 'arrow-counterclockwise', tone: 'warning', category: 'documents' },
    UPDATE_INVOICE: { describe: () => 'Invoice updated', icon: 'receipt', tone: 'neutral', category: 'documents' },
    FORCED_UPDATE_INVOICE: { describe: () => 'Invoice updated by an administrator', icon: 'receipt', tone: 'warning', category: 'documents' },
    BILLING_DATA_UPDATED: { describe: () => 'Billing information updated', icon: 'receipt', tone: 'neutral', category: 'documents' },
    EXTERNAL_INVOICE_NUMBER: { describe: () => 'Invoice number assigned by the accounting system', icon: 'receipt', tone: 'neutral', category: 'documents' },
    EXTERNAL_CREDIT_NOTE_NUMBER: { describe: () => 'Credit note number assigned by the accounting system', icon: 'receipt', tone: 'neutral', category: 'documents' },
    VAT_VALIDATION_SUCCESSFUL: { describe: () => 'VAT number verified', icon: 'patch-check', tone: 'success', category: 'documents' },
    VAT_FORMAL_VALIDATION_SUCCESSFUL: { describe: () => 'VAT number format verified', icon: 'patch-check', tone: 'success', category: 'documents' },
    VAT_VALIDATION_SKIPPED: { describe: () => 'VAT number not verified', icon: 'patch-question', tone: 'warning', category: 'documents' },
    VAT_CUSTOM_CONFIGURATION_APPLIED: { describe: () => 'Custom VAT settings applied', icon: 'percent', tone: 'neutral', category: 'documents' },
    FORCE_VAT_APPLICATION: { describe: () => 'VAT/GST setting changed by an administrator', icon: 'percent', tone: 'warning', category: 'documents' },
};

const CATEGORIES: { name: Category, label: string }[] = [
    { name: 'reservation', label: 'Reservation' },
    { name: 'payment', label: 'Payments' },
    { name: 'attendees', label: 'Attendees' },
    { name: 'check-in', label: 'Check-in' },
    { name: 'documents', label: 'Invoicing' },
];

// changes that don't mean anything to the organizer
// (documentId is an internal identifier, not the number of the invoice)
const HIDDEN_PROPERTIES = ['assigned', 'fullName', 'documentId'];

const PROPERTY_LABELS: Record<string, string> = {
    email: 'E-mail',
    firstName: 'First name',
    lastName: 'Last name',
    userLanguage: 'Language',
    status: 'Status',
    paymentMethod: 'Payment method',
    paymentId: 'Payment reference',
    refund: 'Refunded amount',
    vatNumber: 'VAT number',
    country: 'Country',
    validationType: 'Verified with',
    vatStatus: 'VAT/GST',
    termsAndConditionsUrl: 'Terms and conditions',
    privacyPolicyUrl: 'Privacy policy',
};

// ticket statuses
const VALUE_LABELS: Record<string, string> = {
    PENDING: 'Pending',
    TO_BE_PAID: 'Waiting for payment',
    ACQUIRED: 'Confirmed',
    CHECKED_IN: 'Checked in',
    CANCELLED: 'Cancelled',
    RELEASED: 'Released',
};

// color of a ticket status change, by (lowercase) label of the new status
const STATUS_TONES: Record<string, Tone> = {
    confirmed: 'success',
    'checked in': 'success',
    cancelled: 'danger',
    released: 'danger',
};

const WARNINGS: Record<string, (params: string[]) => string> = {
    'error.STEP_2_EMAIL_TYPO': (params) => `The e-mail address "${params[0]}" might contain a typo`,
};

function ticketOf(who: Who, capitalized = true): string {
    let ticket = 'ticket';
    if (who.many) {
        ticket = 'tickets';
    }
    if (capitalized) {
        ticket = ticket.charAt(0).toUpperCase() + ticket.substring(1);
    }
    return `${ticket} of ${who.names}`;
}

const CHECK_IN_TYPES = ['CHECK_IN', 'MANUAL_CHECK_IN', 'REVERT_CHECK_IN', 'BADGE_SCAN'];

interface Change {
    label: string;
    before?: string;
    after: string | TemplateResult;
}

/**
 * Check-in operations, grouped by ticket ID
 */
export function checkInLog(audit: AuditEntry[]): Map<string, AuditEntry[]> {
    const log = new Map<string, AuditEntry[]>();
    audit.filter(entry => entry.entityType === 'TICKET' && CHECK_IN_TYPES.includes(entry.eventType))
        .forEach(entry => log.set(entry.entityId, [...(log.get(entry.entityId) ?? []), entry]));
    return log;
}

/**
 * History of a reservation, as a timeline grouped by day (most recent first), written for organizers:
 * operations are described in plain language, tickets are identified by the name of their holder,
 * and the technical data is available on demand.
 */
@customElement('alfio-activity-log')
export class ActivityLog extends LitElement {
    @property({ type: Array }) entries: AuditEntry[] = [];
    // ticket ID -> attendee name
    @property({ attribute: false }) attendees: Map<string, string> = new Map();
    @property({ type: String, attribute: 'time-zone' }) timeZone = 'UTC';
    @property({ type: String }) currency = '';
    // without filters and technical details, e.g. inside a dialog
    @property({ type: Boolean }) compact = false;
    @state() private category: Category | 'all' = 'all';
    @state() private showTechnicalDetails = false;

    static readonly styles = [
        base,
        retroCompat,
        textColors,
        modernLayout,
        detailList,
        css`
            :host {
                display: block;
            }
            .toolbar {
                display: flex;
                flex-wrap: wrap;
                align-items: flex-end;
                justify-content: space-between;
                gap: var(--sl-spacing-medium);
                margin-bottom: var(--sl-spacing-large);
            }
            .toolbar sl-switch {
                min-height: auto;
                padding-bottom: 0;
            }
            .day + .day {
                margin-top: var(--sl-spacing-large);
            }
            .day-title {
                margin: 0 0 var(--sl-spacing-small);
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-small);
                font-weight: var(--sl-font-weight-semibold);
                letter-spacing: 0.025em;
                text-transform: uppercase;
            }
            ol {
                list-style: none;
                margin: 0;
                padding: 0;
            }
            .activity {
                position: relative;
                display: grid;
                grid-template-columns: 2.25rem 1fr;
                gap: var(--sl-spacing-small);
                padding-bottom: var(--sl-spacing-medium);
            }
            /* line connecting the icons */
            .activity:not(:last-child)::before {
                content: '';
                position: absolute;
                top: 2.25rem;
                bottom: 0;
                left: calc(1.125rem - 1px);
                border-left: 2px solid var(--sl-color-gray-200);
            }
            .activity-icon {
                display: grid;
                place-items: center;
                width: 2.25rem;
                height: 2.25rem;
                border-radius: 50%;
                font-size: var(--sl-font-size-medium);
            }
            .tone-neutral .activity-icon { background: var(--sl-color-neutral-100); color: var(--sl-color-neutral-600); }
            .tone-primary .activity-icon { background: var(--sl-color-primary-100); color: var(--sl-color-primary-700); }
            .tone-success .activity-icon { background: var(--sl-color-success-100); color: var(--sl-color-success-700); }
            .tone-warning .activity-icon { background: var(--sl-color-warning-100); color: var(--sl-color-warning-700); }
            .tone-danger .activity-icon { background: var(--sl-color-danger-100); color: var(--sl-color-danger-700); }
            .activity-body {
                padding-top: var(--sl-spacing-2x-small);
                min-width: 0;
            }
            .activity-title {
                font-weight: var(--sl-font-weight-semibold);
            }
            .activity-meta {
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-small);
            }
            .changes {
                margin-top: var(--sl-spacing-x-small);
                padding: var(--sl-spacing-x-small) var(--sl-spacing-small);
                background: var(--sl-color-gray-50);
                border-radius: var(--sl-border-radius-medium);
                font-size: var(--sl-font-size-small);
            }
            .before {
                color: var(--sl-color-gray-500);
                text-decoration: line-through;
            }
            .arrow {
                margin: 0 var(--sl-spacing-2x-small);
                color: var(--sl-color-gray-400);
                vertical-align: middle;
            }
            .technical {
                margin: var(--sl-spacing-x-small) 0 0;
                padding: var(--sl-spacing-x-small) var(--sl-spacing-small);
                max-height: 12rem;
                overflow: auto;
                background: var(--sl-color-neutral-50);
                border: 1px dashed var(--sl-color-gray-300);
                border-radius: var(--sl-border-radius-medium);
                font-size: var(--sl-font-size-x-small);
                white-space: pre-wrap;
                overflow-wrap: anywhere;
            }
        `,
    ];

    render(): TemplateResult {
        const activities = toActivities(this.entries, this.attendees, this.currency);
        const visible = activities.filter(activity => this.category === 'all' || activity.type.category === this.category);
        return html`
            ${when(!this.compact && activities.length > 0, () => this.renderToolbar(activities))}
            ${when(visible.length === 0,
                () => emptyState('Nothing has happened yet'),
                () => html`${repeat(groupByDay(visible, this.timeZone), (day) => day.title, (day) => html`
                    <section class="day">
                        <h4 class="day-title">${day.title}</h4>
                        <ol>${repeat(day.activities, (activity) => activity.key, (activity) => this.renderActivity(activity))}</ol>
                    </section>
                `)}`)}
        `;
    }

    private renderToolbar(activities: Activity[]): TemplateResult {
        const counts = new Map<Category, number>();
        activities.forEach(activity => counts.set(activity.type.category, (counts.get(activity.type.category) ?? 0) + 1));
        const available = CATEGORIES.filter(category => counts.has(category.name));
        return html`
            <div class="toolbar">
                <sl-radio-group label="Show" size="small" value=${this.category}
                                @sl-change=${(e: Event) => { this.category = (e.target as SlRadioGroup).value as Category | 'all'; }}>
                    <sl-radio-button value="all">Everything</sl-radio-button>
                    ${repeat(available, (category) => category.name, (category) => html`
                        <sl-radio-button value=${category.name}>${category.label} (${counts.get(category.name)})</sl-radio-button>
                    `)}
                </sl-radio-group>
                <sl-switch size="small" .checked=${this.showTechnicalDetails}
                           @sl-change=${(e: Event) => { this.showTechnicalDetails = (e.target as SlSwitch).checked; }}>
                    Technical details
                </sl-switch>
            </div>
        `;
    }

    private renderActivity(activity: Activity): TemplateResult {
        const first = activity.entries[0];
        return html`
            <li class="activity tone-${activity.type.tone}" data-type=${first.eventType}>
                <span class="activity-icon"><sl-icon name=${activity.type.icon} aria-hidden="true"></sl-icon></span>
                <div class="activity-body">
                    <div class="activity-title">${activity.title}</div>
                    <div class="activity-meta">
                        <alfio-format-date date=${first.eventTime} time-zone=${this.timeZone} format="time"></alfio-format-date>
                        ${this.renderActor(first, activity.type)}
                    </div>
                    ${when(activity.changes.length > 0, () => html`
                        <div class="changes">
                            <dl class="detail-list">
                                ${repeat(activity.changes, (change) => change.label, (change) => html`
                                    <dt>${change.label}</dt>
                                    <dd>
                                        ${when(change.before != null, () => html`
                                            <span class="before">${change.before}</span><sl-icon class="arrow" name="arrow-right" aria-label="changed to"></sl-icon>
                                        `)}${change.after}
                                    </dd>
                                `)}
                            </dl>
                        </div>
                    `)}
                    ${when(this.showTechnicalDetails && !this.compact, () => html`
                        <pre class="technical">${JSON.stringify(activity.entries.map(entry => ({
                            type: entry.eventType,
                            [entry.entityType.toLowerCase()]: entry.entityId,
                            changes: entry.modifications,
                        })), null, 2)}</pre>
                    `)}
                </div>
            </li>
        `;
    }

    private renderActor(entry: AuditEntry, type: ActivityType): TemplateResult | typeof nothing {
        if (entry.username != null) {
            const name = [entry.firstName, entry.lastName].filter(value => value).join(' ') || entry.username;
            return html`· by <span title=${entry.email ?? entry.username}>${name}</span>`;
        }
        if (type.byCustomer) {
            return html`· by the customer`;
        }
        return nothing;
    }
}

interface Activity {
    key: string;
    // operations of the same kind, performed at the same time on different tickets
    entries: AuditEntry[];
    type: ActivityType;
    title: string;
    changes: Change[];
}

/**
 * Converts the audit entries into activities, most recent first:
 * - updates without changes are skipped
 * - an update that only changes the status of a ticket is described in one sentence ("Ticket of Anna confirmed")
 * - the same operation performed at the same time on several tickets becomes a single activity
 */
function toActivities(entries: AuditEntry[], attendees: Map<string, string>, currency: string): Activity[] {
    const activities: { entries: AuditEntry[], type: ActivityType, changes: Change[], status: string | null, groupKey: string }[] = [];
    [...entries].reverse().forEach(entry => {
        let changes = describeChanges(entry, currency);
        if (entry.eventType.startsWith('UPDATE_TICKET') && changes.length === 0) {
            return;
        }
        let status: string | null = null;
        if (entry.eventType === 'UPDATE_TICKET' && changes.length === 1 && changes[0].label === PROPERTY_LABELS.status) {
            status = String(changes[0].after).toLowerCase();
            changes = [];
        }
        const groupKey = JSON.stringify([
            entry.eventType,
            entry.entityType,
            entry.eventTime.substring(0, 16),
            entry.username,
            status,
            changes.map(change => [change.label, change.before, String(change.after)]),
        ]);
        const previous = activities[activities.length - 1];
        if (entry.entityType === 'TICKET' && previous?.groupKey === groupKey) {
            previous.entries.push(entry);
            return;
        }
        activities.push({ entries: [entry], type: activityType(entry), changes, status, groupKey });
    });
    return activities.map((activity, index) => {
        const who = attendeesOf(activity.entries, attendees);
        let title = activity.type.describe(who);
        let type = activity.type;
        if (activity.status != null) {
            title = `${ticketOf(who)} ${activity.status}`;
            type = { ...type, icon: 'ticket-perforated', tone: STATUS_TONES[activity.status] ?? type.tone };
        } else if (activity.entries[0].eventType === 'UPDATE_TICKET' && activity.changes.every(change => change.before == null)) {
            // nothing has been overwritten
            title = `Details of ${who.names} filled in`;
        }
        return { key: `${index}-${activity.groupKey}`, entries: activity.entries, type, title, changes: activity.changes };
    });
}

function attendeesOf(entries: AuditEntry[], attendees: Map<string, string>): Who {
    const names = [...new Set(entries
        .filter(entry => entry.entityType === 'TICKET')
        .map(entry => attendees.get(entry.entityId) ?? 'an attendee'))];
    return { names: joinNames(names), many: names.length > 1 };
}

/**
 * ["Anna", "Bob", "Carl"] -> "Anna, Bob and Carl"
 */
function joinNames(names: string[]): string {
    if (names.length <= 1) {
        return names[0] ?? '';
    }
    return `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`;
}

function activityType(entry: AuditEntry): ActivityType {
    return ACTIVITY_TYPES[entry.eventType] ?? {
        describe: () => humanize(entry.eventType),
        icon: 'info-circle',
        tone: 'neutral',
        category: 'reservation',
    };
}

function groupByDay(activities: Activity[], timeZone: string): { title: string, activities: Activity[] }[] {
    const days = new Map<string, Activity[]>();
    activities.forEach(activity => {
        const title = formatDate(activity.entries[0].eventTime, { timeZone, style: 'date' });
        days.set(title, [...(days.get(title) ?? []), activity]);
    });
    return [...days.entries()].map(([title, dayActivities]) => ({ title, activities: dayActivities }));
}

/**
 * Turns the technical description of the modifications into "label: before -> after" pairs.
 * Two shapes are stored: lists of differences ({propertyName, oldValue, newValue}) and plain key/value maps.
 */
function describeChanges(entry: AuditEntry, currency: string): Change[] {
    return (entry.modifications ?? []).flatMap(modification => {
        if (modification == null || typeof modification !== 'object') {
            return [];
        }
        const record = modification as Record<string, unknown>;
        if (typeof record.propertyName === 'string') {
            return describeDifference(record);
        }
        return Object.entries(record)
            .filter(([key]) => !HIDDEN_PROPERTIES.includes(key))
            .flatMap(([key, value]) => describeValue(key, value, currency));
    });
}

function describeDifference(difference: Record<string, unknown>): Change[] {
    // "/firstName", or "/{DietaryRequirements}" for additional fields
    const property = String(difference.propertyName).replace(/^\//, '').replace(/[{}]/g, '');
    if (HIDDEN_PROPERTIES.includes(property)) {
        return [];
    }
    const before = displayValue(difference.oldValue);
    const after = displayValue(difference.newValue);
    if (before === after) {
        return [];
    }
    if (before === EMPTY) {
        // the value has been set for the first time
        return [{ label: propertyLabel(property), after }];
    }
    return [{ label: propertyLabel(property), before, after }];
}

function describeValue(key: string, value: unknown, currency: string): Change[] {
    if (key === 'warnings' && Array.isArray(value)) {
        return value.map((warning: { code: string, params: string[] }) => ({
            label: 'Warning',
            after: WARNINGS[warning.code]?.(warning.params ?? []) ?? humanize(warning.code.replace(/^error\./, '')),
        }));
    }
    if (key === 'refund') {
        return [{ label: propertyLabel(key), after: refundAmount(String(value), currency) }];
    }
    if (key.endsWith('Url') && typeof value === 'string') {
        return [{ label: propertyLabel(key), after: html`<a href=${value} target="_blank" rel="noopener">Open the document</a>` }];
    }
    if (key === 'paymentMethod' || key === 'vatStatus') {
        return [{ label: propertyLabel(key), after: humanize(String(value)) }];
    }
    return [{ label: propertyLabel(key), after: displayValue(value) }];
}

function refundAmount(value: string, currency: string): string {
    const cents = Number(value);
    if (Number.isFinite(cents)) {
        return formatAmount(cents / 100, currency);
    }
    return 'Full amount';
}

/**
 * "DietaryRequirements" or "dietary_requirements" -> "Dietary requirements"
 */
function propertyLabel(property: string): string {
    return PROPERTY_LABELS[property] ?? humanize(property.replace(/([a-z])([A-Z])/g, '$1_$2'));
}

const EMPTY = '—';

function displayValue(value: unknown): string {
    if (value == null || value === '' || (Array.isArray(value) && value.length === 0)) {
        return EMPTY;
    }
    if (value === true) {
        return 'Yes';
    }
    if (value === false) {
        return 'No';
    }
    if (Array.isArray(value)) {
        return value.map(item => displayValue(item)).join(', ');
    }
    if (typeof value === 'string' && /^[A-Z][A-Z_]+$/.test(value)) {
        // enum values, e.g. "ACQUIRED"
        return VALUE_LABELS[value] ?? humanize(value);
    }
    return String(value);
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-activity-log': ActivityLog;
    }
}
