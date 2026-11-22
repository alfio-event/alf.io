import { html, nothing, TemplateResult } from 'lit';
import { decodeParam } from './admin-route-paths';

/**
 * The pages of the admin. Paths are URLPattern pathnames:
 * - ADMIN_ROUTES are relative to `${contextPath}/admin`;
 * - EVENT_ROUTES to `/events/:eventName/` (rendered by <alfio-event-layout>);
 * - SUBSCRIPTION_ROUTES to `/subscriptions/:organizationId/:subscriptionId/` (<alfio-subscription-layout>).
 *
 * The pages receive the route params as the same data-* attributes they had with AngularJS. A route renders the same
 * template whatever its params and query: Lit keeps the element when only those change, so a page keeps its state
 * (filters, pagination) when it updates its own query.
 */

export type Params = { [key: string]: string | undefined };

export interface AdminRoute {
    path: string;
    /** for document.title */
    title: string;
    render: (params: Params) => TemplateResult;
}

/** what EVENT_ROUTES need from the event, loaded by <alfio-event-layout> */
export interface RoutedEvent {
    shortName: string;
    publicIdentifier: string;
    organizationId: number;
    displayName: string;
}

export interface EventRoute {
    path: string;
    title: string;
    render: (params: Params, event: RoutedEvent) => TemplateResult;
}

/** what SUBSCRIPTION_ROUTES need from the subscription, loaded by <alfio-subscription-layout> */
export interface RoutedSubscription {
    publicIdentifier: string;
    organizationId: number;
    title: string;
    timeZone: string;
}

export interface SubscriptionRoute {
    path: string;
    title: string;
    render: (params: Params, subscription: RoutedSubscription) => TemplateResult;
}

function p(params: Params, name: string): string {
    return decodeParam(params[name]);
}

function query(name: string): string {
    return new URLSearchParams(window.location.search).get(name) ?? '';
}

// the wrappers render the same template for every child: Lit keeps the wrapper when moving between its pages
function subscriptionsPage(content: TemplateResult | typeof nothing): TemplateResult {
    return html`<alfio-subscriptions-page>${content}</alfio-subscriptions-page>`;
}

function groupsPage(content: TemplateResult | typeof nothing): TemplateResult {
    return html`<alfio-groups-page>${content}</alfio-groups-page>`;
}

function organizationsPage(mode: string, organizationId: string): TemplateResult {
    return html`<alfio-organizations-page data-mode=${mode} data-organization-id=${organizationId}></alfio-organizations-page>`;
}

export const ADMIN_ROUTES: AdminRoute[] = [
    { path: '/', title: 'Events', render: () => html`<alfio-events-page></alfio-events-page>` },

    { path: '/configuration/system', title: 'Configuration', render: () => html`<alfio-settings-page></alfio-settings-page>` },
    {
        path: '/configuration/organization/:organizationId',
        title: 'Organization configuration',
        render: (params) => html`<alfio-settings-page data-organization-id=${p(params, 'organizationId')}></alfio-settings-page>`,
    },
    {
        path: '/configuration/organization/:organizationId/event/:eventId',
        title: 'Event configuration',
        render: (params) => html`<alfio-settings-page data-event-id=${p(params, 'eventId')}></alfio-settings-page>`,
    },

    { path: '/organizations{/}?', title: 'Organizations', render: () => organizationsPage('list', '') },
    { path: '/organizations/new', title: 'New organization', render: () => organizationsPage('new', '') },
    {
        path: '/organizations/:organizationId/edit',
        title: 'Edit organization',
        render: (params) => organizationsPage('edit', p(params, 'organizationId')),
    },
    {
        path: '/show-resources/:resourceName{/}?',
        title: 'Template',
        render: (params) => html`<alfio-template-edit data-system="" data-resource-name=${p(params, 'resourceName')}></alfio-template-edit>`,
    },
    {
        path: '/organizations/:organizationId/show-resources/:resourceName{/}?',
        title: 'Template',
        render: (params) => html`<alfio-template-edit data-organization-id=${p(params, 'organizationId')} data-resource-name=${p(params, 'resourceName')}></alfio-template-edit>`,
    },

    { path: '/users{/}?', title: 'Users', render: () => html`<alfio-users alfio-title="Users" type="user"></alfio-users>` },
    { path: '/api-keys{/}?', title: 'API keys', render: () => html`<alfio-users alfio-title="API keys" type="apikey"></alfio-users>` },
    { path: '/profile/edit', title: 'Account', render: () => html`<alfio-user-profile-edit></alfio-user-profile-edit>` },

    { path: '/events/new', title: 'New event', render: () => html`<alfio-event-create></alfio-event-create>` },
    {
        path: '/events/:eventName{/}?',
        title: 'Event',
        render: (params) => html`<alfio-redirect data-route=${`/events/${params.eventName}/detail`}></alfio-redirect>`,
    },
    {
        path: '/events/:eventName/*',
        title: 'Event',
        render: (params) => html`<alfio-event-layout data-event-name=${p(params, 'eventName')}></alfio-event-layout>`,
    },

    { path: '/extension{/}?', title: 'Extensions', render: () => html`<alfio-redirect data-route="/extension/list"></alfio-redirect>` },
    { path: '/extension/list', title: 'Extensions', render: () => html`<alfio-extension-list></alfio-extension-list>` },
    { path: '/extension/new', title: 'New extension', render: () => html`<alfio-extension-edit></alfio-extension-edit>` },
    { path: '/extension/log', title: 'Extension log', render: () => html`<alfio-extension-log></alfio-extension-log>` },
    {
        path: '/extension/:path/:name/edit',
        title: 'Edit extension',
        render: (params) => html`<alfio-extension-edit data-path=${p(params, 'path')} data-name=${p(params, 'name')}></alfio-extension-edit>`,
    },

    { path: '/subscriptions{/}?', title: 'Subscriptions', render: () => subscriptionsPage(nothing) },
    {
        path: '/subscriptions/:organizationId/list',
        title: 'Subscriptions',
        render: (params) => subscriptionsPage(html`<alfio-subscriptions-list data-organization-id=${p(params, 'organizationId')}></alfio-subscriptions-list>`),
    },
    {
        path: '/subscriptions/:organizationId/create',
        title: 'New subscription',
        render: (params) => subscriptionsPage(html`<alfio-subscription-edit data-organization-id=${p(params, 'organizationId')}></alfio-subscription-edit>`),
    },
    {
        path: '/subscriptions/:organizationId/:subscriptionId/edit',
        title: 'Edit subscription',
        render: (params) => subscriptionsPage(html`<alfio-subscription-edit data-organization-id=${p(params, 'organizationId')} data-subscription-id=${p(params, 'subscriptionId')}></alfio-subscription-edit>`),
    },
    {
        path: '/subscriptions/:organizationId/:subscriptionId/*',
        title: 'Subscription',
        render: (params) => subscriptionsPage(html`<alfio-subscription-layout data-organization-id=${p(params, 'organizationId')} data-subscription-id=${p(params, 'subscriptionId')}></alfio-subscription-layout>`),
    },

    { path: '/group{/}?', title: 'Groups', render: () => groupsPage(nothing) },
    {
        path: '/group/:organizationId/all',
        title: 'Groups',
        render: (params) => groupsPage(html`<alfio-groups-list data-organization-id=${p(params, 'organizationId')}></alfio-groups-list>`),
    },
    {
        path: '/group/:organizationId/new',
        title: 'New group',
        render: (params) => groupsPage(html`<alfio-group-edit data-organization-id=${p(params, 'organizationId')} data-create-new="true"></alfio-group-edit>`),
    },
    {
        path: '/group/:organizationId/edit/:groupId',
        title: 'Edit group',
        render: (params) => groupsPage(html`<alfio-group-edit data-organization-id=${p(params, 'organizationId')} data-group-id=${p(params, 'groupId')}></alfio-group-edit>`),
    },
];

function additionalItems(event: RoutedEvent, type: string, title: string, icon: string): TemplateResult {
    return html`<alfio-additional-item-list data-public-identifier=${event.shortName} data-type=${type} data-title=${title} data-icon=${icon}></alfio-additional-item-list>`;
}

function eventEmailLog(event: RoutedEvent, messageId: string): TemplateResult {
    return html`<alfio-email-log data-purchase-context-type="event" data-public-identifier=${event.shortName} data-message-id=${messageId}></alfio-email-log>`;
}

/** relative to /events/:eventName/ */
export const EVENT_ROUTES: EventRoute[] = [
    { path: 'detail', title: 'Overview', render: (_, e) => html`<alfio-event-detail data-event-name=${e.shortName}></alfio-event-detail>` },
    { path: 'edit', title: 'Edit', render: (_, e) => html`<alfio-event-create data-event-name=${e.shortName}></alfio-event-create>` },
    { path: 'configuration', title: 'Configuration', render: (_, e) => html`<alfio-settings-page data-event-name=${e.shortName}></alfio-settings-page>` },
    {
        path: 'attendee-data-to-collect',
        title: 'Data to collect',
        render: (_, e) => html`<alfio-additional-field-list data-public-identifier=${e.publicIdentifier} data-purchase-context-type="event" data-organization-id=${e.organizationId}></alfio-additional-field-list>`,
    },
    {
        path: 'promo-codes',
        title: 'Promo codes',
        render: (_, e) => html`<alfio-promo-code data-event-name=${e.shortName} data-organization-id=${e.organizationId} data-for-event="true"></alfio-promo-code>`,
    },
    { path: 'additional-services', title: 'Additional options', render: (_, e) => additionalItems(e, 'SUPPLEMENT', 'Additional options', 'cash') },
    { path: 'donations', title: 'Donation options', render: (_, e) => additionalItems(e, 'DONATION', 'Donation options', 'gift') },
    { path: 'check-in', title: 'Check-in', render: (_, e) => html`<alfio-check-in data-event-name=${e.shortName}></alfio-check-in>` },
    { path: 'check-in/scan', title: 'Scan tickets', render: (_, e) => html`<alfio-check-in-scan data-event-name=${e.shortName}></alfio-check-in-scan>` },
    {
        path: 'c/:categoryId/send-invitation',
        title: 'Send invitations',
        render: (params, e) => html`<alfio-send-invitations data-event-name=${e.shortName} data-category-id=${p(params, 'categoryId')}></alfio-send-invitations>`,
    },
    { path: 'reservations{/}?', title: 'Reservations', render: (_, e) => html`<alfio-reservations-list data-event-name=${e.shortName}></alfio-reservations-list>` },
    { path: 'transactions{/}?', title: 'Payments', render: (_, e) => html`<alfio-payments-list data-event-name=${e.shortName}></alfio-payments-list>` },
    { path: 'email-log', title: 'E-mail log', render: (_, e) => eventEmailLog(e, '') },
    { path: 'email-log/:messageId', title: 'E-mail log', render: (params, e) => eventEmailLog(e, p(params, 'messageId')) },
    {
        path: 'category/:categoryId/tickets',
        title: 'Tickets',
        render: (params, e) => html`<alfio-tickets-list data-event-name=${e.shortName} data-category-id=${p(params, 'categoryId')}></alfio-tickets-list>`,
    },
    { path: 'pending-payments{/}?', title: 'Pending payments', render: (_, e) => html`<alfio-pending-payments data-event-name=${e.shortName}></alfio-pending-payments>` },
    { path: 'compose-custom-message', title: 'Compose message', render: (_, e) => html`<alfio-compose-message data-event-name=${e.shortName}></alfio-compose-message>` },
    { path: 'waiting-queue', title: 'Waiting list', render: (_, e) => html`<alfio-waiting-list data-event-name=${e.shortName}></alfio-waiting-list>` },
    { path: 'show-resources', title: 'Templates', render: (_, e) => html`<alfio-template-list data-event-name=${e.shortName}></alfio-template-list>` },
    {
        path: 'show-resources/:resourceName{/}?',
        title: 'Template',
        render: (params, e) => html`<alfio-template-edit data-event-name=${e.shortName} data-resource-name=${p(params, 'resourceName')}></alfio-template-edit>`,
    },
    { path: 'reservation/new', title: 'Create reservation', render: (_, e) => html`<alfio-reservation-create data-event-name=${e.shortName}></alfio-reservation-create>` },
    { path: 'reservation/import', title: 'Import attendees', render: (_, e) => html`<alfio-attendees-import data-event-name=${e.shortName}></alfio-attendees-import>` },
    {
        path: 'reservation/import/:requestId/status',
        title: 'Import status',
        render: (params, e) => html`<alfio-attendees-import-status data-event-name=${e.shortName} data-request-id=${p(params, 'requestId')}></alfio-attendees-import-status>`,
    },
    {
        path: 'reservation/:reservationId',
        title: 'Reservation',
        render: (params, e) => html`<alfio-reservation-detail data-purchase-context-type="event" data-public-identifier=${e.shortName} data-reservation-id=${p(params, 'reservationId')} data-from-creation=${query('fromCreation')}></alfio-reservation-detail>`,
    },
    { path: 'polls/list', title: 'Polls', render: (_, e) => html`<alfio-polls-list data-event-name=${e.shortName}></alfio-polls-list>` },
    {
        path: 'polls/view/:pollId',
        title: 'Poll',
        render: (params, e) => html`<alfio-poll-detail data-event-name=${e.shortName} data-poll-id=${p(params, 'pollId')}></alfio-poll-detail>`,
    },
    { path: 'polls/new', title: 'New poll', render: (_, e) => html`<alfio-poll-edit data-event-name=${e.shortName}></alfio-poll-edit>` },
    {
        path: 'polls/edit/:pollId',
        title: 'Edit poll',
        render: (params, e) => html`<alfio-poll-edit data-event-name=${e.shortName} data-poll-id=${p(params, 'pollId')}></alfio-poll-edit>`,
    },
    {
        path: 'polls/:pollId/participants',
        title: 'Poll participants',
        render: (params, e) => html`<alfio-poll-participants data-event-name=${e.shortName} data-poll-id=${p(params, 'pollId')}></alfio-poll-participants>`,
    },
];

function subscriptionEmailLog(s: RoutedSubscription, messageId: string): TemplateResult {
    return html`<alfio-email-log data-purchase-context-type="subscription" data-public-identifier=${s.publicIdentifier} data-title=${s.title} data-time-zone=${s.timeZone} data-message-id=${messageId}></alfio-email-log>`;
}

/** relative to /subscriptions/:organizationId/:subscriptionId/ */
export const SUBSCRIPTION_ROUTES: SubscriptionRoute[] = [
    {
        path: 'reservations{/}?',
        title: 'Reservations',
        render: (_, s) => html`<alfio-reservations-list data-event-name=${s.publicIdentifier} data-organization-id=${s.organizationId} data-purchase-context-type="subscription" data-title=${s.title}></alfio-reservations-list>`,
    },
    {
        path: 'reservation/:reservationId',
        title: 'Reservation',
        render: (params, s) => html`<alfio-reservation-detail data-purchase-context-type="subscription" data-public-identifier=${s.publicIdentifier} data-organization-id=${s.organizationId} data-reservation-id=${p(params, 'reservationId')}></alfio-reservation-detail>`,
    },
    { path: 'email-log', title: 'E-mail log', render: (_, s) => subscriptionEmailLog(s, '') },
    { path: 'email-log/:messageId', title: 'E-mail log', render: (params, s) => subscriptionEmailLog(s, p(params, 'messageId')) },
    {
        path: 'additional-fields',
        title: 'Additional fields',
        render: (_, s) => html`<alfio-additional-field-list data-public-identifier=${s.publicIdentifier} data-purchase-context-type="subscription" data-organization-id=${s.organizationId}></alfio-additional-field-list>`,
    },
];

/**
 * The titles of the page of a route, most specific first: ["Check-in", "Event"] for "/events/x/check-in".
 * Matches like the router: the first route whose path matches.
 */
export function routeTitles(route: string): string[] {
    const pathname = route.split('?')[0];
    const top = matchRoute(ADMIN_ROUTES, pathname);
    if (top == null) {
        return [];
    }
    let children: { path: string, title: string }[] = [];
    if (top.route.path === '/events/:eventName/*') {
        children = EVENT_ROUTES;
    } else if (top.route.path === '/subscriptions/:organizationId/:subscriptionId/*') {
        children = SUBSCRIPTION_ROUTES;
    }
    const tail = top.groups['0'];
    if (children.length > 0 && tail != null) {
        const child = matchRoute(children, tail);
        if (child != null) {
            // the layout shows the name of the event or subscription instead of its generic title
            return [child.route.title];
        }
    }
    return [top.route.title];
}

function matchRoute<R extends { path: string }>(routes: R[], pathname: string): { route: R, groups: Params } | null {
    for (const route of routes) {
        const result = new URLPattern({ pathname: route.path }).exec({ pathname });
        if (result != null) {
            return { route, groups: result.pathname.groups };
        }
    }
    return null;
}
