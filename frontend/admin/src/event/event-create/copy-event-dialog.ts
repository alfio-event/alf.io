import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {classMap} from 'lit/directives/class-map.js';
import type {SlCheckbox, SlDialog, SlInput, SlSelect, SlRequestCloseEvent} from '@shoelace-style/shoelace';
import {Task} from '@lit/task';
import {EventStatistic} from '../../model/event.ts';
import {AdditionalField} from '../../model/additional-field.ts';
import {AdditionalItem} from '../../model/additional-item.ts';
import {fetchJson} from '../../service/helpers.ts';
import {dialog as dialogStyles, form, modernLayout, row, textColors} from '../../styles.ts';
import {sectionCard} from '../../components/section-card.ts';
import {taskContent} from '../../components/task-content.ts';
import '../../components/format-date.ts';

/**
 * What the user picked in the copy dialog: the event to copy from, the dates of the new event,
 * and the additional fields/options to copy
 */
export interface CopyEventSelection {
    source: EventStatistic;
    // datetime-local values
    begin: string;
    end: string;
    additionalFields: AdditionalField[];
    additionalServices: AdditionalItem[];
}

export interface CopyEventDialogOptions {
    // short name of the event to preselect (?eventName=... route param)
    preselectShortName?: string;
    // current begin/end of the form, used as default for the new event dates
    currentBegin: string;
    currentEnd: string;
}

interface LoadedEvents {
    events: EventStatistic[];
}

function itemTitle(item: AdditionalItem): string {
    const titles = item.title.filter((t) => t.type === 'TITLE');
    if (titles.length === 0) {
        return `#${item.id}`;
    }
    const first = titles[0];
    let label = first.value;
    if (item.type === 'DONATION') {
        label += ' (Donation)';
    } else {
        label += ' (Additional option)';
    }
    if (titles.length > 1) {
        label += ` (${titles.map((t) => t.locale).join(', ')})`;
    }
    return label;
}

/**
 * Dialog to pre-fill the create-event form from a previous event: pick the event and the dates
 * of the new one, and choose which additional fields and options to copy.
 * open() resolves with the selection, or with null when the user cancels
 */
@customElement('alfio-copy-event-dialog')
export class CopyEventDialog extends LitElement {

    @query('sl-dialog') private dialog?: SlDialog;

    @state() private isOpen = false;
    @state() private selectedEvent: EventStatistic | null = null;
    @state() private begin = '';
    @state() private end = '';
    @state() private additionalFields: AdditionalField[] = [];
    @state() private additionalServices: AdditionalItem[] = [];
    @state() private selectedFieldNames: Set<string> = new Set();
    @state() private selectedServiceIds: Set<number> = new Set();
    @state() private errors: Record<string, string> = {};

    @state() private preselectShortName = '';

    private resolve: ((result: CopyEventSelection | null) => void) | null = null;

    private readonly loadEventsTask = new Task(this, {
        task: async ([open, preselect]): Promise<LoadedEvents | null> => {
            if (!open) {
                return null;
            }
            const [active, expired] = await Promise.all([
                fetchJson<EventStatistic[]>('/admin/api/active-events'),
                fetchJson<EventStatistic[]>('/admin/api/expired-events'),
            ]);
            const events = [...active, ...expired];
            // preselect an event (?eventName=... route param) once the list is loaded
            if (preselect !== '') {
                const match = events.find((e) => e.shortName === preselect);
                if (match != null) {
                    await this.onEventSelected(match);
                }
            }
            return { events };
        },
        args: () => [this.isOpen, this.preselectShortName] as const,
    });

    static readonly styles = [
        dialogStyles,
        modernLayout,
        form,
        row,
        textColors,
        css`
            :host {
                display: block;
            }
            .dialog-form {
                display: grid;
                gap: var(--sl-spacing-medium);
            }
            .dialog-form .section-card {
                margin-bottom: 0;
            }
            sl-checkbox {
                display: block;
                margin-top: var(--sl-spacing-x-small);
            }
            sl-input.error, sl-select.error {
                --sl-input-border-color: var(--sl-color-danger-600);
            }
            .selected-event {
                display: flex;
                align-items: center;
                justify-content: space-between;
                gap: var(--sl-spacing-small);
                padding: var(--sl-spacing-small) var(--sl-spacing-medium);
                background: var(--sl-color-primary-50);
                border: 1px solid var(--sl-color-primary-200);
                border-radius: var(--sl-input-border-radius-large);
            }
            .selected-event h4 {
                margin: 0;
            }
            .selected-event .text-muted {
                margin: 0;
                font-size: var(--sl-font-size-small);
            }
        `,
    ];

    public open(options: CopyEventDialogOptions): Promise<CopyEventSelection | null> {
        this.selectedEvent = null;
        this.begin = options.currentBegin;
        this.end = options.currentEnd;
        this.additionalFields = [];
        this.additionalServices = [];
        this.selectedFieldNames = new Set();
        this.selectedServiceIds = new Set();
        this.errors = {};
        this.preselectShortName = options.preselectShortName ?? '';
        this.isOpen = true;
        const promise = new Promise<CopyEventSelection | null>((resolve) => {
            this.resolve = resolve;
        });
        void this.updateComplete.then(() => void this.dialog?.show());
        return promise;
    }

    private errorOf(field: string): string {
        return this.errors[field] ?? '';
    }

    protected render(): TemplateResult {
        if (!this.isOpen) {
            return html``;
        }
        return html`
            <sl-dialog class="responsive-dialog" style="--alfio-dialog-max-width: 46rem" label="Copy from previous event"
                       @sl-request-close=${this.onRequestClose} @sl-after-hide=${this.onAfterHide}>
                <div class="dialog-form">
                    ${taskContent(this.loadEventsTask, this.loadEventsTask.value ?? null,
                        'Failed to load the events. Please try again.',
                        (data) => this.renderBody(data))}
                </div>
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${this.cancel}>Cancel</sl-button>
                        <div></div>
                        <sl-button id="copy-event-confirm" variant="warning" size="large" @click=${this.onSubmit}>Copy</sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    private renderBody(data: LoadedEvents): TemplateResult {
        const events = data.events;
        return html`
            ${sectionCard({ icon: 'clipboard', title: 'Event to copy from' }, html`
                ${when(this.selectedEvent == null, () => html`
                    <sl-select id="copy-event-source" label="Search event" placeholder="Select an event" hoist clearable
                               class=${classMap({ error: this.errorOf('source') !== '' })}
                               help-text=${this.errorOf('source')}
                               @sl-change=${(e: Event) => this.onSourceChange(events, String((e.target as SlSelect).value))}>
                        ${repeat(events, (e) => e.shortName, (e) => html`
                            <sl-option value=${e.shortName}>
                                ${e.displayName} (${e.shortName}) — <alfio-format-date date=${e.formattedBegin} time-zone=${e.timeZone}></alfio-format-date>
                            </sl-option>
                        `)}
                    </sl-select>
                `, () => html`
                    <div class="selected-event">
                        <div>
                            <h4>${this.selectedEvent!.displayName}</h4>
                            <p class="text-muted">
                                <alfio-format-date date=${this.selectedEvent!.formattedBegin} end=${this.selectedEvent!.formattedEnd} time-zone=${this.selectedEvent!.timeZone}></alfio-format-date>
                            </p>
                        </div>
                        <sl-button variant="default" size="small" @click=${this.clearSelection}>
                            <sl-icon slot="prefix" name="x-circle"></sl-icon>
                            Change
                        </sl-button>
                    </div>
                `)}
            `)}
            ${sectionCard({ icon: 'calendar-range', title: 'Dates of the new event' }, html`
                <div class="row" style="--alfio-row-cols: 2">
                    <sl-input id="copy-event-begin" label="Begin" type="datetime-local" required
                              class=${classMap({ error: this.errorOf('begin') !== '' })}
                              help-text=${this.errorOf('begin')}
                              .value=${this.begin}
                              @sl-input=${(e: Event) => this.setDate('begin', (e.target as SlInput).value)}></sl-input>
                    <sl-input id="copy-event-end" label="End" type="datetime-local" required
                              class=${classMap({ error: this.errorOf('end') !== '' })}
                              help-text=${this.errorOf('end')}
                              .value=${this.end}
                              @sl-input=${(e: Event) => this.setDate('end', (e.target as SlInput).value)}></sl-input>
                </div>
            `)}
            ${when(this.additionalFields.length > 0, () => sectionCard({ icon: 'list-check', title: 'Copy additional fields' }, html`
                ${repeat(this.additionalFields, (f) => f.name, (f) => html`
                    <sl-checkbox ?checked=${this.selectedFieldNames.has(f.name)}
                                 @sl-change=${(e: Event) => this.toggleField(f.name, (e.target as SlCheckbox).checked)}>
                        ${f.name}
                    </sl-checkbox>
                `)}
            `))}
            ${when(this.additionalServices.length > 0, () => sectionCard({ icon: 'cart-plus', title: 'Copy additional options and donations' }, html`
                ${repeat(this.additionalServices, (s) => s.id, (s) => html`
                    <sl-checkbox ?checked=${this.selectedServiceIds.has(s.id)}
                                 @sl-change=${(e: Event) => this.toggleService(s.id, (e.target as SlCheckbox).checked)}>
                        ${itemTitle(s)}
                    </sl-checkbox>
                `)}
            `))}
        `;
    }

    private onSourceChange(events: EventStatistic[], shortName: string): void {
        const match = events.find((e) => e.shortName === shortName);
        if (match != null) {
            void this.onEventSelected(match);
        }
    }

    private async onEventSelected(event: EventStatistic): Promise<void> {
        this.selectedEvent = event;
        this.additionalFields = [];
        this.additionalServices = [];
        this.selectedFieldNames = new Set();
        this.selectedServiceIds = new Set();
        const [fields, services] = await Promise.all([
            fetchJson<AdditionalField[]>(`/admin/api/event/${event.shortName}/additional-field`),
            fetchJson<AdditionalItem[]>(`/admin/api/event/${event.id}/additional-services`),
        ]);
        this.additionalFields = fields;
        this.additionalServices = services;
        this.selectedFieldNames = new Set(fields.map((f) => f.name));
        this.selectedServiceIds = new Set(services.map((s) => s.id));
    }

    private clearSelection = (): void => {
        this.selectedEvent = null;
        this.additionalFields = [];
        this.additionalServices = [];
        this.selectedFieldNames = new Set();
        this.selectedServiceIds = new Set();
    };

    private setDate(field: 'begin' | 'end', value: string): void {
        if (field === 'begin') {
            this.begin = value;
        } else {
            this.end = value;
        }
        const errors = { ...this.errors };
        delete errors[field];
        this.errors = errors;
    }

    private toggleField(name: string, checked: boolean): void {
        const next = new Set(this.selectedFieldNames);
        if (checked) {
            next.add(name);
        } else {
            next.delete(name);
        }
        this.selectedFieldNames = next;
    }

    private toggleService(id: number, checked: boolean): void {
        const next = new Set(this.selectedServiceIds);
        if (checked) {
            next.add(id);
        } else {
            next.delete(id);
        }
        this.selectedServiceIds = next;
    }

    private onSubmit = (e: Event): void => {
        e.preventDefault();
        const errors: Record<string, string> = {};
        if (this.selectedEvent == null) {
            errors.source = 'Please select an event to copy from';
        }
        if (this.begin.trim() === '') {
            errors.begin = 'Please fill in this field';
        }
        if (this.end.trim() === '') {
            errors.end = 'Please fill in this field';
        }
        if (errors.begin == null && errors.end == null && this.begin >= this.end) {
            errors.end = 'The end must be after the begin';
        }
        this.errors = errors;
        if (Object.keys(errors).length > 0 || this.selectedEvent == null) {
            return;
        }
        const selection: CopyEventSelection = {
            source: this.selectedEvent,
            begin: this.begin,
            end: this.end,
            additionalFields: this.additionalFields.filter((f) => this.selectedFieldNames.has(f.name)),
            additionalServices: this.additionalServices.filter((s) => this.selectedServiceIds.has(s.id)),
        };
        const resolve = this.resolve;
        this.resolve = null;
        resolve?.(selection);
        void this.dialog?.hide();
    };

    private onRequestClose = (e: SlRequestCloseEvent): void => {
        if (e.detail.source === 'overlay') {
            e.preventDefault();
        }
    };

    private onAfterHide = (e: Event): void => {
        if (e.target !== e.currentTarget) {
            return;
        }
        this.isOpen = false;
        this.preselectShortName = '';
        this.selectedEvent = null;
        const resolve = this.resolve;
        this.resolve = null;
        resolve?.(null);
    };

    private cancel = (): void => {
        void this.dialog?.hide();
    };
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-copy-event-dialog': CopyEventDialog;
    }
}
