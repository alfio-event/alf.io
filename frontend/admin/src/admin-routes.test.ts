import 'urlpattern-polyfill';
import { describe, expect, it } from 'vitest';
import { ADMIN_ROUTES, EVENT_ROUTES, SUBSCRIPTION_ROUTES } from './admin-routes';

type Route = { path: string };

/** the first route matching the pathname, as the router picks it, with its params */
function match(routes: Route[], pathname: string): { path: string, groups: Record<string, string | undefined> } | null {
    for (const route of routes) {
        const result = new URLPattern({ pathname: route.path }).exec({ pathname });
        if (result != null) {
            return { path: route.path, groups: result.pathname.groups };
        }
    }
    return null;
}

/** resolves a route through the nested routes, like <alfio-event-layout> and <alfio-subscription-layout> do */
function resolve(route: string): string[] {
    const pathname = route.split('?')[0];
    const top = match(ADMIN_ROUTES, pathname);
    if (top == null) {
        return [];
    }
    const tail = top.groups['0'];
    if (top.path === '/events/:eventName/*') {
        const child = match(EVENT_ROUTES, tail ?? '');
        return [top.path, child?.path ?? '(none)'];
    }
    if (top.path === '/subscriptions/:organizationId/:subscriptionId/*') {
        const child = match(SUBSCRIPTION_ROUTES, tail ?? '');
        return [top.path, child?.path ?? '(none)'];
    }
    return [top.path];
}

// every URL of the AngularJS admin (ui-router states), with sample params
const LEGACY_ROUTES: Record<string, string[]> = {
    '/': ['/'],
    '/configuration/system': ['/configuration/system'],
    '/configuration/organization/1': ['/configuration/organization/:organizationId'],
    '/configuration/organization/1/event/2': ['/configuration/organization/:organizationId/event/:eventId'],
    '/organizations/': ['/organizations{/}?'],
    '/organizations/new': ['/organizations/new'],
    '/organizations/1/edit': ['/organizations/:organizationId/edit'],
    '/show-resources/TICKET_EMAIL/': ['/show-resources/:resourceName{/}?'],
    '/organizations/1/show-resources/TICKET_EMAIL/': ['/organizations/:organizationId/show-resources/:resourceName{/}?'],
    '/users/': ['/users{/}?'],
    '/api-keys/': ['/api-keys{/}?'],
    '/profile/edit': ['/profile/edit'],
    '/events/new?eventName=copy-me': ['/events/new'],
    '/events/ev/detail': ['/events/:eventName/*', 'detail'],
    '/events/ev/edit': ['/events/:eventName/*', 'edit'],
    '/events/ev/configuration': ['/events/:eventName/*', 'configuration'],
    '/events/ev/attendee-data-to-collect': ['/events/:eventName/*', 'attendee-data-to-collect'],
    '/events/ev/promo-codes': ['/events/:eventName/*', 'promo-codes'],
    '/events/ev/additional-services': ['/events/:eventName/*', 'additional-services'],
    '/events/ev/donations': ['/events/:eventName/*', 'donations'],
    '/events/ev/check-in?search=a&page=2': ['/events/:eventName/*', 'check-in'],
    '/events/ev/check-in/scan': ['/events/:eventName/*', 'check-in/scan'],
    '/events/ev/c/3/send-invitation': ['/events/:eventName/*', 'c/:categoryId/send-invitation'],
    '/events/ev/reservations/?search=a': ['/events/:eventName/*', 'reservations{/}?'],
    '/events/ev/transactions/': ['/events/:eventName/*', 'transactions{/}?'],
    '/events/ev/email-log?search=a&page=1': ['/events/:eventName/*', 'email-log'],
    '/events/ev/email-log/5': ['/events/:eventName/*', 'email-log/:messageId'],
    '/events/ev/category/3/tickets?page=2': ['/events/:eventName/*', 'category/:categoryId/tickets'],
    '/events/ev/pending-payments/': ['/events/:eventName/*', 'pending-payments{/}?'],
    '/events/ev/compose-custom-message': ['/events/:eventName/*', 'compose-custom-message'],
    '/events/ev/waiting-queue': ['/events/:eventName/*', 'waiting-queue'],
    '/events/ev/show-resources': ['/events/:eventName/*', 'show-resources'],
    '/events/ev/show-resources/TICKET_EMAIL/': ['/events/:eventName/*', 'show-resources/:resourceName{/}?'],
    '/events/ev/reservation/new': ['/events/:eventName/*', 'reservation/new'],
    '/events/ev/reservation/import': ['/events/:eventName/*', 'reservation/import'],
    '/events/ev/reservation/import/req-1/status': ['/events/:eventName/*', 'reservation/import/:requestId/status'],
    '/events/ev/reservation/abc-def?fromCreation=true': ['/events/:eventName/*', 'reservation/:reservationId'],
    '/events/ev/polls/list': ['/events/:eventName/*', 'polls/list'],
    '/events/ev/polls/view/7': ['/events/:eventName/*', 'polls/view/:pollId'],
    '/events/ev/polls/new': ['/events/:eventName/*', 'polls/new'],
    '/events/ev/polls/edit/7': ['/events/:eventName/*', 'polls/edit/:pollId'],
    '/events/ev/polls/7/participants': ['/events/:eventName/*', 'polls/:pollId/participants'],
    '/extension/list': ['/extension/list'],
    '/extension/new': ['/extension/new'],
    '/extension/-/my-script/edit': ['/extension/:path/:name/edit'],
    '/extension/log?search=a&page=1&type=ERROR': ['/extension/log'],
    '/subscriptions': ['/subscriptions{/}?'],
    '/subscriptions/1/list': ['/subscriptions/:organizationId/list'],
    '/subscriptions/1/create': ['/subscriptions/:organizationId/create'],
    '/subscriptions/1/sub-id/edit': ['/subscriptions/:organizationId/:subscriptionId/edit'],
    '/subscriptions/1/sub-id/reservations/?search=a': ['/subscriptions/:organizationId/:subscriptionId/*', 'reservations{/}?'],
    '/subscriptions/1/sub-id/reservation/abc': ['/subscriptions/:organizationId/:subscriptionId/*', 'reservation/:reservationId'],
    '/subscriptions/1/sub-id/email-log': ['/subscriptions/:organizationId/:subscriptionId/*', 'email-log'],
    '/subscriptions/1/sub-id/email-log/5': ['/subscriptions/:organizationId/:subscriptionId/*', 'email-log/:messageId'],
    '/subscriptions/1/sub-id/additional-fields': ['/subscriptions/:organizationId/:subscriptionId/*', 'additional-fields'],
    '/group': ['/group{/}?'],
    '/group/1/all': ['/group/:organizationId/all'],
    '/group/1/edit/4': ['/group/:organizationId/edit/:groupId'],
    '/group/1/new': ['/group/:organizationId/new'],
};

describe('admin routes', () => {
    for (const [route, expected] of Object.entries(LEGACY_ROUTES)) {
        it(`resolve the AngularJS URL ${route}`, () => {
            expect(resolve(route)).toEqual(expected);
        });
    }

    it('send an event URL without page to its overview', () => {
        expect(resolve('/events/ev')).toEqual(['/events/:eventName{/}?']);
        expect(resolve('/events/ev/')).toEqual(['/events/:eventName{/}?']);
    });

    it('extract the params', () => {
        const top = match(ADMIN_ROUTES, '/events/my%20event/reservation/abc');
        expect(top?.groups.eventName).toBe('my%20event');
        expect(top?.groups['0']).toBe('reservation/abc');
        expect(match(EVENT_ROUTES, 'reservation/abc')?.groups.reservationId).toBe('abc');
    });

    it('leave unknown pages to the fallback', () => {
        expect(resolve('/unknown')).toEqual([]);
        expect(resolve('/events/ev/unknown')).toEqual(['/events/:eventName/*', '(none)']);
    });
});
