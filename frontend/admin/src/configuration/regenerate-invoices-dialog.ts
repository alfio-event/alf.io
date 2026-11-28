import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import type {SlDialog, SlRequestCloseEvent} from '@shoelace-style/shoelace';
import {TanStackFormController} from '@tanstack/lit-form';
import {SettingsService} from '../service/settings.ts';
import {errorMessage} from '../service/helpers.ts';
import {dispatchFeedback} from '../model/dom-events.ts';
import {
    BoundField,
    focusFirstError,
    FormMountController,
    INVALID_FORM_MESSAGE,
    requiredValue,
    submitValidated,
    validatedInput,
} from '../components/form-validation.ts';
import {base, dialog, form, modernLayout, row, textColors} from '../styles.ts';
import { t, tHtml } from '../service/i18n';

interface PeriodValues {
    from: string;
    to: string;
}

/**
 * Regenerates the invoices of an event created in a period, so that they show the current settings of the event.
 * The regeneration runs in the background: the user receives an e-mail when it is complete.
 */
@customElement('alfio-regenerate-invoices-dialog')
export class RegenerateInvoicesDialog extends LitElement {

    // the content is rendered only while the dialog is open, so that every opening starts from an empty form
    @state() private opened = false;
    @state() private eventId = -1;
    @state() private eventName = '';
    // invoices can't be older than the day before the first invoice
    @state() private minDate = '';
    // the invoices of the period, null while they are being searched or when the period is incomplete
    @state() private matching: number[] | null = null;
    @state() private searching = false;
    @state() private submitting = false;
    @state() private invalid = false;
    @state() private submitError: string | null = null;
    @query('sl-dialog') private dialog!: SlDialog;
    private searchRequest = 0;

    readonly #form = new TanStackFormController(this, {
        defaultValues: {from: '', to: ''} as PeriodValues,
        onSubmit: async () => {
            await this.regenerate();
        },
    });

    static readonly styles = [
        base,
        textColors,
        form,
        row,
        modernLayout,
        dialog,
        css`
            sl-dialog {
                --alfio-dialog-max-width: 40rem;
            }
            .matching {
                margin: 0;
            }
        `,
    ];

    constructor() {
        super();
        new FormMountController(this, this.#form.api);
    }

    async show(eventId: number, eventName: string): Promise<void> {
        this.eventId = eventId;
        this.eventName = eventName;
        this.matching = null;
        this.invalid = false;
        this.submitError = null;
        this.#form.api.reset({from: '', to: ''});
        const firstInvoice = new Date(await SettingsService.loadFirstInvoiceDate(eventId));
        firstInvoice.setDate(firstInvoice.getDate() - 1);
        this.minDate = toDateValue(firstInvoice);
        this.opened = true;
        await this.updateComplete;
        await this.dialog.show();
    }

    render(): TemplateResult {
        return html`
            <sl-dialog class="responsive-dialog" label=${t('admin.settings.regenerate-invoices')}
                       @sl-request-close=${this.onRequestClose}
                       @sl-after-hide=${this.onAfterHide}>
                ${when(this.opened, () => this.renderForm())}
            </sl-dialog>
        `;
    }

    private renderForm(): TemplateResult {
        const submitted = this.#form.api.state.submissionAttempts > 0;
        return html`
            <form id="regenerate-invoices-form" class="form-stack" novalidate @submit=${this.submit}>
                <p class="text-muted">
                    ${t('admin.regenerate-invoices.description', this.eventName)}
                </p>
                <div class="row" style="--alfio-row-cols: 2">
                    ${this.#form.field({
                        name: 'from',
                        validators: {
                            onChange: ({value}) => requiredValue(value),
                            onSubmit: ({value}) => requiredValue(value),
                        },
                    }, (field: BoundField<string>) => validatedInput(field, submitted, {
                        id: 'invoices-from', label: t('admin.regenerate-invoices.from'), type: 'date', min: this.minDate,
                    }))}
                    ${this.#form.field({
                        name: 'to',
                        validators: {
                            onChangeListenTo: ['from'],
                            onChange: ({value, fieldApi}) => checkEndDate(value, fieldApi.form.getFieldValue('from')),
                            onSubmit: ({value, fieldApi}) => checkEndDate(value, fieldApi.form.getFieldValue('from')),
                        },
                    }, (field: BoundField<string>) => validatedInput(field, submitted, {
                        id: 'invoices-to', label: t('admin.regenerate-invoices.to'), type: 'date', min: this.minDate,
                    }))}
                </div>
                ${this.renderMatching()}
                ${when(this.submitError, () => html`
                    <sl-alert variant="danger" open>
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        ${this.submitError}
                    </sl-alert>
                `)}
            </form>
            <div slot="footer">
                <sl-divider></sl-divider>
                ${when(this.invalid, () => html`<p class="text-danger">${INVALID_FORM_MESSAGE}</p>`)}
                <div class="row" style="--alfio-row-cols: 3">
                    <sl-button variant="default" size="large" @click=${this.close}>${t('admin.common.cancel')}</sl-button>
                    <div></div>
                    <sl-button id="confirm-regenerate-invoices" variant="warning" size="large"
                               ?loading=${this.submitting} ?disabled=${this.submitting}
                               @click=${this.submit}>${t('admin.reservation.billing.regenerate')}</sl-button>
                </div>
            </div>
        `;
    }

    private renderMatching(): TemplateResult {
        if (this.searching) {
            return html`<p class="matching text-muted"><sl-spinner></sl-spinner> ${t('admin.regenerate-invoices.searching')}</p>`;
        }
        if (this.matching == null) {
            return html``;
        }
        if (this.matching.length === 0) {
            return html`<p class="matching text-muted" id="matching-invoices">${t('admin.regenerate-invoices.none')}</p>`;
        }
        let label = t('admin.regenerate-invoices.count', { count: this.matching.length });
        if (this.matching.length === 1) {
            label = t('admin.regenerate-invoices.count', { count: 1 });
        }
        return html`<p class="matching" id="matching-invoices">${tHtml('admin.regenerate-invoices.will-be-regenerated', html`<strong>${label}</strong>`)}</p>`;
    }

    // the period searched last
    private lastSearch = '';

    protected updated(): void {
        const {from, to} = this.#form.api.state.values;
        const key = `${from}|${to}`;
        if (this.opened && key !== this.lastSearch) {
            this.lastSearch = key;
            void this.search(from, to);
        }
    }

    private async search(from: string, to: string): Promise<void> {
        const request = ++this.searchRequest;
        if (from === '' || to === '' || checkEndDate(to, from) != null) {
            this.matching = null;
            return;
        }
        this.searching = true;
        try {
            const matching = await SettingsService.findMatchingInvoices(this.eventId, startOfDay(from), endOfDay(to));
            if (request === this.searchRequest) {
                this.matching = matching;
            }
        } catch (e) {
            if (request === this.searchRequest) {
                this.matching = null;
                this.submitError = errorMessage(e, t('admin.settings.error.search-invoices'));
            }
        } finally {
            if (request === this.searchRequest) {
                this.searching = false;
            }
        }
    }

    private submit = async (event: Event): Promise<void> => {
        event.preventDefault();
        if (this.submitting) {
            return;
        }
        this.submitError = null;
        const valid = await submitValidated(this.#form.api);
        this.invalid = !valid;
        if (!valid) {
            await this.updateComplete;
            focusFirstError(this.renderRoot);
        }
    };

    private async regenerate(): Promise<void> {
        const {from, to} = this.#form.api.state.values;
        this.submitting = true;
        try {
            const matching = await SettingsService.findMatchingInvoices(this.eventId, startOfDay(from), endOfDay(to));
            this.matching = matching;
            if (matching.length === 0) {
                this.submitError = t('admin.regenerate-invoices.none-choose-another');
                return;
            }
            const scheduled = await SettingsService.regenerateInvoices(this.eventId, matching);
            if (!scheduled) {
                this.submitError = t('admin.regenerate-invoices.failed');
                return;
            }
            dispatchFeedback({type: 'success', message: t('admin.regenerate-invoices.scheduled')}, this);
            this.close();
        } catch (e) {
            this.submitError = errorMessage(e, t('admin.regenerate-invoices.failed'));
        } finally {
            this.submitting = false;
        }
    }

    private close = (): void => {
        void this.dialog.hide();
    };

    // a click outside the dialog would discard the period: only the close button and Cancel close it
    private onRequestClose = (event: SlRequestCloseEvent): void => {
        if (event.detail.source === 'overlay') {
            event.preventDefault();
        }
    };

    private onAfterHide = (event: Event): void => {
        // nested Shoelace components (e.g. tooltips) emit their own sl-after-hide
        if (event.target !== event.currentTarget) {
            return;
        }
        this.opened = false;
        this.lastSearch = '';
    };
}

function checkEndDate(value: unknown, from: unknown): string | undefined {
    const required = requiredValue(value);
    if (required != null) {
        return required;
    }
    if (from != null && from !== '' && String(value) < String(from)) {
        return t('admin.regenerate-invoices.error.to');
    }
    return undefined;
}

function toDateValue(date: Date): string {
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${date.getFullYear()}-${month}-${day}`;
}

// a date (yyyy-MM-dd) in the browser time zone, as epoch milliseconds
function startOfDay(date: string): number {
    return new Date(`${date}T00:00:00`).getTime();
}

function endOfDay(date: string): number {
    return new Date(`${date}T23:59:59.999`).getTime();
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-regenerate-invoices-dialog': RegenerateInvoicesDialog;
    }
}
