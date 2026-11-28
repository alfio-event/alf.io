import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {classMap} from 'lit/directives/class-map.js';
import {Task} from '@lit/task';
import type {SlSelect} from '@shoelace-style/shoelace';
import {AlfioEvent, CategoryTicket, TicketCategory, TicketStatus} from '../../model/event.ts';
import {PageAndContent} from '../../model/reservation.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {EventService} from '../../service/event.ts';
import {ReservationService} from '../../service/reservation.ts';
import {errorMessage, humanize, readRouteParams, replaceRouteParams, toPageNumber} from '../../service/helpers.ts';
import {formatAmount, formatFullName} from '../../service/reservation-format.ts';
import {removeTicketRequest, ticketRemovalChoice} from '../../reservation-detail/cancellation.ts';
import {CancellationDialog} from '../../components/cancellation-dialog.ts';
import '../../components/cancellation-dialog.ts';
import {AlfioPageChange} from '../../components/pagination-bar.ts';
import '../../components/pagination-bar.ts';
import '../../components/payment-method.ts';
import '../../components/format-date.ts';
import {emptyState} from '../../components/empty-state.ts';
import {ListSearchController} from '../../components/list-search.ts';
import {taskContent} from '../../components/task-content.ts';
import {sectionCard} from '../../components/section-card.ts';
import {
    badges,
    base,
    modernLayout,
    modernTable,
    purchaseContextListPage,
    reservationTable,
    retroCompat,
    textColors,
} from '../../styles.ts';
import { navigate, adminUrl } from '../../admin-router';

const ITEMS_PER_PAGE = 30;

const STATUS_ICONS: Partial<Record<TicketStatus, { icon: string, className: string }>> = {
    PENDING: { icon: 'exclamation-triangle-fill', className: 'text-warning' },
    ACQUIRED: { icon: 'bookmark-fill', className: 'text-success' },
    TO_BE_PAID: { icon: 'bookmark', className: 'text-success' },
    CHECKED_IN: { icon: 'check-circle-fill', className: 'text-success' },
    CANCELLED: { icon: 'x-lg', className: 'text-danger' },
};

interface TicketsListData {
    event: AlfioEvent;
    tickets: PageAndContent<CategoryTicket[]>;
}

/**
 * The tickets of one ticket category of an event (/admin/events/{eventName}/category/{categoryId}/tickets),
 * with server-side search and pagination, ticket locking and ticket removal.
 */
@customElement('alfio-tickets-list')
export class TicketsList extends LitElement {
    @property({ type: String, attribute: 'data-event-name' }) eventName = '';
    @property({ type: Number, attribute: 'data-category-id' }) categoryId = 0;

    @state() private search = '';
    @state() private page = 1;
    @state() private lastData: TicketsListData | null = null;
    // ticket on which an action (lock/unlock, removal) is in progress
    @state() private busyTicketId: number | null = null;

    @query('alfio-cancellation-dialog') private cancellationDialog!: CancellationDialog;

    private readonly searchController = new ListSearchController(this, (search) => {
        this.search = search;
        this.page = 1;
        this.syncLocation();
    });

    private readonly loadDataTask = new Task(this, {
        task: async ([eventName, categoryId, search, page]): Promise<TicketsListData> => {
            const [eventWithOrg, tickets] = await Promise.all([
                EventService.load(eventName),
                EventService.loadCategoryTickets(eventName, categoryId, page, search),
            ]);
            return { event: eventWithOrg.event, tickets };
        },
        args: () => [this.eventName, this.categoryId, this.search, this.page] as const,
        // Task invokes this only for the latest request. Keep results mounted during refreshes.
        onComplete: (data) => {
            this.lastData = data;
        },
    });

    static readonly styles = [
        base,
        retroCompat,
        textColors,
        badges,
        modernLayout,
        modernTable,
        reservationTable,
        purchaseContextListPage,
        css`
            .category-select {
                flex: 0 0 12rem;
            }
            .customer-email,
            .confirmation,
            .timestamp,
            .discount,
            .amount {
                display: block;
                color: var(--sl-color-neutral-600);
                font-size: var(--sl-font-size-small);
            }
            .amount {
                color: var(--sl-color-neutral-900);
                font-weight: var(--sl-font-weight-semibold);
            }
            .discount {
                text-decoration: line-through;
            }
            .ticket-uuid {
                font-family: var(--sl-font-mono);
                font-size: var(--sl-font-size-small);
                overflow-wrap: anywhere;
            }
            .assignee.text-warning {
                color: var(--sl-color-warning-700);
            }
        `,
    ];

    connectedCallback(): void {
        super.connectedCallback();
        const params = readRouteParams();
        this.search = params.get('search') ?? '';
        this.searchController.init(this.search);
        this.page = toPageNumber(params.get('page'));
    }

    render(): TemplateResult {
        return taskContent(this.loadDataTask, this.lastData, 'Failed to load the tickets. Please try again.', (data) =>
            this.renderContent(data),
        );
    }

    private renderContent(data: TicketsListData): TemplateResult {
        const event = data.event;
        const category = event.ticketCategories.find((c) => c.id === this.categoryId);
        if (category == null) {
            return html`
                <div class="container">
                    <sl-alert open variant="danger">
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        The category does not exist anymore.
                    </sl-alert>
                </div>
            `;
        }
        return html`
            <div class="container">
                <div class="page-title-row">
                    <h1>Tickets for <i>${category.name}</i></h1>
                </div>
                <hr class="page-separator" />
                <div class="filter-toolbar">
                    <div class="filter-left">
                        ${this.renderCategorySelect(event, category)}
                        ${this.searchController.render('tickets')}
                    </div>
                </div>
                ${sectionCard({
                    icon: 'ticket-perforated',
                    title: 'Tickets',
                    badge: html`<sl-badge variant="primary" pill>${data.tickets.right}</sl-badge>`,
                }, html`
                    ${when(data.tickets.right === 0,
                        () => emptyState('No tickets have been found'),
                        () => html`
                            ${this.renderTable(data.tickets.left, event)}
                            <alfio-pagination-bar
                                .page=${this.page}
                                .total=${data.tickets.right}
                                page-size=${ITEMS_PER_PAGE}
                                item-label="tickets"
                                @alfio-page-change=${(e: AlfioPageChange) => this.changePage(e.detail.page)}
                            ></alfio-pagination-bar>
                        `)}
                `)}
                <alfio-cancellation-dialog></alfio-cancellation-dialog>
            </div>
        `;
    }

    private renderCategorySelect(event: AlfioEvent, category: TicketCategory): TemplateResult {
        return html`
            <sl-select class="filter-select category-select label-hidden" label="Category" hoist
                       .value=${String(category.id)}
                       @sl-change=${(e: Event) => this.switchCategory(event, Number((e.target as SlSelect).value))}>
                ${repeat(event.ticketCategories, (c) => c.id, (c) => html`
                    <sl-option value=${String(c.id)}>${c.name}</sl-option>
                `)}
            </sl-select>
        `;
    }

    private renderTable(tickets: CategoryTicket[], event: AlfioEvent): TemplateResult {
        return html`
            <div class="table-responsive">
                <table class="table table-striped tickets-table table-stacked">
                    <thead>
                        <tr>
                            <th class="status-column"><span class="sr-only">Status</span></th>
                            <th>Ticket</th>
                            <th>Reservation</th>
                            <th class="hide-small">Assignee</th>
                            <th class="hide-small">Payment</th>
                            <th class="hide-small">Code</th>
                            <th><span class="sr-only">Actions</span></th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(tickets, (ticket) => ticket.id, (ticket) => this.renderRow(ticket, event))}
                    </tbody>
                </table>
            </div>
        `;
    }

    private renderRow(ticket: CategoryTicket, event: AlfioEvent): TemplateResult {
        let lockIcon = 'lock';
        let lockLabel = 'Lock';
        let lockTooltip = 'Forbid ticket reassignment';
        if (ticket.lockedAssignment) {
            lockIcon = 'unlock';
            lockLabel = 'Unlock';
            lockTooltip = 'Allow ticket reassignment';
        }
        let transactionLabel = '';
        if (ticket.transaction != null) {
            transactionLabel = `TX ${ticket.transaction.transactionId ?? ticket.transaction.id}`;
        }
        return html`
            <tr data-ticket-uuid=${ticket.uuid}>
                <td class="stacked-inline">${this.renderStatus(ticket.status)}</td>
                <td class="ticket-uuid">
                    <a href=${this.reservationHref(ticket)} title=${`View reservation ${ticket.uuid}`}>${ticket.uuid.substring(0, 8)}</a>
                </td>
                <td data-label="Reservation">
                    <a href=${this.reservationHref(ticket)}>${formatFullName(ticket.ticketReservation)}</a>
                    <span class="customer-email">${ticket.ticketReservation.email ?? ''}</span>
                    ${when(!ticket.pending, () => html`
                        <span class="confirmation">Confirmed on
                            <alfio-format-date date=${ticket.ticketReservation.confirmationTimestamp ?? ''} time-zone=${event.timeZone}></alfio-format-date>
                        </span>
                    `)}
                </td>
                <td class=${classMap({ 'hide-small': true, 'assignee': true, 'text-warning': formatFullName(ticket) === '' })} data-label="Assignee">
                    ${formatFullName(ticket)}
                    ${when(ticket.email != null && ticket.email !== '', () => html`
                        <span class="customer-email">${ticket.email}</span>
                    `)}
                </td>
                <td class="hide-small" data-label="Payment">
                    ${when(ticket.appliedDiscount != null && ticket.appliedDiscount > 0, () => html`
                        <span class="discount">- ${formatAmount(ticket.appliedDiscount, ticket.currencyCode)}</span>
                    `)}
                    ${when(ticket.paid, () => html`
                        <span class="amount">${formatAmount(ticket.formattedFinalPrice, ticket.currencyCode)}</span>
                        <alfio-payment-method method=${ticket.ticketReservation.paymentMethod ?? ''}></alfio-payment-method>
                        <span class="timestamp">${transactionLabel}
                            <alfio-format-date date=${ticket.transactionTimestamp ?? ''}></alfio-format-date>
                        </span>
                    `)}
                </td>
                <td class="hide-small" data-label="Code">
                    ${when(ticket.promoCodeOrToken != null && ticket.promoCodeOrToken !== '', () => html`
                        <a href=${adminUrl(`/events/${this.eventName}/reservations/?search=${ticket.promoCodeOrToken}`)} title="Find all reservations">${ticket.promoCodeOrToken}</a>
                    `)}
                </td>
                <td class="actions-cell">
                    <sl-tooltip content=${lockTooltip}>
                        <sl-button class="lock-button" size="small" variant="default" outline
                                   ?disabled=${this.busyTicketId != null}
                                   ?loading=${this.busyTicketId === ticket.id}
                                   @click=${() => void this.toggleLocking(ticket)}>
                            <sl-icon slot="prefix" name=${lockIcon}></sl-icon>
                            ${lockLabel}
                        </sl-button>
                    </sl-tooltip>
                    ${when(ticket.status !== 'CHECKED_IN', () => html`
                        <sl-button class="remove-ticket" size="small" variant="danger" outline
                                   ?disabled=${this.busyTicketId != null}
                                   @click=${() => void this.removeTicket(ticket)}>
                            <sl-icon slot="prefix" name="trash"></sl-icon>
                            Remove
                        </sl-button>
                    `)}
                </td>
            </tr>
        `;
    }

    private renderStatus(status: TicketStatus): TemplateResult {
        const config = STATUS_ICONS[status] ?? { icon: 'gear', className: 'text-muted' };
        const label = humanize(status);
        return html`
            <sl-tooltip content=${label}>
                <sl-icon class="ticket-status ${config.className}" name=${config.icon} label=${label} data-status=${status}></sl-icon>
            </sl-tooltip>
        `;
    }

    private reservationHref(ticket: CategoryTicket): string {
        return adminUrl(`/events/${this.eventName}/reservation/${ticket.ticketReservation.id}`);
    }

    private switchCategory(event: AlfioEvent, categoryId: number): void {
        if (categoryId !== this.categoryId) {
            navigate(`/events/${event.shortName}/category/${categoryId}/tickets`);
        }
    }

    private changePage(page: number): void {
        this.page = page;
        this.syncLocation();
    }

    private syncLocation(): void {
        const params = new URLSearchParams({ page: String(this.page), search: this.search });
        replaceRouteParams(params);
    }

    private async toggleLocking(ticket: CategoryTicket): Promise<void> {
        this.busyTicketId = ticket.id;
        try {
            await EventService.toggleTicketLocking(this.eventName, this.categoryId, ticket.id);
            void this.loadDataTask.run();
        } catch (e) {
            dispatchFeedback({ type: 'danger', message: errorMessage(e, 'Failed to change the ticket lock') }, this);
        } finally {
            this.busyTicketId = null;
        }
    }

    private async removeTicket(ticket: CategoryTicket): Promise<void> {
        this.busyTicketId = ticket.id;
        try {
            const service = new ReservationService('event', this.eventName, ticket.ticketReservation.id);
            const [descriptor, paymentInfo] = await Promise.all([service.load(), service.paymentInfo()]);
            const choice = await this.cancellationDialog.open(removeTicketRequest(descriptor.reservation, paymentInfo, ticket));
            if (choice == null) {
                return;
            }
            const creditNoteGenerated = await service.removeTicket(ticket.id, ticketRemovalChoice(choice, paymentInfo));
            dispatchFeedback({ type: 'success', message: 'Ticket removed' }, this);
            if (creditNoteGenerated) {
                dispatchFeedback({ type: 'neutral', message: 'A credit note has been generated, see the billing documents.' }, this);
            }
            void this.loadDataTask.run();
        } catch (e) {
            dispatchFeedback({ type: 'danger', message: errorMessage(e, 'Failed to remove the ticket') }, this);
        } finally {
            this.busyTicketId = null;
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-tickets-list': TicketsList;
    }
}
