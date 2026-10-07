import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import type {SlInput, SlRadioGroup, SlSwitch} from '@shoelace-style/shoelace';
import {PurchaseContextType} from '../model/purchase-context.ts';
import {EmailMessage} from '../model/email-message.ts';
import {AlfioDialogClosed, dispatchFeedback} from '../model/dom-events.ts';
import {
    AdditionalFieldValue,
    AuditEntry,
    BillingDocument,
    BillingDocumentType,
    CustomerBillingDetails,
    PaymentInfo,
    ReservationDescriptor,
    ReservationModification,
    SubscriptionWithUsageDetails,
    Ticket,
} from '../model/reservation-detail.ts';
import {ReservationService} from '../service/reservation.ts';
import {PurchaseContextService} from '../service/purchase-context.ts';
import {PendingPaymentsService} from '../service/pending-payments.ts';
import {CustomPaymentMethodsService} from '../service/custom-payment-methods.ts';
import {ConfirmationDialogService} from '../service/confirmation-dialog.ts';
import {UtilService} from '../service/util.ts';
import {toDateTimeModification} from '../service/helpers.ts';
import {toDateTimeLocal} from '../service/date-format.ts';
import {formatAmount, formatFullName, localizedTitle, shortReservationId} from '../service/reservation-format.ts';
import {
    badges,
    base,
    detailList,
    form,
    modernLayout,
    modernTable,
    purchaseContextListPage,
    reservationTable,
    retroCompat,
    row,
    textColors,
} from '../styles.ts';
import {taskContent} from '../components/task-content.ts';
import {emptyState} from '../components/empty-state.ts';
import {sectionCard} from '../components/section-card.ts';
import {detailList as renderDetailList} from '../components/detail-list.ts';
import {formField, selectField} from '../components/form-field.ts';
import {CancellationDialog} from '../components/cancellation-dialog.ts';
import {InfoDialog} from '../components/info-dialog.ts';
import {EditPaymentDialog} from '../event/payments-list/edit-payment-dialog.ts';
import {MatchingTransactionDialog} from '../event/pending-payments/matching-transaction-dialog.ts';
import {NotifyAttendeesDialog} from './notify-attendees-dialog.ts';
import {isNotPaid, isOfflinePayment, reservationStatusBadge} from './reservation-status.ts';
import {orderSummary, orderSummaryTable} from './order-summary.ts';
import {checkInLog} from './activity-log.ts';
import {
    cancellationChoice,
    cancelReservationRequest,
    removeTicketRequest,
    ticketRemovalChoice,
} from './cancellation.ts';
import '../components/cancellation-dialog.ts';
import '../components/info-dialog.ts';
import '../components/format-date.ts';
import '../components/payment-method.ts';
import '../email-log/email-table.ts';
import '../event/payments-list/edit-payment-dialog.ts';
import '../event/pending-payments/matching-transaction-dialog.ts';
import '../event/reservations-list/reservations-list.ts';
import './notify-attendees-dialog.ts';
import './activity-log.ts';

interface PurchaseContextInfo {
    title: string;
    timeZone: string;
    organizationId: number;
    currency: string;
    locales: string[];
    // false if the current user can only see the reservation (e.g. a supervisor)
    visible: boolean;
}

interface ReservationData {
    context: PurchaseContextInfo;
    descriptor: ReservationDescriptor;
    paymentInfo: PaymentInfo | null;
    // name of the custom offline payment method selected by the customer, if any
    customPaymentMethod: string | null;
    ticketsWithAdditionalData: Set<number>;
    audit: AuditEntry[];
    billingDocuments: BillingDocument[];
    emails: EmailMessage[];
}

interface ContactDraft {
    firstName: string;
    lastName: string;
    emailAddress: string;
    userLanguage: string;
    vatNr: string;
    vatCountryCode: string;
    customerType: CustomerType;
    companyName: string;
    // false: the address is a free text (e.g. entered by an administrator), true: it is entered field by field
    structuredAddress: boolean;
    addressLine1: string;
    addressLine2: string;
    zip: string;
    city: string;
    state: string;
    billingAddress: string;
    fiscalCode: string;
    referenceType: string;
    addresseeCode: string;
    pec: string;
}

interface AttendeeDraft {
    firstName: string;
    lastName: string;
    emailAddress: string;
}

interface SubscriptionDraft {
    firstName: string;
    lastName: string;
    email: string;
    maxAllowed: string;
    validityFrom: string;
    validityTo: string;
}

interface Drafts {
    expiration: string;
    contact: ContactDraft | null;
    // by category ID
    attendees: Map<number, Map<number, AttendeeDraft>>;
    subscription: SubscriptionDraft | null;
    vatApplied: boolean | null;
    // the advanced invoice options are dangerous: they are displayed only on request
    showAdvancedBilling: boolean;
}

export interface ReservationChangedDetail {
    reservationId: string;
    confirmed: boolean;
}

const BILLING_DOCUMENT_TYPES: Record<BillingDocumentType, string> = {
    RECEIPT: 'Receipt',
    INVOICE: 'Invoice',
    CREDIT_NOTE: 'Credit note',
};

type CustomerType = 'private' | 'company';

const VAT_APPLIED_STATUSES = ['INCLUDED', 'NOT_INCLUDED'];
const VAT_EXEMPT_STATUSES = ['INCLUDED_EXEMPT', 'NOT_INCLUDED_EXEMPT'];

const E_INVOICING_REFERENCE_TYPES = [
    { value: 'ADDRESSEE_CODE', label: 'Codice destinatario' },
    { value: 'PEC', label: 'PEC' },
    { value: 'NONE', label: 'Nessun codice destinatario o PEC' },
];

/**
 * Details of a reservation, for events and subscriptions.
 * Fires "alfio-reservation-changed" after every successful modification, and "alfio-reservation-close" when
 * the user clicks on "Close" (embedded mode only, e.g. when displayed in a modal).
 */
@customElement('alfio-reservation-detail')
export class ReservationDetailPage extends LitElement {
    @property({ type: String, attribute: 'data-purchase-context-type' }) purchaseContextType: PurchaseContextType = 'event';
    @property({ type: String, attribute: 'data-public-identifier' }) publicIdentifier = '';
    @property({ type: String, attribute: 'data-reservation-id' }) reservationId = '';
    // subscriptions only
    @property({ type: Number, attribute: 'data-organization-id' }) organizationId?: number;
    // "true" right after the reservation has been created from the admin
    @property({ type: String, attribute: 'data-from-creation' }) fromCreation = '';
    // displayed inside a modal: no sidebar offset, "Close" button
    @property({ type: Boolean, attribute: 'data-embedded', reflect: true }) embedded = false;

    @state() private lastData: ReservationData | null = null;
    @state() private drafts: Drafts = emptyDrafts();
    @state() private countries: Record<string, string> = {};
    @state() private creationNoticeVisible = true;
    @state() private refundAmount = '';
    @state() private busy = false;

    @query('alfio-cancellation-dialog') private cancellationDialog!: CancellationDialog;
    @query('alfio-info-dialog') private infoDialog!: InfoDialog;
    @query('alfio-edit-payment-dialog') private confirmPaymentDialog!: EditPaymentDialog;
    @query('alfio-matching-transaction-dialog') private matchingDialog!: MatchingTransactionDialog;
    @query('alfio-notify-attendees-dialog') private notifyAttendeesDialog!: NotifyAttendeesDialog;

    private readonly isOwner = window.USER_IS_OWNER;

    private readonly loadDataTask = new Task(this, {
        task: async ([type, publicIdentifier, reservationId, organizationId]): Promise<ReservationData> => {
            const service = new ReservationService(type, publicIdentifier, reservationId);
            const [context, descriptor] = await Promise.all([
                this.loadPurchaseContext(type, publicIdentifier, organizationId),
                service.load(),
            ]);
            if (!context.visible) {
                return emptyData(context, descriptor);
            }
            const ownerData = this.isOwner;
            const [paymentInfo, ticketsWithAdditionalData, audit, billingDocuments, emails] = await Promise.all([
                optional(service.paymentInfo(), null),
                optional(service.ticketsWithAdditionalData(), []),
                optional(service.audit(), []),
                ownerOnly(ownerData, () => service.billingDocuments()),
                ownerOnly(ownerData, () => service.emails()),
            ]);
            return {
                context,
                descriptor,
                paymentInfo,
                customPaymentMethod: await this.loadCustomPaymentMethod(context.organizationId, paymentInfo),
                ticketsWithAdditionalData: new Set(ticketsWithAdditionalData),
                audit,
                billingDocuments,
                emails,
            };
        },
        args: () => [this.purchaseContextType, this.publicIdentifier, this.reservationId, this.organizationId] as const,
        // Keep results mounted during refreshes.
        onComplete: (data) => {
            this.lastData = data;
            this.drafts = initialDrafts(data);
        },
    });

    static readonly styles = [
        base,
        retroCompat,
        textColors,
        badges,
        modernTable,
        reservationTable,
        modernLayout,
        purchaseContextListPage,
        detailList,
        orderSummary,
        form,
        row,
        css`
            :host([data-embedded]) .container {
                width: 100%;
                margin-inline: 0;
            }
            .page-title-row {
                align-items: center;
                gap: var(--sl-spacing-x-small);
            }
            .page-title-row .reservation-status {
                margin-inline-start: var(--sl-spacing-x-small);
            }
            .page-title-row .reservation-status::part(base) {
                padding: var(--sl-spacing-2x-small) var(--sl-spacing-x-small);
                font-size: var(--sl-font-size-small);
            }
            .notices {
                display: grid;
                gap: var(--sl-spacing-medium);
                margin-bottom: var(--sl-spacing-medium);
            }
            .share-link {
                flex: 1 1 24rem;
                margin-top: 0;
            }
            .details-grid {
                display: grid;
                grid-template-columns: repeat(2, minmax(0, 1fr));
                gap: 0 var(--sl-spacing-medium);
            }
            .details-grid .wide {
                grid-column: 1 / -1;
            }
            .section-body {
                --alfio-section-body-padding: var(--sl-spacing-medium);
            }
            .section-body .table-responsive {
                margin: calc(-1 * var(--sl-spacing-medium));
                width: calc(100% + 2 * var(--sl-spacing-medium));
            }
            .refund-form {
                display: flex;
                align-items: flex-end;
                gap: var(--sl-spacing-small);
                margin-top: var(--sl-spacing-medium);
                padding-top: var(--sl-spacing-medium);
                border-top: 1px solid var(--sl-color-gray-200);
            }
            .refund-form sl-input {
                flex: 1;
                margin-top: 0;
            }
            /* forms inside the cards of the grid: never wider than their card */
            .details-grid .form-stack {
                grid-template-columns: minmax(0, 1fr);
            }
            .details-grid .form-stack .row {
                column-gap: var(--sl-spacing-medium);
            }
            @media only screen and (min-width: 768px) {
                .details-grid .form-stack .row {
                    grid-template-columns: repeat(var(--alfio-row-cols), minmax(0, 1fr));
                }
            }
            .advanced-billing-toggle {
                margin-bottom: var(--sl-spacing-medium);
            }
            .address-switch {
                justify-self: start;
            }
            .billing-address {
                margin: 0;
                font-family: inherit;
                white-space: pre-wrap;
            }
            .monospace {
                font-family: var(--sl-font-mono);
                font-size: var(--sl-font-size-small);
            }
            .attendee-cell sl-input {
                margin-top: 0;
            }

            .tab-label {
                display: inline-flex;
                align-items: center;
                gap: var(--sl-spacing-2x-small);
            }
            .tab-label sl-badge {
                margin-inline-start: var(--sl-spacing-2x-small);
            }
            .tab-toolbar {
                display: flex;
                justify-content: flex-end;
                margin-bottom: var(--sl-spacing-medium);
            }
            .save-bar {
                position: sticky;
                bottom: 0;
                z-index: 1;
                padding-bottom: var(--sl-spacing-medium);
                background: var(--sl-color-neutral-0);
            }
            @media (max-width: 900px) {
                .details-grid {
                    grid-template-columns: minmax(0, 1fr);
                }
            }
        `,
    ];

    render(): TemplateResult {
        return taskContent(this.loadDataTask, this.lastData, 'Failed to load the reservation. Please try again.',
            (data) => this.renderContent(data));
    }

    private renderContent(data: ReservationData): TemplateResult {
        const reservation = data.descriptor.reservation;
        return html`
            <div class="container">
                <div class="page-title-row secondary">
                    <h1>Reservation ${shortReservationId(reservation.id)}</h1>
                    <sl-copy-button hoist value=${reservation.id} copy-label="Copy the full reservation ID"></sl-copy-button>
                    ${reservationStatusBadge(reservation.status)}
                </div>
                <p class="page-description text-muted">${data.context.title}</p>
                <hr class="page-separator" />
                ${this.renderNotices(data)}
                ${this.renderToolbar(data)}
                <sl-tab-group>
                    ${this.renderTab('details', 'Details', 'card-text', null)}
                    ${when(this.isOwner && data.context.visible, () => html`
                        ${this.renderTab('billing-documents', 'Billing documents', 'receipt', data.billingDocuments.length)}
                        ${this.renderTab('emails', 'E-mails', 'envelope', data.emails.length)}
                        ${this.renderTab('history', 'History', 'clock-history', null)}
                    `)}
                    <sl-tab-panel name="details">${this.renderDetails(data)}</sl-tab-panel>
                    ${when(this.isOwner && data.context.visible, () => html`
                        <sl-tab-panel name="billing-documents">${this.renderBillingDocuments(data)}</sl-tab-panel>
                        <sl-tab-panel name="emails">
                            <alfio-email-table .messages=${data.emails}
                                               data-purchase-context-type=${this.purchaseContextType}
                                               data-public-identifier=${this.publicIdentifier}
                                               data-time-zone=${data.context.timeZone}
                                               data-empty-message="No e-mails have been sent for this reservation"></alfio-email-table>
                        </sl-tab-panel>
                        <sl-tab-panel name="history">${this.renderHistory(data)}</sl-tab-panel>
                    `)}
                </sl-tab-group>
            </div>
            <alfio-cancellation-dialog></alfio-cancellation-dialog>
            <alfio-info-dialog></alfio-info-dialog>
            <alfio-notify-attendees-dialog></alfio-notify-attendees-dialog>
            <alfio-matching-transaction-dialog time-zone=${data.context.timeZone}></alfio-matching-transaction-dialog>
            <alfio-edit-payment-dialog purchase-context-type=${this.purchaseContextType}
                                       public-identifier=${this.publicIdentifier}
                                       time-zone=${data.context.timeZone}
                                       @alfio-dialog-closed=${this.onPaymentConfirmed}></alfio-edit-payment-dialog>
        `;
    }

    private renderTab(name: string, label: string, icon: string, count: number | null): TemplateResult {
        return html`
            <sl-tab slot="nav" panel=${name}>
                <span class="tab-label">
                    <sl-icon name=${icon} aria-hidden="true"></sl-icon>
                    ${label}
                    ${when(count != null, () => html`<sl-badge variant="primary" pill>${count}</sl-badge>`)}
                </span>
            </sl-tab>
        `;
    }

    private renderNotices(data: ReservationData): TemplateResult {
        const reservation = data.descriptor.reservation;
        const transaction = data.paymentInfo?.transaction;
        return html`
            <div class="notices">
                ${when(this.fromCreation === 'true' && this.creationNoticeVisible, () => html`
                    <sl-alert open closable variant="success" @sl-after-hide=${() => { this.creationNoticeVisible = false; }}>
                        <sl-icon slot="icon" name="check2-circle"></sl-icon>
                        <strong>The reservation has been created.</strong><br />
                        Nobody has been notified yet: use the "Send e-mail" button to send the notifications.
                    </sl-alert>
                `)}
                ${when(reservation.status === 'PENDING', () => html`
                    <sl-alert open variant="primary">
                        <sl-icon slot="icon" name="info-circle"></sl-icon>
                        You can confirm this reservation, or leave it pending and send the link to the contact person for the payment.
                        Change the expiration date to give them more time.
                    </sl-alert>
                `)}
                ${when(reservation.status === 'STUCK', () => html`
                    <sl-alert open variant="danger">
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        This reservation is in an unknown state. This could happen when there are troubles after receiving a response
                        from the payment gateway. Please check the payment status on the payment gateway's dashboard, then confirm or cancel this reservation.
                    </sl-alert>
                `)}
                ${when(transaction?.potentialMatch, () => html`
                    <sl-alert open variant="primary" data-testid="matching-transaction">
                        <sl-icon slot="icon" name="arrow-left-right"></sl-icon>
                        <strong>A matching transaction has been found.</strong>
                        The payment provider has received a payment of ${formatAmount(transaction!.formattedAmount, transaction!.currency)}
                        which matches this reservation.
                        <sl-button size="small" variant="primary" outline ?disabled=${this.busy}
                                   @click=${() => this.reviewMatchingTransaction(data)}>
                            <sl-icon slot="prefix" name="arrow-left-right"></sl-icon>
                            Review match
                        </sl-button>
                    </sl-alert>
                `)}
            </div>
        `;
    }

    private renderToolbar(data: ReservationData): TemplateResult {
        const reservation = data.descriptor.reservation;
        const url = this.reservationUrl(data);
        const complete = reservation.status === 'COMPLETE';
        return html`
            <div class="filter-toolbar">
                <div class="filter-left">
                    <sl-input class="share-link" label="URL to share" readonly .value=${url}
                              help-text="The customer can see and manage the reservation at this address">
                        <sl-icon slot="prefix" name="link-45deg"></sl-icon>
                        <sl-copy-button hoist slot="suffix" value=${url} copy-label="Copy URL"></sl-copy-button>
                        <sl-icon-button slot="suffix" name="box-arrow-up-right" label="Open" href=${url} target="_blank" rel="noopener"></sl-icon-button>
                    </sl-input>
                </div>
                <div class="filter-right">
                    ${when(this.canConfirm(data), () => html`
                        <sl-button variant="success" ?disabled=${this.busy} @click=${() => this.confirm(data)}>
                            <sl-icon slot="prefix" name="check2"></sl-icon>
                            ${this.confirmLabel(data)}
                        </sl-button>
                    `)}
                    ${when(complete, () => html`
                        <sl-dropdown hoist placement="bottom-end">
                            <sl-button slot="trigger" caret ?disabled=${this.busy}>
                                <sl-icon slot="prefix" name="envelope"></sl-icon>
                                Send e-mail
                            </sl-button>
                            <sl-menu @sl-select=${(e: CustomEvent) => this.sendEmail(data, e.detail.item.value)}>
                                <sl-menu-item value="customer">
                                    <sl-icon slot="prefix" name="person"></sl-icon>
                                    Reservation to the contact person
                                </sl-menu-item>
                                ${when(this.purchaseContextType === 'event', () => html`
                                    <sl-menu-item value="attendees">
                                        <sl-icon slot="prefix" name="ticket-perforated"></sl-icon>
                                        Tickets to the attendees…
                                    </sl-menu-item>
                                `)}
                            </sl-menu>
                        </sl-dropdown>
                    `)}
                    ${when(this.canCancel(data), () => html`
                        <sl-dropdown hoist placement="bottom-end">
                            <sl-icon-button slot="trigger" name="three-dots-vertical" label="More actions" ?disabled=${this.busy}></sl-icon-button>
                            <sl-menu @sl-select=${(e: CustomEvent) => this.cancel(data, e.detail.item.value === 'credit')}>
                                ${when(isOfflinePayment(reservation.status), () => html`
                                    <sl-menu-item value="credit">
                                        <sl-icon slot="prefix" name="arrow-counterclockwise"></sl-icon>
                                        Issue credit note
                                    </sl-menu-item>
                                `)}
                                <sl-menu-item value="cancel" class="danger">
                                    <sl-icon slot="prefix" name="trash"></sl-icon>
                                    Cancel reservation
                                </sl-menu-item>
                            </sl-menu>
                        </sl-dropdown>
                    `)}
                </div>
            </div>
        `;
    }

    private renderDetails(data: ReservationData): TemplateResult {
        const descriptor = data.descriptor;
        const reservation = descriptor.reservation;
        const subscriptionDetails = this.subscriptionDetails(data);
        return html`
            <form class="details-grid" @submit=${(e: Event) => { e.preventDefault(); void this.save(data); }}>
                ${this.renderReservationCard(data)}
                ${this.renderContactCard(data)}
                ${when(this.displayPaymentInfo(data), () => html`<div class="wide">${this.renderPaymentCard(data)}</div>`)}
                ${when(subscriptionDetails, () => html`<div class="wide">${this.renderSubscriptionCard(subscriptionDetails!)}</div>`)}
                ${when(isNotPaid(reservation.status), () => html`<div class="wide">${this.renderAdvancedBilling(data)}</div>`)}
                <div class="wide">
                    ${sectionCard({ icon: 'receipt', title: 'Order summary' },
                        orderSummaryTable(descriptor.orderSummary, reservation.currencyCode ?? data.context.currency))}
                </div>
                ${when(this.purchaseContextType === 'event',
                    () => html`${repeat(descriptor.ticketsByCategory, (entry) => entry.key.id, (entry) => html`
                        <div class="wide">${this.renderAttendeesCard(data, entry.key, entry.value)}</div>
                    `)}`,
                    () => html`<div class="wide">${this.renderSubscriptionReservations(data)}</div>`)}
                <div class="wide save-bar">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        ${when(this.embedded,
                            () => html`<sl-button variant="default" size="large" @click=${this.close}>Close</sl-button>`,
                            () => html`<sl-button variant="default" size="large" ?disabled=${!this.dirty(data) || this.busy}
                                                  @click=${() => { this.drafts = initialDrafts(data); }}>Discard changes</sl-button>`)}
                        <div></div>
                        <sl-button type="submit" variant="warning" size="large" ?disabled=${!this.dirty(data) || this.busy}>
                            ${when(this.busy,
                                () => html`<sl-spinner slot="prefix"></sl-spinner>`,
                                () => html`<sl-icon name="check2" slot="prefix"></sl-icon>`)}
                            Save changes
                        </sl-button>
                    </div>
                </div>
            </form>
        `;
    }

    private renderReservationCard(data: ReservationData): TemplateResult {
        const reservation = data.descriptor.reservation;
        const expirationEditable = reservation.status !== 'COMPLETE' && reservation.status !== 'CANCELLED';
        return sectionCard({ icon: 'info-circle', title: 'Reservation' }, html`
            ${renderDetailList([
                { label: 'Payment method', value: this.paymentMethod(data) },
                reservation.hasInvoiceNumber && { label: 'Invoice number', value: reservation.invoiceNumber },
                !expirationEditable && {
                    label: 'Expiration',
                    value: html`<alfio-format-date date=${reservation.validity} time-zone=${data.context.timeZone}></alfio-format-date>`,
                },
            ])}
            ${when(expirationEditable, () => html`
                <sl-input type="datetime-local" name="expiration" label="Expiration" required
                          help-text="Time zone: ${data.context.timeZone}"
                          .value=${this.drafts.expiration}
                          @sl-input=${(e: Event) => this.updateDrafts({ expiration: (e.target as SlInput).value })}>
                    <sl-icon slot="prefix" name="calendar-event"></sl-icon>
                </sl-input>
            `)}
        `);
    }

    private renderPaymentCard(data: ReservationData): TemplateResult {
        const paymentInfo = data.paymentInfo;
        if (paymentInfo == null) {
            return sectionCard({ icon: 'cash-coin', title: 'Payment' }, emptyState('No payments have been found'));
        }
        const transaction = paymentInfo.transaction;
        const information = paymentInfo.paymentInformation;
        const currency = transaction?.currency ?? data.context.currency;
        const canRefund = this.isOwner && paymentInfo.supportRefund && data.descriptor.reservation.status !== 'STUCK';
        return sectionCard({ icon: 'cash-coin', title: 'Payment' }, html`
            ${when(transaction?.complete,
                () => renderDetailList([
                    { label: 'Payment ID', value: transaction!.transactionId ?? '', monospace: true },
                    { label: 'Date', value: html`<alfio-format-date date=${transaction!.timestamp} time-zone=${data.context.timeZone}></alfio-format-date>` },
                    information != null && { label: 'Paid amount', value: html`<strong>${formatAmount(information.paidAmount, currency)}</strong>` },
                    information != null && paymentInfo.supportRefund && { label: 'Refunded amount', value: formatAmount(information.refundedAmount ?? '0.00', currency) },
                ]),
                () => emptyState('The payment has not been completed yet'))}
            ${when(canRefund, () => html`
                <div class="refund-form">
                    <sl-input type="number" name="refundAmount" label="Refund an arbitrary amount" min="0" step="0.01"
                              .value=${this.refundAmount}
                              @sl-input=${(e: Event) => { this.refundAmount = (e.target as SlInput).value; }}>
                        <span slot="suffix">${currency}</span>
                    </sl-input>
                    <sl-button variant="warning" ?disabled=${this.busy || !(Number(this.refundAmount) > 0)}
                               @click=${() => this.refund(data, currency)}>
                        <sl-icon slot="prefix" name="arrow-counterclockwise"></sl-icon>
                        Refund
                    </sl-button>
                </div>
            `)}
        `);
    }

    private renderContactCard(data: ReservationData): TemplateResult {
        const reservation = data.descriptor.reservation;
        const draft = this.drafts.contact;
        const eInvoicing = data.descriptor.additionalInfo?.invoicingAdditionalInfo?.italianEInvoicing;
        return sectionCard({
            icon: 'person',
            title: 'Contact',
            className: 'contact',
            actions: this.editToggle(draft != null, () => this.toggleContact(data)),
        }, when(draft,
            () => this.renderContactForm(data, draft!),
            () => renderDetailList([
                { label: 'Customer', value: this.customerTypeLabel(data.descriptor) },
                isCompany(data.descriptor) && { label: 'Company', value: html`<strong>${data.descriptor.additionalInfo?.billingAddressCompany ?? ''}</strong>` },
                { label: 'Name', value: formatFullName(reservation) },
                { label: 'E-mail', value: reservation.email },
                { label: 'Language', value: reservation.userLanguage },
                !!reservation.vatNr && { label: 'Tax ID (VAT / GST)', value: reservation.vatNr },
                !!reservation.vatCountryCode && { label: 'Country', value: reservation.vatCountryCode },
                !!eInvoicing?.referenceType && { label: 'Codice fiscale', value: eInvoicing.fiscalCode },
                eInvoicing?.referenceType === 'ADDRESSEE_CODE' && { label: 'Codice destinatario', value: eInvoicing.addresseeCode },
                eInvoicing?.referenceType === 'PEC' && { label: 'PEC', value: eInvoicing.pec },
                eInvoicing?.referenceType === 'NONE' && { label: 'E-invoicing', value: 'Nessun codice destinatario o PEC' },
                !!reservation.billingAddress && { label: 'Billing address', value: html`<pre class="billing-address">${reservation.billingAddress}</pre>` },
            ])));
    }

    private renderContactForm(data: ReservationData, draft: ContactDraft): TemplateResult {
        const onChange = () => this.requestUpdate();
        const eInvoicing = data.descriptor.additionalInfo?.invoicingAdditionalInfo?.italianEInvoicing;
        return html`
            <div class="form-stack">
                <sl-radio-group name="customerType" label="Customer" value=${draft.customerType}
                                help-text="Invoices are issued to the company, if any"
                                @sl-change=${(e: Event) => { draft.customerType = (e.target as SlRadioGroup).value as CustomerType; onChange(); }}>
                    <sl-radio-button value="private"><sl-icon slot="prefix" name="person"></sl-icon>Private person</sl-radio-button>
                    <sl-radio-button value="company"><sl-icon slot="prefix" name="building"></sl-icon>Company</sl-radio-button>
                </sl-radio-group>
                ${when(draft.customerType === 'company', () => html`
                    ${formField(draft, 'companyName', { label: 'Company name', icon: 'building', required: true }, onChange)}
                    ${formField(draft, 'vatNr', { label: 'Tax ID (VAT / GST)' }, onChange)}
                `)}
                <div class="row">
                    ${formField(draft, 'firstName', { label: 'First name', required: true }, onChange)}
                    ${formField(draft, 'lastName', { label: 'Last name', required: true }, onChange)}
                </div>
                ${formField(draft, 'emailAddress', { label: 'E-mail', type: 'email', icon: 'envelope', required: true }, onChange)}
                ${selectField(draft, 'userLanguage', {
                    label: 'Language',
                    options: data.context.locales.map(locale => ({ value: locale, label: locale })),
                }, onChange)}
                ${this.renderAddressFields(draft, onChange)}
                ${when(eInvoicing?.referenceType, () => html`
                    ${formField(draft, 'fiscalCode', { label: 'Codice fiscale' }, onChange)}
                    ${selectField(draft, 'referenceType', { label: 'Italian e-invoicing', options: E_INVOICING_REFERENCE_TYPES }, onChange)}
                    ${when(draft.referenceType === 'ADDRESSEE_CODE', () => formField(draft, 'addresseeCode', { label: 'Codice destinatario' }, onChange))}
                    ${when(draft.referenceType === 'PEC', () => formField(draft, 'pec', { label: 'PEC', type: 'email' }, onChange))}
                `)}
            </div>
        `;
    }

    private renderAddressFields(draft: ContactDraft, onChange: () => void): TemplateResult {
        if (!draft.structuredAddress) {
            return html`
                ${formField(draft, 'billingAddress', { label: 'Billing address', type: 'textarea' }, onChange)}
                <sl-button variant="text" size="small" class="address-switch"
                           @click=${() => { draft.structuredAddress = true; onChange(); }}>
                    <sl-icon slot="prefix" name="pencil"></sl-icon>
                    Enter the address field by field
                </sl-button>
                ${this.renderCountryField(draft, onChange)}
            `;
        }
        return html`
            ${formField(draft, 'addressLine1', { label: 'Address' }, onChange)}
            ${formField(draft, 'addressLine2', { label: 'Address (second line)' }, onChange)}
            <div class="row" style="--alfio-row-cols: 3">
                ${formField(draft, 'zip', { label: 'ZIP code' }, onChange)}
                ${formField(draft, 'city', { label: 'City' }, onChange)}
                ${formField(draft, 'state', { label: 'State / province' }, onChange)}
            </div>
            ${this.renderCountryField(draft, onChange)}
        `;
    }

    private customerTypeLabel(descriptor: ReservationDescriptor): TemplateResult {
        if (isCompany(descriptor)) {
            return html`<sl-icon name="building" aria-hidden="true"></sl-icon> Company`;
        }
        return html`<sl-icon name="person" aria-hidden="true"></sl-icon> Private person`;
    }

    private renderCountryField(draft: ContactDraft, onChange: () => void): TemplateResult {
        const countries = Object.entries(this.countries);
        // sl-select ignores a value set before its options: render it once the countries have been loaded
        if (countries.length === 0) {
            return html`<sl-input label="Country" disabled .value=${draft.vatCountryCode}><sl-spinner slot="suffix"></sl-spinner></sl-input>`;
        }
        return selectField(draft, 'vatCountryCode', {
            label: 'Country',
            options: countries
                .sort(([, a], [, b]) => a.localeCompare(b))
                .map(([code, name]) => ({ value: code, label: `${code} - ${name}` })),
        }, onChange);
    }

    private renderSubscriptionCard(details: SubscriptionWithUsageDetails): TemplateResult {
        const subscription = details.subscription;
        const draft = this.drafts.subscription;
        const timeZone = this.lastData!.context.timeZone;
        const onChange = () => this.requestUpdate();
        const maxAllowed = details.usageDetails.total;
        return sectionCard({
            icon: 'card-checklist',
            title: 'Subscription',
            actions: this.editToggle(draft != null, () => this.toggleSubscription()),
        }, when(draft,
            () => html`
                <div class="form-stack">
                    <div class="row">
                        ${formField(draft!, 'firstName', { label: "Owner's first name", required: true }, onChange)}
                        ${formField(draft!, 'lastName', { label: "Owner's last name", required: true }, onChange)}
                    </div>
                    ${formField(draft!, 'email', { label: 'E-mail', type: 'email', icon: 'envelope', required: true }, onChange)}
                    ${when(maxAllowed != null && maxAllowed > 0, () => formField(draft!, 'maxAllowed', { label: 'Max allowed usages', type: 'number', icon: 'hash' }, onChange))}
                    <div class="row">
                        ${when(subscription.validityFrom, () => formField(draft!, 'validityFrom', { label: 'Valid from', type: 'datetime-local', required: true, helpText: `Time zone: ${timeZone}` }, onChange))}
                        ${when(subscription.validityTo, () => formField(draft!, 'validityTo', { label: 'Valid to', type: 'datetime-local', required: true, helpText: `Time zone: ${timeZone}` }, onChange))}
                    </div>
                </div>
            `,
            () => renderDetailList([
                { label: 'PIN', value: html`<span class="monospace">${subscription.pin}</span> <sl-copy-button hoist value=${subscription.pin} copy-label="Copy PIN"></sl-copy-button>` },
                { label: 'Owner', value: formatFullName(subscription) },
                { label: 'E-mail', value: subscription.email },
                { label: 'Usages', value: this.usages(details) },
                subscription.validityFrom != null && {
                    label: 'Validity',
                    value: html`<alfio-format-date date=${subscription.validityFrom} end=${subscription.validityTo ?? ''} time-zone=${timeZone}></alfio-format-date>`,
                },
            ])));
    }

    /**
     * Dangerous options are hidden behind a low-emphasis link: hiding them again discards their changes
     */
    private renderAdvancedBilling(data: ReservationData): TemplateResult {
        if (!this.drafts.showAdvancedBilling) {
            return html`
                <sl-button variant="text" size="large" class="advanced-billing-toggle"
                           @click=${() => this.updateDrafts({ showAdvancedBilling: true })}>
                    <sl-icon slot="prefix" name="sliders"></sl-icon>
                    Show advanced invoice options
                </sl-button>
            `;
        }
        const vatApplied = this.drafts.vatApplied;
        const hide = html`
            <sl-button size="small" variant="default" outline
                       @click=${() => this.updateDrafts({ showAdvancedBilling: false, vatApplied: initialDrafts(data).vatApplied })}>
                <sl-icon slot="prefix" name="eye-slash"></sl-icon>
                Hide
            </sl-button>
        `;
        return sectionCard({ icon: 'exclamation-triangle', title: 'Advanced invoice data', className: 'danger-zone', actions: hide }, html`
            <p class="text-danger">Change these options with care: they might affect the total price of the reservation, or lead to inconsistencies.</p>
            ${when(vatApplied != null,
                () => html`
                    <sl-switch .checked=${vatApplied!}
                               @sl-change=${(e: Event) => this.updateDrafts({ vatApplied: (e.target as SlSwitch).checked })}>
                        Apply VAT/GST
                    </sl-switch>`,
                () => html`<span class="text-muted">VAT/GST not applicable</span>`)}
        `);
    }

    private renderAttendeesCard(data: ReservationData, category: { id: number, name: string }, tickets: Ticket[]): TemplateResult {
        const drafts = this.drafts.attendees.get(category.id);
        const checkIns = checkInLog(data.audit);
        return sectionCard({
            icon: 'people',
            title: `Attendees for ${category.name}`,
            badge: html`<sl-badge variant="primary" pill>${tickets.length}</sl-badge>`,
            actions: this.editToggle(drafts != null, () => this.toggleAttendees(category.id, tickets)),
        }, html`
            <div class="table-responsive">
                <table class="table table-striped">
                    <thead>
                        <tr>
                            <th>#</th>
                            <th>First name</th>
                            <th>Last name</th>
                            <th>E-mail</th>
                            <th class="hide-small">Check-in</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(tickets, (ticket) => ticket.id, (ticket, index) => {
                            const draft = drafts?.get(ticket.id);
                            const log = checkIns.get(String(ticket.id));
                            return html`
                                <tr data-ticket-id=${ticket.id}>
                                    <td>${index + 1}</td>
                                    ${this.attendeeCell(draft, 'firstName', ticket.firstName, 'First name')}
                                    ${this.attendeeCell(draft, 'lastName', ticket.lastName, 'Last name')}
                                    ${this.attendeeCell(draft, 'emailAddress', ticket.email, 'E-mail')}
                                    <td class="hide-small">
                                        ${when(ticket.status === 'CHECKED_IN', () => html`<sl-badge variant="success">Checked in</sl-badge>`)}
                                    </td>
                                    <td>
                                        <div class="actions-cell">
                                            ${when(data.ticketsWithAdditionalData.has(ticket.id), () => html`
                                                <sl-button size="small" variant="default" outline @click=${() => this.showFullData(ticket)}>
                                                    <sl-icon slot="prefix" name="card-list"></sl-icon>
                                                    Full data
                                                </sl-button>
                                            `)}
                                            ${when(log, () => html`
                                                <sl-button size="small" variant="default" outline @click=${() => this.showCheckInLog(ticket, log!)}>
                                                    <sl-icon slot="prefix" name="clock-history"></sl-icon>
                                                    Check-in log
                                                </sl-button>
                                            `)}
                                            ${when(ticket.status !== 'CHECKED_IN', () => html`
                                                <sl-button size="small" variant="danger" outline ?disabled=${this.busy}
                                                           @click=${() => this.removeTicket(data, ticket)}>
                                                    <sl-icon slot="prefix" name="trash"></sl-icon>
                                                    Remove
                                                </sl-button>
                                            `)}
                                        </div>
                                    </td>
                                </tr>
                            `;
                        })}
                    </tbody>
                </table>
            </div>
        `);
    }

    private attendeeCell(draft: AttendeeDraft | undefined, key: keyof AttendeeDraft, value: string | null, label: string): TemplateResult {
        if (draft == null) {
            return html`<td>${value ?? ''}</td>`;
        }
        return html`
            <td class="attendee-cell">
                <sl-input class="label-hidden" size="small" name=${key} label=${label} required .value=${draft[key]}
                          @sl-input=${(e: Event) => { draft[key] = (e.target as SlInput).value; this.requestUpdate(); }}></sl-input>
            </td>
        `;
    }

    private renderSubscriptionReservations(data: ReservationData): TemplateResult {
        return sectionCard({ icon: 'calendar-check', title: 'Reservations completed using this subscription' }, html`
            <alfio-reservations-list data-event-name=${this.publicIdentifier}
                                     data-purchase-context-type="subscription"
                                     data-completed-only
                                     .reservations=${data.descriptor.subscriptionDetails?.reservations ?? []}></alfio-reservations-list>
        `);
    }

    private renderBillingDocuments(data: ReservationData): TemplateResult {
        const service = this.service();
        const valid = data.billingDocuments.filter(document => document.status === 'VALID');
        const notValid = data.billingDocuments.filter(document => document.status === 'NOT_VALID');
        return html`
            ${when(data.descriptor.reservation.status !== 'CANCELLED', () => html`
                <div class="tab-toolbar">
                    <sl-button variant="warning" ?disabled=${this.busy}
                               @click=${() => this.perform(() => service.regenerateBillingDocument(), 'Billing document regenerated', 'Failed to regenerate the billing document')}>
                        <sl-icon slot="prefix" name="arrow-repeat"></sl-icon>
                        Regenerate
                    </sl-button>
                </div>
            `)}
            ${sectionCard({ icon: 'receipt', title: 'Active', badge: html`<sl-badge variant="primary" pill>${valid.length}</sl-badge>` },
                this.billingDocumentsTable(data, valid, (document) => html`
                    <sl-button size="small" variant="danger" outline ?disabled=${this.busy}
                               @click=${() => this.invalidateDocument(document)}>
                        <sl-icon slot="prefix" name="x-octagon"></sl-icon>
                        Invalidate
                    </sl-button>
                `))}
            ${sectionCard({ icon: 'archive', title: 'Not active', badge: html`<sl-badge variant="neutral" pill>${notValid.length}</sl-badge>` },
                this.billingDocumentsTable(data, notValid, (document) => html`
                    <sl-button size="small" variant="warning" outline ?disabled=${this.busy}
                               @click=${() => this.perform(() => service.restoreBillingDocument(document.id), 'Billing document restored', 'Failed to restore the billing document')}>
                        <sl-icon slot="prefix" name="arrow-counterclockwise"></sl-icon>
                        Restore
                    </sl-button>
                `))}
        `;
    }

    private billingDocumentsTable(data: ReservationData,
                                  documents: BillingDocument[],
                                  action: (document: BillingDocument) => TemplateResult): TemplateResult {
        if (documents.length === 0) {
            return emptyState('No documents found');
        }
        const service = this.service();
        return html`
            <div class="table-responsive">
                <table class="table table-striped">
                    <thead>
                        <tr>
                            <th>Type</th>
                            <th>Number</th>
                            <th>Date</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(documents, (document) => document.id, (document) => html`
                            <tr>
                                <td>${BILLING_DOCUMENT_TYPES[document.type] ?? document.type}</td>
                                <td class="monospace">${document.number}</td>
                                <td class="timestamp">
                                    <alfio-format-date date=${document.generationTimestamp} time-zone=${data.context.timeZone}></alfio-format-date>
                                </td>
                                <td>
                                    <div class="actions-cell">
                                        <sl-button size="small" variant="default" outline href=${service.billingDocumentUrl(document.id)}
                                                   target="_blank" rel="noopener">
                                            <sl-icon slot="prefix" name="download"></sl-icon>
                                            Download
                                        </sl-button>
                                        ${action(document)}
                                    </div>
                                </td>
                            </tr>
                        `)}
                    </tbody>
                </table>
            </div>
        `;
    }

    private renderHistory(data: ReservationData): TemplateResult {
        return sectionCard({ icon: 'clock-history', title: 'What happened to this reservation' }, html`
            <alfio-activity-log .entries=${data.audit}
                                .attendees=${attendeeNames(data.descriptor)}
                                time-zone=${data.context.timeZone}
                                currency=${data.descriptor.reservation.currencyCode ?? data.context.currency}></alfio-activity-log>
        `);
    }

    private editToggle(editing: boolean, toggle: () => void): TemplateResult {
        if (editing) {
            return html`
                <sl-button size="small" variant="default" outline @click=${toggle}>
                    <sl-icon slot="prefix" name="x-circle"></sl-icon>
                    Cancel
                </sl-button>
            `;
        }
        return html`
            <sl-button size="small" variant="default" outline ?disabled=${this.busy} @click=${toggle}>
                <sl-icon slot="prefix" name="pencil"></sl-icon>
                Edit
            </sl-button>
        `;
    }

    // ---------- state ----------

    private service(): ReservationService {
        return new ReservationService(this.purchaseContextType, this.publicIdentifier, this.reservationId);
    }

    private updateDrafts(update: Partial<Drafts>): void {
        this.drafts = { ...this.drafts, ...update };
    }

    private dirty(data: ReservationData): boolean {
        const initial = initialDrafts(data);
        const drafts = this.drafts;
        return drafts.expiration !== initial.expiration
            || drafts.contact != null
            || drafts.attendees.size > 0
            || drafts.subscription != null
            || drafts.vatApplied !== initial.vatApplied;
    }

    private async toggleContact(data: ReservationData): Promise<void> {
        if (this.drafts.contact != null) {
            this.updateDrafts({ contact: null });
            return;
        }
        this.updateDrafts({ contact: contactDraft(data.descriptor) });
        if (Object.keys(this.countries).length === 0) {
            this.countries = await optional(UtilService.countriesForVat(), {});
        }
    }

    private toggleAttendees(categoryId: number, tickets: Ticket[]): void {
        const attendees = new Map(this.drafts.attendees);
        if (attendees.has(categoryId)) {
            attendees.delete(categoryId);
        } else {
            attendees.set(categoryId, new Map(tickets.map(ticket => [ticket.id, {
                firstName: ticket.firstName ?? '',
                lastName: ticket.lastName ?? '',
                emailAddress: ticket.email ?? '',
            }])));
        }
        this.updateDrafts({ attendees });
    }

    private toggleSubscription(): void {
        if (this.drafts.subscription != null) {
            this.updateDrafts({ subscription: null });
            return;
        }
        const details = this.subscriptionDetails(this.lastData!)!;
        const subscription = details.subscription;
        const timeZone = this.lastData!.context.timeZone;
        this.updateDrafts({
            subscription: {
                firstName: subscription.firstName ?? '',
                lastName: subscription.lastName ?? '',
                email: subscription.email ?? '',
                maxAllowed: String(details.usageDetails.total ?? ''),
                validityFrom: toDateTimeLocal(subscription.validityFrom, timeZone),
                validityTo: toDateTimeLocal(subscription.validityTo, timeZone),
            },
        });
    }

    // ---------- actions ----------

    private async save(data: ReservationData): Promise<void> {
        const form = this.renderRoot.querySelector('form.details-grid') as HTMLFormElement;
        if (!form.reportValidity()) {
            return;
        }
        await this.perform(() => this.service().update(this.toModification(data)), 'Reservation updated successfully', 'Failed to update the reservation');
    }

    private toModification(data: ReservationData): ReservationModification {
        const descriptor = data.descriptor;
        const reservation = descriptor.reservation;
        const contact = this.drafts.contact ?? contactDraft(descriptor);
        const subscription = this.drafts.subscription;
        let vatApplied: 'Y' | 'N' | null = null;
        if (this.drafts.vatApplied === true) {
            vatApplied = 'Y';
        } else if (this.drafts.vatApplied === false) {
            vatApplied = 'N';
        }
        return {
            expiration: toDateTimeModification(this.drafts.expiration || toDateTimeLocal(reservation.validity, data.context.timeZone)),
            language: reservation.userLanguage ?? '',
            updateContactData: this.drafts.contact != null,
            customerData: {
                firstName: contact.firstName,
                lastName: contact.lastName,
                emailAddress: contact.emailAddress,
                billingAddress: contact.billingAddress,
                userLanguage: contact.userLanguage,
                vatNr: vatNumber(contact),
                vatCountryCode: contact.vatCountryCode,
                invoicingAdditionalInfo: this.invoicingAdditionalInfo(descriptor, contact),
                billingDetails: billingDetails(this.drafts.contact),
            },
            updateAdvancedBillingOptions: this.drafts.vatApplied !== initialDrafts(data).vatApplied,
            advancedBillingOptions: { vatApplied },
            ticketsInfo: descriptor.ticketsByCategory.map(entry => {
                const drafts = this.drafts.attendees.get(entry.key.id);
                return {
                    category: { existingCategoryId: entry.key.id, name: entry.key.name },
                    updateAttendees: drafts != null,
                    attendees: entry.value.map(ticket => ({
                        ticketId: ticket.id,
                        ...(drafts?.get(ticket.id) ?? { firstName: ticket.firstName ?? '', lastName: ticket.lastName ?? '', emailAddress: ticket.email ?? '' }),
                    })),
                };
            }),
            subscriptionDetails: subscription && {
                firstName: subscription.firstName,
                lastName: subscription.lastName,
                email: subscription.email,
                maxAllowed: toNumber(subscription.maxAllowed),
                validityFrom: optionalDateTime(subscription.validityFrom),
                validityTo: optionalDateTime(subscription.validityTo),
            },
        };
    }

    private invoicingAdditionalInfo(descriptor: ReservationDescriptor, contact: ContactDraft) {
        const current = descriptor.additionalInfo?.invoicingAdditionalInfo ?? null;
        if (current?.italianEInvoicing == null) {
            return current;
        }
        return {
            italianEInvoicing: {
                ...current.italianEInvoicing,
                fiscalCode: contact.fiscalCode,
                referenceType: contact.referenceType as 'ADDRESSEE_CODE' | 'PEC' | 'NONE',
                addresseeCode: contact.addresseeCode,
                pec: contact.pec,
            },
        };
    }

    private async confirm(data: ReservationData): Promise<void> {
        if (isOfflinePayment(data.descriptor.reservation.status)) {
            // the dialog fires alfio-dialog-closed, see onPaymentConfirmed
            await this.confirmPaymentDialog.openForConfirmation(this.reservationId);
            return;
        }
        await this.perform(() => this.service().confirm(), 'Reservation confirmed successfully', 'Failed to confirm the reservation', true);
    }

    private async reviewMatchingTransaction(data: ReservationData): Promise<void> {
        const transaction = data.paymentInfo!.transaction!;
        const choice = await this.matchingDialog.open(this.reservationId, transaction);
        if (choice === 'confirm') {
            await this.confirmPaymentDialog.openForConfirmation(this.reservationId);
        } else if (choice === 'discard') {
            await this.perform(
                () => PendingPaymentsService.discardMatchingTransaction(this.publicIdentifier, this.reservationId, transaction.id),
                'Transaction flagged as not valid',
                'Failed to flag the transaction as not valid');
        }
    }

    private async sendEmail(data: ReservationData, recipient: 'customer' | 'attendees'): Promise<void> {
        const service = this.service();
        if (recipient === 'customer') {
            await this.perform(() => service.notifyCustomer(), 'E-mail sent to the contact person', 'Failed to send the e-mail', false, false);
            return;
        }
        const ticketIds = await this.notifyAttendeesDialog.open(data.descriptor.ticketsByCategory.map(entry => ({
            name: entry.key.name,
            tickets: entry.value,
        })));
        if (ticketIds != null && ticketIds.length > 0) {
            await this.perform(() => service.notifyAttendees(ticketIds), 'Tickets sent to the attendees', 'Failed to send the tickets', false, false);
        }
    }

    private async cancel(data: ReservationData, credit: boolean): Promise<void> {
        const request = cancelReservationRequest(data.descriptor.reservation, data.paymentInfo, credit, data.context.timeZone);
        const choice = await this.cancellationDialog.open(request);
        if (choice == null) {
            return;
        }
        let message = 'Reservation cancelled successfully';
        if (credit) {
            message = 'Credit note issued successfully';
        } else if (data.descriptor.reservation.status === 'CREDIT_NOTE_ISSUED') {
            message += '. A credit note has been generated, see the billing documents.';
        }
        await this.perform(
            () => this.service().cancel({ credit, ...cancellationChoice(choice, data.paymentInfo) }),
            message,
            'Failed to cancel the reservation');
    }

    private async removeTicket(data: ReservationData, ticket: Ticket): Promise<void> {
        const choice = await this.cancellationDialog.open(removeTicketRequest(data.descriptor.reservation, data.paymentInfo, ticket));
        if (choice == null) {
            return;
        }
        let creditNoteGenerated = false;
        await this.perform(async () => {
            creditNoteGenerated = await this.service().removeTicket(ticket.id, ticketRemovalChoice(choice, data.paymentInfo));
        }, 'Ticket removed successfully', 'Failed to remove the ticket');
        if (creditNoteGenerated) {
            dispatchFeedback({ type: 'neutral', message: 'A credit note has been generated, see the billing documents.' }, this);
        }
    }

    private async refund(data: ReservationData, currency: string): Promise<void> {
        const amount = formatAmount(this.refundAmount, currency);
        if (!await ConfirmationDialogService.requestConfirm('Refund', `Are you sure you want to refund ${amount}?`, 'warning')) {
            return;
        }
        let message = 'Refund successful';
        if (data.descriptor.reservation.invoiceRequested) {
            message += '. A credit note has been generated, see the billing documents.';
        }
        await this.perform(() => this.service().refund(this.refundAmount), message, 'Failed to refund the payment');
        this.refundAmount = '';
    }

    private async invalidateDocument(document: BillingDocument): Promise<void> {
        const label = `${BILLING_DOCUMENT_TYPES[document.type] ?? document.type} ${document.number}`;
        if (!await ConfirmationDialogService.requestConfirm('Invalidate document', `Invalidate ${label}? You can restore it later.`, 'danger')) {
            return;
        }
        await this.perform(() => this.service().invalidateBillingDocument(document.id), 'Billing document invalidated', 'Failed to invalidate the billing document');
    }

    private showFullData(ticket: Ticket): void {
        const locale = this.lastData?.context.locales[0] ?? 'en';
        void this.infoDialog.open({
            icon: 'card-list',
            title: `Data of ${formatFullName(ticket)}`,
            description: 'Information collected from the attendee.',
            errorMessage: 'Failed to load the attendee data',
            body: async () => {
                const fullData = await this.service().fullTicketData(ticket.uuid);
                const field = (item: AdditionalFieldValue) => ({
                    label: item.description[locale]?.label ?? item.name,
                    value: item.description[locale]?.restrictedValuesDescription?.[item.value ?? ''] ?? item.value,
                });
                return renderDetailList([
                    ...fullData.ticketFieldConfigurationBeforeStandard.map(field),
                    { label: 'First name', value: fullData.firstName },
                    { label: 'Last name', value: fullData.lastName },
                    { label: 'E-mail', value: fullData.email },
                    ...fullData.ticketFieldConfigurationAfterStandard.map(field),
                ]);
            },
        });
    }

    private showCheckInLog(ticket: Ticket, entries: AuditEntry[]): void {
        void this.infoDialog.open({
            icon: 'clock-history',
            title: `Check-in log for ${formatFullName(ticket)}`,
            description: 'Check-in operations performed on this ticket.',
            body: () => html`<alfio-activity-log compact .entries=${entries} .attendees=${attendeeNames(this.lastData!.descriptor)}
                                                  time-zone=${this.lastData!.context.timeZone}></alfio-activity-log>`,
        });
    }

    /**
     * Runs an operation, notifies the outcome and reloads the reservation
     * @param confirmed whether the operation confirms the reservation
     * @param changed whether the operation modifies the reservation
     */
    private async perform(action: () => Promise<unknown>,
                          successMessage: string,
                          errorMessage: string,
                          confirmed = false,
                          changed = true): Promise<void> {
        this.busy = true;
        try {
            await action();
            dispatchFeedback({ type: 'success', message: successMessage }, this);
            if (changed) {
                this.notifyChange(confirmed);
            }
        } catch (e) {
            dispatchFeedback({ type: 'danger', message: errorDescription(e, errorMessage) }, this);
        } finally {
            this.busy = false;
            void this.loadDataTask.run();
        }
    }

    private onPaymentConfirmed = (event: AlfioDialogClosed): void => {
        if (event.detail.success) {
            this.notifyChange(true);
            void this.loadDataTask.run();
        }
    };

    private notifyChange(confirmed: boolean): void {
        this.dispatchEvent(new CustomEvent<ReservationChangedDetail>('alfio-reservation-changed', {
            detail: { reservationId: this.reservationId, confirmed },
            bubbles: true,
            composed: true,
        }));
    }

    private close = (): void => {
        this.dispatchEvent(new CustomEvent('alfio-reservation-close', { bubbles: true, composed: true }));
    };

    // ---------- derived values ----------

    private canConfirm(data: ReservationData): boolean {
        const status = data.descriptor.reservation.status;
        const pending = isNotPaid(status) || status === 'STUCK';
        return pending && data.paymentInfo?.transaction?.potentialMatch !== true;
    }

    private confirmLabel(data: ReservationData): string {
        if (isOfflinePayment(data.descriptor.reservation.status)) {
            return 'Confirm payment received';
        }
        return 'Mark as completed';
    }

    private canCancel(data: ReservationData): boolean {
        const reservation = data.descriptor.reservation;
        const checkedIn = data.descriptor.ticketsByCategory.some(entry => entry.value.some(ticket => ticket.status === 'CHECKED_IN'));
        return this.isOwner && reservation.status !== 'CANCELLED' && reservation.status !== 'CREDIT_NOTE_ISSUED' && !checkedIn;
    }

    private displayPaymentInfo(data: ReservationData): boolean {
        return data.context.visible
            && data.paymentInfo?.transaction?.potentialMatch !== true
            && !isNotPaid(data.descriptor.reservation.status);
    }

    private subscriptionDetails(data: ReservationData): SubscriptionWithUsageDetails | null {
        if (this.purchaseContextType === 'subscription' && data.descriptor.reservation.status === 'COMPLETE') {
            return data.descriptor.subscriptionDetails;
        }
        return null;
    }

    private usages(details: SubscriptionWithUsageDetails): string {
        const usage = details.usageDetails;
        if (usage.total != null && usage.total > 0) {
            return `${usage.used.toLocaleString()} of ${usage.total.toLocaleString()}`;
        }
        return usage.used.toLocaleString();
    }

    private paymentMethod(data: ReservationData): TemplateResult {
        const method = data.paymentInfo?.paymentMethod ?? data.descriptor.reservation.paymentMethod ?? '';
        return html`
            ${when(data.customPaymentMethod, () => html`${data.customPaymentMethod} `)}
            <alfio-payment-method method=${method}></alfio-payment-method>
        `;
    }

    private reservationUrl(data: ReservationData): string {
        const reservation = data.descriptor.reservation;
        let baseUrl = window.BASE_URL;
        if (!baseUrl) {
            baseUrl = window.location.href.substring(0, window.location.href.indexOf('/admin'));
        }
        const params = new URLSearchParams({ lang: reservation.userLanguage ?? '' });
        return `${baseUrl.replace(/\/$/, '')}/${this.purchaseContextType}/${this.publicIdentifier}/reservation/${reservation.id}?${params}`;
    }

    // ---------- loading ----------

    private async loadPurchaseContext(type: PurchaseContextType, publicIdentifier: string, organizationId?: number): Promise<PurchaseContextInfo> {
        const { eventWithOrganization, subscriptionDescriptor } = await PurchaseContextService.load(publicIdentifier, type, organizationId ?? 0);
        if (eventWithOrganization != null) {
            const event = eventWithOrganization.event;
            return {
                title: localizedTitle(event),
                timeZone: event.timeZone,
                organizationId: event.organizationId,
                currency: event.currency,
                locales: event.contentLanguages.map(language => language.locale),
                visible: event.visibleForCurrentUser,
            };
        }
        const subscription = subscriptionDescriptor!;
        return {
            title: Object.values(subscription.title)[0] ?? publicIdentifier,
            timeZone: subscription.timeZone,
            organizationId: subscription.organizationId,
            currency: subscription.currency,
            locales: subscription.contentLanguages.map(language => language.locale),
            visible: true,
        };
    }

    private async loadCustomPaymentMethod(organizationId: number, paymentInfo: PaymentInfo | null): Promise<string | null> {
        const selected = paymentInfo?.transaction?.metadata?.selectedPaymentMethod;
        if (selected == null) {
            return null;
        }
        const methods = await optional(new CustomPaymentMethodsService().getPaymentMethodsForOrganization(organizationId, true), []);
        const method = methods.find(paymentMethod => paymentMethod.paymentMethodId === selected);
        if (method == null) {
            return selected;
        }
        return (method.localizations['en'] ?? Object.values(method.localizations)[0])?.paymentName ?? selected;
    }
}

/**
 * Ticket ID -> name of the attendee
 */
function attendeeNames(descriptor: ReservationDescriptor): Map<string, string> {
    return new Map(descriptor.ticketsByCategory.flatMap(entry => entry.value)
        .map(ticket => [String(ticket.id), formatFullName(ticket) || ticket.email || 'an attendee']));
}

function emptyDrafts(): Drafts {
    return { expiration: '', contact: null, attendees: new Map(), subscription: null, vatApplied: null, showAdvancedBilling: false };
}

function initialDrafts(data: ReservationData): Drafts {
    const reservation = data.descriptor.reservation;
    let vatApplied: boolean | null = null;
    if (VAT_APPLIED_STATUSES.includes(reservation.vatStatus ?? '')) {
        vatApplied = true;
    } else if (VAT_EXEMPT_STATUSES.includes(reservation.vatStatus ?? '')) {
        vatApplied = false;
    }
    return {
        ...emptyDrafts(),
        expiration: toDateTimeLocal(reservation.validity, data.context.timeZone),
        vatApplied,
    };
}

function isCompany(descriptor: ReservationDescriptor): boolean {
    const additionalInfo = descriptor.additionalInfo;
    return additionalInfo?.addCompanyBillingDetails === true || !!additionalInfo?.billingAddressCompany;
}

function customerType(descriptor: ReservationDescriptor): CustomerType {
    if (isCompany(descriptor)) {
        return 'company';
    }
    return 'private';
}

/**
 * The address has been entered field by field (checkout), or there is no address yet
 */
function hasStructuredAddress(descriptor: ReservationDescriptor): boolean {
    const additionalInfo = descriptor.additionalInfo;
    const structured = [additionalInfo?.billingAddressLine1, additionalInfo?.billingAddressZip, additionalInfo?.billingAddressCity]
        .some(value => !!value);
    return structured || !descriptor.reservation.billingAddress;
}

/**
 * Private persons have no VAT number
 */
function vatNumber(contact: ContactDraft): string {
    if (contact.customerType === 'company') {
        return contact.vatNr;
    }
    return '';
}

/**
 * Structured billing details, sent only when the contact has been edited
 */
function billingDetails(contact: ContactDraft | null): CustomerBillingDetails | null {
    if (contact == null) {
        return null;
    }
    let address = { addressLine1: '', addressLine2: '', zip: '', city: '', state: '' };
    if (contact.structuredAddress) {
        address = { addressLine1: contact.addressLine1, addressLine2: contact.addressLine2, zip: contact.zip, city: contact.city, state: contact.state };
    }
    return {
        company: contact.customerType === 'company',
        companyName: contact.companyName,
        ...address,
    };
}

function contactDraft(descriptor: ReservationDescriptor): ContactDraft {
    const reservation = descriptor.reservation;
    const additionalInfo = descriptor.additionalInfo;
    const eInvoicing = additionalInfo?.invoicingAdditionalInfo?.italianEInvoicing;
    return {
        firstName: reservation.firstName ?? '',
        lastName: reservation.lastName ?? '',
        emailAddress: reservation.email ?? '',
        userLanguage: reservation.userLanguage ?? '',
        vatNr: reservation.vatNr ?? '',
        vatCountryCode: reservation.vatCountryCode ?? '',
        customerType: customerType(descriptor),
        companyName: additionalInfo?.billingAddressCompany ?? '',
        structuredAddress: hasStructuredAddress(descriptor),
        addressLine1: additionalInfo?.billingAddressLine1 ?? '',
        addressLine2: additionalInfo?.billingAddressLine2 ?? '',
        zip: additionalInfo?.billingAddressZip ?? '',
        city: additionalInfo?.billingAddressCity ?? '',
        state: additionalInfo?.billingAddressState ?? '',
        billingAddress: reservation.billingAddress ?? '',
        fiscalCode: eInvoicing?.fiscalCode ?? '',
        referenceType: eInvoicing?.referenceType ?? '',
        addresseeCode: eInvoicing?.addresseeCode ?? '',
        pec: eInvoicing?.pec ?? '',
    };
}

function emptyData(context: PurchaseContextInfo, descriptor: ReservationDescriptor): ReservationData {
    return {
        context,
        descriptor,
        paymentInfo: null,
        customPaymentMethod: null,
        ticketsWithAdditionalData: new Set(),
        audit: [],
        billingDocuments: [],
        emails: [],
    };
}

function optional<T>(promise: Promise<T>, fallback: T): Promise<T> {
    return promise.catch(() => fallback);
}

function ownerOnly<T>(isOwner: boolean, load: () => Promise<T[]>): Promise<T[]> {
    if (!isOwner) {
        return Promise.resolve([]);
    }
    return optional(load(), []);
}

function toNumber(value: string): number | null {
    if (value.trim() === '') {
        return null;
    }
    return Number(value);
}

function optionalDateTime(value: string) {
    if (value === '') {
        return null;
    }
    return toDateTimeModification(value);
}

function errorDescription(error: unknown, fallback: string): string {
    if (error instanceof Error && error.message && !error.message.startsWith('<')) {
        return `${fallback}: ${error.message}`;
    }
    return fallback;
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-reservation-detail': ReservationDetailPage;
    }
    interface GlobalEventHandlersEventMap {
        'alfio-reservation-changed': CustomEvent<ReservationChangedDetail>;
        'alfio-reservation-close': CustomEvent<void>;
    }
}
