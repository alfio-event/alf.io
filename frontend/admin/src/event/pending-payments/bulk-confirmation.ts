import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {keyed} from 'lit/directives/keyed.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {base, badges, modernLayout, modernTable, reservationTable, retroCompat, row} from '../../styles.ts';
import {BulkConfirmationResult} from '../../model/reservation.ts';
import {PendingPaymentsService} from '../../service/pending-payments.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {FileUploadChangeEvent} from '../../components/file-upload.ts';
import '../../components/file-upload.ts';

const PAYMENTS_CONFIRMED_EVENT = 'alfio-payments-confirmed';

/**
 * Confirms pending payments by uploading a CSV file with the received payments.
 * Fires "alfio-payments-confirmed" once the file has been processed.
 */
@customElement('alfio-bulk-confirmation')
export class BulkConfirmation extends LitElement {
    @property({ type: String, attribute: 'event-name' }) eventName = '';
    @state() private file: File | null = null;
    @state() private uploading = false;
    @state() private results: BulkConfirmationResult[] = [];
    // changed after each upload, to reset the file selection
    @state() private uploadCount = 0;

    static readonly styles = [
        base,
        retroCompat,
        badges,
        modernTable,
        modernLayout,
        reservationTable,
        row,
        css`
            :host {
                display: block;
                --alfio-section-body-padding: var(--sl-spacing-large);
            }
            .section-header small {
                color: var(--sl-color-gray-600);
            }
            .upload {
                display: grid;
                gap: var(--sl-spacing-medium);
                align-content: start;
            }
            .format h4 {
                margin-top: 0;
            }
            .format p {
                margin: 0 0 var(--sl-spacing-small);
                color: var(--sl-color-gray-700);
                font-size: var(--sl-font-size-small);
            }
            .format pre {
                margin: 0 0 var(--sl-spacing-small);
                padding: var(--sl-spacing-small) var(--sl-spacing-medium);
                background: var(--sl-color-gray-50);
                border: 1px solid var(--sl-color-gray-200);
                border-radius: var(--sl-input-border-radius);
                font-family: var(--sl-font-mono);
                font-size: var(--sl-font-size-small);
                overflow-x: auto;
            }
            .reservation-code {
                color: var(--sl-color-primary-600);
            }
            .amount-value {
                color: var(--sl-color-success-700);
            }
            .results {
                margin-top: var(--sl-spacing-large);
            }
            .results-summary {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-small);
                margin-bottom: var(--sl-spacing-small);
            }
            .results-summary h4 {
                margin: 0;
            }
            .status-success {
                color: var(--sl-color-success-600);
            }
            .status-error {
                color: var(--sl-color-danger-600);
            }
            .error-message {
                overflow-wrap: anywhere;
            }
        `,
    ];

    render(): TemplateResult {
        return html`
            <section class="section-card">
                <div class="section-header">
                    <sl-icon name="file-earmark-spreadsheet"></sl-icon>
                    <h3>Bulk confirmation</h3>
                    <small>Upload a CSV file with the received payments</small>
                </div>
                <div class="section-body">
                    <div class="row">
                        <div class="upload">
                            ${keyed(this.uploadCount, html`
                                <alfio-file-upload accept=".csv,text/csv" label="Choose a CSV file or drag it here"
                                                   @change=${(e: FileUploadChangeEvent) => { this.file = e.detail.file; }}></alfio-file-upload>
                            `)}
                            <sl-button variant="success" ?disabled=${this.file == null || this.uploading} @click=${this.upload}>
                                ${when(this.uploading,
                                    () => html`<sl-spinner slot="prefix"></sl-spinner>`,
                                    () => html`<sl-icon slot="prefix" name="upload"></sl-icon>`)}
                                Upload and confirm
                            </sl-button>
                        </div>
                        ${this.renderFormat()}
                    </div>
                    ${when(this.results.length > 0, () => this.renderResults())}
                </div>
            </section>
        `;
    }

    private renderFormat(): TemplateResult {
        return html`
            <div class="format">
                <h4>File format</h4>
                <p>
                    No header row. Use commas (<code>,</code>) as separator, double quotes (<code>"</code>) as quote
                    character and backslash (<code>\\</code>) as escape character.
                </p>
                <p>Each row contains the (partial or full) reservation code and the paid amount:</p>
                <pre><span class="reservation-code">abcd-efghi-jklm</span>,<span class="amount-value">10.00</span></pre>
            </div>
        `;
    }

    private renderResults(): TemplateResult {
        const confirmed = this.results.filter(result => result.left).length;
        const failed = this.results.length - confirmed;
        return html`
            <div class="results">
                <div class="results-summary">
                    <h4>Upload results</h4>
                    <sl-badge variant="success" pill>${confirmed} confirmed</sl-badge>
                    ${when(failed > 0, () => html`<sl-badge variant="danger" pill>${failed} failed</sl-badge>`)}
                </div>
                <div class="table-responsive">
                    <table class="table table-striped">
                        <thead>
                            <tr>
                                <th>Row</th>
                                <th>Status</th>
                                <th>Reservation ID</th>
                                <th>Message</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${repeat(this.results, (_, index) => index, (result, index) => html`
                                <tr>
                                    <td>${index + 1}</td>
                                    <td>${this.renderStatus(result)}</td>
                                    <td class="reservation-id">${result.middle}</td>
                                    <td class="error-message">${result.right}</td>
                                </tr>
                            `)}
                        </tbody>
                    </table>
                </div>
            </div>
        `;
    }

    private renderStatus(result: BulkConfirmationResult): TemplateResult {
        if (result.left) {
            return html`<sl-icon class="status-success" name="check-circle-fill" label="Confirmed"></sl-icon>`;
        }
        return html`<sl-icon class="status-error" name="exclamation-triangle-fill" label="Failed"></sl-icon>`;
    }

    private upload = async (): Promise<void> => {
        if (this.file == null || this.uploading) {
            return;
        }
        this.uploading = true;
        try {
            this.results = await PendingPaymentsService.bulkConfirm(this.eventName, this.file);
            this.file = null;
            this.uploadCount++;
            this.dispatchEvent(new CustomEvent(PAYMENTS_CONFIRMED_EVENT, { bubbles: true, composed: true }));
        } catch {
            dispatchFeedback({ type: 'danger', message: 'Failed to process the uploaded file' }, this);
        } finally {
            this.uploading = false;
        }
    };
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-bulk-confirmation': BulkConfirmation;
    }
}
