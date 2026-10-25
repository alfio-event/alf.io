import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task, TaskStatus} from '@lit/task';
import type {SlDialog} from '@shoelace-style/shoelace';
import {AlfioEvent} from '../../model/event.ts';
import {CheckInTicket, TicketStatus} from '../../model/check-in.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {EventService} from '../../service/event.ts';
import {CheckInService, isCheckedIn, matchesSearch} from '../../service/check-in.ts';
import {humanize, readRouteParams, replaceRouteParams, toPageNumber} from '../../service/helpers.ts';
import {formatFullName, localizedTitle} from '../../service/reservation-format.ts';
import {
    badges,
    base,
    dialog,
    listTabs,
    modernLayout,
    modernTable,
    purchaseContextListPage,
    reservationTable,
    retroCompat,
    textColors,
} from '../../styles.ts';
import {AlfioPageChange} from '../../components/pagination-bar.ts';
import '../../components/pagination-bar.ts';
import '../../components/payment-method.ts';
import {emptyState} from '../../components/empty-state.ts';
import {ListSearchController} from '../../components/list-search.ts';
import {taskContent} from '../../components/task-content.ts';

type TabName = 'pending' | 'checked-in';

interface CheckInData {
    event: AlfioEvent;
    tickets: CheckInTicket[];
}

const ITEMS_PER_PAGE = 10;
const FIRST_PAGES: Record<TabName, number> = { pending: 1, 'checked-in': 1 };
const TABS: TabName[] = ['pending', 'checked-in'];
// set in the session storage once the mobile apps info has been dismissed
const APPS_INFO_DISMISSED_KEY = 'alfio-check-in-apps-info-dismissed';

const STATUS_ICONS: Partial<Record<TicketStatus, { icon: string, className: string }>> = {
    PENDING: { icon: 'exclamation-triangle-fill', className: 'text-warning' },
    ACQUIRED: { icon: 'bookmark-fill', className: 'text-success' },
    TO_BE_PAID: { icon: 'bookmark', className: 'text-success' },
    CHECKED_IN: { icon: 'check-circle-fill', className: 'text-success' },
    CANCELLED: { icon: 'x-lg', className: 'text-danger' },
};

@customElement('alfio-check-in')
export class CheckIn extends LitElement {
    @property({ type: String, attribute: 'data-event-name' }) eventName = '';
    @state() private search = '';
    @state() private selectedTab: TabName = 'pending';
    @state() private pages = { ...FIRST_PAGES };
    @state() private lastData: CheckInData | null = null;
    @state() private showAppsInfo = !appsInfoDismissed();
    // [completed, total] chunks, while the attendees are being downloaded
    @state() private progress: [number, number] | null = null;
    // ticket on which an action (check-in, revert) is in progress
    @state() private busyTicketId: number | null = null;
    @state() private qrCodeTicket: CheckInTicket | null = null;
    @query('#qr-code-dialog') private qrCodeDialog!: SlDialog;

    private readonly searchController = new ListSearchController(this, (search) => {
        this.search = search;
        this.pages = { ...FIRST_PAGES };
        this.syncLocation();
    });

    private readonly loadDataTask = new Task(this, {
        task: async ([eventName]): Promise<CheckInData> => {
            const run = ++this.loadRun;
            const event = this.lastData?.event ?? (await EventService.load(eventName)).event;
            const tickets = await CheckInService.loadTickets(event.id, (completed, total, partial) => {
                // a big event takes a while to download: display the attendees as they arrive
                if (run === this.loadRun) {
                    this.progress = [completed, total];
                    this.lastData = { event, tickets: partial };
                }
            });
            return { event, tickets };
        },
        args: () => [this.eventName] as const,
        // Task invokes this only for the latest request. Keep results mounted during refreshes.
        onComplete: (data) => {
            this.lastData = data;
            this.progress = null;
        },
        onError: () => {
            this.progress = null;
        },
    });
    // identifies the latest load, so that the progress of a superseded one is ignored
    private loadRun = 0;

    static readonly styles = [
        base,
        retroCompat,
        textColors,
        badges,
        modernTable,
        modernLayout,
        purchaseContextListPage,
        reservationTable,
        listTabs,
        dialog,
        css`
            .apps-info {
                margin-bottom: var(--sl-spacing-large);
            }
            .apps-info p {
                margin: 0 0 var(--sl-spacing-small);
                font-size: var(--sl-font-size-large);
            }
            .store-badges {
                display: flex;
                flex-wrap: wrap;
                align-items: center;
                gap: var(--sl-spacing-large);
            }
            .store-badges img {
                display: block;
                height: 40px;
            }
            /* the Google Play badge image includes a transparent padding */
            .store-badges img.google-play {
                height: 58px;
                margin: -9px;
            }
            .apps-info small {
                display: block;
                margin-top: var(--sl-spacing-x-small);
            }
            .download-progress {
                margin-bottom: var(--sl-spacing-medium);
            }
            .text-warning {
                color: var(--sl-color-warning-600);
            }
            .ticket-status {
                font-size: var(--sl-font-size-medium);
                vertical-align: middle;
            }
            .table > thead > tr > th.status-column {
                width: 2.5rem;
            }
            .actions-cell {
                flex-wrap: nowrap;
                white-space: nowrap;
            }
            .customer-email {
                display: block;
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-small);
            }
            .qr-code {
                display: block;
                max-width: 100%;
                margin-inline: auto;
            }
            #qr-code-dialog {
                --width: 24rem;
            }
        `,
    ];

    connectedCallback(): void {
        super.connectedCallback();
        const params = readRouteParams();
        this.search = params.get('search') ?? '';
        this.searchController.init(this.search);
        this.selectedTab = TABS[Number(params.get('t')) - 1] ?? 'pending';
        this.pages = {
            pending: toPageNumber(params.get('page')),
            'checked-in': toPageNumber(params.get('checkedInPage')),
        };
        window.addEventListener('beforeunload', this.onBeforeUnload);
        window.addEventListener('alfio-logout', this.stopUnloadConfirmation);
    }

    disconnectedCallback(): void {
        this.stopUnloadConfirmation();
        super.disconnectedCallback();
    }

    private stopUnloadConfirmation = (): void => {
        window.removeEventListener('beforeunload', this.onBeforeUnload);
        window.removeEventListener('alfio-logout', this.stopUnloadConfirmation);
    };

    /**
     * The attendees are kept in memory only: ask for confirmation before a reload, which would download them again.
     * The browser shows its own message, and only if the user has interacted with the page.
     */
    private onBeforeUnload = (event: BeforeUnloadEvent): void => {
        if ((this.lastData?.tickets.length ?? 0) > 0) {
            event.preventDefault();
            // legacy browsers need returnValue to be set
            event.returnValue = '';
        }
    };

    render(): TemplateResult {
        return taskContent(this.loadDataTask, this.lastData, 'Failed to load attendees. Please try again.',
            (data) => this.renderContent(data));
    }

    private renderContent(data: CheckInData): TemplateResult {
        const matching = data.tickets.filter(ticket => matchesSearch(ticket, this.search));
        const pending = matching.filter(ticket => !isCheckedIn(ticket));
        const checkedIn = matching.filter(ticket => isCheckedIn(ticket));
        const title = localizedTitle(data.event);
        return html`
            <div class="container">
                <div class="page-title-row secondary">
                    <h1>Check-in for <i>${title}</i></h1>
                </div>
                <hr class="page-separator" />
                ${this.renderAppsInfo(title)}
                ${when(this.progress != null, () => html`
                    <sl-progress-bar class="download-progress" label="Loading attendees"
                                     value=${Math.round(100 * this.progress![0] / this.progress![1])}></sl-progress-bar>
                `)}
                <div class="filter-toolbar">
                    <div class="filter-left">${this.searchController.render('attendees')}</div>
                    <div class="filter-right">
                        <sl-button id="refresh-button" variant="default" ?loading=${this.loadDataTask.status === TaskStatus.PENDING}
                                   @click=${() => this.loadDataTask.run()}>
                            <sl-icon slot="prefix" name="arrow-clockwise"></sl-icon>
                            Refresh
                        </sl-button>
                        <sl-button id="add-attendees-button" variant="success" size="large" @click=${this.addAttendees}>
                            <sl-icon slot="prefix" name="plus-circle"></sl-icon>
                            Add new attendees
                        </sl-button>
                    </div>
                </div>
                <sl-tab-group class="list-tabs" @sl-tab-show=${this.onTabShow}>
                    <sl-tab slot="nav" panel="pending" ?active=${this.selectedTab === 'pending'}>
                        <span class="tab-label">
                            <sl-icon name="qr-code"></sl-icon>
                            Pending
                            <sl-badge variant="primary" pill>${pending.length}</sl-badge>
                        </span>
                    </sl-tab>
                    <sl-tab slot="nav" panel="checked-in" ?active=${this.selectedTab === 'checked-in'}>
                        <span class="tab-label">
                            <sl-icon name="check-circle"></sl-icon>
                            Checked in
                            <sl-badge variant="primary" pill>${checkedIn.length}</sl-badge>
                        </span>
                    </sl-tab>
                    <sl-tab-panel name="pending">
                        ${this.renderSection('pending', pending, 'No attendees to check in',
                            (tickets) => this.renderPendingTable(tickets, data.event))}
                    </sl-tab-panel>
                    <sl-tab-panel name="checked-in">
                        ${this.renderSection('checked-in', checkedIn, 'No attendees have been checked in yet',
                            (tickets) => this.renderCheckedInTable(tickets))}
                    </sl-tab-panel>
                </sl-tab-group>
            </div>
            <sl-dialog id="qr-code-dialog" label=${this.qrCodeTicket?.fullName ?? ''}
                       @sl-after-hide=${(e: Event) => e.target === e.currentTarget && (this.qrCodeTicket = null)}>
                ${when(this.qrCodeTicket != null, () => html`
                    <img class="qr-code" alt="QR code of the ticket of ${this.qrCodeTicket!.fullName}"
                         src=${`/event/${encodeURIComponent(this.eventName)}/ticket/${this.qrCodeTicket!.publicUuid}/code.png`}>
                `)}
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <sl-button variant="default" size="large" @click=${() => this.qrCodeDialog.hide()}>Close</sl-button>
                </div>
            </sl-dialog>
        `;
    }

    private dismissAppsInfo = (event: Event): void => {
        // nested Shoelace components (e.g. tooltips) emit their own sl-after-hide
        if (event.target !== event.currentTarget) {
            return;
        }
        this.showAppsInfo = false;
        try {
            sessionStorage.setItem(APPS_INFO_DISMISSED_KEY, 'true');
        } catch {
            // storage not available (e.g. disabled by the browser): the info will be displayed again next time
        }
    };

    private renderAppsInfo(title: string): TemplateResult | typeof nothing {
        if (!this.showAppsInfo) {
            return nothing;
        }
        return html`
            <sl-alert class="apps-info" variant="primary" open closable @sl-after-hide=${this.dismissAppsInfo}>
                <sl-icon slot="icon" name="info-circle"></sl-icon>
                <p>
                    Scan tickets for event <i>${title}</i> using the
                    <a href="https://play.google.com/store/apps/details?id=alfio.scan" target="_blank" rel="noopener">Android app</a>,
                    <a href="https://itunes.apple.com/us/app/alf-io-scan/id1425492093" target="_blank" rel="noopener">iOS app</a>
                    or the <a id="scan-page-link" href=${`#/events/${this.eventName}/check-in/scan`}>embedded scan functionality</a>
                </p>
                <div class="store-badges">
                    <a href="https://play.google.com/store/apps/details?id=alfio.scan" target="_blank" rel="noopener">
                        <img class="google-play" alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png">
                    </a>
                    <a href="https://itunes.apple.com/us/app/alf-io-scan/id1425492093" target="_blank" rel="noopener">
                        <img alt="Download on the App Store" src="https://tools.applemediaservices.com/api/badges/download-on-the-app-store/black/en-us?size=250x83">
                    </a>
                </div>
                <small class="text-muted">Google Play and the Google Play logo are trademarks of Google LLC.</small>
            </sl-alert>
        `;
    }

    private renderSection(name: TabName,
                          tickets: CheckInTicket[],
                          emptyMessage: string,
                          renderTable: (tickets: CheckInTicket[]) => TemplateResult): TemplateResult {
        const lastPage = Math.max(1, Math.ceil(tickets.length / ITEMS_PER_PAGE));
        const page = Math.min(this.pages[name], lastPage);
        const pageContent = tickets.slice((page - 1) * ITEMS_PER_PAGE, page * ITEMS_PER_PAGE);
        return html`
            <section class="section-card">
                <div class="section-body">
                    ${when(tickets.length === 0,
                        () => emptyState(this.search ? 'No attendees match your filter' : emptyMessage),
                        () => html`
                            ${renderTable(pageContent)}
                            <alfio-pagination-bar
                                .page=${page}
                                .total=${tickets.length}
                                page-size=${ITEMS_PER_PAGE}
                                item-label="attendees"
                                @alfio-page-change=${(e: AlfioPageChange) => this.changePage(name, e.detail.page)}
                            ></alfio-pagination-bar>
                        `)}
                </div>
            </section>
        `;
    }

    private renderPendingTable(tickets: CheckInTicket[], event: AlfioEvent): TemplateResult {
        return html`
            <div class="table-responsive">
                <table class="table table-striped pending-tickets">
                    <thead>
                        <tr>
                            <th class="status-column"><span class="sr-only">Status</span></th>
                            <th>Attendee</th>
                            <th class="hide-small">Email</th>
                            <th class="hide-small">Category</th>
                            <th>Ref Nr.</th>
                            ${when(!event.freeOfCharge, () => html`<th class="hide-small">Payment</th>`)}
                            <th><span class="sr-only">Actions</span></th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(tickets, (ticket) => ticket.id, (ticket) => html`
                            <tr data-ticket-uuid=${ticket.uuid}>
                                <td>${this.renderStatus(ticket.status)}</td>
                                <td class="attendee">${formatFullName(ticket)}</td>
                                <td class="email hide-small">${ticket.email}</td>
                                <td class="hide-small">${ticket.ticketCategory.name}</td>
                                <td class="reservation-id">${this.renderReference(ticket)}</td>
                                ${when(!event.freeOfCharge, () => html`
                                    <td class="hide-small">
                                        <alfio-payment-method method=${ticket.ticketReservation.paymentMethod ?? ''}></alfio-payment-method>
                                    </td>
                                `)}
                                <td class="actions-cell">
                                    ${this.renderQrCodeButton(ticket)}
                                    <sl-button class="check-in-button" size="small" variant="primary"
                                               ?disabled=${this.busyTicketId != null}
                                               ?loading=${this.busyTicketId === ticket.id}
                                               @click=${() => this.manualCheckIn(ticket)}>
                                        <sl-icon slot="prefix" name="check-lg"></sl-icon>
                                        Check-in
                                    </sl-button>
                                </td>
                            </tr>
                        `)}
                    </tbody>
                </table>
            </div>
        `;
    }

    private renderCheckedInTable(tickets: CheckInTicket[]): TemplateResult {
        return html`
            <div class="table-responsive">
                <table class="table table-striped checked-in-tickets">
                    <thead>
                        <tr>
                            <th>Attendee</th>
                            <th class="hide-small">Email</th>
                            <th class="hide-small">Category</th>
                            <th>Ref Nr.</th>
                            <th class="hide-small">Customer</th>
                            <th class="hide-small">Payment</th>
                            <th><span class="sr-only">Actions</span></th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(tickets, (ticket) => ticket.id, (ticket) => html`
                            <tr data-ticket-uuid=${ticket.uuid}>
                                <td class="attendee">${formatFullName(ticket)}</td>
                                <td class="email hide-small">${ticket.email}</td>
                                <td class="hide-small">${ticket.ticketCategory.name}</td>
                                <td class="reservation-id">${this.renderReference(ticket)}</td>
                                <td class="hide-small">
                                    ${formatFullName(ticket.ticketReservation)}
                                    <span class="customer-email">${ticket.ticketReservation.email ?? ''}</span>
                                </td>
                                <td class="hide-small">
                                    <alfio-payment-method method=${ticket.ticketReservation.paymentMethod ?? ''}></alfio-payment-method>
                                </td>
                                <td class="actions-cell">
                                    ${this.renderQrCodeButton(ticket)}
                                    <sl-button class="revert-button" size="small" variant="warning" outline
                                               ?disabled=${this.busyTicketId != null}
                                               ?loading=${this.busyTicketId === ticket.id}
                                               @click=${() => this.revertCheckIn(ticket)}>
                                        <sl-icon slot="prefix" name="arrow-counterclockwise"></sl-icon>
                                        Revert check-in
                                    </sl-button>
                                </td>
                            </tr>
                        `)}
                    </tbody>
                </table>
            </div>
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

    private renderReference(ticket: CheckInTicket): TemplateResult {
        return html`
            <a href=${`#/events/${this.eventName}/reservation/${ticket.ticketsReservationId}`} title="View reservation">
                ${ticket.publicUuid.substring(0, 8)}
            </a>
        `;
    }

    private renderQrCodeButton(ticket: CheckInTicket): TemplateResult {
        return html`
            <sl-button class="qr-code-button" size="small" variant="default" outline @click=${() => this.showQrCode(ticket)}>
                <sl-icon slot="prefix" name="qr-code"></sl-icon>
                QR code
            </sl-button>
        `;
    }

    /**
     * The reservation is created by the hosting (AngularJS) page, which then invokes "onAdded"
     */
    private addAttendees = (): void => {
        this.dispatchEvent(new CustomEvent<AlfioAddAttendeesDetail>('alfio-add-attendees', {
            detail: {
                onAdded: (reservationId) => {
                    // show only the new attendees, ready to be checked in
                    this.searchController.init(reservationId);
                    this.search = reservationId;
                    this.selectedTab = 'pending';
                    this.pages = { ...FIRST_PAGES };
                    this.syncLocation();
                    this.loadDataTask.run();
                },
            },
            bubbles: true,
            composed: true,
        }));
    };

    private showQrCode(ticket: CheckInTicket): void {
        this.qrCodeTicket = ticket;
        void this.qrCodeDialog.show();
    }

    // the filter is kept on purpose: the operator may be checking in a whole group
    private manualCheckIn(ticket: CheckInTicket): Promise<void> {
        return this.performAction(ticket, () => CheckInService.manualCheckIn(ticket),
            `${ticket.fullName} checked in!`, `Can't check-in ${ticket.fullName}`);
    }

    private revertCheckIn(ticket: CheckInTicket): Promise<void> {
        return this.performAction(ticket, () => CheckInService.revertCheckIn(ticket),
            `Reverted ${ticket.fullName}`, `Can't revert the check-in of ${ticket.fullName}`);
    }

    private async performAction(ticket: CheckInTicket,
                                action: () => Promise<boolean>,
                                successMessage: string,
                                failureMessage: string): Promise<void> {
        this.busyTicketId = ticket.id;
        try {
            const success = await action();
            dispatchFeedback({ type: success ? 'success' : 'warning', message: success ? successMessage : failureMessage }, this);
            this.loadDataTask.run();
            await this.loadDataTask.taskComplete;
        } catch (e) {
            console.error(e);
            dispatchFeedback({ type: 'danger', message: failureMessage }, this);
        } finally {
            this.busyTicketId = null;
        }
    }

    private onTabShow = (event: CustomEvent<{ name: string }>): void => {
        this.selectedTab = event.detail.name as TabName;
        this.syncLocation();
    };

    private changePage(name: TabName, page: number): void {
        this.pages = { ...this.pages, [name]: page };
        this.syncLocation();
    }

    private syncLocation(): void {
        replaceRouteParams(new URLSearchParams({
            page: String(this.pages.pending),
            checkedInPage: String(this.pages['checked-in']),
            search: this.search,
            t: String(TABS.indexOf(this.selectedTab) + 1),
        }));
    }
}

export interface AlfioAddAttendeesDetail {
    onAdded: (reservationId: string) => void;
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-check-in': CheckIn;
    }
    interface GlobalEventHandlersEventMap {
        'alfio-add-attendees': CustomEvent<AlfioAddAttendeesDetail>;
    }
}

function appsInfoDismissed(): boolean {
    try {
        return sessionStorage.getItem(APPS_INFO_DISMISSED_KEY) === 'true';
    } catch {
        return false;
    }
}
