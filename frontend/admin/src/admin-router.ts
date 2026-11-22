// URLPattern is missing in Safari < 26 and older Firefox: the polyfill installs itself only where it's missing
import 'urlpattern-polyfill';
import { Router, Routes } from '@lit-labs/router';
import type { BaseRouteConfig, PathRouteConfig } from '@lit-labs/router';
import type { ReactiveControllerHost } from 'lit';
import { adminConfig } from './admin-config';
import { isOutsideAdminRoutes, toAdminRoute, toAdminUrl } from './admin-route-paths';

export { adminRoot, decodeParam, legacyHashToRoute, toAdminUrl } from './admin-route-paths';

/**
 * Routing of the admin. This is the only module that uses @lit-labs/router: pages navigate with navigate() and
 * build links with adminUrl(), so that the router can be replaced without touching them.
 *
 * A "route" is the part of the URL below `${contextPath}/admin`, with its query: "/events/my-event/detail?search=x".
 * It's what the hash contained when the admin used hash routing.
 */

/** dispatched on window after every navigation; detail: {popped: boolean} */
export const ROUTE_CHANGED_EVENT = 'alfio-route-changed';

/** dispatched on window before leaving a page; listeners add Promise<boolean> (true = leave) to detail.confirmations */
export const BEFORE_LEAVE_EVENT = 'alfio-before-leave';

/** dispatched on window by a layout with the name of what its pages are about (an event), for the page title */
export const PAGE_NAME_EVENT = 'alfio-page-name';

export interface RouteChangedDetail {
    /** true for Back/Forward */
    popped: boolean;
}

// -- runtime

/** the URL of an admin page, for links: adminUrl("/events/x/detail") */
export function adminUrl(route: string): string {
    return toAdminUrl(route, adminConfig().contextPath);
}

/** the current route, e.g. "/events/x/detail?search=a" */
export function adminRoute(): string {
    return toAdminRoute(window.location.pathname, window.location.search, adminConfig().contextPath) ?? '/';
}

let router: Router | null = null;

/** goes to an admin page: navigate("/events/x/detail") */
export function navigate(route: string, options: { replace?: boolean } = {}): void {
    const url = adminUrl(route);
    if (router == null) {
        window.location.assign(url);
        return;
    }
    if (options.replace === true) {
        window.history.replaceState({}, '', url);
    } else {
        window.history.pushState({}, '', url);
    }
    void router.goto(window.location.pathname);
}

export type AdminRouteConfig = PathRouteConfig;

/**
 * The top-level router of the shell. Every route is guarded: before leaving a page, the pages listening to
 * BEFORE_LEAVE_EVENT can keep the user on it. Paths outside the admin routes are loaded by the browser.
 */
export function createAdminRouter(host: ReactiveControllerHost & HTMLElement, routes: AdminRouteConfig[]): Router {
    const guarded = routes.map(route => ({ ...route, enter: guardedEnter(route) }));
    router = new AdminRouter(host, guarded, { fallback: fallbackRoute });
    return router;
}

/** the routes of a layout below the top-level router, e.g. the pages of an event */
export interface ChildRoutes {
    outlet(): unknown;
    readonly params: Record<string, string | undefined>;
}

/**
 * Child routes, relative to the tail ("/*") of the parent route that renders the host.
 * An unknown child page goes to defaultRoute.
 */
export function createChildRoutes(host: ReactiveControllerHost & HTMLElement, routes: AdminRouteConfig[],
                                  defaultRoute: () => string): ChildRoutes {
    return new Routes(host, routes, {
        fallback: {
            enter: () => {
                setTimeout(() => navigate(defaultRoute(), { replace: true }));
                return false;
            },
        },
    });
}

/** the URL of the page being displayed; null before the first navigation */
let currentUrl: string | null = null;
/** true while the router handles a Back/Forward navigation */
let popped = false;
/** id of the latest navigation, whether it is a Back/Forward, and the id of the latest one the guard let through */
let navigation = 0;
let navigationPopped = false;
let acceptedNavigation = -1;

// registered before the router's own listener (the router connects later), so it runs first
window.addEventListener('popstate', () => {
    popped = true;
});

class AdminRouter extends Router {
    override async goto(pathname: string): Promise<void> {
        const id = ++navigation;
        const wasPopped = popped;
        popped = false;
        navigationPopped = wasPopped;
        await super.goto(pathname);
        if (acceptedNavigation === id) {
            currentUrl = window.location.href;
            window.dispatchEvent(new CustomEvent<RouteChangedDetail>(ROUTE_CHANGED_EVENT, {
                detail: { popped: wasPopped },
            }));
        }
    }
}

function guardedEnter(route: AdminRouteConfig): (params: Record<string, string | undefined>) => Promise<boolean> {
    return async (params) => {
        // runs synchronously from goto(): these describe the navigation being entered
        const id = navigation;
        const wasPopped = navigationPopped;
        if (!(await confirmLeave())) {
            restoreCurrentUrl(wasPopped);
            return false;
        }
        if (route.enter != null && !(await route.enter(params))) {
            return false;
        }
        acceptedNavigation = id;
        return true;
    };
}

/** asks the pages whether the user can leave; only when the page changes, not its query */
async function confirmLeave(): Promise<boolean> {
    if (currentUrl == null || new URL(currentUrl).pathname === window.location.pathname) {
        return true;
    }
    const check = new CustomEvent<{ confirmations: Promise<boolean>[] }>(BEFORE_LEAVE_EVENT, {
        detail: { confirmations: [] },
    });
    window.dispatchEvent(check);
    if (check.detail.confirmations.length === 0) {
        return true;
    }
    const answers = await Promise.all(check.detail.confirmations);
    return answers.every(Boolean);
}

/** the URL already changed (the router pushes it before entering the route): put back the page's URL */
function restoreCurrentUrl(wasPopped: boolean): void {
    if (currentUrl == null) {
        return;
    }
    if (wasPopped) {
        // Back/Forward can't be cancelled: go forward again to the page the user stays on
        window.history.pushState({}, '', currentUrl);
    } else {
        window.history.replaceState({}, '', currentUrl);
    }
}

const fallbackRoute: BaseRouteConfig = {
    enter: () => {
        const target = window.location.href;
        if (currentUrl == null) {
            // the page was loaded at a URL the admin doesn't route (the server answers 404 to the others):
            // loading it again would loop
            setTimeout(() => navigate('/', { replace: true }));
        } else if (isOutsideAdminRoutes(window.location.pathname, adminConfig().contextPath)) {
            // a same-origin link that is not an admin page (an export, a public page): the router already pushed
            // its URL; put back the page's one and let the browser load the link (a download keeps the page)
            window.history.replaceState({}, '', currentUrl);
            window.location.assign(target);
        } else {
            // an unknown admin page: once this navigation is over, go to the events
            setTimeout(() => navigate('/', { replace: true }));
        }
        return false;
    },
};

/** goes to an admin page given its URL, e.g. one built with adminUrl() */
export function navigateToUrl(url: string, options: { replace?: boolean } = {}): void {
    const parsed = new URL(url, window.location.href);
    const route = toAdminRoute(parsed.pathname, parsed.search, adminConfig().contextPath);
    if (route == null) {
        window.location.assign(parsed.href);
        return;
    }
    navigate(route, options);
}
