import { html, LitElement, TemplateResult } from 'lit';
import { customElement, property } from 'lit/decorators.js';
import { Task } from '@lit/task';
import { createChildRoutes, PAGE_NAME_EVENT, ROUTE_CHANGED_EVENT } from '../admin-router';
import { EVENT_ROUTES, RoutedEvent } from '../admin-routes';
import { EventService } from '../service/event';
import { taskContent } from '../components/task-content';
import { t } from '../service/i18n';

/**
 * The pages of an event (/admin/events/{eventName}/…): the event menu (<alfio-event-sidebar>) next to the page.
 * Loads the event once for all its pages, which receive what they need from it as attributes.
 */
@customElement('alfio-event-layout')
export class EventLayout extends LitElement {

    @property({ type: String, attribute: 'data-event-name' }) eventName = '';

    private readonly routes = createChildRoutes(this, EVENT_ROUTES.map(route => ({
        path: route.path,
        render: (params) => this.renderPage((event) => route.render(params, event)),
    })), () => `/events/${this.eventName}/detail`);

    private readonly loadEventTask = new Task(this, {
        task: async ([eventName]): Promise<RoutedEvent> => {
            const { event } = await EventService.load(eventName);
            return {
                shortName: event.shortName,
                publicIdentifier: event.publicIdentifier,
                organizationId: event.organizationId,
                displayName: event.displayName,
            };
        },
        args: () => [this.eventName] as const,
        onComplete: () => this.announceName(),
    });

    // light DOM: the pages stay reachable from the document (E2E tests, document.querySelector).
    // Styles: "alfio-event-layout" in main.css
    protected createRenderRoot(): HTMLElement {
        return this;
    }

    connectedCallback(): void {
        super.connectedCallback();
        window.addEventListener(ROUTE_CHANGED_EVENT, this.announceName);
    }

    disconnectedCallback(): void {
        window.removeEventListener(ROUTE_CHANGED_EVENT, this.announceName);
        super.disconnectedCallback();
    }

    protected render(): TemplateResult {
        return html`
            <div class="event-sidebar-column">
                <alfio-event-sidebar></alfio-event-sidebar>
            </div>
            <div class="event-page">${this.routes.outlet()}</div>
        `;
    }

    private renderPage(page: (event: RoutedEvent) => TemplateResult): TemplateResult {
        return taskContent(this.loadEventTask, this.currentEvent(), t('admin.load-error.event'), page);
    }

    /** the loaded event, unless it's the previous one while moving to another event */
    private currentEvent(): RoutedEvent | null {
        const event = this.loadEventTask.value;
        if (event == null || event.shortName !== this.eventName) {
            return null;
        }
        return event;
    }

    private announceName = (): void => {
        const event = this.currentEvent();
        if (event != null) {
            window.dispatchEvent(new CustomEvent(PAGE_NAME_EVENT, { detail: { name: event.displayName } }));
        }
    };
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-event-layout': EventLayout;
    }
}
