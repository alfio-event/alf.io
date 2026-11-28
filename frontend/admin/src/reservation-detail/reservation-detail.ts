import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {TanStackFormController} from '@tanstack/lit-form';
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
import {AdditionalFieldService} from '../service/additional-field.ts';
import {AdditionalField} from '../model/additional-field.ts';
import {fieldsForCategory} from '../components/attendee-fields.ts';
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
import {
    emailAddress,
    focusFirstError,
    FormMountController,
    requiredValue,
    submitValidated,
    TextField,
    validatedInput,
    ValidatedInputOptions,
    validateWith,
} from '../components/form-validation.ts';
import {CancellationDialog} from '../components/cancellation-dialog.ts';
import {InfoDialog} from '../components/info-dialog.ts';
import {EditPaymentDialog} from '../event/payments-list/edit-payment-dialog.ts';
import {MatchingTransactionDialog} from '../event/pending-payments/matching-transaction-dialog.ts';
import {NotifyAttendeesDialog} from './notify-attendees-dialog.ts';
import {AttendeeDataDialog} from './attendee-data-dialog.ts';
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
import './attendee-data-dialog.ts';
import './activity-log.ts';

import { adminConfig } from '../admin-config';
import { formatNumber, t, tHtml } from '../service/i18n.ts';
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
    // additional fields of the event, if the user can edit their values (owners only)
    attendeeFields: readonly AdditionalField[];
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

/**
 * The values being edited, managed by TanStackFormController. A section is edited when its draft is not null.
 */
interface Drafts {
    expiration: string;
    contact: ContactDraft | null;
    // category ID -> ticket ID -> draft, for the categories being edited
    attendees: Record<string, Record<string, AttendeeDraft>>;
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
    RECEIPT: t('admin.reservation.billing.receipt'),
    INVOICE: t('admin.reservation.billing.invoice'),
    CREDIT_NOTE: t('admin.reservation.billing.credit-note'),
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
    // each validator checks first whether its section is being edited: a field keeps its validators after its
    // control has been hidden
    readonly #form = new TanStackFormController(this, {
        defaultValues: emptyDrafts(),
        onSubmit: async ({value}) => {
            await this.save(value);
        },
    });

    private get drafts(): Drafts {
        return this.#form.api.state.values;
    }

    constructor() {
        super();
        new FormMountController(this, this.#form.api);
    }
    @state() private countries: Record<string, string> = {};
    @state() private creationNoticeVisible = true;
    @state() private refundAmount = '';
    @state() private busy = false;
    // tickets invalidated by the last save: their owners must receive the new ticket
    @state() private ticketsToResend: number[] = [];

    @query('alfio-cancellation-dialog') private cancellationDialog!: CancellationDialog;
    @query('alfio-info-dialog') private infoDialog!: InfoDialog;
    @query('alfio-edit-payment-dialog') private confirmPaymentDialog!: EditPaymentDialog;
    @query('alfio-matching-transaction-dialog') private matchingDialog!: MatchingTransactionDialog;
    @query('alfio-notify-attendees-dialog') private notifyAttendeesDialog!: NotifyAttendeesDialog;
    @query('alfio-attendee-data-dialog') private attendeeDataDialog!: AttendeeDataDialog;

    private readonly isOwner = adminConfig().isOwner;

    private readonly loadDataTask = new Task(this, {
        task: async ([type, publicIdentifier, reservationId, organizationId]): Promise<ReservationData> => {
            if (!this.ready) {
                // AngularJS has not interpolated the attributes yet: the next run will have them
                return new Promise<ReservationData>(() => {});
            }
            const service = new ReservationService(type, publicIdentifier, reservationId);
            const [context, descriptor] = await Promise.all([
                this.loadPurchaseContext(type, publicIdentifier, organizationId),
                service.load(),
            ]);
            if (!context.visible) {
                return emptyData(context, descriptor);
            }
            const ownerData = this.isOwner;
            const [paymentInfo, ticketsWithAdditionalData, audit, billingDocuments, emails, attendeeFields] = await Promise.all([
                optional(service.paymentInfo(), null),
                optional(service.ticketsWithAdditionalData(), []),
                optional(service.audit(), []),
                ownerOnly(ownerData, () => service.billingDocuments()),
                ownerOnly(ownerData, () => service.emails()),
                // as the full data of the tickets, the additional fields can be edited by the owners only
                ownerOnly(ownerData && type === 'event', async () => [...await AdditionalFieldService.loadAllByPurchaseContext({ type, publicIdentifier })]),
            ]);
            return {
                context,
                descriptor,
                paymentInfo,
                customPaymentMethod: await this.loadCustomPaymentMethod(context.organizationId, paymentInfo),
                ticketsWithAdditionalData: new Set(ticketsWithAdditionalData),
                attendeeFields,
                audit,
                billingDocuments,
                emails,
            };
        },
        args: () => [this.purchaseContextType, this.publicIdentifier, this.reservationId, this.organizationId] as const,
        // Keep results mounted during refreshes.
        onComplete: (data) => {
            this.lastData = data;
            this.#form.api.reset(initialDrafts(data));
        },
    });

    /**
     * False while AngularJS has not interpolated the attributes yet (e.g. data-organization-id="{{...}}",
     * which Lit reads as NaN): the next run will have the real values.
     */
    private get ready(): boolean {
        if (this.publicIdentifier === '' || this.publicIdentifier.includes('{{')
            || this.reservationId === '' || this.reservationId.includes('{{')) {
            return false;
        }
        if (this.purchaseContextType === 'subscription'
            && (this.organizationId == null || Number.isNaN(this.organizationId))) {
            return false;
        }
        return true;
    }

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
            .invalidation-warning {
                display: block;
                margin-top: var(--sl-spacing-medium);
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
        return taskContent(this.loadDataTask, this.lastData, t('admin.reservation.load-failed'),
            (data) => this.renderContent(data));
    }

    private renderContent(data: ReservationData): TemplateResult {
        const reservation = data.descriptor.reservation;
        return html`
            <div class="container">
                ${when(!this.embedded, () => html`
                    <div class="page-title-row secondary">
                        <h1>${t('admin.reservation.title', shortReservationId(reservation.id))}</h1>
                        <sl-copy-button hoist value=${reservation.id} copy-label=${t('admin.reservation.copy-id')}></sl-copy-button>
                        ${reservationStatusBadge(reservation.status)}
                    </div>
                    <hr class="page-separator" />
                    <p class="page-description text-muted">${data.context.title}</p>
                `)}
                ${this.renderNotices(data)}
                ${this.renderToolbar(data)}
                <sl-tab-group>
                    ${this.renderTab('details', t('admin.promo-code.details'), 'card-text', null, true)}
                    ${when(this.isOwner && data.context.visible, () => html`
                        ${when(this.displayBillingDocuments(data), () => this.renderTab('billing-documents', t('admin.reservation.tab.billing-documents'), 'receipt', data.billingDocuments.length))}
                        ${this.renderTab('emails', t('admin.reservation.tab.emails'), 'envelope', data.emails.length)}
                        ${this.renderTab('history', t('admin.invitations.tab.history'), 'clock-history', null)}
                    `)}
                    <sl-tab-panel name="details" active>${this.renderDetails(data)}</sl-tab-panel>
                    ${when(this.isOwner && data.context.visible, () => html`
                        ${when(this.displayBillingDocuments(data), () => html`<sl-tab-panel name="billing-documents">${this.renderBillingDocuments(data)}</sl-tab-panel>`)}
                        <sl-tab-panel name="emails">
                            <alfio-email-table .messages=${data.emails}
                                               data-purchase-context-type=${this.purchaseContextType}
                                               data-public-identifier=${this.publicIdentifier}
                                               data-time-zone=${data.context.timeZone}
                                               data-empty-message=${t('admin.reservation.emails.none')}></alfio-email-table>
                        </sl-tab-panel>
                        <sl-tab-panel name="history">${this.renderHistory(data)}</sl-tab-panel>
                    `)}
                </sl-tab-group>
            </div>
            <alfio-cancellation-dialog></alfio-cancellation-dialog>
            <alfio-info-dialog></alfio-info-dialog>
            <alfio-notify-attendees-dialog></alfio-notify-attendees-dialog>
            <alfio-attendee-data-dialog></alfio-attendee-data-dialog>
            <alfio-matching-transaction-dialog time-zone=${data.context.timeZone}></alfio-matching-transaction-dialog>
            <alfio-edit-payment-dialog purchase-context-type=${this.purchaseContextType}
                                       public-identifier=${this.publicIdentifier}
                                       time-zone=${data.context.timeZone}
                                       @alfio-dialog-closed=${this.onPaymentConfirmed}></alfio-edit-payment-dialog>
        `;
    }

    private renderTab(name: string, label: string, icon: string, count: number | null, active = false): TemplateResult {
        return html`
            <sl-tab slot="nav" panel=${name} ?active=${active}>
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
                        <strong>${t('admin.reservation.notice.created')}</strong><br />
                        ${t('admin.reservation.notice.created.help')}
                    </sl-alert>
                `)}
                ${when(reservation.status === 'PENDING', () => html`
                    <sl-alert open variant="primary">
                        <sl-icon slot="icon" name="info-circle"></sl-icon>
                        ${t('admin.reservation.notice.pending')}
                    </sl-alert>
                `)}
                ${when(reservation.status === 'STUCK', () => html`
                    <sl-alert open variant="danger">
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        ${t('admin.reservation.notice.stuck')}
                    </sl-alert>
                `)}
                ${when(this.ticketsToResend.length > 0, () => html`
                    <sl-alert open closable variant="warning" class="resend-tickets" @sl-after-hide=${() => { this.ticketsToResend = []; }}>
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        <strong>${t('admin.reservation.notice.resend')}</strong>
                        ${t('admin.reservation.notice.resend.help')}
                        <sl-button size="small" variant="primary" outline ?disabled=${this.busy}
                                   @click=${() => this.resendTickets(data)}>
                            <sl-icon slot="prefix" name="envelope"></sl-icon>
                            ${t('admin.reservation.send-tickets')}
                        </sl-button>
                    </sl-alert>
                `)}
                ${when(transaction?.potentialMatch, () => html`
                    <sl-alert open variant="primary" data-testid="matching-transaction">
                        <sl-icon slot="icon" name="arrow-left-right"></sl-icon>
                        <strong>${t('admin.reservation.notice.match')}</strong>
                        ${t('admin.reservation.notice.match.help', formatAmount(transaction!.formattedAmount, transaction!.currency))}
                        <sl-button size="small" variant="primary" outline ?disabled=${this.busy}
                                   @click=${() => this.reviewMatchingTransaction(data)}>
                            <sl-icon slot="prefix" name="arrow-left-right"></sl-icon>
                            ${t('admin.pending-payments.review-match')}
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
                    <sl-input class="share-link" label=${t('admin.reservation.share-url')} readonly .value=${url}
                              help-text=${t('admin.reservation.share-url.help')}>
                        <sl-icon slot="prefix" name="link-45deg"></sl-icon>
                        <sl-copy-button hoist slot="suffix" value=${url} copy-label=${t('admin.reservation.copy-url')}></sl-copy-button>
                        <sl-icon-button slot="suffix" name="box-arrow-up-right" label=${t('admin.common.open')} href=${url} target="_blank" rel="noopener"></sl-icon-button>
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
                                ${t('admin.reservation.send-email')}
                            </sl-button>
                            <sl-menu @sl-select=${(e: CustomEvent) => this.sendEmail(data, e.detail.item.value)}>
                                <sl-menu-item value="customer">
                                    <sl-icon slot="prefix" name="person"></sl-icon>
                                    ${t('admin.reservation.send-email.customer')}
                                </sl-menu-item>
                                ${when(this.purchaseContextType === 'event', () => html`
                                    <sl-menu-item value="attendees">
                                        <sl-icon slot="prefix" name="ticket-perforated"></sl-icon>
                                        ${t('admin.reservation.send-email.attendees')}
                                    </sl-menu-item>
                                `)}
                            </sl-menu>
                        </sl-dropdown>
                    `)}
                    ${when(this.canCancel(data), () => html`
                        <sl-dropdown hoist placement="bottom-end">
                            <sl-icon-button slot="trigger" name="three-dots-vertical" label=${t('admin.common.more-actions')} ?disabled=${this.busy}></sl-icon-button>
                            <sl-menu @sl-select=${(e: CustomEvent) => this.cancel(data, e.detail.item.value === 'credit')}>
                                ${when(isOfflinePayment(reservation.status), () => html`
                                    <sl-menu-item value="credit">
                                        <sl-icon slot="prefix" name="arrow-counterclockwise"></sl-icon>
                                        ${t('admin.pending-payments.issue-credit-note')}
                                    </sl-menu-item>
                                `)}
                                <sl-menu-item value="cancel" class="danger">
                                    <sl-icon slot="prefix" name="trash"></sl-icon>
                                    ${t('admin.reservation.cancel')}
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
            <form class="details-grid" novalidate @submit=${(e: Event) => { e.preventDefault(); void this.submit(); }}>
                ${this.renderReservationCard(data)}
                ${this.renderContactCard(data)}
                ${when(this.displayPaymentInfo(data), () => html`<div class="wide">${this.renderPaymentCard(data)}</div>`)}
                ${when(subscriptionDetails, () => html`<div class="wide">${this.renderSubscriptionCard(subscriptionDetails!)}</div>`)}
                ${when(isNotPaid(reservation.status) && !isFree(data), () => html`<div class="wide">${this.renderAdvancedBilling(data)}</div>`)}
                <div class="wide">
                    ${sectionCard({ icon: 'receipt', title: t('admin.reservation.order-summary') },
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
                            () => html`<sl-button variant="default" size="large" @click=${this.close}>${t('admin.common.close')}</sl-button>`,
                            () => html`<sl-button variant="default" size="large" ?disabled=${!this.dirty(data) || this.busy}
                                                  @click=${() => this.#form.api.reset(initialDrafts(data))}>${t('admin.common.cancel')}</sl-button>`)}
                        <div></div>
                        <sl-button type="submit" variant="warning" size="large" ?loading=${this.busy} ?disabled=${!this.dirty(data) || this.busy}>
                            ${t('admin.common.save')}
                        </sl-button>
                    </div>
                </div>
            </form>
        `;
    }

    private renderReservationCard(data: ReservationData): TemplateResult {
        const reservation = data.descriptor.reservation;
        const expirationEditable = reservation.status !== 'COMPLETE' && reservation.status !== 'CANCELLED';
        return sectionCard({ icon: 'info-circle', title: t('admin.page-title.reservation') }, html`
            ${renderDetailList([
                !isFree(data) && { label: t('admin.reservation.payment-method'), value: this.paymentMethod(data) },
                reservation.hasInvoiceNumber && { label: t('admin.reservation.invoice-number'), value: reservation.invoiceNumber },
                !expirationEditable && {
                    label: t('admin.additional-items.expiration'),
                    value: html`<alfio-format-date date=${reservation.validity} time-zone=${data.context.timeZone}></alfio-format-date>`,
                },
            ])}
            ${when(expirationEditable, () => html`
                ${this.#form.field({ name: 'expiration', validators: validateWith(value => requiredValue(value)) }, field => this.input(field, {
                    name: 'expiration',
                    label: t('admin.additional-items.expiration'),
                    type: 'datetime-local',
                    prefixIcon: 'calendar-event',
                    helpText: t('admin.reservation.time-zone', data.context.timeZone),
                }))}
            `)}
        `);
    }

    private renderPaymentCard(data: ReservationData): TemplateResult {
        const paymentInfo = data.paymentInfo;
        if (paymentInfo == null) {
            return sectionCard({ icon: 'cash-coin', title: t('admin.common.payment') }, emptyState(t('admin.reservation.payment.none')));
        }
        const transaction = paymentInfo.transaction;
        const information = paymentInfo.paymentInformation;
        const currency = transaction?.currency ?? data.context.currency;
        const canRefund = this.isOwner && paymentInfo.supportRefund && data.descriptor.reservation.status !== 'STUCK';
        return sectionCard({ icon: 'cash-coin', title: t('admin.common.payment') }, html`
            ${when(transaction?.complete,
                () => renderDetailList([
                    { label: t('admin.matching-transaction.payment-id'), value: transaction!.transactionId ?? '', monospace: true },
                    { label: t('admin.common.date'), value: html`<alfio-format-date date=${transaction!.timestamp} time-zone=${data.context.timeZone}></alfio-format-date>` },
                    information != null && { label: t('admin.matching-transaction.paid-amount'), value: html`<strong>${formatAmount(information.paidAmount, currency)}</strong>` },
                    information != null && paymentInfo.supportRefund && { label: t('admin.reservation.payment.refunded'), value: formatAmount(information.refundedAmount ?? '0.00', currency) },
                ]),
                () => emptyState(t('admin.reservation.payment.not-completed')))}
            ${when(canRefund, () => html`
                <div class="refund-form">
                    <sl-input type="number" name="refundAmount" label=${t('admin.reservation.payment.refund-amount')} min="0" step="0.01"
                              .value=${this.refundAmount}
                              @sl-input=${(e: Event) => { this.refundAmount = (e.target as SlInput).value; }}>
                        <span slot="suffix">${currency}</span>
                    </sl-input>
                    <sl-button variant="warning" ?disabled=${this.busy || !(Number(this.refundAmount) > 0)}
                               @click=${() => this.refund(data, currency)}>
                        <sl-icon slot="prefix" name="arrow-counterclockwise"></sl-icon>
                        ${t('admin.reservation.payment.refund')}
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
            title: t('admin.reservation.contact'),
            className: 'contact',
            actions: this.editToggle(draft != null, () => this.toggleContact(data)),
        }, when(draft,
            () => this.renderContactForm(data, draft!),
            () => renderDetailList([
                { label: t('admin.common.customer'), value: this.customerTypeLabel(data.descriptor) },
                isCompany(data.descriptor) && { label: t('admin.reservation.company'), value: html`<strong>${data.descriptor.additionalInfo?.billingAddressCompany ?? ''}</strong>` },
                { label: t('admin.common.name'), value: formatFullName(reservation) },
                { label: t('admin.common.email'), value: reservation.email },
                { label: t('admin.reservation-create.language'), value: reservation.userLanguage },
                !!reservation.vatNr && { label: t('admin.reservation.tax-id'), value: reservation.vatNr },
                !!reservation.vatCountryCode && { label: t('admin.reservation.country'), value: reservation.vatCountryCode },
                !!eInvoicing?.referenceType && { label: 'Codice fiscale', value: eInvoicing.fiscalCode },
                eInvoicing?.referenceType === 'ADDRESSEE_CODE' && { label: 'Codice destinatario', value: eInvoicing.addresseeCode },
                eInvoicing?.referenceType === 'PEC' && { label: 'PEC', value: eInvoicing.pec },
                eInvoicing?.referenceType === 'NONE' && { label: t('admin.reservation.e-invoicing'), value: 'Nessun codice destinatario o PEC' },
                !!reservation.billingAddress && { label: t('admin.reservation.billing-address'), value: html`<pre class="billing-address">${reservation.billingAddress}</pre>` },
            ])));
    }

    private renderContactForm(data: ReservationData, draft: ContactDraft): TemplateResult {
        // the fields without validation change the draft: the copy notifies the form
        const onChange = () => this.#form.api.setFieldValue('contact', { ...draft }, { dontUpdateMeta: true });
        const ifContact = (validate: () => string | undefined) => {
            if (this.drafts.contact == null) {
                return undefined;
            }
            return validate();
        };
        const eInvoicing = data.descriptor.additionalInfo?.invoicingAdditionalInfo?.italianEInvoicing;
        return html`
            <div class="form-stack">
                <sl-radio-group name="customerType" label=${t('admin.common.customer')} value=${draft.customerType}
                                help-text=${t('admin.reservation.customer-type.help')}
                                @sl-change=${(e: Event) => this.#form.api.setFieldValue('contact.customerType', (e.target as SlRadioGroup).value as CustomerType)}>
                    <sl-radio-button value="private"><sl-icon slot="prefix" name="person"></sl-icon>${t('admin.reservation.private-person')}</sl-radio-button>
                    <sl-radio-button value="company"><sl-icon slot="prefix" name="building"></sl-icon>${t('admin.reservation.company')}</sl-radio-button>
                </sl-radio-group>
                ${when(draft.customerType === 'company', () => html`
                    ${this.#form.field({
                        name: 'contact.companyName',
                        validators: validateWith(value => ifContact(() => {
                            if (this.drafts.contact?.customerType !== 'company') {
                                return undefined;
                            }
                            return requiredValue(value);
                        })),
                    }, field => this.input(field, { name: 'companyName', label: t('admin.reservation.company-name'), prefixIcon: 'building' }))}
                    ${formField(draft, 'vatNr', { label: t('admin.reservation.tax-id') }, onChange)}
                `)}
                <div class="row">
                    ${this.#form.field({ name: 'contact.firstName', validators: validateWith(value => ifContact(() => requiredValue(value))) },
                        field => this.input(field, { name: 'firstName', label: t('admin.common.first-name') }))}
                    ${this.#form.field({ name: 'contact.lastName', validators: validateWith(value => ifContact(() => requiredValue(value))) },
                        field => this.input(field, { name: 'lastName', label: t('admin.common.last-name') }))}
                </div>
                ${this.#form.field({ name: 'contact.emailAddress', validators: validateWith(value => ifContact(() => emailAddress(value))) },
                    field => this.input(field, { name: 'emailAddress', label: t('admin.common.email'), type: 'email', prefixIcon: 'envelope' }))}
                ${selectField(draft, 'userLanguage', {
                    label: t('admin.reservation-create.language'),
                    options: data.context.locales.map(locale => ({ value: locale, label: locale })),
                }, onChange)}
                ${this.renderAddressFields(draft, onChange)}
                ${when(eInvoicing?.referenceType, () => html`
                    ${formField(draft, 'fiscalCode', { label: 'Codice fiscale' }, onChange)}
                    ${selectField(draft, 'referenceType', { label: t('admin.reservation.italian-e-invoicing'), options: E_INVOICING_REFERENCE_TYPES }, onChange)}
                    ${when(draft.referenceType === 'ADDRESSEE_CODE', () => formField(draft, 'addresseeCode', { label: 'Codice destinatario' }, onChange))}
                    ${when(draft.referenceType === 'PEC', () => this.#form.field({
                        name: 'contact.pec',
                        validators: validateWith(value => ifContact(() => {
                            if (this.drafts.contact?.referenceType !== 'PEC' || String(value ?? '').trim() === '') {
                                return undefined;
                            }
                            return emailAddress(value);
                        })),
                    }, field => this.input(field, { name: 'pec', label: 'PEC', type: 'email', optional: true })))}
                `)}
            </div>
        `;
    }

    private renderAddressFields(draft: ContactDraft, onChange: () => void): TemplateResult {
        if (!draft.structuredAddress) {
            return html`
                ${formField(draft, 'billingAddress', { label: t('admin.reservation.billing-address'), type: 'textarea' }, onChange)}
                <sl-button variant="text" size="small" class="address-switch"
                           @click=${() => { draft.structuredAddress = true; onChange(); }}>
                    <sl-icon slot="prefix" name="pencil"></sl-icon>
                    ${t('admin.reservation.structured-address')}
                </sl-button>
                ${this.renderCountryField(draft, onChange)}
            `;
        }
        return html`
            ${formField(draft, 'addressLine1', { label: t('admin.reservation.address') }, onChange)}
            ${formField(draft, 'addressLine2', { label: t('admin.reservation.address-2') }, onChange)}
            <div class="row" style="--alfio-row-cols: 3">
                ${formField(draft, 'zip', { label: t('admin.reservation.zip') }, onChange)}
                ${formField(draft, 'city', { label: t('admin.reservation.city') }, onChange)}
                ${formField(draft, 'state', { label: t('admin.reservation.state') }, onChange)}
            </div>
            ${this.renderCountryField(draft, onChange)}
        `;
    }

    private customerTypeLabel(descriptor: ReservationDescriptor): TemplateResult {
        if (isCompany(descriptor)) {
            return html`<sl-icon name="building" aria-hidden="true"></sl-icon> ${t('admin.reservation.company')}`;
        }
        return html`<sl-icon name="person" aria-hidden="true"></sl-icon> ${t('admin.reservation.private-person')}`;
    }

    private renderCountryField(draft: ContactDraft, onChange: () => void): TemplateResult {
        const countries = Object.entries(this.countries);
        // sl-select ignores a value set before its options: render it once the countries have been loaded
        if (countries.length === 0) {
            return html`<sl-input label=${t('admin.reservation.country')} disabled .value=${draft.vatCountryCode}><sl-spinner slot="suffix"></sl-spinner></sl-input>`;
        }
        return selectField(draft, 'vatCountryCode', {
            label: t('admin.reservation.country'),
            options: countries
                .sort(([, a], [, b]) => a.localeCompare(b))
                .map(([code, name]) => ({ value: code, label: `${code} - ${name}` })),
        }, onChange);
    }

    private renderSubscriptionCard(details: SubscriptionWithUsageDetails): TemplateResult {
        const subscription = details.subscription;
        const draft = this.drafts.subscription;
        const timeZone = this.lastData!.context.timeZone;
        const onChange = () => this.#form.api.setFieldValue('subscription', { ...draft! }, { dontUpdateMeta: true });
        const maxAllowed = details.usageDetails.total;
        const ifSubscription = (validate: () => string | undefined) => {
            if (this.drafts.subscription == null) {
                return undefined;
            }
            return validate();
        };
        return sectionCard({
            icon: 'card-checklist',
            title: t('admin.page-title.subscription'),
            actions: this.editToggle(draft != null, () => this.toggleSubscription()),
        }, when(draft,
            () => html`
                <div class="form-stack">
                    <div class="row">
                        ${this.#form.field({ name: 'subscription.firstName', validators: validateWith(value => ifSubscription(() => requiredValue(value))) },
                            field => this.input(field, { name: 'firstName', label: t('admin.reservation.subscription.owner-first-name') }))}
                        ${this.#form.field({ name: 'subscription.lastName', validators: validateWith(value => ifSubscription(() => requiredValue(value))) },
                            field => this.input(field, { name: 'lastName', label: t('admin.reservation.subscription.owner-last-name') }))}
                    </div>
                    ${this.#form.field({ name: 'subscription.email', validators: validateWith(value => ifSubscription(() => emailAddress(value))) },
                        field => this.input(field, { name: 'email', label: t('admin.common.email'), type: 'email', prefixIcon: 'envelope' }))}
                    ${when(maxAllowed != null && maxAllowed > 0, () => formField(draft!, 'maxAllowed', { label: t('admin.reservation.subscription.max-usages'), type: 'number', icon: 'hash' }, onChange))}
                    <div class="row">
                        ${when(subscription.validityFrom, () => this.#form.field({
                            name: 'subscription.validityFrom',
                            validators: validateWith(value => ifSubscription(() => requiredValue(value))),
                        }, field => this.input(field, { name: 'validityFrom', label: t('admin.category-dialog.valid-from'), type: 'datetime-local', helpText: t('admin.reservation.time-zone', timeZone) })))}
                        ${when(subscription.validityTo, () => this.#form.field({
                            name: 'subscription.validityTo',
                            validators: validateWith(value => ifSubscription(() => requiredValue(value))),
                        }, field => this.input(field, { name: 'validityTo', label: t('admin.additional-item.valid-to'), type: 'datetime-local', helpText: t('admin.reservation.time-zone', timeZone) })))}
                    </div>
                </div>
            `,
            () => renderDetailList([
                { label: 'PIN', value: html`<span class="monospace">${subscription.pin}</span> <sl-copy-button hoist value=${subscription.pin} copy-label=${t('admin.reservation.subscription.copy-pin')}></sl-copy-button>` },
                { label: t('admin.reservation.subscription.owner'), value: formatFullName(subscription) },
                { label: t('admin.common.email'), value: subscription.email },
                { label: t('admin.reservation.subscription.usages'), value: this.usages(details) },
                subscription.validityFrom != null && {
                    label: t('admin.category-dialog.validity'),
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
                    ${t('admin.reservation.advanced.show')}
                </sl-button>
            `;
        }
        const vatApplied = this.drafts.vatApplied;
        const hide = html`
            <sl-button size="small" variant="default" outline
                       @click=${() => this.updateDrafts({ showAdvancedBilling: false, vatApplied: initialDrafts(data).vatApplied })}>
                <sl-icon slot="prefix" name="eye-slash"></sl-icon>
                ${t('admin.common.hide')}
            </sl-button>
        `;
        return sectionCard({ icon: 'exclamation-triangle', title: t('admin.reservation.advanced.title'), className: 'danger-zone', actions: hide }, html`
            <p class="text-danger">${t('admin.reservation.advanced.warning')}</p>
            ${when(vatApplied != null,
                () => html`
                    <sl-switch .checked=${vatApplied!}
                               help-text=${t('admin.reservation.advanced.vat.help')}
                               @sl-change=${(e: Event) => this.updateDrafts({ vatApplied: (e.target as SlSwitch).checked })}>
                        ${t('admin.reservation.advanced.vat')}
                    </sl-switch>`,
                () => html`<span class="text-muted">${t('admin.reservation.advanced.vat.not-applicable')}</span>`)}
        `);
    }

    private renderAttendeesCard(data: ReservationData, category: { id: number, name: string }, tickets: Ticket[]): TemplateResult {
        const drafts = this.drafts.attendees[String(category.id)];
        const checkIns = checkInLog(data.audit);
        const fields = fieldsForCategory(data.attendeeFields, category.id);
        return sectionCard({
            icon: 'people',
            title: t('admin.reservation.attendees-for', category.name),
            badge: html`<sl-badge variant="primary" pill>${tickets.length}</sl-badge>`,
            actions: this.editToggle(drafts != null, () => this.toggleAttendees(category.id, tickets)),
        }, html`
            <div class="table-responsive">
                <table class="table table-striped table-stacked">
                    <thead>
                        <tr>
                            <th>#</th>
                            <th>${t('admin.common.first-name')}</th>
                            <th>${t('admin.common.last-name')}</th>
                            <th>${t('admin.common.email')}</th>
                            <th class="hide-small">${t('admin.page-title.check-in')}</th>
                            <th>${t('admin.common.actions')}</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(tickets, (ticket) => ticket.id, (ticket, index) => {
                            const draft = drafts?.[String(ticket.id)];
                            const log = checkIns.get(String(ticket.id));
                            return html`
                                <tr data-ticket-id=${ticket.id}>
                                    <td data-label="#">${index + 1}</td>
                                    ${this.attendeeCell(category.id, ticket.id, draft, 'firstName', ticket.firstName, t('admin.common.first-name'))}
                                    ${this.attendeeCell(category.id, ticket.id, draft, 'lastName', ticket.lastName, t('admin.common.last-name'))}
                                    ${this.attendeeCell(category.id, ticket.id, draft, 'emailAddress', ticket.email, t('admin.common.email'))}
                                    <td class="hide-small" data-label=${t('admin.page-title.check-in')}>
                                        ${when(ticket.status === 'CHECKED_IN', () => html`<sl-badge variant="success" pill>${t('admin.allocation.checked-in')}</sl-badge>`)}
                                    </td>
                                    <td>
                                        <div class="actions-cell">
                                            ${when(data.ticketsWithAdditionalData.has(ticket.id) || fields.length > 0, () => html`
                                                <sl-button class="full-data" size="small" variant="default" outline
                                                           @click=${() => this.showFullData(data, ticket, fields)}>
                                                    <sl-icon slot="prefix" name="card-list"></sl-icon>
                                                    ${t('admin.reservation.full-data')}
                                                </sl-button>
                                            `)}
                                            ${when(log, () => html`
                                                <sl-button size="small" variant="default" outline @click=${() => this.showCheckInLog(ticket, log!)}>
                                                    <sl-icon slot="prefix" name="clock-history"></sl-icon>
                                                    ${t('admin.reservation.check-in-log')}
                                                </sl-button>
                                            `)}
                                            ${when(ticket.status !== 'CHECKED_IN', () => html`
                                                <sl-button size="small" variant="danger" outline ?disabled=${this.busy}
                                                           @click=${() => this.removeTicket(data, ticket)}>
                                                    <sl-icon slot="prefix" name="trash"></sl-icon>
                                                    ${t('admin.common.remove')}
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
            ${when(drafts != null && this.ticketsToInvalidate(tickets, drafts).length > 0, () => html`
                <sl-alert open variant="warning" class="invalidation-warning">
                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                    ${tHtml('admin.reservation.invalidation-warning', html`<strong>${t('admin.reservation.invalidation-warning.strong')}</strong>`,
                        this.ticketsToInvalidate(tickets, drafts!).map(ticket => formatFullName(ticket) || ticket.email).join(', '))}
                </sl-alert>
            `)}
        `);
    }

    /**
     * Tickets already sent whose QR code changes with the draft: the code depends on the name and the e-mail,
     * but not on their case
     */
    private ticketsToInvalidate(tickets: Ticket[], drafts: Record<string, AttendeeDraft>): Ticket[] {
        return tickets.filter(ticket => {
            const draft = drafts[String(ticket.id)];
            if (ticket.status !== 'ACQUIRED' || draft == null) {
                return false;
            }
            return changed(ticket.firstName, draft.firstName)
                || changed(ticket.lastName, draft.lastName)
                || changed(ticket.email, draft.emailAddress);
        });
    }

    private attendeeCell(categoryId: number, ticketId: number, draft: AttendeeDraft | undefined, key: keyof AttendeeDraft,
                         value: string | null, label: string): TemplateResult {
        if (draft == null) {
            return html`<td data-label=${label}>${value ?? ''}</td>`;
        }
        // only the attendees of the categories being edited are checked
        const validate = (fieldValue: unknown) => {
            if (this.drafts.attendees[String(categoryId)]?.[String(ticketId)] == null) {
                return undefined;
            }
            if (key === 'emailAddress') {
                return emailAddress(fieldValue);
            }
            return requiredValue(fieldValue);
        };
        let type = 'text';
        if (key === 'emailAddress') {
            type = 'email';
        }
        return html`
            <td class="attendee-cell" data-label=${label}>
                ${this.#form.field({ name: `attendees.${categoryId}.${ticketId}.${key}`, validators: validateWith(validate) },
                    field => this.input(field, { name: key, label, type, className: 'label-hidden' }))}
            </td>
        `;
    }

    private input(field: TextField, options: ValidatedInputOptions): TemplateResult {
        return validatedInput(field, this.#form.api.state.submissionAttempts > 0, options);
    }

    private renderSubscriptionReservations(data: ReservationData): TemplateResult {
        return sectionCard({ icon: 'calendar-check', title: t('admin.reservation.subscription.reservations') }, html`
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
                    <sl-button variant="warning" size="small" ?disabled=${this.busy}
                               @click=${() => this.perform(() => service.regenerateBillingDocument(), t('admin.reservation.billing.regenerated'), t('admin.reservation.billing.regenerate-failed'))}>
                        <sl-icon slot="prefix" name="arrow-repeat"></sl-icon>
                        ${t('admin.reservation.billing.regenerate')}
                    </sl-button>
                </div>
            `)}
            ${sectionCard({ icon: 'receipt', title: t('admin.categories.filter.active'), badge: html`<sl-badge variant="primary" pill>${valid.length}</sl-badge>` },
                this.billingDocumentsTable(data, valid, (document) => html`
                    <sl-button size="small" variant="danger" outline ?disabled=${this.busy}
                               @click=${() => this.invalidateDocument(document)}>
                        <sl-icon slot="prefix" name="x-octagon"></sl-icon>
                        ${t('admin.reservation.billing.invalidate')}
                    </sl-button>
                `))}
            ${sectionCard({ icon: 'archive', title: t('admin.reservation.billing.not-active'), badge: html`<sl-badge variant="neutral" pill>${notValid.length}</sl-badge>` },
                this.billingDocumentsTable(data, notValid, (document) => html`
                    <sl-button size="small" variant="warning" outline ?disabled=${this.busy}
                               @click=${() => this.perform(() => service.restoreBillingDocument(document.id), t('admin.reservation.billing.restored'), t('admin.reservation.billing.restore-failed'))}>
                        <sl-icon slot="prefix" name="arrow-counterclockwise"></sl-icon>
                        ${t('admin.reservation.billing.restore')}
                    </sl-button>
                `))}
        `;
    }

    private billingDocumentsTable(data: ReservationData,
                                  documents: BillingDocument[],
                                  action: (document: BillingDocument) => TemplateResult): TemplateResult {
        if (documents.length === 0) {
            return emptyState(t('admin.reservation.billing.none'));
        }
        const service = this.service();
        return html`
            <div class="table-responsive">
                <table class="table table-striped table-stacked">
                    <thead>
                        <tr>
                            <th>${t('admin.common.type')}</th>
                            <th>${t('admin.reservation.billing.number')}</th>
                            <th>${t('admin.common.date')}</th>
                            <th>${t('admin.common.actions')}</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(documents, (document) => document.id, (document) => html`
                            <tr>
                                <td data-label=${t('admin.common.type')}>${BILLING_DOCUMENT_TYPES[document.type] ?? document.type}</td>
                                <td class="monospace" data-label=${t('admin.reservation.billing.number')}>${document.number}</td>
                                <td class="timestamp" data-label=${t('admin.common.date')}>
                                    <alfio-format-date date=${document.generationTimestamp} time-zone=${data.context.timeZone}></alfio-format-date>
                                </td>
                                <td>
                                    <div class="actions-cell">
                                        <sl-button size="small" variant="default" outline href=${service.billingDocumentUrl(document.id)}
                                                   target="_blank" rel="noopener">
                                            <sl-icon slot="prefix" name="download"></sl-icon>
                                            ${t('admin.event.menu.download')}
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
        return sectionCard({ icon: 'clock-history', title: t('admin.reservation.history') }, html`
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
                    ${t('admin.common.cancel')}
                </sl-button>
            `;
        }
        return html`
            <sl-button size="small" variant="default" outline ?disabled=${this.busy} @click=${toggle}>
                <sl-icon slot="prefix" name="pencil"></sl-icon>
                ${t('admin.common.edit')}
            </sl-button>
        `;
    }

    // ---------- state ----------

    private service(): ReservationService {
        return new ReservationService(this.purchaseContextType, this.publicIdentifier, this.reservationId);
    }

    private updateDrafts(update: Partial<Drafts>): void {
        for (const [key, value] of Object.entries(update)) {
            this.#form.api.setFieldValue(key as keyof Drafts, value as never, { dontUpdateMeta: true });
        }
    }

    private dirty(data: ReservationData): boolean {
        const initial = initialDrafts(data);
        const drafts = this.drafts;
        return drafts.expiration !== initial.expiration
            || drafts.contact != null
            || Object.keys(drafts.attendees).length > 0
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
        const attendees = { ...this.drafts.attendees };
        const key = String(categoryId);
        if (attendees[key] != null) {
            delete attendees[key];
        } else {
            attendees[key] = Object.fromEntries(tickets.map(ticket => [String(ticket.id), {
                firstName: ticket.firstName ?? '',
                lastName: ticket.lastName ?? '',
                emailAddress: ticket.email ?? '',
            }]));
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

    private async submit(): Promise<void> {
        if (this.busy) {
            return;
        }
        if (!await submitValidated(this.#form.api)) {
            await this.updateComplete;
            focusFirstError(this.renderRoot);
        }
    }

    /**
     * Called by the form once all the fields are valid
     */
    private async save(drafts: Drafts): Promise<void> {
        const data = this.lastData;
        if (data == null) {
            return;
        }
        const invalidated = data.descriptor.ticketsByCategory.flatMap(entry => {
            const drafts = this.drafts.attendees[String(entry.key.id)];
            if (drafts == null) {
                return [];
            }
            return this.ticketsToInvalidate(entry.value, drafts).map(ticket => ticket.id);
        });
        const saved = await this.perform(() => this.service().update(this.toModification(data, drafts)), t('admin.reservation.updated'), t('admin.reservation.update-failed'));
        if (saved && invalidated.length > 0) {
            this.ticketsToResend = [...new Set([...this.ticketsToResend, ...invalidated])];
        }
    }

    private toModification(data: ReservationData, drafts: Drafts): ReservationModification {
        const descriptor = data.descriptor;
        const reservation = descriptor.reservation;
        const contact = drafts.contact ?? contactDraft(descriptor);
        const subscription = drafts.subscription;
        let vatApplied: 'Y' | 'N' | null = null;
        if (drafts.vatApplied === true) {
            vatApplied = 'Y';
        } else if (drafts.vatApplied === false) {
            vatApplied = 'N';
        }
        return {
            expiration: toDateTimeModification(drafts.expiration || toDateTimeLocal(reservation.validity, data.context.timeZone)),
            language: reservation.userLanguage ?? '',
            updateContactData: drafts.contact != null,
            customerData: {
                firstName: contact.firstName,
                lastName: contact.lastName,
                emailAddress: contact.emailAddress,
                billingAddress: contact.billingAddress,
                userLanguage: contact.userLanguage,
                vatNr: vatNumber(contact),
                vatCountryCode: contact.vatCountryCode,
                invoicingAdditionalInfo: this.invoicingAdditionalInfo(descriptor, contact),
                billingDetails: billingDetails(drafts.contact),
            },
            updateAdvancedBillingOptions: drafts.vatApplied !== initialDrafts(data).vatApplied,
            advancedBillingOptions: { vatApplied },
            ticketsInfo: descriptor.ticketsByCategory.map(entry => {
                const attendees = drafts.attendees[String(entry.key.id)];
                return {
                    category: { existingCategoryId: entry.key.id, name: entry.key.name },
                    updateAttendees: attendees != null,
                    attendees: entry.value.map(ticket => ({
                        ticketId: ticket.id,
                        ...(attendees?.[String(ticket.id)] ?? { firstName: ticket.firstName ?? '', lastName: ticket.lastName ?? '', emailAddress: ticket.email ?? '' }),
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
        await this.perform(() => this.service().confirm(), t('admin.reservation.confirmed'), t('admin.reservation.confirm-failed'), true);
    }

    private async reviewMatchingTransaction(data: ReservationData): Promise<void> {
        const transaction = data.paymentInfo!.transaction!;
        const choice = await this.matchingDialog.open(this.reservationId, transaction);
        if (choice === 'confirm') {
            await this.confirmPaymentDialog.openForConfirmation(this.reservationId);
        } else if (choice === 'discard') {
            await this.perform(
                () => PendingPaymentsService.discardMatchingTransaction(this.publicIdentifier, this.reservationId, transaction.id),
                t('admin.pending-payments.flagged'),
                t('admin.pending-payments.flag-failed'));
        }
    }

    private async sendEmail(data: ReservationData, recipient: 'customer' | 'attendees'): Promise<void> {
        const service = this.service();
        if (recipient === 'customer') {
            await this.perform(() => service.notifyCustomer(), t('admin.reservation.email-sent'), t('admin.reservation.email-failed'), false, false);
            return;
        }
        await this.notifyAttendees(data);
    }

    private async resendTickets(data: ReservationData): Promise<void> {
        await this.notifyAttendees(data, this.ticketsToResend);
    }

    private async notifyAttendees(data: ReservationData, preselected?: number[]): Promise<void> {
        const ticketIds = await this.notifyAttendeesDialog.open(data.descriptor.ticketsByCategory.map(entry => ({
            name: entry.key.name,
            tickets: entry.value,
        })), preselected);
        if (ticketIds == null || ticketIds.length === 0) {
            return;
        }
        const sent = await this.perform(() => this.service().notifyAttendees(ticketIds), t('admin.reservation.tickets-sent'), t('admin.reservation.tickets-failed'), false, false);
        if (sent) {
            this.ticketsToResend = this.ticketsToResend.filter(id => !ticketIds.includes(id));
        }
    }

    private async cancel(data: ReservationData, credit: boolean): Promise<void> {
        const request = cancelReservationRequest(data.descriptor.reservation, data.paymentInfo, credit, data.context.timeZone);
        const choice = await this.cancellationDialog.open(request);
        if (choice == null) {
            return;
        }
        let message = t('admin.reservation.cancelled');
        if (credit) {
            message = t('admin.pending-payments.credit.done');
        } else if (data.descriptor.reservation.status === 'CREDIT_NOTE_ISSUED') {
            message = t('admin.reservation.cancelled-credit-note');
        }
        await this.perform(
            () => this.service().cancel({ credit, ...cancellationChoice(choice, data.paymentInfo) }),
            message,
            t('admin.reservation.cancel-failed'));
    }

    private async removeTicket(data: ReservationData, ticket: Ticket): Promise<void> {
        const choice = await this.cancellationDialog.open(removeTicketRequest(data.descriptor.reservation, data.paymentInfo, ticket));
        if (choice == null) {
            return;
        }
        let creditNoteGenerated = false;
        await this.perform(async () => {
            creditNoteGenerated = await this.service().removeTicket(ticket.id, ticketRemovalChoice(choice, data.paymentInfo));
        }, t('admin.reservation.ticket-removed'), t('admin.tickets.remove-failed'));
        if (creditNoteGenerated) {
            dispatchFeedback({ type: 'neutral', message: t('admin.tickets.credit-note') }, this);
        }
    }

    private async refund(data: ReservationData, currency: string): Promise<void> {
        const amount = formatAmount(this.refundAmount, currency);
        if (!await ConfirmationDialogService.requestConfirm(t('admin.reservation.payment.refund'), t('admin.reservation.refund.confirm', amount), 'warning')) {
            return;
        }
        let message = t('admin.reservation.refund.done');
        if (data.descriptor.reservation.invoiceRequested) {
            message = t('admin.reservation.refund.done-credit-note');
        }
        await this.perform(() => this.service().refund(this.refundAmount), message, t('admin.reservation.refund.failed'));
        this.refundAmount = '';
    }

    private async invalidateDocument(document: BillingDocument): Promise<void> {
        const label = `${BILLING_DOCUMENT_TYPES[document.type] ?? document.type} ${document.number}`;
        if (!await ConfirmationDialogService.requestConfirm(t('admin.reservation.billing.invalidate.title'), t('admin.reservation.billing.invalidate.text', label), 'danger')) {
            return;
        }
        await this.perform(() => this.service().invalidateBillingDocument(document.id), t('admin.reservation.billing.invalidated'), t('admin.reservation.billing.invalidate-failed'));
    }

    /**
     * The data of the attendee: the values of the additional fields can be edited, if there are any
     */
    private async showFullData(data: ReservationData, ticket: Ticket, fields: readonly AdditionalField[]): Promise<void> {
        const locale = this.lastData?.context.locales[0] ?? 'en';
        if (fields.length > 0) {
            const saved = await this.attendeeDataDialog.open({
                service: this.service(),
                ticket,
                fields,
                language: data.descriptor.reservation.userLanguage || locale,
            });
            if (saved === true) {
                dispatchFeedback({ type: 'success', message: t('admin.reservation.attendee-data-updated') }, this);
                this.notifyChange(false);
                void this.loadDataTask.run();
            }
            return;
        }
        void this.infoDialog.open({
            icon: 'card-list',
            title: t('admin.reservation.attendee-data.title', formatFullName(ticket)),
            description: t('admin.reservation.attendee-data.description'),
            errorMessage: t('admin.reservation.attendee-data.load-failed'),
            body: async () => {
                const fullData = await this.service().fullTicketData(ticket.uuid);
                const field = (item: AdditionalFieldValue) => ({
                    label: item.description[locale]?.label ?? item.name,
                    value: item.description[locale]?.restrictedValuesDescription?.[item.value ?? ''] ?? item.value,
                });
                return renderDetailList([
                    ...fullData.ticketFieldConfigurationBeforeStandard.map(field),
                    { label: t('admin.common.first-name'), value: fullData.firstName },
                    { label: t('admin.common.last-name'), value: fullData.lastName },
                    { label: t('admin.common.email'), value: fullData.email },
                    ...fullData.ticketFieldConfigurationAfterStandard.map(field),
                ]);
            },
        });
    }

    private showCheckInLog(ticket: Ticket, entries: AuditEntry[]): void {
        void this.infoDialog.open({
            icon: 'clock-history',
            title: t('admin.reservation.check-in-log.title', formatFullName(ticket)),
            description: t('admin.reservation.check-in-log.description'),
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
                          changed = true): Promise<boolean> {
        this.busy = true;
        try {
            await action();
            dispatchFeedback({ type: 'success', message: successMessage }, this);
            if (changed) {
                this.notifyChange(confirmed);
            }
            return true;
        } catch (e) {
            dispatchFeedback({ type: 'danger', message: errorDescription(e, errorMessage) }, this);
            return false;
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
            return t('admin.reservation.confirm-payment');
        }
        return t('admin.reservation.mark-completed');
    }

    private canCancel(data: ReservationData): boolean {
        const reservation = data.descriptor.reservation;
        const checkedIn = data.descriptor.ticketsByCategory.some(entry => entry.value.some(ticket => ticket.status === 'CHECKED_IN'));
        return this.isOwner && reservation.status !== 'CANCELLED' && reservation.status !== 'CREDIT_NOTE_ISSUED' && !checkedIn;
    }

    private displayPaymentInfo(data: ReservationData): boolean {
        return data.context.visible
            && !isFree(data)
            && data.paymentInfo?.transaction?.potentialMatch !== true
            && !isNotPaid(data.descriptor.reservation.status);
    }

    // a free reservation has no billing documents, unless they have been generated before it became free
    private displayBillingDocuments(data: ReservationData): boolean {
        return !isFree(data) || data.billingDocuments.length > 0;
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
            return t('admin.reservation.subscription.usage', formatNumber(usage.used), formatNumber(usage.total));
        }
        return formatNumber(usage.used);
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
        let baseUrl = adminConfig().baseUrl;
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
        .map(ticket => [String(ticket.id), formatFullName(ticket) || ticket.email || t('admin.reservation.an-attendee')]));
}

function emptyDrafts(): Drafts {
    return { expiration: '', contact: null, attendees: {}, subscription: null, vatApplied: null, showAdvancedBilling: false };
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
        attendeeFields: [],
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

function isFree(data: ReservationData): boolean {
    return data.descriptor.orderSummary.free;
}

// the ticket code ignores the case of the name and of the e-mail, and the values are trimmed when saved
function changed(current: string | null, draft: string): boolean {
    return (current ?? '').trim().toLowerCase() !== draft.trim().toLowerCase();
}
