import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import type {SlDialog, SlRequestCloseEvent} from '@shoelace-style/shoelace';
import {AlfioEvent, GroupLink} from '../../model/event.ts';
import {Organization} from '../../model/organization.ts';
import {ContentLanguage, LocalizedContent} from '../../model/purchase-context.ts';
import {SubscriptionWithStatistics} from '../../model/subscription-descriptor.ts';
import {dispatchEventChanged, EVENT_CHANGED_EVENT, EventService, isEventChangedElsewhere} from '../../service/event.ts';
import {ConfigurationService} from '../../service/configuration.ts';
import {GroupService} from '../../service/group.ts';
import {LocalizationService} from '../../service/localization.ts';
import {LocationService} from '../../service/location.ts';
import {describePaymentProxy, PaymentProxyService} from '../../service/payment-proxy.ts';
import type {PaymentProviderOption} from '../../service/payment-proxy.ts';
import {errorMessage, fetchJson} from '../../service/helpers.ts';
import {formatAmount} from '../../service/reservation-format.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {sectionCard} from '../../components/section-card.ts';
import {taskContent} from '../../components/task-content.ts';
import {detailList} from '../../components/detail-list.ts';
import {statusBadge} from '../../components/status-badge.ts';
import type {StatusBadgeConfig} from '../../components/status-badge.ts';
import '../../components/format-date.ts';
import '../../components/localized-markdown.ts';
import './event-statistics.ts';
import './category-list.ts';
import './online-settings.ts';
import '../event-create/event-create.ts';
import type {EventEditSection} from '../event-create/event-create.ts';
import {
    badges,
    base,
    detailList as detailListStyles,
    dialog,
    modernLayout,
    purchaseContextListPage,
    row,
    textColors,
} from '../../styles.ts';

import { adminConfig } from '../../admin-config';
import { adminUrl } from '../../admin-router';
interface EventDetailData {
    event: AlfioEvent;
    organization: Organization;
    baseUrl: string;
    mapUrl: string | null;
    // all the languages of the instance
    languages: ContentLanguage[];
    descriptionMaxLength: number;
    paymentProxies: PaymentProviderOption[];
}

interface WaitingQueueStatus {
    active: boolean;
    paused: boolean;
}

function formatLabel(format: string): string {
    if (format === 'IN_PERSON') {
        return 'In person';
    }
    if (format === 'HYBRID') {
        return 'Hybrid';
    }
    return 'Online';
}

/**
 * The status of an event, as shown next to its name. A draft event is on sale for anyone who has
 * its link: it is just not listed on the public event list.
 */
export function eventStatusBadge(status: string): StatusBadgeConfig {
    if (status === 'PUBLIC') {
        return {label: 'Live', variant: 'success', icon: 'eye'};
    }
    if (status === 'DISABLED') {
        return {label: 'Disabled', variant: 'neutral', icon: 'slash-circle'};
    }
    return {label: 'Unlisted', variant: 'neutral', icon: 'eye-slash'};
}

function hasText(value: string | null | undefined): value is string {
    return value != null && value.trim() !== '';
}

function firstText(content: LocalizedContent | null | undefined): string {
    return Object.values(content ?? {}).find((text) => hasText(text)) ?? '';
}

/**
 * The event detail page (/admin/events/{eventName}/detail): headline, status alerts, statistics, event
 * details, seats and payment info, online settings and the ticket categories. Editing reuses the
 * event creation form (/admin/events/{eventName}/edit renders <alfio-event-create> in edit mode).
 */
@customElement('alfio-event-detail')
export class EventDetail extends LitElement {

    @property({type: String, attribute: 'data-event-name'})
    eventName = '';

    @state() private waitingQueueStatus: WaitingQueueStatus | null = null;

    @state() private justPublished = false;

    @state() private changingStatus = false;

    // the part of the event being edited in the section dialog
    @state() private editedSection: EventEditSection | null = null;

    @query('#section-dialog') private sectionDialog!: SlDialog;

    private readonly loadTask = new Task(this, {
        task: async (): Promise<EventDetailData> => {
            const {event, organization} = await EventService.load(this.eventName);
            let loadMapUrl: Promise<string | null> = Promise.resolve(null);
            if (hasText(event.latitude) && hasText(event.longitude)) {
                loadMapUrl = LocationService.staticMapImage(event.latitude, event.longitude).catch(() => null);
            }
            const [groupLinks, groups, subscriptions, languages, instanceSettings, paymentProxies, mapUrl] = await Promise.all([
                EventService.loadGroupLinks(this.eventName).catch(() => [] as GroupLink[]),
                GroupService.loadGroups(organization.id).catch(() => []),
                fetchJson<SubscriptionWithStatistics[]>(`/admin/api/organization/${organization.id}/subscription/list`).catch(() => []),
                new LocalizationService().getEventsSupportedLanguages(),
                ConfigurationService.loadInstanceSettings(),
                PaymentProxyService.providers(organization.id).catch(() => [] as PaymentProviderOption[]),
                loadMapUrl,
            ]);
            // BASE_URL is embedded in the admin page (admin-index.ms)
            const baseUrl = adminConfig().baseUrl.replace(/\/+$/, '');
            const groupNameById = new Map(groups.map((group) => [group.id, group.name]));
            for (const link of groupLinks) {
                link.groupName = groupNameById.get(link.groupId) ?? '';
            }
            event.attendeesList = groupLinks.find((link) => link.ticketCategoryId == null) ?? null;
            for (const category of event.ticketCategories) {
                category.attendeesList = groupLinks.find((link) => link.ticketCategoryId === category.id) ?? null;
            }
            const linked = new Set(event.linkedSubscriptions ?? []);
            event.subscriptionDescriptors = subscriptions
                .filter((subscription) => linked.has(subscription.descriptor.id))
                .map((subscription) => subscription.descriptor);
            void this.loadWaitingQueueStatus();
            return {
                event,
                organization,
                baseUrl,
                mapUrl,
                languages,
                descriptionMaxLength: instanceSettings.descriptionMaxLength,
                paymentProxies,
            };
        },
        args: () => [this.eventName] as const,
    });

    static readonly styles = [
        base,
        badges,
        detailListStyles,
        dialog,
        modernLayout,
        purchaseContextListPage,
        row,
        textColors,
        css`
            :host {
                display: block;
                --alfio-section-body-padding: var(--sl-spacing-medium);
            }
            .page-title-row {
                flex-wrap: wrap;
                row-gap: var(--sl-spacing-x-small);
            }
            .page-actions {
                margin-inline-start: auto;
                display: flex;
                flex-wrap: wrap;
                gap: var(--sl-spacing-small);
            }
            .page-alerts {
                display: grid;
                gap: var(--sl-spacing-small);
                margin-bottom: var(--sl-spacing-medium);
            }
            .alert-content {
                display: flex;
                flex-wrap: wrap;
                align-items: center;
                justify-content: space-between;
                gap: var(--sl-spacing-x-small) var(--sl-spacing-medium);
            }
            .alert-content a {
                overflow-wrap: anywhere;
            }
            .event-summary {
                display: grid;
                grid-template-columns: minmax(0, 1fr);
                gap: var(--sl-spacing-large);
            }
            @media (min-width: 768px) {
                .event-summary {
                    grid-template-columns: 8rem minmax(0, 1fr);
                }
            }
            .event-logo img {
                display: block;
                max-width: 100%;
                max-height: 8rem;
                object-fit: contain;
            }
            .event-logo a {
                overflow-wrap: anywhere;
                font-size: var(--sl-font-size-small);
            }
            .list-title {
                display: block;
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-small);
                font-weight: var(--sl-font-weight-semibold);
                margin-bottom: var(--sl-spacing-x-small);
            }
            .subscription-list {
                list-style: none;
                padding: 0;
                margin: 0;
                display: grid;
                gap: var(--sl-spacing-x-small);
            }
            .subscription-list li {
                border-bottom: 1px solid var(--sl-color-gray-200);
                padding-bottom: var(--sl-spacing-x-small);
            }
            .subscription-list li:last-child {
                border-bottom: none;
                padding-bottom: 0;
            }
            /* the first lines of the description: the full text is on the subscription page */
            .subscription-description {
                display: -webkit-box;
                -webkit-line-clamp: 2;
                -webkit-box-orient: vertical;
                overflow: hidden;
                font-size: var(--sl-font-size-small);
            }
            .unavailable-payment-methods {
                margin-top: var(--sl-spacing-medium);
            }
            .seats-body {
                row-gap: var(--sl-spacing-large);
            }
            #section-dialog {
                --alfio-dialog-max-width: 60rem;
            }
        `,
    ];

    private async loadWaitingQueueStatus(): Promise<void> {
        try {
            const status = await EventService.loadWaitingQueueStatus(this.eventName);
            this.waitingQueueStatus = {active: status.active === true, paused: status.paused === true};
        } catch {
            this.waitingQueueStatus = null;
        }
    }

    private async toggleWaitingQueuePaused(): Promise<void> {
        if (this.waitingQueueStatus == null) {
            return;
        }
        const paused = !this.waitingQueueStatus.paused;
        try {
            await EventService.setWaitingQueuePaused(this.eventName, paused);
            this.waitingQueueStatus = {active: true, paused};
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, 'Failed to update the waiting list')}, this);
        }
    }

    /**
     * Lists the event on the public event list (publish = true), or removes it from the list
     */
    private async changeStatus(publish: boolean): Promise<void> {
        const data = this.loadTask.value;
        if (data == null || this.changingStatus) {
            return;
        }
        this.changingStatus = true;
        try {
            const info = await fetchJson<{ isDemoMode: boolean }>('/admin/api/utils/alfio/info');
            if (publish && info.isDemoMode) {
                dispatchFeedback({type: 'danger', message: 'Events cannot be published in demo mode, for privacy reasons'}, this);
                return;
            }
            await EventService.updateStatus(data.event.id, publish);
            // the alert that confirms the publication offers an Undo
            this.justPublished = publish;
            void this.loadTask.run([this.eventName]);
            // the event menu shows the status too
            dispatchEventChanged(this.eventName, this);
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, 'Failed to change the event status')}, this);
        } finally {
            this.changingStatus = false;
        }
    }

    connectedCallback(): void {
        super.connectedCallback();
        window.addEventListener(EVENT_CHANGED_EVENT, this.onEventChanged);
    }

    disconnectedCallback(): void {
        window.removeEventListener(EVENT_CHANGED_EVENT, this.onEventChanged);
        super.disconnectedCallback();
    }

    // the event menu has changed the event (e.g. removed it from the event list)
    private onEventChanged = (e: Event): void => {
        if (isEventChangedElsewhere(e, this.eventName, this)) {
            this.justPublished = false;
            void this.loadTask.run([this.eventName]);
        }
    };

    render(): TemplateResult {
        return html`
            <div class="container">
                ${taskContent(this.loadTask, this.loadTask.value ?? null,
                    'Failed to load the event. Please try again.',
                    (data) => this.renderPage(data))}
            </div>
        `;
    }

    private renderPage(data: EventDetailData): TemplateResult {
        const event = data.event;
        const online = event.format === 'ONLINE' || event.format === 'HYBRID';
        return html`
            <div class="page-title-row secondary">
                <h1>${event.displayName}</h1>
                ${statusBadge(event.status, eventStatusBadge(event.status), 'event-status')}
                ${when(event.expired, () => html`<sl-badge variant="neutral" pill>Expired</sl-badge>`, () => nothing)}
            </div>
            <hr class="page-separator">
            ${this.renderAlerts(data)}
            ${when(event.displayStatistics, () => html`<alfio-event-statistics .event=${event}></alfio-event-statistics>`, () => nothing)}
            ${this.renderEventDetailsCard(data)}
            ${when(hasText(firstText(event.description)) || event.visibleForCurrentUser, () => sectionCard({
                icon: 'card-text',
                title: 'Description',
                actions: this.editButton(event, 'description', 'edit-description'),
            }, html`
                ${when(hasText(firstText(event.description)),
                    () => html`<alfio-localized-markdown class="event-description" .value=${event.description} .languages=${event.contentLanguages ?? []}></alfio-localized-markdown>`,
                    () => html`<p class="text-muted">No description yet.</p>`)}
            `), () => nothing)}
            ${this.renderSeatsCard(data)}
            ${when(online, () => html`
                <alfio-event-online-settings .event=${event} .languages=${data.languages}
                                             @alfio-event-reload=${this.reload}></alfio-event-online-settings>
            `, () => nothing)}
            <alfio-event-categories .event=${event}
                                    .languages=${data.languages}
                                    .baseUrl=${data.baseUrl}
                                    .descriptionMaxLength=${data.descriptionMaxLength}
                                    @alfio-event-reload=${this.reload}></alfio-event-categories>
            ${this.renderSectionDialog(event)}
        `;
    }

    /**
     * The Edit button of a card, which edits that part of the event in the section dialog
     */
    private editButton(event: AlfioEvent, section: EventEditSection, id: string): TemplateResult | undefined {
        if (!event.visibleForCurrentUser) {
            return undefined;
        }
        return html`
            <sl-button id=${id} variant="default" size="small" @click=${() => void this.editSection(section)}>
                <sl-icon slot="prefix" name="pencil"></sl-icon>
                Edit
            </sl-button>
        `;
    }

    private renderSectionDialog(event: AlfioEvent): TemplateResult {
        let label = 'Edit event details';
        if (this.editedSection === 'description') {
            label = 'Edit description';
        } else if (this.editedSection === 'seats') {
            label = 'Edit seats and payment info';
        }
        // the form is created at every opening, so that it always starts from the saved event
        return html`
            <sl-dialog id="section-dialog" class="responsive-dialog" label=${label}
                       @sl-request-close=${this.onSectionDialogRequestClose}
                       @sl-after-hide=${(e: Event) => { if (e.target === this.sectionDialog) { this.editedSection = null; } }}>
                ${when(this.editedSection != null, () => html`
                    <alfio-event-create data-event-name=${event.shortName} data-section=${this.editedSection!}
                                        @alfio-event-section-saved=${this.onSectionSaved}
                                        @alfio-event-section-cancelled=${() => void this.sectionDialog.hide()}></alfio-event-create>
                `, () => nothing)}
            </sl-dialog>
        `;
    }

    private async editSection(section: EventEditSection): Promise<void> {
        this.editedSection = section;
        await this.updateComplete;
        await this.sectionDialog.show();
    }

    // a click outside the dialog doesn't discard the changes
    private onSectionDialogRequestClose = (e: SlRequestCloseEvent): void => {
        if (e.target === this.sectionDialog && e.detail.source === 'overlay') {
            e.preventDefault();
        }
    };

    private onSectionSaved = async (): Promise<void> => {
        await this.sectionDialog.hide();
        this.reload();
    };

    private reload = (): void => {
        void this.loadTask.run([this.eventName]);
    };

    /**
     * The alerts below the title: only what needs the organizer's attention, or explains how the event is sold
     */
    private renderAlerts(data: EventDetailData): TemplateResult {
        const event = data.event;
        const publicUrl = `${data.baseUrl}/event/${event.shortName}`;
        const unlisted = event.status === 'DRAFT' && !event.expired;
        const waitingQueue = this.waitingQueueStatus;
        const alerts: TemplateResult[] = [];
        if (unlisted) {
            alerts.push(html`
                <sl-alert open variant="primary" class="unlisted-alert">
                    <sl-icon slot="icon" name="eye-slash"></sl-icon>
                    <div class="alert-content">
                        <span>
                            This event is not listed on the public event list. Anyone with the link can buy tickets:
                            <a href=${publicUrl} target="_blank" rel="noopener">${publicUrl}</a>
                        </span>
                        ${when(event.visibleForCurrentUser, () => html`
                            <sl-button id="publish-now" variant="primary" size="small" ?loading=${this.changingStatus}
                                       @click=${() => void this.changeStatus(true)}>Publish on the event list</sl-button>
                        `, () => nothing)}
                    </div>
                </sl-alert>
            `);
        }
        if (this.justPublished && event.status === 'PUBLIC') {
            alerts.push(html`
                <sl-alert open variant="success" class="activated-alert" closable @sl-after-hide=${() => { this.justPublished = false; }}>
                    <sl-icon slot="icon" name="check2-circle"></sl-icon>
                    <div class="alert-content">
                        <span>This event has been successfully published on the event list.</span>
                        <sl-button variant="default" size="small" class="undo-publish" ?loading=${this.changingStatus}
                                   @click=${() => void this.changeStatus(false)}>Undo</sl-button>
                    </div>
                </sl-alert>
            `);
        }
        if (waitingQueue?.active) {
            alerts.push(this.renderWaitingQueueAlert(waitingQueue.paused));
        }
        if (event.attendeesList != null) {
            alerts.push(html`
                <sl-alert open variant="primary" class="attendees-list-alert">
                    <sl-icon slot="icon" name="person-lock"></sl-icon>
                    <div class="alert-content">
                        <span>
                            Only the members of the group
                            <a href=${adminUrl(`/group/${event.organizationId}/edit/${event.attendeesList.groupId}`)}>${event.attendeesList.groupName}</a>
                            can buy tickets for this event.
                        </span>
                        <sl-button size="small" href=${adminUrl(`/events/${event.shortName}/configuration`)}>
                            <sl-icon slot="prefix" name="wrench"></sl-icon>
                            Configure
                        </sl-button>
                    </div>
                </sl-alert>
            `);
        }
        if (alerts.length === 0) {
            return html`${nothing}`;
        }
        return html`<div class="page-alerts">${alerts}</div>`;
    }

    private renderWaitingQueueAlert(paused: boolean): TemplateResult {
        let variant: 'primary' | 'warning' = 'primary';
        let icon = 'pause';
        let label = 'Pause';
        let message = 'The waiting list is open: people can join it.';
        if (paused) {
            variant = 'warning';
            icon = 'play';
            label = 'Resume';
            message = 'The waiting list is paused: people can\'t join it.';
        }
        return html`
            <sl-alert open variant=${variant} class="waiting-queue-alert">
                <sl-icon slot="icon" name="people"></sl-icon>
                <div class="alert-content">
                    <span>${message}</span>
                    <sl-button variant="default" size="small" class="toggle-waiting-queue" @click=${() => void this.toggleWaitingQueuePaused()}>
                        <sl-icon slot="prefix" name=${icon}></sl-icon>
                        ${label}
                    </sl-button>
                </div>
            </sl-alert>
        `;
    }

    private renderEventDetailsCard(data: EventDetailData): TemplateResult {
        const event = data.event;
        const publicUrl = `${data.baseUrl}/event/${event.shortName}`;
        const languages = (event.contentLanguages ?? []).map((language) => language.displayLanguage).join(', ');
        let location: TemplateResult = html`${event.location}`;
        if (data.mapUrl != null) {
            location = html`<a href=${data.mapUrl} target="_blank" rel="noopener">${event.location}</a>`;
        }
        return sectionCard({icon: 'info-circle', title: 'Event details', actions: this.editButton(event, 'details', 'edit-event')}, html`
            <div class="event-summary">
                <div class="event-logo">
                    ${when(event.fileBlobId != null, () => html`<img src="/file/${event.fileBlobId}" alt="Logo of ${event.displayName}">`, () => nothing)}
                    ${when(event.fileBlobId == null && hasText(event.imageUrl), () => html`<a href=${event.imageUrl!} target="_blank" rel="noopener">${event.imageUrl}</a>`, () => nothing)}
                </div>
                ${detailList([
                    {label: 'Organized by', value: `${data.organization.name} <${data.organization.email}>`},
                    {label: 'Format', value: formatLabel(event.format)},
                    event.format !== 'ONLINE' && hasText(event.location) && {label: 'Location', value: location},
                    {label: 'Time zone', value: event.timeZone},
                    {label: 'Start', value: html`<alfio-format-date date=${event.formattedBegin} time-zone=${event.timeZone}></alfio-format-date>`},
                    {label: 'End', value: html`<alfio-format-date date=${event.formattedEnd} time-zone=${event.timeZone}></alfio-format-date>`},
                    {label: 'Languages', value: languages},
                    {label: 'Public URL', value: html`<a href=${publicUrl} target="_blank" rel="noopener">${publicUrl}</a>`},
                    hasText(event.websiteUrl) && {label: 'Website', value: html`<a href=${event.websiteUrl} target="_blank" rel="noopener">${event.websiteUrl}</a>`},
                    hasText(event.termsAndConditionsUrl) && {label: 'Terms and conditions', value: html`<a href=${event.termsAndConditionsUrl} target="_blank" rel="noopener">${event.termsAndConditionsUrl}</a>`},
                    hasText(event.privacyPolicyUrl) && {label: 'Privacy policy', value: html`<a href=${event.privacyPolicyUrl} target="_blank" rel="noopener">${event.privacyPolicyUrl}</a>`},
                ])}
            </div>
        `);
    }

    private renderSeatsCard(data: EventDetailData): TemplateResult {
        const event = data.event;
        const currency = event.currency ?? '';
        const unavailable = data.paymentProxies.filter((proxy) => proxy.status === 'ACTIVE'
            && proxy.onlyForCurrency != null && proxy.onlyForCurrency.length > 0 && !proxy.onlyForCurrency.includes(currency)
            && !proxy.onlyForCurrency.includes('*'));
        const describe = (proxy: string): string => {
            const provider = data.paymentProxies.find((p) => p.paymentProxy === proxy);
            if (provider != null) {
                return provider.description;
            }
            return describePaymentProxy(proxy);
        };
        const paymentMethods = (event.allowedPaymentProxies ?? []).map(describe).join(', ');
        let ticketPrice = 'Free of charge';
        if (!event.freeOfCharge) {
            ticketPrice = formatAmount(event.regularPrice, currency);
        }
        let vatLabel = `${event.vatPercentage ?? 0}%`;
        if (event.vatIncluded) {
            vatLabel += ' (included in the price)';
        }
        const subscriptions = event.subscriptionDescriptors ?? [];
        return sectionCard({icon: 'cash', title: 'Seats and payment info', actions: this.editButton(event, 'seats', 'edit-seats')}, html`
            <div class="row seats-body" style="--alfio-row-cols: 2">
                <div>
                    ${detailList([
                        {label: 'Ticket price', value: ticketPrice},
                        !event.freeOfCharge && {label: 'VAT', value: vatLabel},
                        !event.freeOfCharge && {label: 'Final price', value: formatAmount(event.finalPrice, currency)},
                        {label: 'Max tickets', value: String(event.availableSeats ?? 0)},
                        !event.freeOfCharge && {label: 'Payment methods', value: paymentMethods},
                    ])}
                    ${when(!event.freeOfCharge && unavailable.length > 0, () => html`
                        <sl-alert open variant="warning" class="unavailable-payment-methods">
                            <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                            These payment methods don't accept ${currency}:
                            ${unavailable.map((proxy) => proxy.description).join(', ')}.
                            Check the <a href=${adminUrl(`/configuration/organization/${event.organizationId}`)}>organization configuration</a>.
                        </sl-alert>
                    `, () => nothing)}
                </div>
                ${when(subscriptions.length > 0, () => html`
                    <div>
                        <span class="list-title">Subscriptions accepted</span>
                        <ul class="subscription-list">
                            ${repeat(subscriptions, (descriptor) => descriptor.id, (descriptor) => html`
                                <li>
                                    <strong>${firstText(descriptor.titleAsText ?? descriptor.title) || descriptor.id}</strong>
                                    <div class="subscription-description text-muted">${firstText(descriptor.descriptionAsText ?? descriptor.description)}</div>
                                </li>
                            `)}
                        </ul>
                    </div>
                `, () => nothing)}
            </div>
        `);
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-event-detail': EventDetail;
    }
}
