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
import { adminRoute, adminUrl, navigate, navigateToUrl, ROUTE_CHANGED_EVENT } from '../../admin-router';
import { t, tHtml } from '../../service/i18n';

interface NavItem {
    id: string;
    label: string;
    icon: string;
    // path below /admin/events/{eventName}/
    path: string;
    // the pages, below /admin/events/{eventName}/, where the item is the current one
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

// /events/{eventName}/{page}, below /admin
const EVENT_ROUTE = /^\/events\/([^/?]+)\/?(.*)$/;

/**
 * The menu of the event pages (/admin/events/{eventName}/…): the event name and status, the main actions, and the
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
    // the page below /admin/events/{eventName}/, e.g. "reservations/"
    @state() private route = '';
    @state() private changingStatus = false;

    // the dialogs live in the body: the sticky column of the menu would trap their overlay below the page
    private readonly exportDialog: EventExportDialog = document.createElement('alfio-event-export-dialog');
    private readonly deleteDialog: DeleteEventDialog = document.createElement('alfio-delete-event-dialog');
    private readonly linkedApplicationDialog: LinkedApplicationDialog = document.createElement('alfio-linked-application-dialog');

    // discards the answers of a previous event
    private request = 0;

    static readonly styles = [
        badges,
        css`
            :host {
                display: block;
                height: 100%;
                box-sizing: border-box;
            }
            /* the column of the layout (<alfio-event-layout>) sticks below the navbar, whose height the shell
               publishes as --admin-navbar-height: the menu scrolls within the height left */
            .sidebar {
                max-height: calc(100vh - var(--admin-navbar-height, 0px));
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
                .event-name {
                    margin: 0;
                }
                /* the actions on one row: "Create reservation" takes the space left by the menus */
                .actions {
                    flex-wrap: nowrap;
                }
                .actions .create-reservation {
                    flex: 1 1 auto;
                    min-width: 0;
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
            /* on a phone, Download shows its icon only */
            @media (max-width: 480px) {
                .download-label {
                    position: absolute;
                    width: 1px;
                    height: 1px;
                    overflow: hidden;
                    clip: rect(0, 0, 0, 0);
                    white-space: nowrap;
                }
            }
        `,
    ];

    connectedCallback(): void {
        super.connectedCallback();
        window.addEventListener(ROUTE_CHANGED_EVENT, this.onRouteChange);
        window.addEventListener(EVENT_CHANGED_EVENT, this.onEventChanged);
        document.body.append(this.exportDialog, this.deleteDialog, this.linkedApplicationDialog);
        this.onRouteChange();
        void this.loadVersion();
    }

    disconnectedCallback(): void {
        this.exportDialog.remove();
        this.deleteDialog.remove();
        this.linkedApplicationDialog.remove();
        window.removeEventListener(ROUTE_CHANGED_EVENT, this.onRouteChange);
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
                    <p class="event-name"><a href=${adminUrl(`/events/${event.shortName}/detail`)}>${event.displayName}</a></p>
                </header>
                ${this.renderActions(event)}
                ${this.renderGoTo(groups)}
                <nav aria-label=${t('admin.page-title.event')}>
                    ${repeat(groups, g => g.label, g => this.renderGroup(event, g))}
                    ${this.renderLinkedApplications(event)}
                </nav>
                <footer>
                    <p>${tHtml('admin.event-menu.powered-by', html`<a href="https://alf.io" target="_blank" rel="noopener">alf.io</a>`)}${when(this.version !== '', () => html` v${this.version}`, () => nothing)}</p>
                    <p>
                        <a href="https://github.com/alfio-event/alf.io/issues" target="_blank" rel="noopener">${t('admin.event-menu.report-an-issue')}</a>
                        ·
                        <a href="https://github.com/alfio-event/alf.io/discussions" target="_blank" rel="noopener">${t('admin.event-menu.ask-for-help')}</a>
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
                               href=${adminUrl(`/events/${event.shortName}/reservation/new`)}>
                        <sl-icon slot="prefix" name="plus-circle"></sl-icon>
                        ${t('admin.event.menu.create-reservation')}
                    </sl-button>
                `, () => nothing)}
                ${when(owner, () => html`
                    <sl-dropdown id="event-actions" hoist>
                        <sl-button slot="trigger" caret>${t('admin.event.menu.actions')}</sl-button>
                        <sl-menu @sl-select=${this.onAction}>
                            <sl-menu-item value="copy">
                                <sl-icon slot="prefix" name="copy"></sl-icon>
                                ${t('admin.event.menu.copy-this-event')}
                            </sl-menu-item>
                            ${when(event.status === 'PUBLIC' && !event.expired, () => html`
                                <sl-menu-item value="unlist" ?disabled=${this.changingStatus}>
                                    <sl-icon slot="prefix" name="eye-slash"></sl-icon>
                                    ${t('admin.event-menu.unlist')}
                                </sl-menu-item>
                            `, () => nothing)}
                            <sl-divider></sl-divider>
                            <sl-menu-item value="delete" class="delete-event">
                                <sl-icon slot="prefix" name="trash"></sl-icon>
                                ${t('admin.event-menu.delete')}
                            </sl-menu-item>
                        </sl-menu>
                    </sl-dropdown>
                    <sl-dropdown id="event-downloads" hoist>
                        <sl-button slot="trigger" caret>
                            <sl-icon slot="prefix" name="download"></sl-icon>
                            <span class="download-label">${t('admin.event.menu.download')}</span>
                        </sl-button>
                        <sl-menu @sl-select=${this.onDownload}>
                            <sl-menu-item value="attendees">${t('admin.event-menu.download.attendees')}</sl-menu-item>
                            <sl-menu-item value="waiting-list">${t('admin.event-menu.download.waiting-list')}</sl-menu-item>
                            <sl-menu-item value="sponsor-scan">${t('admin.event-menu.download.sponsor-scans')}</sl-menu-item>
                            <sl-divider></sl-divider>
                            <sl-menu-item value="documents-pdf">${t('admin.event-menu.download.billing-pdf')}</sl-menu-item>
                            <sl-menu-item value="documents-xls">${t('admin.event-menu.download.billing-excel')}</sl-menu-item>
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
            <sl-select class="go-to" id="event-go-to" label=${t('admin.event-menu.go-to')} hoist placeholder=${t('admin.event-menu.select-page')}
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
                <p class="nav-group-label">${t('admin.event-menu.linked-applications')}</p>
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
        const overview: NavItem = { id: 'overview', label: t('admin.page-title.overview'), icon: 'speedometer2', path: 'detail', current: /^(detail|edit|category\/)/ };
        const checkIn: NavItem = { id: 'check-in', label: t('admin.page-title.check-in'), icon: 'qr-code-scan', path: 'check-in', current: /^check-in/ };
        if (!event.visibleForCurrentUser) {
            return [{ label: '', items: [overview, checkIn] }];
        }
        const paid = !event.freeOfCharge;
        const offlinePayments = paid && (event.allowedPaymentProxies ?? []).some(p => p === 'OFFLINE' || p === 'CUSTOM_OFFLINE');
        const sales: NavItem[] = [
            { id: 'reservations', label: t('admin.event.menu.reservations'), icon: 'ticket-perforated', path: 'reservations/', current: /^(reservations|reservation\/(?!new|import))/ },
        ];
        if (paid) {
            sales.push({ id: 'payments', label: t('admin.page-title.payments'), icon: 'cash-stack', path: 'transactions/', current: /^transactions/ });
        }
        if (offlinePayments) {
            sales.push({ id: 'pending-payments', label: t('admin.page-title.pending-payments'), icon: 'hourglass-split', path: 'pending-payments/', current: /^pending-payments/, count: 'pending-payments' });
        }
        sales.push({ id: 'waiting-list', label: t('admin.event.menu.waiting-list'), icon: 'people', path: 'waiting-queue', current: /^waiting-queue/, count: 'waiting-list' });

        const attendees: NavItem[] = [
            checkIn,
            { id: 'compose-message', label: t('admin.event.menu.compose-message'), icon: 'envelope', path: 'compose-custom-message', current: /^compose-custom-message/ },
            { id: 'email-log', label: t('admin.event.menu.email-log'), icon: 'send', path: 'email-log', current: /^email-log/ },
        ];
        if (!event.expired) {
            attendees.push({ id: 'import-attendees', label: t('admin.event.menu.import-attendees'), icon: 'upload', path: 'reservation/import', current: /^reservation\/import/ });
        }
        attendees.push({ id: 'polls', label: t('admin.event.menu.polls'), icon: 'bar-chart', path: 'polls/list', current: /^polls/ });

        let codesLabel = t('admin.page-title.promo-codes');
        if (this.usePartnerCode) {
            codesLabel = t('admin.event-menu.partner-codes');
        }
        const setup: NavItem[] = [
            { id: 'promo-codes', label: codesLabel, icon: 'percent', path: 'promo-codes', current: /^promo-codes/ },
            { id: 'data-to-collect', label: t('admin.page-title.data-to-collect'), icon: 'person-lines-fill', path: 'attendee-data-to-collect', current: /^attendee-data-to-collect/ },
        ];
        if (paid) {
            setup.push({ id: 'additional-options', label: t('admin.page-title.additional-options'), icon: 'bag-plus', path: 'additional-services', current: /^additional-services/ });
            setup.push({ id: 'donations', label: t('admin.page-title.donation-options'), icon: 'gift', path: 'donations', current: /^donations/ });
        }
        setup.push({ id: 'templates', label: t('admin.page-title.templates'), icon: 'file-earmark-text', path: 'show-resources', current: /^show-resources/ });
        setup.push({ id: 'configuration', label: t('admin.menu.settings'), icon: 'gear', path: 'configuration', current: /^configuration/ });

        return [
            { label: '', items: [overview] },
            { label: t('admin.event-menu.group.sales'), items: sales },
            { label: t('admin.event-menu.group.attendees'), items: attendees },
            { label: t('admin.event-menu.group.setup'), items: setup },
        ];
    }

    private href(event: AlfioEvent, item: NavItem): string {
        return adminUrl(`/events/${event.shortName}/${item.path}`);
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
                this.loadError = errorMessage(error, t('admin.load-error.event'));
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

    private onRouteChange = (): void => {
        const match = EVENT_ROUTE.exec(adminRoute());
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
        navigateToUrl(this.href(event, item));
    };

    private onAction = (e: CustomEvent<{ item: { value: string } }>): void => {
        const event = this.event;
        if (event == null) {
            return;
        }
        const action = e.detail.item.value;
        if (action === 'copy') {
            navigate(`/events/new?eventName=${encodeURIComponent(event.shortName)}`);
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
                dispatchFeedback({type: 'neutral', message: t('admin.event-menu.no-billing-documents')}, this);
            }
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, t('admin.event-menu.download-billing-failed'))}, this);
        }
    }

    /**
     * Removes the event from the public event list: it stays on sale for anyone who has its link
     */
    private async unlist(event: AlfioEvent): Promise<void> {
        this.changingStatus = true;
        try {
            await EventService.updateStatus(event.id, false);
            dispatchFeedback({type: 'success', message: t('admin.event-menu.unlisted')}, this);
            dispatchEventChanged(event.shortName, this);
            void this.load();
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, t('admin.event-menu.status-change-failed'))}, this);
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
