import {css, html, LitElement, nothing, PropertyValues, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {classMap} from 'lit/directives/class-map.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import type {SlDialog, SlInput, SlRequestCloseEvent, SlSelect} from '@shoelace-style/shoelace';
import {AlfioEvent, TicketCategory} from '../../model/event.ts';
import {ContentLanguage, LocalizedContent} from '../../model/purchase-context.ts';
import {EventFormat} from '../../model/event-modification.ts';
import {SpecialPrice} from '../../model/special-price.ts';
import {EventService} from '../../service/event.ts';
import {ConfigurationService} from '../../service/configuration.ts';
import {badgeSwatch, findBadgeColor} from '../../components/badge-color.ts';
import {errorMessage} from '../../service/helpers.ts';
import {formatAmount} from '../../service/reservation-format.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {ConfirmationDialogService} from '../../service/confirmation-dialog.ts';
import {sectionCard} from '../../components/section-card.ts';
import {emptyState} from '../../components/empty-state.ts';
import '../../components/format-date.ts';
import '../../components/localized-markdown.ts';
import '../event-create/category-dialog.ts';
import type {CategoryDialogContext, CategoryDialogTab, CategoryDraft, EventCategoryDialog} from '../event-create/category-dialog.ts';
import {categoryDraftToPayload, newCategoryDraft, wallClockToDateTimeLocal} from '../event-create/category-dialog.ts';
import './online-settings.ts';
import {allocationSegments} from './ticket-allocation.ts';
import {badges, dialog, form, modernLayout, modernTable, row, textColors} from '../../styles.ts';
import { navigate, adminUrl } from '../../admin-router';
import { t, tHtml, formatNumber, translateErrorCode } from '../../service/i18n';

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
    // INHERIT (same as the event) is the empty value of the draft
    let ticketAccessType = '';
    if (category.ticketAccessType != null && category.ticketAccessType !== 'INHERIT') {
        ticketAccessType = category.ticketAccessType;
    }
    let description: LocalizedContent = {};
    if (category.description != null) {
        description = {...category.description};
    }
    // a category without a limit has maxTickets 0: the field starts empty when the limit is turned on
    let maxTickets = '';
    if (category.bounded && category.maxTickets != null) {
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
        ticketCheckInStrategy: category.ticketCheckInStrategy ?? 'ONCE_PER_EVENT',
        // loaded by the dialog, from the check-in settings of the event
        badgeColor: '',
        ordinal: category.ordinal ?? key,
    };
}

function hasText(value: string | null | undefined): boolean {
    return value != null && value.trim() !== '';
}


/**
 * The categories of the event detail page: filter toolbar, one card per category with its ticket
 * bar and actions, add/edit through the same category dialog used by the event creation form,
 * rearrange, orphan handling, the invitation codes of the unlisted categories and their online
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

    @query('#rearrange-dialog') private rearrangeDialog!: SlDialog;

    // the category being dragged in the rearrange dialog
    @state() private draggedId: number | null = null;

    @state() private draftOrder: CategoryOrder[] = [];

    @state() private orphanTarget: Record<number, string> = {};

    @state() private saving = false;
    // badge color of the categories (check-in settings of the event), by category id
    @state() private badgeColors = new Map<number, string>();
    // discards the colors loaded for a previous version of the event
    private badgeColorsRequest = 0;

    private nextKey = 1;

    static readonly styles = [
        badges,
        dialog,
        form,
        modernLayout,
        modernTable,
        row,
        textColors,
        css`
            :host {
                display: block;
                margin-top: var(--sl-spacing-x-large);
                --alfio-section-body-padding: var(--sl-spacing-medium);
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
            }
            .badge-swatch {
                display: inline-block;
                width: 0.75rem;
                height: 0.75rem;
                border-radius: var(--sl-border-radius-circle);
                border: 1px solid var(--sl-color-neutral-300);
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
            /* the badge color of the category, as the check-in app shows it (after the
               rules above, so that it also marks a category that needs attention) */
            .category-card.has-badge-color {
                border-inline-start: 4px solid var(--category-badge-border);
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
            .rearrange-order.dragged {
                opacity: 0.5;
            }
            .rearrange-order .drag-handle {
                cursor: grab;
                color: var(--sl-color-neutral-500);
            }
            #rearrange-dialog {
                --alfio-dialog-max-width: 36rem;
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
            this.orphanTarget = {};
            this.loadBadgeColors();
        }
    }

    private loadBadgeColors(): void {
        const request = ++this.badgeColorsRequest;
        ConfigurationService.loadBadgeColors(this.event.shortName)
            .then(colors => {
                if (request === this.badgeColorsRequest) {
                    this.badgeColors = colors;
                }
            })
            // the colors are a visual aid: the cards are shown without them
            .catch(() => undefined);
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
                    <h2 id="categories-title">${t('admin.categories.title')}</h2>
                    <sl-badge variant="primary" pill>${event.ticketCategories.length}</sl-badge>
                </div>
                ${this.renderCategoryList()}
            </section>
            ${this.renderRearrange()}
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
                        ${t('admin.categories.filter.active')} <sl-badge variant="neutral" pill class="selection-count">${activeCount}</sl-badge>
                    </sl-checkbox>
                    <sl-checkbox class="filter-expired" ?checked=${this.selection.expired}
                                 @sl-change=${(e: Event) => { this.selection = {...this.selection, expired: (e.target as HTMLInputElement).checked}; }}>
                        ${t('admin.event.status.expired')} <sl-badge variant="neutral" pill class="selection-count">${expiredCount}</sl-badge>
                    </sl-checkbox>
                    <sl-input class="list-search label-hidden" label=${t('admin.categories.filter')} type="search" placeholder=${t('admin.categories.filter.placeholder')} clearable
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
                                ${t('admin.categories.rearrange')}
                            </sl-button>
                        `, () => nothing)}
                        ${when(event.addCategoryEnabled, () => html`
                            <sl-button id="add-category" variant="success" size="large" @click=${this.addCategory}>
                                <sl-icon slot="prefix" name="plus-circle"></sl-icon>
                                ${t('admin.categories.add')}
                            </sl-button>
                        `, () => nothing)}
                    </div>
                `, () => nothing)}
            </div>
            ${when(visible.length === 0,
                () => html`<section class="section-card">${emptyState(t('admin.categories.none-matching'))}</section>`,
                () => html`${repeat(visible, (category) => category.id, (category) => this.renderCategory(category))}`)}
        `;
    }

    private renderRearrange(): TemplateResult {
        const order = this.draftOrder;
        return html`
            <sl-dialog id="rearrange-dialog" class="responsive-dialog" label=${t('admin.categories.rearrange.title')}
                       @sl-request-close=${this.onRearrangeRequestClose}
                       @sl-after-hide=${(e: Event) => { if (e.target === this.rearrangeDialog) { this.draftOrder = []; } }}>
                <p class="text-muted">${t('admin.categories.rearrange.text')}</p>
                <ol class="rearrange-list">
                    ${repeat(order, (entry) => entry.id, (entry, index) => html`
                        <li class=${classMap({'rearrange-order': true, dragged: this.draggedId === entry.id})} draggable="true"
                            @dragstart=${(e: DragEvent) => this.onDragStart(e, entry.id)}
                            @dragover=${(e: DragEvent) => this.onDragOver(e, index)}
                            @dragend=${() => { this.draggedId = null; }}>
                            <sl-icon class="drag-handle" name="grip-vertical" aria-hidden="true"></sl-icon>
                            <strong>${index + 1}.</strong>
                            <span class="name">${entry.name}</span>
                            <sl-icon-button name="arrow-up" label=${t('admin.categories.rearrange.up', entry.name)} ?disabled=${index === 0}
                                            @click=${() => this.moveCategory(entry.id, -1)}></sl-icon-button>
                            <sl-icon-button name="arrow-down" label=${t('admin.categories.rearrange.down', entry.name)} ?disabled=${index === order.length - 1}
                                            @click=${() => this.moveCategory(entry.id, 1)}></sl-icon-button>
                        </li>
                    `)}
                </ol>
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button id="cancel-rearrange" variant="default" size="large" @click=${() => void this.rearrangeDialog.hide()}>${t('admin.common.cancel')}</sl-button>
                        <div></div>
                        <sl-button id="save-order" variant="warning" size="large" ?loading=${this.saving} ?disabled=${this.saving} @click=${this.saveOrder}>${t('admin.common.save')}</sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    private renderCategory(category: TicketCategory): TemplateResult {
        const event = this.event;
        const needsAttention = (category.containingOrphans ?? false) || (category.containingStuckTickets ?? false);
        const badgeRow = html`
            <span class="category-badges">
                ${when(category.expired, () => html`<sl-badge variant="neutral" pill>${t('admin.event.status.expired')}</sl-badge>`, () => nothing)}
                ${when(category.accessRestricted, () => html`<sl-badge variant="neutral" pill class="unlisted-badge"><sl-icon name="eye-slash" aria-hidden="true"></sl-icon>${t('admin.event.status.unlisted')}</sl-badge>`, () => nothing)}
                ${when(event.format === 'HYBRID' && category.ticketAccessType === 'ONLINE', () => html`<sl-badge variant="primary" pill>${t('admin.event.format.online')}</sl-badge>`, () => nothing)}
            </span>
        `;
        let cardClass = 'category-card';
        let icon = 'tag';
        if (needsAttention) {
            cardClass += ' needs-attention';
            icon = 'exclamation-triangle';
        }
        const badgeColor = findBadgeColor(this.badgeColors.get(category.id));
        let colorStyle = '';
        if (badgeColor != null) {
            cardClass += ' has-badge-color';
            colorStyle = `--category-badge-border: ${badgeColor.border}`;
        }
        return html`
            <div id="ticket-category-${category.id}" style=${colorStyle}>
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
                            ${tHtml('admin.categories.group-only', html`<a href=${adminUrl(`/group/${event.organizationId}/edit/${category.attendeesList!.groupId}`)}>${category.attendeesList!.groupName}</a>`)}
                        </sl-alert>
                    `, () => nothing)}
                    ${when(category.containingOrphans, () => this.renderOrphanWarning(category), () => nothing)}
                    ${when(category.containingStuckTickets, () => html`
                        <sl-alert open variant="warning">
                            <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                            ${t('admin.categories.stuck-tickets')}
                            <a href=${adminUrl(`/events/${event.shortName}/category/${category.id}/tickets`)}>${t('admin.categories.stuck-tickets.fix')}</a>
                        </sl-alert>
                    `, () => nothing)}
                    ${this.renderProgress(category)}
                    ${this.renderFacts(category)}
                    ${this.renderDetails(category)}
                `)}
            </div>
        `;
    }

    private renderBadgeColorFact(category: TicketCategory): TemplateResult | typeof nothing {
        const badgeColor = findBadgeColor(this.badgeColors.get(category.id));
        if (badgeColor == null) {
            return nothing;
        }
        return html`
            <span class="fact badge-color-fact">
                ${badgeSwatch(badgeColor)}
                ${t('admin.categories.badge-color', badgeColor.label)}
            </span>
        `;
    }

    private renderCategoryActions(category: TicketCategory): TemplateResult {
        const event = this.event;
        if (!event.visibleForCurrentUser) {
            return html`${nothing}`;
        }
        const canSendInvitations = (category.accessRestricted ?? false) && !category.expired;
        return html`
            <sl-button variant="default" size="small" class="tickets-link" href=${adminUrl(`/events/${event.shortName}/category/${category.id}/tickets`)}>
                <sl-icon slot="prefix" name="ticket"></sl-icon>
                ${t('admin.page-title.tickets')}
            </sl-button>
            <sl-button variant="default" size="small" class="edit-category" @click=${() => void this.editCategory(category)}>
                <sl-icon slot="prefix" name="pencil"></sl-icon>
                ${t('admin.common.edit')}
            </sl-button>
            <sl-dropdown hoist placement="bottom-end">
                <sl-icon-button slot="trigger" class="category-menu" name="three-dots-vertical" label=${t('admin.categories.more-actions', category.name)}></sl-icon-button>
                <sl-menu @sl-select=${(e: CustomEvent) => void this.onCategoryMenu(category, String(e.detail.item.value))}>
                    ${when(canSendInvitations, () => html`
                        <sl-menu-item value="invitations" class="send-invitations-link">
                            <sl-icon slot="prefix" name="envelope"></sl-icon>
                            ${t('admin.page-title.send-invitations')}
                        </sl-menu-item>
                    `, () => nothing)}
                    ${when(this.isOnline(category), () => html`
                        <sl-menu-item value="online" class="online-settings-link">
                            <sl-icon slot="prefix" name="camera-video"></sl-icon>
                            ${t('admin.categories.online-settings')}
                        </sl-menu-item>
                    `, () => nothing)}
                    <sl-menu-item value="options" class="options-link">
                        <sl-icon slot="prefix" name="wrench"></sl-icon>
                        ${t('admin.categories.options')}
                    </sl-menu-item>
                    ${when(this.canBeDeleted(category), () => html`
                        <sl-divider></sl-divider>
                        <sl-menu-item value="delete" class="danger delete-category">
                            <sl-icon slot="prefix" name="trash"></sl-icon>
                            ${t('admin.common.delete')}
                        </sl-menu-item>
                    `, () => nothing)}
                </sl-menu>
            </sl-dropdown>
        `;
    }

    private async onCategoryMenu(category: TicketCategory, action: string): Promise<void> {
        const event = this.event;
        if (action === 'invitations') {
            navigate(`/events/${event.shortName}/c/${category.id}/send-invitation`);
        } else if (action === 'online') {
            await this.editOnlineSettings(category);
        } else if (action === 'options') {
            await this.editCategory(category, 'options');
        } else if (action === 'delete') {
            await this.deleteCategory(category);
        }
    }

    private renderProgress(category: TicketCategory): TemplateResult {
        const event = this.event;
        const bounded = category.bounded ?? false;
        let max = category.maxTickets ?? 0;
        let available: Record<string, number> = {availableFixed: category.notSoldTickets ?? 0};
        let labels: Record<string, string> = {availableFixed: t('admin.allocation.available')};
        if (!bounded) {
            max = (event.dynamicAllocation ?? 0) + (category.checkedInTickets ?? 0) + (category.soldTickets ?? 0) + (category.pendingTickets ?? 0);
            available = {availableDynamic: event.dynamicAllocation ?? 0};
            labels = {availableDynamic: t('admin.allocation.available-shared')};
        }
        const segments = allocationSegments({
            checkedIn: category.checkedInTickets ?? 0,
            sold: category.soldTickets ?? 0,
            pending: category.pendingTickets ?? 0,
            ...available,
        }, labels);
        const summary = segments.map((segment) => `${segment.label}: ${formatNumber(segment.value)}`).join(', ');
        return html`
            <div class="ticket-allocation">
                <div class="progress-track" role="img" aria-label=${t('admin.categories.allocation', category.name, summary || t('admin.categories.allocation.none'))}>
                    ${when(max > 0, () => html`
                        ${repeat(segments, (segment) => segment.key, (segment) => html`
                            <div class="progress-segment" style="width: ${Math.max(0, segment.value * 100 / max)}%; background: ${segment.color}"
                                 title="${segment.label}: ${formatNumber(segment.value)}"></div>
                        `)}
                    `, () => nothing)}
                </div>
                <div class="progress-legend">
                    ${repeat(segments, (segment) => segment.key, (segment) => html`
                        <span><span class="swatch" style="background: ${segment.color}"></span>${segment.label}: ${formatNumber(segment.value)}</span>
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
                    <span>${tHtml('admin.categories.on-sale', html`<alfio-format-date date=${category.formattedInception ?? ''} end=${category.formattedExpiration ?? ''} time-zone=${event.timeZone}></alfio-format-date>`)}</span>
                </span>
                ${when(category.bounded, () => html`
                    <span class="fact">
                        <sl-icon name="ticket" aria-hidden="true"></sl-icon>
                        ${t('admin.categories.up-to', { count: category.maxTickets ?? 0 })}
                    </span>
                `, () => nothing)}
                ${when(!event.freeOfCharge, () => html`
                    <span class="fact">
                        <sl-icon name="cash" aria-hidden="true"></sl-icon>
                        ${formatAmount(category.price ?? 0, event.currency)}
                    </span>
                `, () => nothing)}
                ${when(category.formattedValidCheckInFrom != null || category.formattedValidCheckInTo != null, () => html`
                    <span class="fact">
                        <sl-icon name="qr-code" aria-hidden="true"></sl-icon>
                        <span>${tHtml('admin.categories.check-in', html`<alfio-format-date date=${category.formattedValidCheckInFrom ?? ''} end=${category.formattedValidCheckInTo ?? ''} time-zone=${event.timeZone}></alfio-format-date>`)}</span>
                    </span>
                `, () => nothing)}
                ${when(category.formattedTicketValidityStart != null || category.formattedTicketValidityEnd != null, () => html`
                    <span class="fact">
                        <sl-icon name="clock" aria-hidden="true"></sl-icon>
                        <span>${tHtml('admin.categories.valid', html`<alfio-format-date date=${category.formattedTicketValidityStart ?? ''} end=${category.formattedTicketValidityEnd ?? ''} time-zone=${event.timeZone}></alfio-format-date>`)}</span>
                    </span>
                `, () => nothing)}
                ${when(hasText(category.code), () => html`
                    <span class="fact">
                        <sl-icon name="link-45deg" aria-hidden="true"></sl-icon>
                        <a href="${this.baseUrl}/event/${event.shortName}/code/${category.code}" target="_blank" rel="noopener">${category.code}</a>
                    </span>
                `, () => nothing)}
                ${this.renderBadgeColorFact(category)}
                <span class="fact text-muted">${t('admin.categories.id', category.id)}</span>
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
                    <sl-details summary=${t('admin.common.description')} class="category-description">
                        <alfio-localized-markdown .value=${category.description} .languages=${this.event.contentLanguages ?? []}></alfio-localized-markdown>
                    </sl-details>
                `, () => nothing)}
                ${when(category.accessRestricted, () => this.renderTokens(category), () => nothing)}
                ${when(ownOnlineSettings, () => html`
                    <sl-details summary=${t('admin.categories.own-online-settings')} class="category-online-settings">
                        ${when(links.length > 0, () => html`
                            <ul class="meeting-links">
                                ${repeat(links, (link, index) => `${index}-${link.link}`, (link) => html`
                                    <li>
                                        <a href=${link.link} target="_blank" rel="noopener">${link.link}</a>,
                                        <alfio-format-date date=${link.validFrom} end=${link.validTo} time-zone=${this.event.timeZone}></alfio-format-date>
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
                ${t('admin.categories.orphans')}
                <div class="orphan-actions">
                    <sl-select class="orphan-target label-hidden" label=${t('admin.categories.orphans.target')} size="small" placeholder=${t('admin.category-select.placeholder')} hoist
                               .value=${selected}
                               @sl-change=${(e: Event) => {
                                   this.orphanTarget = {...this.orphanTarget, [category.id]: String((e.target as SlSelect).value)};
                               }}>
                        ${repeat(targets, (target) => target.id, (target) => html`
                            <sl-option value=${String(target.id)}>${target.name}</sl-option>
                        `)}
                    </sl-select>
                    <sl-button variant="warning" size="small" class="move-orphans" ?disabled=${selected === '' || this.saving}
                               @click=${() => void this.moveOrphans(category, Number(selected))}>${t('admin.categories.orphans.move')}</sl-button>
                    ${when(this.event.containingUnboundedCategories, () => html`
                        <sl-button variant="default" size="small" class="unbind-tickets" ?disabled=${this.saving}
                                   @click=${() => void this.unbindTickets(category)}>${t('admin.categories.orphans.put-back-on-sale')}</sl-button>
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
            <sl-details class="tokens-details" summary=${t('admin.categories.invitation-codes', count)}>
                ${when(containsWaiting, () => html`
                    <sl-alert open variant="primary">
                        <sl-icon slot="icon" name="info-circle"></sl-icon>
                        ${t('admin.categories.invitation-codes.generating')}
                    </sl-alert>
                `, () => nothing)}
                ${repeat(Object.entries(groups), ([status]) => status, ([status, groupTokens]) => html`
                    <div class="tokens-group">
                        <strong>${status}</strong>
                        <ul class="tokens-list">
                            ${repeat(groupTokens, (token) => token.id, (token) => html`
                                <li>
                                    ${when(token.status === 'TAKEN', () => html`
                                        <a href=${adminUrl(`/events/${this.event.shortName}/reservations/?search=${token.code}`)} title=${t('admin.categories.invitation-codes.find-reservation')}>${token.code}</a>
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
                    status = t('admin.categories.invitation-codes.free');
                } else {
                    status = t('admin.categories.invitation-codes.sent');
                }
            } else if (token.status === 'PENDING') {
                status = t('admin.allocation.pending');
            } else if (token.status === 'TAKEN') {
                status = t('admin.statistics.confirmed');
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
                dispatchFeedback({type: 'danger', message: validation.validationErrors?.map((e) => translateErrorCode(e.code, e.message ?? e.code)).join(' ') || t('admin.categories.create-failed')}, this);
                return;
            }
            dispatchFeedback({type: 'success', message: t('admin.categories.created', result.name)}, this);
            this.reload();
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, t('admin.categories.create-failed'))}, this);
        } finally {
            this.saving = false;
        }
    }

    private async editCategory(category: TicketCategory, tab: CategoryDialogTab = 'general'): Promise<void> {
        const dialog = this.categoryDialog();
        if (dialog == null) {
            return;
        }
        const event = this.event;
        const result = await dialog.open(this.categoryDialogContext(), categoryToDraft(category, this.nextKey++), {
            tab,
            configuration: {
                eventId: event.id,
                eventName: event.shortName,
                organizationId: event.organizationId,
                categoryId: category.id,
                allowedPaymentProxies: event.allowedPaymentProxies ?? [],
            },
        });
        if (result == null) {
            return;
        }
        this.saving = true;
        try {
            const payload = categoryDraftToPayload(result);
            payload.id = category.id;
            const validation = await EventService.updateCategory(this.event.id, category.id, payload);
            if (!validation.success) {
                dispatchFeedback({type: 'danger', message: validation.validationErrors?.map((e) => translateErrorCode(e.code, e.message ?? e.code)).join(' ') || t('admin.categories.save-failed')}, this);
                return;
            }
            dispatchFeedback({type: 'success', message: t('admin.categories.saved', result.name)}, this);
            this.reload();
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, t('admin.categories.save-failed'))}, this);
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
            label: t('admin.categories.online-settings-of', category.name),
            metadata,
            eventBegin: wallClockToDateTimeLocal(event.formattedBegin),
            eventEnd: wallClockToDateTimeLocal(event.formattedEnd),
            languages: this.languages,
            contextLanguages: event.contentLanguages ?? [],
        });
        if (saved) {
            dispatchFeedback({type: 'success', message: t('admin.categories.online-settings-saved', category.name)}, this);
            this.reload();
        }
    }

    private async deleteCategory(category: TicketCategory): Promise<void> {
        const confirmed = await ConfirmationDialogService.requestConfirm(
            t('admin.categories.delete.title', category.name),
            t('admin.categories.delete.text'),
            'danger');
        if (!confirmed) {
            return;
        }
        try {
            await EventService.deleteCategory(this.event.shortName, category.id);
            dispatchFeedback({type: 'success', message: t('admin.categories.deleted', category.name)}, this);
            this.reload();
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, t('admin.categories.delete-failed'))}, this);
        }
    }

    private async moveOrphans(category: TicketCategory, targetCategoryId: number): Promise<void> {
        this.saving = true;
        try {
            await EventService.reallocateOrphans(category.id, targetCategoryId, this.event.id);
            dispatchFeedback({type: 'success', message: t('admin.categories.orphans.moved')}, this);
            this.reload();
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, t('admin.categories.orphans.move-failed'))}, this);
        } finally {
            this.saving = false;
        }
    }

    private async unbindTickets(category: TicketCategory): Promise<void> {
        this.saving = true;
        try {
            await EventService.unbindTickets(this.event.shortName, category.id);
            dispatchFeedback({type: 'success', message: t('admin.categories.orphans.back-on-sale')}, this);
            this.reload();
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, t('admin.categories.orphans.assign-failed'))}, this);
        } finally {
            this.saving = false;
        }
    }

    // ---------- rearrange ----------

    private async startRearrange(): Promise<void> {
        this.draftOrder = this.sortedCategories().map((category, index) => ({
            id: category.id,
            name: category.name,
            ordinal: index + 1,
        }));
        await this.updateComplete;
        await this.rearrangeDialog.show();
    }

    // the order is changed on purpose: a click outside the dialog doesn't discard it
    private onRearrangeRequestClose = (e: SlRequestCloseEvent): void => {
        if (e.target === this.rearrangeDialog && e.detail.source === 'overlay') {
            e.preventDefault();
        }
    };

    private moveCategory(categoryId: number, delta: number): void {
        const index = this.draftOrder.findIndex((entry) => entry.id === categoryId);
        this.moveCategoryTo(categoryId, index + delta);
    }

    private moveCategoryTo(categoryId: number, target: number): void {
        const order = this.draftOrder;
        const index = order.findIndex((entry) => entry.id === categoryId);
        if (index < 0 || target < 0 || target >= order.length || target === index) {
            return;
        }
        const next = order.slice();
        const [moved] = next.splice(index, 1);
        next.splice(target, 0, moved);
        this.draftOrder = next.map((entry, i) => ({...entry, ordinal: i + 1}));
    }

    private onDragStart(e: DragEvent, categoryId: number): void {
        this.draggedId = categoryId;
        if (e.dataTransfer != null) {
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/plain', String(categoryId));
        }
    }

    // the dragged category takes the place of the one under the pointer
    private onDragOver(e: DragEvent, index: number): void {
        if (this.draggedId == null) {
            return;
        }
        e.preventDefault();
        this.moveCategoryTo(this.draggedId, index);
    }

    private saveOrder = async (): Promise<void> => {
        this.saving = true;
        try {
            await EventService.rearrangeCategories(this.event.shortName, this.draftOrder);
            dispatchFeedback({type: 'success', message: t('admin.categories.rearrange.saved')}, this);
            await this.rearrangeDialog.hide();
            this.reload();
        } catch (error) {
            dispatchFeedback({type: 'danger', message: errorMessage(error, t('admin.categories.rearrange.failed'))}, this);
        } finally {
            this.saving = false;
        }
    };
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-event-categories': EventCategories;
    }
}
