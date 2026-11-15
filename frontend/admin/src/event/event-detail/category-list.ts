import {css, html, LitElement, nothing, PropertyValues, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import type {SlInput, SlSelect} from '@shoelace-style/shoelace';
import {AlfioEvent, TicketCategory} from '../../model/event.ts';
import {ContentLanguage, LocalizedContent} from '../../model/purchase-context.ts';
import {EventFormat} from '../../model/event-modification.ts';
import {SpecialPrice} from '../../model/special-price.ts';
import {EventService} from '../../service/event.ts';
import {errorMessage} from '../../service/helpers.ts';
import {formatAmount} from '../../service/reservation-format.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {ConfirmationDialogService} from '../../service/confirmation-dialog.ts';
import {sectionCard} from '../../components/section-card.ts';
import {emptyState} from '../../components/empty-state.ts';
import '../../components/format-date.ts';
import '../../components/localized-markdown.ts';
import '../event-create/category-dialog.ts';
import type {CategoryDialogContext, CategoryDraft, EventCategoryDialog} from '../event-create/category-dialog.ts';
import {categoryDraftToPayload, newCategoryDraft, wallClockToDateTimeLocal} from '../event-create/category-dialog.ts';
import './online-settings.ts';
import {allocationSegments} from './ticket-allocation.ts';
import {badges, form, modernLayout, modernTable, row, textColors} from '../../styles.ts';

interface Selection {
    active: boolean;
    expired: boolean;
    freeText: string;
}

interface CategoryOrder {
    id: number;
    name: string;
    ordinal: number;
}

/**
 * Maps a category of the loaded event to the draft edited by the category dialog
 */
export function categoryToDraft(category: TicketCategory, key: number): CategoryDraft {
    let ticketAccessType = '';
    if (category.ticketAccessType != null) {
        ticketAccessType = category.ticketAccessType;
    }
    let description: LocalizedContent = {};
    if (category.description != null) {
        description = {...category.description};
    }
    let maxTickets = '';
    if (category.maxTickets != null) {
        maxTickets = String(category.maxTickets);
    }
    let price = '';
    if (category.price != null) {
        price = String(category.price);
    }
    return {
        key,
        name: category.name,
        ticketAccessType,
        description,
        tokenGenerationRequested: category.accessRestricted ?? false,
        bounded: category.bounded ?? false,
        maxTickets,
        inception: wallClockToDateTimeLocal(category.formattedInception),
        expiration: wallClockToDateTimeLocal(category.formattedExpiration),
        price,
        code: category.code ?? '',
        validCheckInFrom: wallClockToDateTimeLocal(category.formattedValidCheckInFrom),
        validCheckInTo: wallClockToDateTimeLocal(category.formattedValidCheckInTo),
        ticketValidityStart: wallClockToDateTimeLocal(category.formattedTicketValidityStart),
        ticketValidityEnd: wallClockToDateTimeLocal(category.formattedTicketValidityEnd),
        ordinal: category.ordinal ?? key,
    };
}

function hasText(value: string | null | undefined): boolean {
    return value != null && value.trim() !== '';
}

function plural(count: number, singular: string, pluralForm: string): string {
    if (count === 1) {
        return `${count} ${singular}`;
    }
    return `${count} ${pluralForm}`;
}

/**
 * The categories of the event detail page: filter toolbar, one card per category with its ticket
 * bar and actions, add/edit through the same category dialog used by the event creation form,
 * rearrange, orphan handling, the access codes of the restricted categories and their online
 * settings.
 *
 * Dispatches a bubbling "alfio-event-reload" event when the categories have changed and the
 * event should be loaded again.
 */
@customElement('alfio-event-categories')
export class EventCategories extends LitElement {

    @property({attribute: false}) event!: AlfioEvent;

    // all the languages of the instance
    @property({attribute: false}) languages: ContentLanguage[] = [];

    @property({attribute: false}) baseUrl = '';

    @property({attribute: false}) descriptionMaxLength = 4096;

    @state() private selection: Selection = {active: true, expired: false, freeText: ''};

    @state() private rearranging = false;

    @state() private draftOrder: CategoryOrder[] = [];

    @state() private orphanTarget: Record<number, string> = {};

    @state() private saving = false;

    private nextKey = 1;

    static readonly styles = [
        badges,
        form,
        modernLayout,
        modernTable,
        row,
        textColors,
        css`
            :host {
                display: block;
                margin-top: var(--sl-spacing-x-large);
            }
            .categories-heading {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-small);
                margin-bottom: var(--sl-spacing-medium);
            }
            .categories-heading h2 {
                margin: 0;
                font-size: var(--sl-font-size-x-large);
            }
            .filter-toolbar {
                align-items: center;
            }
            .filter-left {
                align-items: center;
            }
            .selection-count {
                margin-inline-start: var(--sl-spacing-2x-small);
            }
            /* on a narrow screen the actions move below the name, instead of squeezing it */
            .category-card .section-header {
                flex-wrap: wrap;
                row-gap: var(--sl-spacing-x-small);
            }
            .category-card .section-actions {
                flex-wrap: nowrap;
                align-items: center;
            }
            .category-card .section-body {
                display: grid;
                gap: var(--sl-spacing-medium);
                padding: var(--sl-spacing-medium);
            }
            /* a category with tickets to fix */
            .category-card.needs-attention {
                border-color: var(--sl-color-warning-300);
            }
            .category-card.needs-attention .section-header {
                background: var(--sl-color-warning-50);
                border-bottom-color: var(--sl-color-warning-200);
            }
            .category-card.needs-attention .section-header > sl-icon {
                color: var(--sl-color-warning-700);
            }
            .category-badges {
                display: inline-flex;
                flex-wrap: wrap;
                align-items: center;
                gap: var(--sl-spacing-2x-small);
            }
            .category-badges sl-icon {
                margin-inline-end: var(--sl-spacing-3x-small);
            }
            .progress-track {
                display: flex;
                height: var(--sl-spacing-small);
                border-radius: var(--sl-border-radius-pill);
                overflow: hidden;
                background: var(--sl-color-gray-100);
            }
            .progress-segment {
                height: 100%;
            }
            .progress-legend {
                display: flex;
                flex-wrap: wrap;
                gap: var(--sl-spacing-2x-small) var(--sl-spacing-medium);
                font-size: var(--sl-font-size-small);
                color: var(--sl-color-gray-600);
                margin-top: var(--sl-spacing-x-small);
            }
            .progress-legend .swatch {
                display: inline-block;
                width: 0.8em;
                height: 0.8em;
                border-radius: var(--sl-border-radius-small);
                margin-inline-end: var(--sl-spacing-2x-small);
            }
            .category-facts {
                display: flex;
                flex-wrap: wrap;
                gap: var(--sl-spacing-x-small) var(--sl-spacing-large);
                font-size: var(--sl-font-size-small);
                color: var(--sl-color-gray-700);
            }
            .category-facts .fact {
                display: inline-flex;
                align-items: center;
                gap: var(--sl-spacing-2x-small);
            }
            .category-facts sl-icon {
                color: var(--sl-color-gray-500);
            }
            .alert-content {
                display: flex;
                flex-wrap: wrap;
                align-items: center;
                justify-content: space-between;
                gap: var(--sl-spacing-x-small) var(--sl-spacing-medium);
            }
            .orphan-actions {
                display: flex;
                flex-wrap: wrap;
                align-items: center;
                gap: var(--sl-spacing-small);
                margin-top: var(--sl-spacing-small);
            }
            .orphan-actions sl-select {
                flex: 1 1 14rem;
                max-width: 20rem;
                margin-top: 0;
            }
            .category-details {
                display: grid;
                gap: var(--sl-spacing-x-small);
            }
            sl-details::part(base) {
                border-color: var(--sl-color-gray-200);
            }
            sl-details::part(header) {
                padding: var(--sl-spacing-x-small) var(--sl-spacing-medium);
            }
            sl-details::part(summary) {
                font-weight: var(--sl-font-weight-semibold);
                font-size: var(--sl-font-size-small);
            }
            .tokens-list {
                list-style: none;
                padding: 0;
                margin: var(--sl-spacing-2x-small) 0 0;
                display: flex;
                flex-wrap: wrap;
                gap: var(--sl-spacing-2x-small) var(--sl-spacing-medium);
                font-family: var(--sl-font-mono);
                font-size: var(--sl-font-size-small);
            }
            .tokens-group + .tokens-group {
                margin-top: var(--sl-spacing-small);
            }
            .meeting-links {
                margin: 0;
                padding-inline-start: var(--sl-spacing-large);
            }
            .meeting-links a {
                overflow-wrap: anywhere;
            }
            .rearrange-list {
                list-style: none;
                padding: 0;
                margin: 0;
                display: grid;
                gap: var(--sl-spacing-2x-small);
            }
            .rearrange-order {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-small);
                padding: var(--sl-spacing-2x-small) var(--sl-spacing-small);
                border-radius: var(--sl-input-border-radius);
            }
            .rearrange-order:nth-child(odd) {
                background: var(--sl-color-gray-50);
            }
            .rearrange-order .name {
                flex: 1;
                min-width: 0;
            }
            .footer {
                margin-top: var(--sl-spacing-medium);
            }
            @media (max-width: 700px) {
                .filter-right {
                    width: 100%;
                }
                .filter-right sl-button {
                    flex: 1;
                }
            }
        `,
    ];

    protected willUpdate(changed: PropertyValues<this>): void {
        if (changed.has('event') && this.event != null) {
            const hasOrphans = this.event.ticketCategories.some((category) => category.containingOrphans);
            this.selection = {active: true, expired: hasOrphans, freeText: ''};
            this.rearranging = false;
            this.orphanTarget = {};
        }
    }

    private reload(): void {
        this.dispatchEvent(new CustomEvent('alfio-event-reload', {bubbles: true, composed: true}));
    }

    private sortedCategories(): TicketCategory[] {
        return this.event.ticketCategories.slice().sort((a, b) => {
            const ordinalA = a.ordinal ?? 0;
            const ordinalB = b.ordinal ?? 0;
            if (ordinalA !== ordinalB) {
                return ordinalA - ordinalB;
            }
            const inceptionA = a.formattedInception ?? '';
            const inceptionB = b.formattedInception ?? '';
            if (inceptionA !== inceptionB) {
                if (inceptionA < inceptionB) {
                    return -1;
                }
                return 1;
            }
            return a.id - b.id;
        });
    }

    private visibleCategories(): TicketCategory[] {
        const selection = this.selection;
        return this.sortedCategories().filter((category) => {
            let matches = (selection.active && !category.expired) || (selection.expired && category.expired);
            if (matches && selection.freeText !== '') {
                matches = category.name.toLowerCase().includes(selection.freeText.toLowerCase());
            }
            return matches;
        });
    }

    private canBeDeleted(category: TicketCategory): boolean {
        return this.event.ticketCategories.length > 1
            && (category.checkedInTickets ?? 0) + (category.soldTickets ?? 0) + (category.pendingTickets ?? 0) === 0;
    }

    private isOnline(category: TicketCategory): boolean {
        return this.event.format === 'ONLINE' || category.ticketAccessType === 'ONLINE';
    }

    // ---------- rendering ----------

    render(): TemplateResult {
        const event = this.event;
        return html`
            <section class="categories" aria-labelledby="categories-title">
                <div class="categories-heading">
                    <h2 id="categories-title">Categories</h2>
                    <sl-badge variant="primary" pill>${event.ticketCategories.length}</sl-badge>
                </div>
                ${when(this.rearranging,
                    () => this.renderRearrange(),
                    () => this.renderCategoryList())}
            </section>
            <alfio-event-category-dialog></alfio-event-category-dialog>
            <alfio-online-settings-dialog></alfio-online-settings-dialog>
        `;
    }

    private renderCategoryList(): TemplateResult {
        const event = this.event;
        const canEdit = event.visibleForCurrentUser;
        const visible = this.visibleCategories();
        const activeCount = event.ticketCategories.filter((category) => !category.expired).length;
        const expiredCount = event.ticketCategories.length - activeCount;
        const showActions = canEdit && (event.addCategoryEnabled || event.ticketCategories.length > 1);
        return html`
            <div class="filter-toolbar">
                <div class="filter-left">
                    <sl-checkbox class="filter-active" ?checked=${this.selection.active}
                                 @sl-change=${(e: Event) => { this.selection = {...this.selection, active: (e.target as HTMLInputElement).checked}; }}>
                        Active <sl-badge variant="neutral" pill class="selection-count">${activeCount}</sl-badge>
                    </sl-checkbox>
                    <sl-checkbox class="filter-expired" ?checked=${this.selection.expired}
                                 @sl-change=${(e: Event) => { this.selection = {...this.selection, expired: (e.target as HTMLInputElement).checked}; }}>
                        Expired <sl-badge variant="neutral" pill class="selection-count">${expiredCount}</sl-badge>
                    </sl-checkbox>
                    <sl-input class="list-search label-hidden" label="Filter categories" type="search" placeholder="Filter by name" clearable
                              .value=${this.selection.freeText}
                              @sl-input=${(e: Event) => { this.selection = {...this.selection, freeText: (e.target as SlInput).value}; }}>
                        <sl-icon slot="prefix" name="search"></sl-icon>
                    </sl-input>
                </div>
                ${when(showActions, () => html`
                    <div class="filter-right">
                        ${when(event.ticketCategories.length > 1, () => html`
                            <sl-button id="rearrange-categories" variant="default" size="large" @click=${this.startRearrange}>
                                <sl-icon slot="prefix" name="arrow-down-up"></sl-icon>
                                Rearrange
                            </sl-button>
                        `, () => nothing)}
                        ${when(event.addCategoryEnabled, () => html`
                            <sl-button id="add-category" variant="success" size="large" @click=${this.addCategory}>
                                <sl-icon slot="prefix" name="plus-circle"></sl-icon>
                                Add category
                            </sl-button>
                        `, () => nothing)}
                    </div>
                `, () => nothing)}
            </div>
            ${when(visible.length === 0,
                () => html`<section class="section-card">${emptyState('No categories match the selected filters')}</section>`,
                () => html`${repeat(visible, (category) => category.id, (category) => this.renderCategory(category))}`)}
        `;
    }

    private renderRearrange(): TemplateResult {
        const order = this.rearrangeOrder();
        return sectionCard({icon: 'arrow-down-up', title: 'Rearrange categories'}, html`
            <p class="text-muted">Move the categories with the arrows: the event page lists them in this order.</p>
            <ol class="rearrange-list">
                ${repeat(order, (entry) => entry.id, (entry, index) => html`
                    <li class="rearrange-order">
                        <strong>${index + 1}.</strong>
                        <span class="name">${entry.name}</span>
                        <sl-icon-button name="arrow-up" label="Move ${entry.name} up" ?disabled=${index === 0}
                                        @click=${() => this.moveCategory(entry.id, -1)}></sl-icon-button>
                        <sl-icon-button name="arrow-down" label="Move ${entry.name} down" ?disabled=${index === order.length - 1}
                                        @click=${() => this.moveCategory(entry.id, 1)}></sl-icon-button>
                    </li>
                `)}
            </ol>
            <div class="footer">
                <sl-divider></sl-divider>
                <div class="row" style="--alfio-row-cols: 3">
                    <sl-button id="cancel-rearrange" variant="default" size="large" @click=${this.cancelRearrange}>Cancel</sl-button>
                    <div></div>
                    <sl-button id="save-order" variant="warning" size="large" ?loading=${this.saving} @click=${this.saveOrder}>Save</sl-button>
                </div>
            </div>
        `);
    }

    private renderCategory(category: TicketCategory): TemplateResult {
        const event = this.event;
        const needsAttention = (category.containingOrphans ?? false) || (category.containingStuckTickets ?? false);
        const badgeRow = html`
            <span class="category-badges">
                ${when(category.expired, () => html`<sl-badge variant="neutral" pill>Expired</sl-badge>`, () => nothing)}
                ${when(category.accessRestricted, () => html`<sl-badge variant="neutral" pill><sl-icon name="lock" aria-hidden="true"></sl-icon>Access code</sl-badge>`, () => nothing)}
                ${when(event.format === 'HYBRID' && category.ticketAccessType === 'ONLINE', () => html`<sl-badge variant="primary" pill>Online</sl-badge>`, () => nothing)}
            </span>
        `;
        let cardClass = 'category-card';
        let icon = 'tag';
        if (needsAttention) {
            cardClass += ' needs-attention';
            icon = 'exclamation-triangle';
        }
        return html`
            <div id="ticket-category-${category.id}">
                ${sectionCard({
                    icon,
                    title: category.name,
                    badge: badgeRow,
                    actions: this.renderCategoryActions(category),
                    className: cardClass,
                }, html`
                    ${when(category.attendeesList != null, () => html`
                        <sl-alert open variant="primary">
                            <sl-icon slot="icon" name="person-lock"></sl-icon>
                            Only the members of the group
                            <a href="#/group/${event.organizationId}/edit/${category.attendeesList!.groupId}">${category.attendeesList!.groupName}</a>
                            can buy tickets of this category.
                        </sl-alert>
                    `, () => nothing)}
                    ${when(category.containingOrphans, () => this.renderOrphanWarning(category), () => nothing)}
                    ${when(category.containingStuckTickets, () => html`
                        <sl-alert open variant="warning">
                            <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                            Some tickets of this category belong to reservations in an unknown state, usually after
                            an error of the payment provider.
                            <a href="#/events/${event.shortName}/category/${category.id}/tickets">Fix them</a>
                        </sl-alert>
                    `, () => nothing)}
                    ${this.renderProgress(category)}
                    ${this.renderFacts(category)}
                    ${this.renderDetails(category)}
                `)}
            </div>
        `;
    }

    private renderCategoryActions(category: TicketCategory): TemplateResult {
        const event = this.event;
        if (!event.visibleForCurrentUser) {
            return html`${nothing}`;
        }
        const canSendInvitations = (category.accessRestricted ?? false) && !category.expired;
        return html`
            <sl-button variant="default" size="small" class="tickets-link" href="#/events/${event.shortName}/category/${category.id}/tickets">
                <sl-icon slot="prefix" name="ticket"></sl-icon>
                Tickets
            </sl-button>
            <sl-button variant="default" size="small" class="edit-category" @click=${() => void this.editCategory(category)}>
                <sl-icon slot="prefix" name="pencil"></sl-icon>
                Edit
            </sl-button>
            <sl-dropdown hoist placement="bottom-end">
                <sl-icon-button slot="trigger" class="category-menu" name="three-dots-vertical" label="More actions for ${category.name}"></sl-icon-button>
                <sl-menu @sl-select=${(e: CustomEvent) => void this.onCategoryMenu(category, String(e.detail.item.value))}>
                    ${when(canSendInvitations, () => html`
                        <sl-menu-item value="invitations" class="send-invitations-link">
                            <sl-icon slot="prefix" name="envelope"></sl-icon>
                            Send invitations
                        </sl-menu-item>
                    `, () => nothing)}
                    ${when(this.isOnline(category), () => html`
                        <sl-menu-item value="online" class="online-settings-link">
                            <sl-icon slot="prefix" name="camera-video"></sl-icon>
                            Online settings
                        </sl-menu-item>
                    `, () => nothing)}
                    <sl-menu-item value="options" class="options-link">
                        <sl-icon slot="prefix" name="wrench"></sl-icon>
                        Options
                    </sl-menu-item>
                    ${when(this.canBeDeleted(category), () => html`
                        <sl-divider></sl-divider>
                        <sl-menu-item value="delete" class="danger delete-category">
                            <sl-icon slot="prefix" name="trash"></sl-icon>
                            Delete
                        </sl-menu-item>
                    `, () => nothing)}
                </sl-menu>
            </sl-dropdown>
        `;
    }

    private async onCategoryMenu(category: TicketCategory, action: string): Promise<void> {
        const event = this.event;
        if (action === 'invitations') {
            window.location.hash = `#/events/${event.shortName}/c/${category.id}/send-invitation`;
        } else if (action === 'online') {
            await this.editOnlineSettings(category);
        } else if (action === 'options') {
            window.location.hash = `#/events/${event.shortName}/category/${category.id}/configuration`;
        } else if (action === 'delete') {
            await this.deleteCategory(category);
        }
    }

    private renderProgress(category: TicketCategory): TemplateResult {
        const event = this.event;
        const bounded = category.bounded ?? false;
        let max = category.maxTickets ?? 0;
        let available: Record<string, number> = {availableFixed: category.notSoldTickets ?? 0};
        let labels: Record<string, string> = {availableFixed: 'Available'};
        if (!bounded) {
            max = (event.dynamicAllocation ?? 0) + (category.checkedInTickets ?? 0) + (category.soldTickets ?? 0) + (category.pendingTickets ?? 0);
            available = {availableDynamic: event.dynamicAllocation ?? 0};
            labels = {availableDynamic: 'Available, shared with the other dynamic categories'};
        }
        const segments = allocationSegments({
            checkedIn: category.checkedInTickets ?? 0,
            sold: category.soldTickets ?? 0,
            pending: category.pendingTickets ?? 0,
            ...available,
        }, labels);
        const summary = segments.map((segment) => `${segment.label}: ${segment.value}`).join(', ');
        return html`
            <div class="ticket-allocation">
                <div class="progress-track" role="img" aria-label="Tickets of ${category.name}: ${summary || 'none'}">
                    ${when(max > 0, () => html`
                        ${repeat(segments, (segment) => segment.key, (segment) => html`
                            <div class="progress-segment" style="width: ${Math.max(0, segment.value * 100 / max)}%; background: ${segment.color}"
                                 title="${segment.label}: ${segment.value}"></div>
                        `)}
                    `, () => nothing)}
                </div>
                <div class="progress-legend">
                    ${repeat(segments, (segment) => segment.key, (segment) => html`
                        <span><span class="swatch" style="background: ${segment.color}"></span>${segment.label}: ${segment.value}</span>
                    `)}
                </div>
            </div>
        `;
    }

    private renderFacts(category: TicketCategory): TemplateResult {
        const event = this.event;
        return html`
            <div class="category-facts">
                <span class="fact">
                    <sl-icon name="calendar-check" aria-hidden="true"></sl-icon>
                    <span>On sale <alfio-format-date date=${category.formattedInception ?? ''} end=${category.formattedExpiration ?? ''} time-zone=${event.timeZone}></alfio-format-date></span>
                </span>
                <span class="fact">
                    <sl-icon name="ticket" aria-hidden="true"></sl-icon>
                    ${when(category.bounded, () => plural(category.maxTickets ?? 0, 'ticket', 'tickets'), () => 'Grows dynamically')}
                </span>
                ${when(!event.freeOfCharge, () => html`
                    <span class="fact">
                        <sl-icon name="cash" aria-hidden="true"></sl-icon>
                        ${formatAmount(category.price ?? 0, event.currency)}
                    </span>
                `, () => nothing)}
                ${when(category.formattedValidCheckInFrom != null || category.formattedValidCheckInTo != null, () => html`
                    <span class="fact">
                        <sl-icon name="qr-code" aria-hidden="true"></sl-icon>
                        <span>Check-in <alfio-format-date date=${category.formattedValidCheckInFrom ?? ''} end=${category.formattedValidCheckInTo ?? ''} time-zone=${event.timeZone}></alfio-format-date></span>
                    </span>
                `, () => nothing)}
                ${when(category.formattedTicketValidityStart != null || category.formattedTicketValidityEnd != null, () => html`
                    <span class="fact">
                        <sl-icon name="clock" aria-hidden="true"></sl-icon>
                        <span>Valid <alfio-format-date date=${category.formattedTicketValidityStart ?? ''} end=${category.formattedTicketValidityEnd ?? ''} time-zone=${event.timeZone}></alfio-format-date></span>
                    </span>
                `, () => nothing)}
                ${when(hasText(category.code), () => html`
                    <span class="fact">
                        <sl-icon name="link-45deg" aria-hidden="true"></sl-icon>
                        <a href="${this.baseUrl}/event/${event.shortName}/code/${category.code}" target="_blank" rel="noopener">${category.code}</a>
                    </span>
                `, () => nothing)}
                <span class="fact text-muted">ID ${category.id}</span>
            </div>
        `;
    }

    /**
     * The secondary content of a category, collapsed: description, access codes, online settings
     */
    private renderDetails(category: TicketCategory): TemplateResult {
        const hasDescription = Object.values(category.description ?? {}).some((text) => hasText(text));
        const metadata = category.metadata;
        const links = metadata?.onlineConfiguration?.callLinks ?? [];
        const requirements = metadata?.requirementsDescriptions ?? {};
        const ownOnlineSettings = this.isOnline(category) && (links.length > 0 || Object.keys(requirements).length > 0);
        if (!hasDescription && !category.accessRestricted && !ownOnlineSettings) {
            return html`${nothing}`;
        }
        return html`
            <div class="category-details">
                ${when(hasDescription, () => html`
                    <sl-details summary="Description" class="category-description">
                        <alfio-localized-markdown .value=${category.description} .languages=${this.event.contentLanguages ?? []}></alfio-localized-markdown>
                    </sl-details>
                `, () => nothing)}
                ${when(category.accessRestricted, () => this.renderTokens(category), () => nothing)}
                ${when(ownOnlineSettings, () => html`
                    <sl-details summary="Online settings of this category" class="category-online-settings">
                        ${when(links.length > 0, () => html`
                            <ul class="meeting-links">
                                ${repeat(links, (link, index) => `${index}-${link.link}`, (link) => html`
                                    <li>
                                        <a href=${link.link} target="_blank" rel="noopener">${link.link}</a>,
                                        <alfio-format-date date=${link.validFrom} end=${link.validTo}></alfio-format-date>
                                    </li>
                                `)}
                            </ul>
                        `, () => nothing)}
                        ${when(Object.keys(requirements).length > 0, () => html`
                            <alfio-localized-markdown .value=${requirements} .languages=${this.event.contentLanguages ?? []}></alfio-localized-markdown>
                        `, () => nothing)}
                    </sl-details>
                `, () => nothing)}
            </div>
        `;
    }

    private renderOrphanWarning(category: TicketCategory): TemplateResult {
        const targets = this.event.ticketCategories.filter((candidate) => !candidate.expired && candidate.bounded);
        const selected = this.orphanTarget[category.id] ?? '';
        return html`
            <sl-alert open variant="danger">
                <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                This category has tickets that can't be sold anymore. Move them to another category.
                <div class="orphan-actions">
                    <sl-select class="orphan-target label-hidden" label="Target category" size="small" placeholder="Select a category" hoist
                               .value=${selected}
                               @sl-change=${(e: Event) => {
                                   this.orphanTarget = {...this.orphanTarget, [category.id]: String((e.target as SlSelect).value)};
                               }}>
                        ${repeat(targets, (target) => target.id, (target) => html`
                            <sl-option value=${String(target.id)}>${target.name}</sl-option>
                        `)}
                    </sl-select>
                    <sl-button variant="warning" size="small" class="move-orphans" ?disabled=${selected === '' || this.saving}
                               @click=${() => void this.moveOrphans(category, Number(selected))}>Move</sl-button>
                    ${when(this.event.containingUnboundedCategories, () => html`
                        <sl-button variant="default" size="small" class="unbind-tickets" ?disabled=${this.saving}
                                   @click=${() => void this.unbindTickets(category)}>Assign to the dynamic categories</sl-button>
                    `, () => nothing)}
                </div>
            </sl-alert>
        `;
    }

    private renderTokens(category: TicketCategory): TemplateResult {
        const tokens = category.tokenStatus ?? [];
        const containsWaiting = tokens.some((token) => token.status === 'WAITING');
        const groups = this.groupTokensByStatus(tokens);
        const count = Object.values(groups).reduce((sum, group) => sum + group.length, 0);
        return html`
            <sl-details class="tokens-details" summary="Access codes (${count})">
                ${when(containsWaiting, () => html`
                    <sl-alert open variant="primary">
                        <sl-icon slot="icon" name="info-circle"></sl-icon>
                        The access codes are being generated. Reload the page in 30 seconds.
                    </sl-alert>
                `, () => nothing)}
                ${repeat(Object.entries(groups), ([status]) => status, ([status, groupTokens]) => html`
                    <div class="tokens-group">
                        <strong>${status}</strong>
                        <ul class="tokens-list">
                            ${repeat(groupTokens, (token) => token.id, (token) => html`
                                <li>
                                    ${when(token.status === 'TAKEN', () => html`
                                        <a href="#/events/${this.event.shortName}/reservations/?search=${token.code}" title="Find the reservation">${token.code}</a>
                                    `, () => html`<span>${token.code}</span>`)}
                                </li>
                            `)}
                        </ul>
                    </div>
                `)}
            </sl-details>
        `;
    }

    private groupTokensByStatus(tokens: SpecialPrice[]): Record<string, SpecialPrice[]> {
        const groups: Record<string, SpecialPrice[]> = {};
        const sorted = tokens.filter((token) => token.status !== 'CANCELLED' && token.status !== 'WAITING').slice().sort((a, b) => b.id - a.id);
        for (const token of sorted) {
            let status: string;
            if (token.status === 'FREE') {
                if (token.sentTimestamp == null) {
                    status = 'Free';
                } else {
                    status = 'Invitation sent';
                }
            } else if (token.status === 'PENDING') {
                status = 'Pending';
            } else if (token.status === 'TAKEN') {
                status = 'Confirmed';
            } else {
                status = token.status;
            }
            (groups[status] ??= []).push(token);
        }
        return groups;
    }

    // ---------- actions ----------

    private categoryDialogContext(): CategoryDialogContext {
        const event = this.event;
        return {
            format: event.format as EventFormat,
            freeOfCharge: event.freeOfCharge,
            currency: event.currency,
            eventBegin: wallClockToDateTimeLocal(event.formattedBegin),
            eventEnd: wallClockToDateTimeLocal(event.formattedEnd),
            availableSeats: String(event.availableSeats ?? ''),
            languages: this.languages,
            selectedLocales: Object.keys(event.description ?? {}),
            descriptionMaxLength: this.descriptionMaxLength,
            baseUrl: this.baseUrl,
            shortName: event.shortName,
            categories: this.event.ticketCategories.map((category) => categoryToDraft(category, this.nextKey++)),
        };
    }

    private categoryDialog(): EventCategoryDialog | null {
        return this.renderRoot.querySelector<EventCategoryDialog>('alfio-event-category-dialog');
    }

    private async addCategory(): Promise<void> {
        const dialog = this.categoryDialog();
        if (dialog == null) {
            return;
        }
        const event = this.event;
        const draft = newCategoryDraft(wallClockToDateTimeLocal(event.formattedBegin), String(event.regularPrice ?? ''), event.format as EventFormat, event.ticketCategories.length + 1, this.nextKey++);
        draft.currency = event.currency;
        const result = await dialog.open(this.categoryDialogContext(), draft);
        if (result == null) {
            return;
        }
        this.saving = true;
        try {
            const validation = await EventService.createCategory(event.id, categoryDraftToPayload(result));
            if (!validation.success) {
                dispatchFeedback({type: 'danger', message: validation.validationErrors?.map((e) => e.message ?? e.code).join(' ') || 'The category could not be created'}, this);
                return;
            }
            dispatchFeedback({type: 'success', message: `Category ${result.name} created`}, this);
            this.reload();
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, 'Failed to create the category')}, this);
        } finally {
            this.saving = false;
        }
    }

    private async editCategory(category: TicketCategory): Promise<void> {
        const dialog = this.categoryDialog();
        if (dialog == null) {
            return;
        }
        const result = await dialog.open(this.categoryDialogContext(), categoryToDraft(category, this.nextKey++));
        if (result == null) {
            return;
        }
        this.saving = true;
        try {
            const payload = categoryDraftToPayload(result);
            payload.id = category.id;
            const validation = await EventService.updateCategory(this.event.id, category.id, payload);
            if (!validation.success) {
                dispatchFeedback({type: 'danger', message: validation.validationErrors?.map((e) => e.message ?? e.code).join(' ') || 'The category could not be saved'}, this);
                return;
            }
            dispatchFeedback({type: 'success', message: `Category ${result.name} saved`}, this);
            this.reload();
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, 'Failed to save the category')}, this);
        } finally {
            this.saving = false;
        }
    }

    private async editOnlineSettings(category: TicketCategory): Promise<void> {
        const dialog = this.renderRoot.querySelector('alfio-online-settings-dialog');
        if (dialog == null) {
            return;
        }
        const event = this.event;
        // a category without its own settings starts from the event's
        let metadata = event.metadata;
        if ((category.metadata?.onlineConfiguration?.callLinks ?? []).length > 0) {
            metadata = category.metadata;
        }
        const saved = await dialog.open({
            eventName: event.shortName,
            categoryId: category.id,
            label: `Online settings of ${category.name}`,
            metadata,
            eventBegin: wallClockToDateTimeLocal(event.formattedBegin),
            eventEnd: wallClockToDateTimeLocal(event.formattedEnd),
            languages: this.languages,
            contextLanguages: event.contentLanguages ?? [],
        });
        if (saved) {
            dispatchFeedback({type: 'success', message: `The online settings of ${category.name} have been saved`}, this);
            this.reload();
        }
    }

    private async deleteCategory(category: TicketCategory): Promise<void> {
        const confirmed = await ConfirmationDialogService.requestConfirm(
            `Delete category ${category.name}?`,
            'The category and its settings will be deleted. This cannot be undone.',
            'danger');
        if (!confirmed) {
            return;
        }
        try {
            await EventService.deleteCategory(this.event.shortName, category.id);
            dispatchFeedback({type: 'success', message: `Category ${category.name} deleted`}, this);
            this.reload();
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, 'Failed to delete the category')}, this);
        }
    }

    private async moveOrphans(category: TicketCategory, targetCategoryId: number): Promise<void> {
        this.saving = true;
        try {
            await EventService.reallocateOrphans(category.id, targetCategoryId, this.event.id);
            dispatchFeedback({type: 'success', message: 'The tickets have been moved'}, this);
            this.reload();
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, 'Failed to move the tickets')}, this);
        } finally {
            this.saving = false;
        }
    }

    private async unbindTickets(category: TicketCategory): Promise<void> {
        this.saving = true;
        try {
            await EventService.unbindTickets(this.event.shortName, category.id);
            dispatchFeedback({type: 'success', message: 'The tickets have been assigned to the dynamic categories'}, this);
            this.reload();
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, 'Failed to assign the tickets')}, this);
        } finally {
            this.saving = false;
        }
    }

    // ---------- rearrange ----------

    private rearrangeOrder(): CategoryOrder[] {
        if (this.draftOrder.length > 0) {
            return this.draftOrder;
        }
        return this.sortedCategories().map((category, index) => ({
            id: category.id,
            name: category.name,
            ordinal: index + 1,
        }));
    }

    private startRearrange(): void {
        this.draftOrder = this.rearrangeOrderFromEvent();
        this.rearranging = true;
    }

    private rearrangeOrderFromEvent(): CategoryOrder[] {
        return this.sortedCategories().map((category, index) => ({
            id: category.id,
            name: category.name,
            ordinal: index + 1,
        }));
    }

    private cancelRearrange(): void {
        this.rearranging = false;
        this.draftOrder = [];
    }

    private moveCategory(categoryId: number, delta: number): void {
        const order = this.rearrangeOrder();
        const index = order.findIndex((entry) => entry.id === categoryId);
        const target = index + delta;
        if (index < 0 || target < 0 || target >= order.length) {
            return;
        }
        const next = order.slice();
        const [moved] = next.splice(index, 1);
        next.splice(target, 0, moved);
        this.draftOrder = next.map((entry, i) => ({...entry, ordinal: i + 1}));
    }

    private async saveOrder(): Promise<void> {
        this.saving = true;
        try {
            await EventService.rearrangeCategories(this.event.shortName, this.draftOrder);
            dispatchFeedback({type: 'success', message: 'The order of the categories has been saved'}, this);
            this.rearranging = false;
            this.draftOrder = [];
            this.reload();
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, 'Failed to save the order of the categories')}, this);
        } finally {
            this.saving = false;
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-event-categories': EventCategories;
    }
}
