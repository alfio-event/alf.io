import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {ifDefined} from 'lit/directives/if-defined.js';
import {
    DATE_FORMAT_CHANGED_EVENT,
    DateFormatStyle,
    DateValue,
    formatDate,
    formatDateRange,
    toIsoString,
} from '../service/date-format.ts';

/**
 * Displays a date, or an interval when "end" is set, using the admin-wide format (see service/date-format.ts).
 * The default slot is rendered when there is no date to display.
 *
 *   <alfio-format-date date="2025-02-28 07:00" end="2025-02-28 08:00"></alfio-format-date>
 *   <alfio-format-date .date=${payment.transactionTimestamp} time-zone=${event.timeZone}>—</alfio-format-date>
 */
@customElement('alfio-format-date')
export class FormatDate extends LitElement {
    @property({ attribute: 'date' }) date: DateValue;
    @property({ attribute: 'end' }) end: DateValue;
    // IANA time zone used to display instants. Wall-clock values are displayed as they are
    @property({ type: String, attribute: 'time-zone' }) timeZone?: string;
    @property({ type: String }) format: DateFormatStyle = 'date-time';

    static readonly styles = css`
        :host {
            display: inline;
        }
    `;

    connectedCallback(): void {
        super.connectedCallback();
        window.addEventListener(DATE_FORMAT_CHANGED_EVENT, this.onFormatChanged);
    }

    disconnectedCallback(): void {
        super.disconnectedCallback();
        window.removeEventListener(DATE_FORMAT_CHANGED_EVENT, this.onFormatChanged);
    }

    render(): TemplateResult {
        const options = { timeZone: this.timeZone, style: this.format };
        const text = this.formattedText(options);
        if (text === '') {
            return html`<slot></slot>`;
        }
        return html`<time datetime=${toIsoString(this.date)} title=${ifDefined(this.timeZone)}>${text}</time>`;
    }

    private formattedText(options: { timeZone?: string, style: DateFormatStyle }): string {
        if (this.end != null && this.end !== '') {
            return formatDateRange(this.date, this.end, options);
        }
        return formatDate(this.date, options);
    }

    private onFormatChanged = (): void => {
        this.requestUpdate();
    };
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-format-date': FormatDate;
    }
}
