import {css, html, TemplateResult} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import type {SlRequestCloseEvent} from '@shoelace-style/shoelace';
import {attendeeFields as attendeeFieldsStyles, base, detailList, dialog, form, row} from '../styles.ts';
import {AdditionalField} from '../model/additional-field.ts';
import {AdditionalFieldValue, Ticket} from '../model/reservation-detail.ts';
import {ReservationService} from '../service/reservation.ts';
import {UtilService} from '../service/util.ts';
import {errorMessage} from '../service/helpers.ts';
import {formatFullName} from '../service/reservation-format.ts';
import {dialogTitle, PromptDialog} from '../components/prompt-dialog.ts';
import {attendeeFields, AttendeeFieldValues, Country, valuesForFields} from '../components/attendee-fields.ts';
import {detailList as renderDetailList} from '../components/detail-list.ts';

export interface AttendeeDataRequest {
    service: ReservationService;
    ticket: Ticket;
    // the additional fields of the ticket's category, see fieldsForCategory()
    fields: readonly AdditionalField[];
    // language of the labels
    language: string;
}

/**
 * Data of an attendee: the organizer can edit the values of the additional fields.
 * The data collected for the additional items is displayed, but can't be changed here.
 * The promise returned by open() is resolved with true if the values have been saved.
 */
@customElement('alfio-attendee-data-dialog')
export class AttendeeDataDialog extends PromptDialog<boolean> {
    // the content is rendered only while the dialog is open, so that every opening starts from the stored values
    @state() private request: AttendeeDataRequest | null = null;
    @state() private loading = false;
    @state() private loadError: string | null = null;
    @state() private values: AttendeeFieldValues = {};
    @state() private otherData: { label: string, value: string | null }[] = [];
    @state() private countries: readonly Country[] = [];
    @state() private saving = false;
    @state() private saveError: string | null = null;

    static readonly styles = [
        base,
        dialog,
        form,
        row,
        detailList,
        attendeeFieldsStyles,
        css`
            sl-dialog {
                --alfio-dialog-max-width: 36rem;
            }
            /* one field below the other */
            .attendee-fields {
                grid-template-columns: minmax(0, 1fr);
                gap: var(--sl-spacing-medium);
            }
            .other-data {
                margin-top: var(--sl-spacing-large);
            }
            .other-data h4 {
                margin: 0 0 var(--sl-spacing-x-small);
                font-size: var(--sl-font-size-medium);
            }
            sl-alert {
                margin-top: var(--sl-spacing-medium);
            }
        `,
    ];

    open(request: AttendeeDataRequest): Promise<boolean | null> {
        this.request = request;
        this.values = {};
        this.otherData = [];
        this.loadError = null;
        this.saveError = null;
        void this.load(request);
        return this.prompt();
    }

    render(): TemplateResult {
        const ticket = this.request?.ticket;
        let name = '';
        if (ticket != null) {
            name = formatFullName(ticket) || ticket.email || 'the attendee';
        }
        return html`
            <sl-dialog class="responsive-dialog" label=${`Data of ${name}`}
                       @sl-request-close=${this.onRequestClose}
                       @sl-after-hide=${this.afterHide}>
                ${dialogTitle('card-list', `Data of ${name}`, 'Fields left empty can be filled in later by the attendee.')}
                ${this.renderBody()}
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => this.close(null)}>
                            Cancel
                        </sl-button>
                        <div></div>
                        <sl-button id="save-attendee-data" variant="warning" size="large" ?loading=${this.saving}
                                   ?disabled=${this.loading || this.loadError != null} @click=${this.save}>
                            Save
                        </sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    private renderBody(): TemplateResult {
        const request = this.request;
        if (request == null) {
            return html``;
        }
        if (this.loading) {
            return html`<div class="dialog-loading"><sl-spinner></sl-spinner></div>`;
        }
        if (this.loadError != null) {
            return html`
                <sl-alert variant="danger" open>
                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                    ${this.loadError}
                </sl-alert>
            `;
        }
        return html`
            ${attendeeFields({
                fields: request.fields,
                values: this.values,
                countries: this.countries,
                language: request.language,
            }, values => { this.values = values; })}
            ${when(this.otherData.length > 0, () => html`
                <div class="other-data">
                    <h4>Other data</h4>
                    ${renderDetailList(this.otherData)}
                </div>
            `)}
            ${when(this.saveError, () => html`
                <sl-alert class="save-error" variant="danger" open>
                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                    ${this.saveError}
                </sl-alert>
            `)}
        `;
    }

    private async load(request: AttendeeDataRequest): Promise<void> {
        this.loading = true;
        try {
            const [values, fullData] = await Promise.all([
                request.service.attendeeFieldValues(request.ticket.uuid),
                request.service.fullTicketData(request.ticket.uuid),
            ]);
            this.values = values;
            // e.g. the data collected for the additional items
            const editable = new Set(request.fields.map(field => field.name));
            const describe = (item: AdditionalFieldValue) => ({
                label: item.description[request.language]?.label ?? item.name,
                value: item.description[request.language]?.restrictedValuesDescription?.[item.value ?? ''] ?? item.value,
            });
            this.otherData = [...fullData.ticketFieldConfigurationBeforeStandard, ...fullData.ticketFieldConfigurationAfterStandard]
                .filter(item => !editable.has(item.name) && !!item.value)
                .map(describe);
            if (this.countries.length === 0 && request.fields.some(field => field.type === 'country')) {
                this.countries = await UtilService.countries(request.language).catch(() => []);
            }
        } catch (error) {
            this.loadError = errorMessage(error, 'Failed to load the attendee data');
        } finally {
            this.loading = false;
        }
    }

    private save = async (): Promise<void> => {
        const request = this.request;
        if (request == null || this.saving) {
            return;
        }
        this.saving = true;
        this.saveError = null;
        try {
            await request.service.updateAttendeeFields(request.ticket.uuid, valuesForFields(request.fields, this.values));
            this.close(true);
        } catch (error) {
            this.saveError = errorMessage(error, 'Failed to save the attendee data');
        } finally {
            this.saving = false;
        }
    };

    // a click outside the dialog would discard the changes: only the close button and Cancel close it
    private onRequestClose = (event: SlRequestCloseEvent): void => {
        if (event.detail.source === 'overlay') {
            event.preventDefault();
        }
    };

    private afterHide = (event: Event): void => {
        this.onAfterHide(event);
        if (event.target === this.dialog) {
            this.request = null;
        }
    };
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-attendee-data-dialog': AttendeeDataDialog;
    }
}
