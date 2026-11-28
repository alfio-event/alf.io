import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {keyed} from 'lit/directives/keyed.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {base, badges, modernLayout, modernTable, reservationTable, retroCompat, row} from '../../styles.ts';
import {BulkConfirmationResult} from '../../model/reservation.ts';
import {PendingPaymentsService} from '../../service/pending-payments.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {sectionCard} from '../../components/section-card.ts';
import {FileUploadChangeEvent} from '../../components/file-upload.ts';
import '../../components/file-upload.ts';
import { t, tHtml, formatNumber } from '../../service/i18n';

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
            .card-description {
                margin: 0 0 var(--sl-spacing-medium);
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
            ${sectionCard({ icon: 'file-earmark-spreadsheet', title: t('admin.bulk-confirmation.title') }, html`
                    <p class="card-description">${t('admin.bulk-confirmation.description')}</p>
                    <div class="row">
                        <div class="upload">
                            ${keyed(this.uploadCount, html`
                                <alfio-file-upload accept=".csv,text/csv" label=${t('admin.bulk-confirmation.upload-label')}
                                                   @change=${(e: FileUploadChangeEvent) => { this.file = e.detail.file; }}></alfio-file-upload>
                            `)}
                            <sl-button variant="warning" size="large" ?loading=${this.uploading} ?disabled=${this.file == null}
                                       @click=${this.upload}>
                                <sl-icon slot="prefix" name="upload"></sl-icon>
                                ${t('admin.bulk-confirmation.upload')}
                            </sl-button>
                        </div>
                        ${this.renderFormat()}
                    </div>
                    ${when(this.results.length > 0, () => this.renderResults())}
            `)}
        `;
    }

    private renderFormat(): TemplateResult {
        return html`
            <div class="format">
                <h4>${t('admin.bulk-confirmation.format')}</h4>
                <p>
                    ${tHtml('admin.bulk-confirmation.format.text', html`<code>,</code>`, html`<code>"</code>`, html`<code>\\</code>`)}
                </p>
                <p>${t('admin.bulk-confirmation.format.rows')}</p>
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
                    <h4>${t('admin.bulk-confirmation.results')}</h4>
                    <sl-badge variant="success" pill>${t('admin.bulk-confirmation.confirmed', formatNumber(confirmed))}</sl-badge>
                    ${when(failed > 0, () => html`<sl-badge variant="danger" pill>${t('admin.attendees-import.status.failed', formatNumber(failed))}</sl-badge>`)}
                </div>
                <div class="table-responsive">
                    <table class="table table-striped">
                        <thead>
                            <tr>
                                <th>${t('admin.bulk-confirmation.row')}</th>
                                <th>${t('admin.common.status')}</th>
                                <th>${t('admin.bulk-confirmation.reservation-id')}</th>
                                <th>${t('admin.compose.message')}</th>
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
            return html`<sl-icon class="status-success" name="check-circle-fill" label=${t('admin.statistics.confirmed')}></sl-icon>`;
        }
        return html`<sl-icon class="status-error" name="exclamation-triangle-fill" label=${t('admin.bulk-confirmation.failed')}></sl-icon>`;
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
            dispatchFeedback({ type: 'danger', message: t('admin.bulk-confirmation.process-failed') }, this);
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
