import { html, LitElement, TemplateResult } from 'lit';
import { customElement, property } from 'lit/decorators.js';
import { Task } from '@lit/task';
import { adminRoute, createChildRoutes, PAGE_NAME_EVENT, ROUTE_CHANGED_EVENT } from '../admin-router';
import { RoutedSubscription, SUBSCRIPTION_ROUTES } from '../admin-routes';
import { SubscriptionDescriptorService } from '../service/subscription-descriptor';
import { taskContent } from '../components/task-content';
import { firstLocalized } from '../model/poll';

/**
 * The pages of a subscription (/admin/subscriptions/{organizationId}/{subscriptionId}/…), inside
 * <alfio-subscription-detail>. Loads the subscription once for all its pages.
 */
@customElement('alfio-subscription-layout')
export class SubscriptionLayout extends LitElement {

    @property({ type: String, attribute: 'data-organization-id' }) organizationId = '';
    @property({ type: String, attribute: 'data-subscription-id' }) subscriptionId = '';

    private readonly routes = createChildRoutes(this, SUBSCRIPTION_ROUTES.map(route => ({
        path: route.path,
        render: (params) => taskContent(this.loadTask, this.currentSubscription(), 'Failed to load the subscription',
            (subscription) => route.render(params, subscription)),
    })), () => `/subscriptions/${this.organizationId}/${this.subscriptionId}/reservations/`);

    private readonly loadTask = new Task(this, {
        task: async ([organizationId, subscriptionId]): Promise<RoutedSubscription & { id: string }> => {
            const descriptor = await SubscriptionDescriptorService.load(subscriptionId, Number(organizationId));
            return {
                id: subscriptionId,
                publicIdentifier: descriptor.publicIdentifier,
                organizationId: descriptor.organizationId,
                title: firstLocalized(descriptor.title, 'en'),
                timeZone: descriptor.timeZone,
            };
        },
        args: () => [this.organizationId, this.subscriptionId] as const,
        onComplete: () => this.announceName(),
    });

    // light DOM: the pages stay reachable from the document (E2E tests, document.querySelector)
    protected createRenderRoot(): HTMLElement {
        return this;
    }

    connectedCallback(): void {
        super.connectedCallback();
        window.addEventListener(ROUTE_CHANGED_EVENT, this.onRouteChange);
    }

    disconnectedCallback(): void {
        window.removeEventListener(ROUTE_CHANGED_EVENT, this.onRouteChange);
        super.disconnectedCallback();
    }

    protected render(): TemplateResult {
        // from a reservation, the back link goes to the reservations instead of the list
        const backToReservations = /^\/subscriptions\/[^/]+\/[^/]+\/reservation\//.test(adminRoute());
        return html`
            <alfio-subscription-detail data-organization-id=${this.organizationId}
                                       data-subscription-id=${this.subscriptionId}
                                       data-back-to-reservations=${String(backToReservations)}>
                ${this.routes.outlet()}
            </alfio-subscription-detail>
        `;
    }

    private currentSubscription(): RoutedSubscription | null {
        const subscription = this.loadTask.value;
        if (subscription == null || subscription.id !== this.subscriptionId) {
            return null;
        }
        return subscription;
    }

    private onRouteChange = (): void => {
        // the back link depends on the route
        this.requestUpdate();
        this.announceName();
    };

    private announceName(): void {
        const subscription = this.currentSubscription();
        if (subscription != null) {
            window.dispatchEvent(new CustomEvent(PAGE_NAME_EVENT, { detail: { name: subscription.title } }));
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-subscription-layout': SubscriptionLayout;
    }
}
