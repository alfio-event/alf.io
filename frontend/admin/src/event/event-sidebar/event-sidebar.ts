import {css, html, LitElement, nothing, PropertyValues, TemplateResult} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {classMap} from 'lit/directives/class-map.js';
import type {SlSelect} from '@shoelace-style/shoelace';
import {AlfioEvent} from '../../model/event.ts';
import {dispatchEventChanged, EVENT_CHANGED_EVENT, EventService, isEventChangedElsewhere} from '../../service/event.ts';
import {ConfigurationService} from '../../service/configuration.ts';
import {errorMessage, fetchJson} from '../../service/helpers.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {badges} from '../../styles.ts';
import '../../components/pending-payments-count.ts';
import './export-dialog.ts';
import type {EventExportDialog} from './export-dialog.ts';
import './delete-event-dialog.ts';
import type {DeleteEventDialog} from './delete-event-dialog.ts';
import './linked-application-dialog.ts';
import type {LinkedApplicationDialog} from './linked-application-dialog.ts';

interface NavItem {
    id: string;
    label: string;
    icon: string;
    // path below #/events/{eventName}/
    path: string;
    // the pages, below #/events/{eventName}/, where the item is the current one
    current: RegExp;
    count?: 'pending-payments' | 'waiting-list';
}

interface NavGroup {
    label: string;
    items: NavItem[];
}

interface LinkedApplication {
    label: string;
    capability: string;
}

interface ApplicationInfo {
    version: string;
}

// #/events/{eventName}/{page}
const EVENT_ROUTE = /^#\/events\/([^/?]+)\/?(.*)$/;

/**
 * The menu of the event pages (#/events/{eventName}/…): the event name and status, the main actions, and the
 * links to the pages of the event, with the current one highlighted. On a narrow screen, the links become a
 * "Go to" select above the page. The event is the one of the current route: the AngularJS layout can't bind it,
 * because its template is also a Mustache template.
 *
 * Reloads the event when another page fires EVENT_CHANGED_EVENT (e.g. the detail page publishing it), and fires
 * it after changing the event itself.
 */
@customElement('alfio-event-sidebar')
export class EventSidebar extends LitElement {

    // from the route
    @state() private eventName = '';

    @state() private event: AlfioEvent | null = null;
    @state() private loadError: string | null = null;
    @state() private usePartnerCode = false;
    @state() private waitingListCount = 0;
    @state() private version = '';
    // the page below #/events/{eventName}/, e.g. "reservations/"
    @state() private route = '';
    @state() private changingStatus = false;

    // the dialogs live in the body: the sticky column of the menu would trap their overlay below the page
    private readonly exportDialog: EventExportDialog = document.createElement('alfio-event-export-dialog');
    private readonly deleteDialog: DeleteEventDialog = document.createElement('alfio-delete-event-dialog');
    private readonly linkedApplicationDialog: LinkedApplicationDialog = document.createElement('alfio-linked-application-dialog');

    // discards the answers of a previous event
    private request = 0;
    private navbarObserver: ResizeObserver | null = null;

    static readonly styles = [
        badges,
        css`
            /* --event-sidebar-top: height of the fixed navbar; --event-sidebar-offset: the part of it that the layout
               doesn't leave free (both set by updateOffsets()) */
            :host {
                display: block;
                height: 100%;
                padding-top: var(--event-sidebar-offset, 0px);
                box-sizing: border-box;
            }
            /* the column of the layout sticks below the navbar (.event-sidebar-column, admin.css): the menu scrolls
               within the height left */
            .sidebar {
                max-height: calc(100vh - var(--event-sidebar-top, 50px));
                overflow-y: auto;
                scrollbar-width: thin;
                box-sizing: border-box;
                padding: var(--sl-spacing-large) var(--sl-spacing-x-small) var(--sl-spacing-large) 0;
                display: flex;
                flex-direction: column;
                gap: var(--sl-spacing-medium);
            }
            .event-name {
                font-size: var(--sl-font-size-large);
                font-weight: var(--sl-font-weight-semibold);
                line-height: var(--sl-line-height-dense);
                margin: 0 0 var(--sl-spacing-2x-small);
                overflow-wrap: anywhere;
            }
            .event-name a {
                color: inherit;
                text-decoration: none;
            }
            .event-name a:hover {
                color: var(--sl-color-primary-700);
            }
            .event-status {
                display: flex;
                flex-wrap: wrap;
                gap: var(--sl-spacing-2x-small);
            }
            .actions {
                display: flex;
                flex-wrap: wrap;
                gap: var(--sl-spacing-x-small);
            }
            .actions .create-reservation {
                flex: 1 1 100%;
            }
            .actions sl-dropdown {
                flex: 1 1 0;
            }
            .actions sl-dropdown sl-button {
                width: 100%;
            }
            nav ul {
                list-style: none;
                margin: 0;
                padding: 0;
            }
            .nav-group + .nav-group {
                margin-top: var(--sl-spacing-medium);
            }
            .nav-group-label {
                margin: 0 0 var(--sl-spacing-2x-small);
                padding: 0 var(--sl-spacing-small);
                font-size: var(--sl-font-size-x-small);
                font-weight: var(--sl-font-weight-semibold);
                letter-spacing: 0.05em;
                text-transform: uppercase;
                color: var(--sl-color-neutral-500);
            }
            .nav-item {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-x-small);
                padding: var(--sl-spacing-2x-small) var(--sl-spacing-small);
                border-radius: var(--sl-border-radius-medium);
                color: var(--sl-color-neutral-700);
                text-decoration: none;
                line-height: var(--sl-line-height-normal);
            }
            .nav-item:hover {
                background: var(--sl-color-neutral-100);
                color: var(--sl-color-neutral-900);
            }
            .nav-item:focus-visible {
                outline: var(--sl-focus-ring);
                outline-offset: -2px;
            }
            .nav-item.current {
                background: var(--sl-color-primary-50);
                color: var(--sl-color-primary-700);
                font-weight: var(--sl-font-weight-semibold);
            }
            .nav-item sl-icon {
                flex: none;
            }
            .nav-item .label {
                flex: 1;
                min-width: 0;
            }
            button.nav-item {
                width: 100%;
                box-sizing: border-box;
                border: none;
                background: none;
                font: inherit;
                cursor: pointer;
                text-align: start;
            }
            footer {
                font-size: var(--sl-font-size-x-small);
                color: var(--sl-color-neutral-500);
                border-top: 1px solid var(--sl-color-neutral-200);
                padding: var(--sl-spacing-small) var(--sl-spacing-small) 0;
            }
            footer a {
                color: inherit;
            }
            footer p {
                margin: 0 0 var(--sl-spacing-2x-small);
            }
            .go-to {
                display: none;
            }
            /* below Bootstrap's md breakpoint, the menu sits above the page */
            @media (max-width: 991px) {
                .sidebar {
                    max-height: none;
                    overflow: visible;
                    padding: var(--sl-spacing-small) 0 var(--sl-spacing-medium);
                    border-bottom: 1px solid var(--sl-color-neutral-200);
                    margin-bottom: var(--sl-spacing-medium);
                }
                .actions .create-reservation {
                    flex: 1 1 auto;
                }
                .actions sl-dropdown {
                    flex: 0 0 auto;
                }
                nav, footer {
                    display: none;
                }
                .go-to {
                    display: block;
                }
            }
        `,
    ];

    connectedCallback(): void {
        super.connectedCallback();
        window.addEventListener('hashchange', this.onHashChange);
        window.addEventListener(EVENT_CHANGED_EVENT, this.onEventChanged);
        document.body.append(this.exportDialog, this.deleteDialog, this.linkedApplicationDialog);
        this.onHashChange();
        void this.loadVersion();
        const navbar = document.querySelector('.navbar-fixed-top');
        if (navbar != null) {
            this.navbarObserver = new ResizeObserver(() => this.updateOffsets(navbar));
            this.navbarObserver.observe(navbar);
        }
    }

    /**
     * The fixed navbar is taller when its items wrap: the menu starts below it, and sticks right below it
     */
    private updateOffsets(navbar: Element): void {
        const navbarHeight = Math.round(navbar.getBoundingClientRect().height);
        // the layout leaves room for a one-line navbar only (.menu-spacer)
        const spacer = document.querySelector('.menu-spacer');
        let reserved = 0;
        if (spacer != null) {
            reserved = Number.parseFloat(getComputedStyle(spacer).paddingTop) || 0;
        }
        this.style.setProperty('--event-sidebar-top', `${navbarHeight}px`);
        this.style.setProperty('--event-sidebar-offset', `${Math.max(0, navbarHeight - reserved)}px`);
    }

    disconnectedCallback(): void {
        this.exportDialog.remove();
        this.deleteDialog.remove();
        this.linkedApplicationDialog.remove();
        this.navbarObserver?.disconnect();
        this.navbarObserver = null;
        window.removeEventListener('hashchange', this.onHashChange);
        window.removeEventListener(EVENT_CHANGED_EVENT, this.onEventChanged);
        super.disconnectedCallback();
    }

    // untyped, because the event name is a private state
    protected willUpdate(changed: PropertyValues): void {
        if (changed.has('eventName')) {
            void this.load();
        }
    }

    render(): TemplateResult {
        const event = this.event;
        if (this.loadError != null) {
            return html`
                <div class="sidebar">
                    <sl-alert variant="danger" open>
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        ${this.loadError}
                    </sl-alert>
                </div>
            `;
        }
        if (event == null) {
            return html`<div class="sidebar"><sl-spinner></sl-spinner></div>`;
        }
        const groups = this.navGroups(event);
        return html`
            <div class="sidebar">
                <header>
                    <p class="event-name"><a href="#/events/${event.shortName}/detail">${event.displayName}</a></p>
                </header>
                ${this.renderActions(event)}
                ${this.renderGoTo(groups)}
                <nav aria-label="Event">
                    ${repeat(groups, g => g.label, g => this.renderGroup(event, g))}
                    ${this.renderLinkedApplications(event)}
                </nav>
                <footer>
                    <p>Powered by <a href="https://alf.io" target="_blank" rel="noopener">alf.io</a>${when(this.version !== '', () => html` v${this.version}`, () => nothing)}</p>
                    <p>
                        <a href="https://github.com/alfio-event/alf.io/issues" target="_blank" rel="noopener">Report an issue</a>
                        ·
                        <a href="https://github.com/alfio-event/alf.io/discussions" target="_blank" rel="noopener">Ask for help</a>
                    </p>
                </footer>
            </div>
        `;
    }

    private renderActions(event: AlfioEvent): TemplateResult {
        const owner = event.visibleForCurrentUser;
        return html`
            <div class="actions">
                ${when(!event.expired, () => html`
                    <sl-button class="create-reservation" id="sidebar-create-reservation" variant="success"
                               href="#/events/${event.shortName}/reservation/new">
                        <sl-icon slot="prefix" name="plus-circle"></sl-icon>
                        Create reservation
                    </sl-button>
                `, () => nothing)}
                ${when(owner, () => html`
                    <sl-dropdown id="event-actions" hoist>
                        <sl-button slot="trigger" caret>Actions</sl-button>
                        <sl-menu @sl-select=${this.onAction}>
                            <sl-menu-item value="copy">
                                <sl-icon slot="prefix" name="copy"></sl-icon>
                                Copy this event
                            </sl-menu-item>
                            ${when(event.status === 'PUBLIC' && !event.expired, () => html`
                                <sl-menu-item value="unlist" ?disabled=${this.changingStatus}>
                                    <sl-icon slot="prefix" name="eye-slash"></sl-icon>
                                    Remove from the event list
                                </sl-menu-item>
                            `, () => nothing)}
                            <sl-divider></sl-divider>
                            <sl-menu-item value="delete" class="delete-event">
                                <sl-icon slot="prefix" name="trash"></sl-icon>
                                Delete the event
                            </sl-menu-item>
                        </sl-menu>
                    </sl-dropdown>
                    <sl-dropdown id="event-downloads" hoist>
                        <sl-button slot="trigger" caret>
                            <sl-icon slot="prefix" name="download"></sl-icon>
                            Download
                        </sl-button>
                        <sl-menu @sl-select=${this.onDownload}>
                            <sl-menu-item value="attendees">Attendees' data…</sl-menu-item>
                            <sl-menu-item value="waiting-list">Waiting list…</sl-menu-item>
                            <sl-menu-item value="sponsor-scan">Sponsors' scans</sl-menu-item>
                            <sl-divider></sl-divider>
                            <sl-menu-item value="documents-pdf">Billing documents (PDF)</sl-menu-item>
                            <sl-menu-item value="documents-xls">Billing documents (Excel)</sl-menu-item>
                        </sl-menu>
                    </sl-dropdown>
                `, () => nothing)}
            </div>
        `;
    }

    /**
     * On a narrow screen: the links of the menu as a select
     */
    private renderGoTo(groups: NavGroup[]): TemplateResult {
        const current = groups.flatMap(g => g.items).find(item => this.isCurrent(item));
        return html`
            <sl-select class="go-to" id="event-go-to" label="Go to" hoist placeholder="Select a page"
                       .value=${current?.id ?? ''}
                       @sl-change=${this.onGoTo}>
                ${repeat(groups, g => g.label, (g, index) => html`
                    ${when(index > 0, () => html`<sl-divider></sl-divider>`, () => nothing)}
                    <small>${g.label}</small>
                    ${repeat(g.items, item => item.id, item => html`<sl-option value=${item.id}>${item.label}</sl-option>`)}
                `)}
            </sl-select>
        `;
    }

    private renderGroup(event: AlfioEvent, group: NavGroup): TemplateResult {
        return html`
            <div class="nav-group">
                ${when(group.label !== '', () => html`<p class="nav-group-label">${group.label}</p>`, () => nothing)}
                <ul>
                    ${repeat(group.items, item => item.id, item => this.renderItem(event, item))}
                </ul>
            </div>
        `;
    }

    private renderItem(event: AlfioEvent, item: NavItem): TemplateResult {
        const current = this.isCurrent(item);
        let ariaCurrent: 'page' | typeof nothing = nothing;
        if (current) {
            ariaCurrent = 'page';
        }
        return html`
            <li>
                <a class=${classMap({ 'nav-item': true, current })} id="event-nav-${item.id}" aria-current=${ariaCurrent}
                   href=${this.href(event, item)}>
                    <sl-icon name=${item.icon} aria-hidden="true"></sl-icon>
                    <span class="label">${item.label}</span>
                    ${this.renderCount(event, item)}
                </a>
            </li>
        `;
    }

    private renderCount(event: AlfioEvent, item: NavItem): TemplateResult | typeof nothing {
        if (item.count === 'pending-payments') {
            return html`<alfio-pending-payments-count event-name=${event.shortName} display="badge"></alfio-pending-payments-count>`;
        }
        if (item.count === 'waiting-list' && this.waitingListCount > 0) {
            return html`<sl-badge variant="neutral" pill class="waiting-list-count">${this.waitingListCount}</sl-badge>`;
        }
        return nothing;
    }

    private renderLinkedApplications(event: AlfioEvent): TemplateResult | typeof nothing {
        const applications = this.linkedApplications(event);
        if (applications.length === 0) {
            return nothing;
        }
        return html`
            <div class="nav-group">
                <p class="nav-group-label">Linked applications</p>
                <ul>
                    ${repeat(applications, a => a.label, a => html`
                        <li>
                            <button class="nav-item linked-application" type="button"
                                    @click=${() => void this.linkedApplicationDialog.show(event.shortName, a.capability, a.label)}>
                                <sl-icon name="box-arrow-up-right" aria-hidden="true"></sl-icon>
                                <span class="label">${a.label}</span>
                            </button>
                        </li>
                    `)}
                </ul>
            </div>
        `;
    }

    /**
     * The pages of the event, grouped by task. Those who can't manage the event only see the overview and check-in
     */
    private navGroups(event: AlfioEvent): NavGroup[] {
        const overview: NavItem = { id: 'overview', label: 'Overview', icon: 'speedometer2', path: 'detail', current: /^(detail|edit|category\/)/ };
        const checkIn: NavItem = { id: 'check-in', label: 'Check-in', icon: 'qr-code-scan', path: 'check-in', current: /^check-in/ };
        if (!event.visibleForCurrentUser) {
            return [{ label: '', items: [overview, checkIn] }];
        }
        const paid = !event.freeOfCharge;
        const offlinePayments = paid && (event.allowedPaymentProxies ?? []).some(p => p === 'OFFLINE' || p === 'CUSTOM_OFFLINE');
        const sales: NavItem[] = [
            { id: 'reservations', label: 'Reservations', icon: 'ticket-perforated', path: 'reservations/', current: /^(reservations|reservation\/(?!new|import))/ },
        ];
        if (paid) {
            sales.push({ id: 'payments', label: 'Payments', icon: 'cash-stack', path: 'transactions/', current: /^transactions/ });
        }
        if (offlinePayments) {
            sales.push({ id: 'pending-payments', label: 'Pending payments', icon: 'hourglass-split', path: 'pending-payments/', current: /^pending-payments/, count: 'pending-payments' });
        }
        sales.push({ id: 'waiting-list', label: 'Waiting list', icon: 'people', path: 'waiting-queue', current: /^waiting-queue/, count: 'waiting-list' });

        const attendees: NavItem[] = [
            checkIn,
            { id: 'compose-message', label: 'Compose message', icon: 'envelope', path: 'compose-custom-message', current: /^compose-custom-message/ },
            { id: 'email-log', label: 'E-mail log', icon: 'send', path: 'email-log', current: /^email-log/ },
        ];
        if (!event.expired) {
            attendees.push({ id: 'import-attendees', label: 'Import attendees', icon: 'upload', path: 'reservation/import', current: /^reservation\/import/ });
        }
        attendees.push({ id: 'polls', label: 'Polls', icon: 'bar-chart', path: 'polls/list', current: /^polls/ });

        let codesLabel = 'Promo codes';
        if (this.usePartnerCode) {
            codesLabel = 'Partner codes';
        }
        const setup: NavItem[] = [
            { id: 'promo-codes', label: codesLabel, icon: 'percent', path: 'promo-codes', current: /^promo-codes/ },
            { id: 'data-to-collect', label: 'Data to collect', icon: 'person-lines-fill', path: 'attendee-data-to-collect', current: /^attendee-data-to-collect/ },
        ];
        if (paid) {
            setup.push({ id: 'additional-options', label: 'Additional options', icon: 'bag-plus', path: 'additional-services', current: /^additional-services/ });
            setup.push({ id: 'donations', label: 'Donation options', icon: 'gift', path: 'donations', current: /^donations/ });
        }
        setup.push({ id: 'templates', label: 'Templates', icon: 'file-earmark-text', path: 'show-resources', current: /^show-resources/ });
        // a page outside of the event pages (see href()): never the current one here
        setup.push({ id: 'configuration', label: 'Configuration', icon: 'gear', path: '', current: /(?!)/ });

        return [
            { label: '', items: [overview] },
            { label: 'Sales', items: sales },
            { label: 'Attendees', items: attendees },
            { label: 'Setup', items: setup },
        ];
    }

    private href(event: AlfioEvent, item: NavItem): string {
        // the configuration of the event is one of the levels of the configuration page
        if (item.id === 'configuration') {
            return `#/configuration/organization/${event.organizationId}/event/${event.id}`;
        }
        return `#/events/${event.shortName}/${item.path}`;
    }

    private isCurrent(item: NavItem): boolean {
        return item.current.test(this.route);
    }

    private linkedApplications(event: AlfioEvent): LinkedApplication[] {
        return (event.supportedCapabilities ?? [])
            .filter(c => c.capability === 'LINK_EXTERNAL_APPLICATION')
            .flatMap(c => (c.details ?? []).map(d => ({ label: d.label, capability: c.capability })));
    }

    private async load(): Promise<void> {
        const eventName = this.eventName;
        const request = ++this.request;
        if (eventName === '') {
            this.event = null;
            return;
        }
        try {
            const { event } = await EventService.load(eventName);
            if (request !== this.request) {
                return;
            }
            this.event = event;
            this.loadError = null;
            if (event.visibleForCurrentUser) {
                void this.loadCounters(event, request);
            }
        } catch (error) {
            if (request === this.request) {
                this.loadError = errorMessage(error, 'Failed to load the event');
            }
        }
    }

    private async loadCounters(event: AlfioEvent, request: number): Promise<void> {
        const [usePartnerCode, waitingListCount] = await Promise.all([
            ConfigurationService.loadSingleConfig(event.shortName, 'USE_PARTNER_CODE_INSTEAD_OF_PROMOTIONAL').then(v => String(v) === 'true').catch(() => false),
            EventService.loadWaitingQueueCount(event.shortName).catch(() => 0),
        ]);
        if (request === this.request) {
            this.usePartnerCode = usePartnerCode;
            this.waitingListCount = Number(waitingListCount) || 0;
        }
    }

    private async loadVersion(): Promise<void> {
        try {
            const info = await fetchJson<ApplicationInfo>('/admin/api/utils/alfio/info');
            this.version = info.version ?? '';
        } catch {
            this.version = '';
        }
    }

    private onHashChange = (): void => {
        const match = EVENT_ROUTE.exec(window.location.hash);
        let route = '';
        let eventName = '';
        if (match != null) {
            eventName = decodeURIComponent(match[1]);
            route = match[2];
        }
        this.route = route;
        this.eventName = eventName;
    };

    private onEventChanged = (e: Event): void => {
        if (isEventChangedElsewhere(e, this.eventName, this)) {
            void this.load();
        }
    };

    private onGoTo = (e: Event): void => {
        const event = this.event;
        if (event == null) {
            return;
        }
        const id = String((e.target as SlSelect).value);
        const item = this.navGroups(event).flatMap(g => g.items).find(i => i.id === id);
        if (item == null || this.isCurrent(item)) {
            return;
        }
        window.location.hash = this.href(event, item);
    };

    private onAction = (e: CustomEvent<{ item: { value: string } }>): void => {
        const event = this.event;
        if (event == null) {
            return;
        }
        const action = e.detail.item.value;
        if (action === 'copy') {
            window.location.hash = `#/events/new?eventName=${encodeURIComponent(event.shortName)}`;
        } else if (action === 'unlist') {
            void this.unlist(event);
        } else if (action === 'delete') {
            void this.deleteDialog.show(event.id, event.shortName);
        }
    };

    private onDownload = (e: CustomEvent<{ item: { value: string } }>): void => {
        const event = this.event;
        if (event == null) {
            return;
        }
        const eventName = encodeURIComponent(event.shortName);
        const download = e.detail.item.value;
        if (download === 'attendees' || download === 'waiting-list') {
            void this.exportDialog.show(event.shortName, download);
        } else if (download === 'sponsor-scan') {
            window.open(`/admin/api/events/${eventName}/sponsor-scan/export`);
        } else if (download === 'documents-pdf') {
            void this.downloadBillingDocuments(event, `/admin/api/events/${eventName}/all-documents`);
        } else if (download === 'documents-xls') {
            void this.downloadBillingDocuments(event, `/admin/api/events/${eventName}/all-documents-xls`);
        }
    };

    private async downloadBillingDocuments(event: AlfioEvent, url: string): Promise<void> {
        try {
            const count = await EventService.countBillingDocuments(event.shortName);
            if (count > 0) {
                window.open(url);
            } else {
                dispatchFeedback({type: 'neutral', message: 'This event has no billing documents yet.'}, this);
            }
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, 'Failed to download the billing documents')}, this);
        }
    }

    /**
     * Removes the event from the public event list: it stays on sale for anyone who has its link
     */
    private async unlist(event: AlfioEvent): Promise<void> {
        this.changingStatus = true;
        try {
            await EventService.updateStatus(event.id, false);
            dispatchFeedback({type: 'success', message: 'The event has been removed from the event list. Anyone with the link can still buy tickets.'}, this);
            dispatchEventChanged(event.shortName, this);
            void this.load();
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, 'Failed to change the event status')}, this);
        } finally {
            this.changingStatus = false;
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-event-sidebar': EventSidebar;
    }
}
