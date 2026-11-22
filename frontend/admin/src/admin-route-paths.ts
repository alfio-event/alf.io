/**
 * The URL arithmetic of the admin router (admin-router.ts), without browser dependencies.
 */

export function adminRoot(contextPath: string): string {
    return `${contextPath}/admin`;
}

/** "/events/x/detail" -> "/ctx/admin/events/x/detail" */
export function toAdminUrl(route: string, contextPath: string): string {
    let normalized = route;
    if (!normalized.startsWith('/')) {
        normalized = `/${normalized}`;
    }
    return `${adminRoot(contextPath)}${normalized}`;
}

/** "/ctx/admin/events/x/detail" + "?a=b" -> "/events/x/detail?a=b"; null outside the admin */
export function toAdminRoute(pathname: string, search: string, contextPath: string): string | null {
    const root = adminRoot(contextPath);
    if (pathname !== root && !pathname.startsWith(`${root}/`)) {
        return null;
    }
    let route = pathname.substring(root.length);
    if (route === '') {
        route = '/';
    }
    return `${route}${search}`;
}

/** "#/events/x/detail?a=b" (AngularJS) -> "/events/x/detail?a=b"; null when the hash is not a route */
export function legacyHashToRoute(hash: string): string | null {
    let route: string | null = null;
    if (hash.startsWith('#/')) {
        route = hash.substring(1);
    } else if (hash.startsWith('#!/')) {
        route = hash.substring(2);
    }
    return route;
}

/** true for the URLs the router must not handle: outside the admin, or the admin's API */
export function isOutsideAdminRoutes(pathname: string, contextPath: string): boolean {
    const root = adminRoot(contextPath);
    if (pathname !== root && !pathname.startsWith(`${root}/`)) {
        return true;
    }
    return pathname.startsWith(`${root}/api/`) || pathname === `${root}/api` || pathname.startsWith(`${root}/i18n/`);
}

/** URLPattern groups are not decoded */
export function decodeParam(value: string | undefined): string {
    if (value == null) {
        return '';
    }
    try {
        return decodeURIComponent(value);
    } catch {
        return value;
    }
}
