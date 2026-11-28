import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {AlfioEvent} from '../../model/event.ts';
import {WaitingQueueSubscription, WaitingQueueSubscriptionType} from '../../model/waiting-queue.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {dispatchEventChanged, EventService} from '../../service/event.ts';
import {errorMessage} from '../../service/helpers.ts';
import {localizedTitle} from '../../service/reservation-format.ts';
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
import {emptyState} from '../../components/empty-state.ts';
import {ListSearchController} from '../../components/list-search.ts';
import {sectionCard} from '../../components/section-card.ts';
import {taskContent} from '../../components/task-content.ts';
import {EventExportDialog} from '../event-sidebar/export-dialog.ts';
import '../../components/format-date.ts';
import '../event-sidebar/export-dialog.ts';
import { currentLanguage, t, tHtml } from '../../service/i18n.ts';

interface WaitingListData {
    event: AlfioEvent;
    subscriptions: WaitingQueueSubscription[];
}

const TYPES: Record<WaitingQueueSubscriptionType, string> = {
    PRE_SALES: t('admin.waiting-list.type.pre-sales'),
    SOLD_OUT: t('admin.waiting-list.type.sold-out'),
};

// in the language of the admin UI
const languageNames = new Intl.DisplayNames([currentLanguage()], { type: 'language' });

/**
 * The people waiting on the waiting list of an event, to be notified when tickets become available.
 * The backend lists only the waiting ones: a removed person disappears, and can be restored right after
 * the removal with Undo
 */
@customElement('alfio-waiting-list')
export class WaitingList extends LitElement {
    @property({ type: String, attribute: 'data-event-name' }) eventName = '';
    @state() private search = '';
    @state() private lastData: WaitingListData | null = null;
    // id of the subscription being removed or restored
    @state() private changing: number | null = null;
    // the person just removed, whose removal can be undone
    @state() private removed: WaitingQueueSubscription | null = null;
    @query('alfio-event-export-dialog') private exportDialog!: EventExportDialog;

    private readonly searchController = new ListSearchController(this, (search) => {
        this.search = search;
    });

    private readonly loadDataTask = new Task(this, {
        task: async ([eventName]): Promise<WaitingListData> => {
            const [eventWithOrganization, subscriptions] = await Promise.all([
                EventService.load(eventName),
                EventService.loadWaitingQueue(eventName),
            ]);
            return { event: eventWithOrganization.event, subscriptions };
        },
        args: () => [this.eventName] as const,
        onComplete: (data) => {
            this.lastData = data;
        },
    });

    static readonly styles = [
        base,
        retroCompat,
        textColors,
        badges,
        modernTable,
        modernLayout,
        purchaseContextListPage,
        reservationTable,
        css`
            .removed-alert {
                margin-bottom: var(--sl-spacing-large);
            }
            .alert-content {
                display: flex;
                flex-wrap: wrap;
                align-items: center;
                justify-content: space-between;
                gap: var(--sl-spacing-x-small) var(--sl-spacing-medium);
            }
            .subscriber-name {
                font-weight: var(--sl-font-weight-semibold);
            }
            .subscription-type {
                display: block;
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-small);
            }
            .language {
                white-space: nowrap;
            }
            .section-card .table-responsive {
                margin: 0;
            }
        `,
    ];

    render(): TemplateResult {
        return taskContent(this.loadDataTask, this.lastData, t('admin.waiting-list.load-failed'),
            (data) => this.renderContent(data));
    }

    private renderContent(data: WaitingListData): TemplateResult {
        const filtered = this.filter(data.subscriptions);
        return html`
            <div class="container">
                <div class="page-title-row secondary">
                    <h1>${tHtml('admin.waiting-list.title', html`<i>${localizedTitle(data.event)}</i>`)}</h1>
                </div>
                <hr class="page-separator" />
                <p class="page-description text-muted">
                    ${t('admin.waiting-list.description')}
                </p>
                <div class="filter-toolbar">
                    <div class="filter-left">
                        ${this.searchController.render(t('admin.waiting-list.items'))}
                    </div>
                    ${when(data.subscriptions.length > 0, () => html`
                        <div class="filter-right">
                            <sl-button id="download-waiting-list" variant="default" size="large"
                                       @click=${() => void this.exportDialog.show(this.eventName, 'waiting-list')}>
                                <sl-icon slot="prefix" name="download"></sl-icon>
                                ${t('admin.event.menu.download')}
                            </sl-button>
                        </div>
                    `)}
                </div>
                ${this.renderRemovedAlert()}
                ${sectionCard({
                    icon: 'people',
                    title: t('admin.waiting-list.people'),
                    badge: html`<sl-badge variant="primary" pill>${filtered.length}</sl-badge>`,
                    className: 'list-card',
                }, this.renderList(data, filtered))}
            </div>
            <alfio-event-export-dialog></alfio-event-export-dialog>
        `;
    }

    private renderList(data: WaitingListData, subscriptions: WaitingQueueSubscription[]): TemplateResult {
        if (data.subscriptions.length === 0) {
            return emptyState(t('admin.waiting-list.none'));
        }
        if (subscriptions.length === 0) {
            return emptyState(t('admin.waiting-list.none-matching'));
        }
        return html`
            <div class="table-responsive">
                <table class="table table-striped table-stacked">
                    <thead>
                        <tr>
                            <th>${t('admin.common.name')}</th>
                            <th class="hide-small">${t('admin.common.email-short')}</th>
                            <th class="hide-small">${t('admin.category-select.label')}</th>
                            <th class="hide-small">${t('admin.reservation-create.language')}</th>
                            <th class="hide-small">${t('admin.waiting-list.joined')}</th>
                            <th><span class="sr-only">${t('admin.common.actions')}</span></th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(subscriptions, (s) => s.id, (s) => this.renderRow(data, s))}
                    </tbody>
                </table>
            </div>
        `;
    }

    private renderRow(data: WaitingListData, subscription: WaitingQueueSubscription): TemplateResult {
        return html`
            <tr class="waiting-list-row" data-id=${subscription.id}>
                <td class="subscriber-name">${subscription.fullName}</td>
                <td class="email hide-small" data-label=${t('admin.common.email-short')}>${subscription.emailAddress}</td>
                <td class="hide-small" data-label=${t('admin.category-select.label')}>${this.categoryName(data.event, subscription.selectedCategoryId)}</td>
                <td class="language hide-small" data-label=${t('admin.reservation-create.language')}>${this.languageName(subscription.userLanguage)}</td>
                <td class="timestamp hide-small" data-label=${t('admin.waiting-list.joined')}>
                    <alfio-format-date date=${subscription.creation} time-zone=${data.event.timeZone}></alfio-format-date>
                    ${when(subscription.subscriptionType != null, () => html`
                        <span class="subscription-type">${TYPES[subscription.subscriptionType as WaitingQueueSubscriptionType]}</span>
                    `)}
                </td>
                <td class="actions-cell">${this.renderActions(subscription)}</td>
            </tr>
        `;
    }

    private renderActions(subscription: WaitingQueueSubscription): TemplateResult {
        const changing = this.changing === subscription.id;
        return html`
            <sl-button class="remove-subscriber" size="small" variant="danger" outline ?loading=${changing}
                       ?disabled=${this.changing != null && !changing}
                       @click=${() => void this.removeSubscriber(subscription)}>
                <sl-icon slot="prefix" name="trash"></sl-icon>
                ${t('admin.common.remove')}
            </sl-button>
        `;
    }

    private renderRemovedAlert(): TemplateResult | typeof nothing {
        const removed = this.removed;
        if (removed == null) {
            return nothing;
        }
        return html`
            <sl-alert open variant="success" class="removed-alert" closable
                      @sl-after-hide=${(e: Event) => { if (e.target === e.currentTarget) { this.removed = null; } }}>
                <sl-icon slot="icon" name="check2-circle"></sl-icon>
                <div class="alert-content">
                    <span>
                        ${t('admin.waiting-list.removed', removed.fullName, removed.emailAddress)}
                    </span>
                    <sl-button variant="default" size="small" class="undo-remove" ?loading=${this.changing === removed.id}
                               @click=${() => void this.restoreSubscriber(removed)}>
                        <sl-icon slot="prefix" name="arrow-counterclockwise"></sl-icon>
                        ${t('admin.common.undo')}
                    </sl-button>
                </div>
            </sl-alert>
        `;
    }

    private filter(subscriptions: WaitingQueueSubscription[]): WaitingQueueSubscription[] {
        const search = this.search.trim().toLowerCase();
        return subscriptions.filter((s) => {
            if (search === '') {
                return true;
            }
            return s.fullName.toLowerCase().includes(search) || s.emailAddress.toLowerCase().includes(search);
        });
    }

    private categoryName(event: AlfioEvent, categoryId: number | null): string {
        if (categoryId == null) {
            return t('admin.waiting-list.any-category');
        }
        const category = event.ticketCategories.find((c) => c.id === categoryId);
        if (category == null) {
            return '';
        }
        return category.name;
    }

    private languageName(language: string): string {
        try {
            return languageNames.of(language) ?? language;
        } catch {
            return language;
        }
    }

    private async removeSubscriber(subscription: WaitingQueueSubscription): Promise<void> {
        const done = await this.change(subscription,
            () => EventService.removeFromWaitingQueue(this.eventName, subscription.id),
            t('admin.waiting-list.remove-failed'));
        if (done) {
            this.removed = subscription;
        }
    }

    private async restoreSubscriber(subscription: WaitingQueueSubscription): Promise<void> {
        const done = await this.change(subscription,
            () => EventService.restoreInWaitingQueue(this.eventName, subscription.id),
            t('admin.waiting-list.restore-failed'));
        if (done) {
            this.removed = null;
            dispatchFeedback({ type: 'success', message: t('admin.waiting-list.restored', subscription.fullName) }, this);
        }
    }

    /**
     * Sends the change and shows the updated list. Returns false if the change failed
     */
    private async change(subscription: WaitingQueueSubscription,
                         request: () => Promise<WaitingQueueSubscription[]>,
                         failure: string): Promise<boolean> {
        this.changing = subscription.id;
        try {
            const subscriptions = await request();
            if (this.lastData != null) {
                this.lastData = { ...this.lastData, subscriptions };
            }
            // the event menu shows the number of waiting people
            dispatchEventChanged(this.eventName, this);
            return true;
        } catch (error) {
            dispatchFeedback({ type: 'danger', message: errorMessage(error, failure) }, this);
            return false;
        } finally {
            this.changing = null;
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-waiting-list': WaitingList;
    }
}
