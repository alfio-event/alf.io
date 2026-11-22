import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {classMap} from 'lit/directives/class-map.js';
import {Task} from '@lit/task';
import {SubscriptionWithStatistics} from '../model/subscription-descriptor.ts';
import {SubscriptionService} from '../service/subscription.ts';
import {ConfigurationService} from '../service/configuration.ts';
import {dispatchFeedback} from '../model/dom-events.ts';
import {formatAmount} from '../service/reservation-format.ts';
import {badges, cardList, modernLayout, textColors} from '../styles.ts';
import {emptyState} from '../components/empty-state.ts';
import {taskContent} from '../components/task-content.ts';
import {sectionCard} from '../components/section-card.ts';
import {organizationFilter} from '../components/organization-filter.ts';
import {OrganizationService} from '../service/organization.ts';
import '../components/format-date.ts';
import './linked-events-dialog.ts';
import type {LinkedEventsDialog} from './linked-events-dialog.ts';
import { adminUrl, navigate } from '../admin-router';

interface SubscriptionsListData {
    subscriptions: SubscriptionWithStatistics[];
    ticketsGenerationJobActive: boolean;
}

/**
 * The subscriptions of an organization, as a card with one row each, like the events list:
 * the row opens the edit page, the actions beside it open the linked events, the public page,
 * the data to collect and the reservations.
 */
@customElement('alfio-subscriptions-list')
export class SubscriptionsList extends LitElement {

    @property({type: Number, attribute: 'data-organization-id'})
    organizationId = 0;

    private readonly loadDataTask = new Task(this, {
        task: async ([organizationId]): Promise<SubscriptionsListData | null> => {
            if (!(organizationId > 0)) {
                // the organization id is still being interpolated by AngularJS
                return null;
            }
            const [subscriptions, ticketsGeneration] = await Promise.all([
                SubscriptionService.loadList(organizationId),
                ConfigurationService.loadSingleConfigForOrganization(organizationId, 'GENERATE_TICKETS_FOR_SUBSCRIPTIONS')
                    .catch(() => ''),
            ]);
            return {
                subscriptions,
                ticketsGenerationJobActive: ticketsGeneration === 'true',
            };
        },
        // the attribute is interpolated by AngularJS after the element is connected: wait for a real id
        args: () => [this.organizationId] as const,
    });

    private readonly loadOrganizationsTask = new Task(this, {
        task: async () => OrganizationService.loadAll(),
        args: () => [] as const,
    });

    static readonly styles = [
        badges,
        cardList,
        modernLayout,
        textColors,
        css`
            .list-card {
                --alfio-section-body-padding: 0;
            }

            /* each row is the link to the edit page; the actions sit beside it */
            .subscription-item {
                display: grid;
                grid-template-columns: minmax(0, 1fr) auto;
                align-items: center;
            }

            .subscription-item + .subscription-item {
                border-top: 1px solid var(--sl-color-gray-200);
            }

            /* hovering anywhere on the row highlights it entirely, actions included */
            .subscription-item {
                transition: background-color var(--sl-transition-fast);
            }

            .subscription-item:hover {
                background: var(--sl-color-primary-50);
            }

            .subscription-item:hover .event-title {
                color: var(--sl-color-primary-700);
                text-decoration: underline;
            }

            .item-actions {
                display: flex;
                flex-wrap: wrap;
                gap: var(--sl-spacing-x-small);
                padding-inline-end: var(--sl-spacing-large);
            }

            .subscription-description {
                margin-top: var(--sl-spacing-2x-small);
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-small);
                display: -webkit-box;
                -webkit-line-clamp: 2;
                -webkit-box-orient: vertical;
                overflow: hidden;
            }

            .subscription-price {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-2x-small);
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-small);
            }

            @media (max-width: 640px) {
                .subscription-item {
                    grid-template-columns: 1fr;
                }

                .item-actions {
                    justify-content: flex-end;
                    padding: 0 var(--sl-spacing-medium) var(--sl-spacing-small);
                }
            }
        `,
    ];

    render(): TemplateResult {
        const data = this.loadDataTask.value;
        let count: TemplateResult | undefined = undefined;
        if (data != null) {
            count = html`<sl-badge variant="primary" pill>${data.subscriptions.length}</sl-badge>`;
        }
        return html`
            <div class="filter-toolbar">
                <div class="filter-left">
                    ${organizationFilter(this.loadOrganizationsTask.value, this.organizationId,
                        (organizationId) => navigate(`/subscriptions/${organizationId}/list`))}
                </div>
                <div class="filter-right">
                    <sl-button id="add-subscription" href=${adminUrl(`/subscriptions/${this.organizationId}/create`)}
                               variant="success" size="large">
                        <sl-icon name="plus-circle" slot="prefix"></sl-icon>
                        Add new
                    </sl-button>
                </div>
            </div>
            ${sectionCard({icon: 'ticket-perforated', title: 'Subscriptions', badge: count, className: 'list-card'},
                taskContent(this.loadDataTask, this.loadDataTask.value ?? null,
                    'Failed to load the subscriptions. Please try again.',
                    (loaded) => this.renderContent(loaded)))}
            <alfio-linked-events-dialog></alfio-linked-events-dialog>
        `;
    }

    private renderSpinner(): TemplateResult {
        return html`
            <div class="state-message">
                <sl-spinner></sl-spinner>
            </div>
        `;
    }

    private renderContent(data: SubscriptionsListData | null): TemplateResult {
        if (data == null) {
            return this.renderSpinner();
        }
        if (data.subscriptions.length === 0) {
            return this.renderEmpty();
        }
        return this.renderSubscriptions(data);
    }

    private renderEmpty(): TemplateResult {
        return emptyState('No subscriptions have been found');
    }

    private renderSubscriptions(data: SubscriptionsListData): TemplateResult {
        return html`
            ${repeat(data.subscriptions, (sub) => sub.descriptor.id, (sub) => this.renderSubscription(data, sub))}
        `;
    }

    private renderSubscription(data: SubscriptionsListData, sub: SubscriptionWithStatistics): TemplateResult {
        const descriptor = sub.descriptor;
        const title = Object.values(descriptor.titleAsText ?? descriptor.title)[0] ?? descriptor.id;
        const description = Object.values(descriptor.descriptionAsText ?? descriptor.description)[0] ?? '';
        return html`
            <div class="subscription-item">
                <a class="event-row subscription-row" title=${descriptor.id}
                   href=${adminUrl(`/subscriptions/${descriptor.organizationId}/${descriptor.id}/edit`)}
                   aria-label=${`Edit ${title}`}>
                    <div class=${classMap({ 'event-logo': true, placeholder: descriptor.fileBlobId == null })}>
                        ${when(descriptor.fileBlobId != null,
                            () => html`<img src=${`/file/${descriptor.fileBlobId}`} alt="${title} logo">`,
                            () => html`<sl-icon name="ticket-perforated" aria-label="No image"></sl-icon>`)}
                    </div>
                    <div class="event-main">
                        <div class="event-heading">
                            <span class="event-title">${title}</span>
                            ${when(descriptor.isPublic,
                                () => html`<sl-badge variant="success" pill><sl-icon name="eye" aria-hidden="true"></sl-icon> Visible</sl-badge>`,
                                () => html`<sl-badge variant="neutral" pill><sl-icon name="eye-slash" aria-hidden="true"></sl-icon> Hidden</sl-badge>`)}
                            ${when(data.ticketsGenerationJobActive && descriptor.supportsTicketsGeneration === true,
                                () => html`<sl-badge variant="primary" pill><sl-icon name="ticket" aria-hidden="true"></sl-icon> Automatic tickets generation</sl-badge>`,
                                () => nothing)}
                        </div>
                        <div class="event-date">
                            <sl-icon name="calendar-event" aria-hidden="true"></sl-icon>
                            <alfio-format-date date=${descriptor.onSaleFrom} end=${descriptor.onSaleTo} time-zone=${descriptor.timeZone}></alfio-format-date>
                        </div>
                        ${when(descriptor.validityType === 'CUSTOM', () => html`
                            <div class="event-date">
                                <sl-icon name="clock" aria-hidden="true"></sl-icon>
                                <alfio-format-date date=${descriptor.validityFrom} end=${descriptor.validityTo} time-zone=${descriptor.timeZone}></alfio-format-date>
                            </div>
                        `, () => nothing)}
                        ${when(description !== '', () => html`<div class="subscription-description">${description}</div>`, () => nothing)}
                        <div class="sales-row">
                            ${this.renderSales(sub)}
                            <div class="subscription-price">
                                <sl-icon name="cash" aria-hidden="true"></sl-icon>
                                ${formatAmount(sub.unitPrice, descriptor.currency)}
                                ${when(descriptor.vatStatus === 'NOT_INCLUDED', () => html`<span class="text-muted">+ Taxes</span>`, () => nothing)}
                            </div>
                        </div>
                    </div>
                </a>
                <div class="item-actions">
                    <sl-button variant="default" size="small" class="show-linked-events"
                               @click=${() => this.showLinkedEvents(descriptor.organizationId, descriptor.id)}>
                        <sl-icon slot="prefix" name="link-45deg"></sl-icon> Linked events
                    </sl-button>
                    <sl-button variant="default" size="small"
                               href=${adminUrl(`/subscriptions/${descriptor.organizationId}/${descriptor.id}/additional-fields`)}>
                        <sl-icon slot="prefix" name="info-circle"></sl-icon> Data to collect
                    </sl-button>
                    ${when(sub.reservationsCount > 0, () => html`
                        <sl-button variant="default" size="small"
                                   href=${adminUrl(`/subscriptions/${descriptor.organizationId}/${descriptor.id}/reservations`)}>
                            <sl-icon slot="prefix" name="ticket"></sl-icon> Reservations
                        </sl-button>
                    `, () => nothing)}
                </div>
            </div>
        `;
    }

    private renderSales(sub: SubscriptionWithStatistics): TemplateResult {
        const descriptor = sub.descriptor;
        let pending = '';
        if (sub.pendingCount > 0) {
            pending = ` · ${sub.pendingCount} pending`;
        }
        if (descriptor.maxAvailable > 0) {
            const soldAndPending = sub.soldCount + sub.pendingCount;
            const percentage = Math.min(100, Math.round(soldAndPending * 100 / descriptor.maxAvailable));
            const nearlyFull = percentage >= 90;
            // when nearly sold out, the remaining subscriptions say more than the percentage
            let progressLabel = `${percentage}%`;
            if (sub.availableCount > 0 && nearlyFull) {
                progressLabel = `${sub.availableCount} left`;
            }
            return html`
                <div class="ticket-summary">
                    <div class="sales-progress">
                        <div class="sales-meta">
                            <span>${sub.soldCount} of ${descriptor.maxAvailable} sold${pending}</span>
                            <span>${progressLabel}</span>
                        </div>
                        <sl-progress-bar class=${classMap({ 'nearly-full': nearlyFull })} value=${percentage}
                                         aria-label="${percentage}% of the subscriptions sold"></sl-progress-bar>
                    </div>
                </div>
            `;
        }
        return html`
            <div class="ticket-summary">
                <div class="sales-meta">
                    <span>${sub.soldCount} sold${pending}</span>
                </div>
            </div>
        `;
    }

    private async showLinkedEvents(organizationId: number, subscriptionId: string): Promise<void> {
        const links = await SubscriptionService.loadLinkedEvents(organizationId, subscriptionId);
        if (links.length === 0) {
            dispatchFeedback({type: 'neutral', message: 'No linked events found.'}, this);
            return;
        }
        const dialog = this.renderRoot.querySelector<LinkedEventsDialog>('alfio-linked-events-dialog');
        await dialog?.show(links);
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-subscriptions-list': SubscriptionsList;
    }
}
