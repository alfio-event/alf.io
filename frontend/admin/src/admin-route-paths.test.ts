import { describe, expect, it } from 'vitest';
import { decodeParam, isOutsideAdminRoutes, legacyHashToRoute, toAdminRoute, toAdminUrl } from './admin-route-paths';

describe('toAdminUrl', () => {
    it('puts the route below /admin', () => {
        expect(toAdminUrl('/events/x/detail', '')).toBe('/admin/events/x/detail');
        expect(toAdminUrl('/events/x/detail?search=a', '')).toBe('/admin/events/x/detail?search=a');
    });

    it('keeps the context path', () => {
        expect(toAdminUrl('/events/x/detail', '/alfio')).toBe('/alfio/admin/events/x/detail');
    });

    it('accepts routes without leading slash, and the root', () => {
        expect(toAdminUrl('users/', '')).toBe('/admin/users/');
        expect(toAdminUrl('/', '')).toBe('/admin/');
        expect(toAdminUrl('', '/ctx')).toBe('/ctx/admin/');
    });
});

describe('toAdminRoute', () => {
    it('is the inverse of toAdminUrl', () => {
        expect(toAdminRoute('/admin/events/x/detail', '?search=a', '')).toBe('/events/x/detail?search=a');
        expect(toAdminRoute('/ctx/admin/users/', '', '/ctx')).toBe('/users/');
    });

    it('maps the admin root to "/"', () => {
        expect(toAdminRoute('/admin', '', '')).toBe('/');
        expect(toAdminRoute('/admin/', '', '')).toBe('/');
    });

    it('is null outside the admin', () => {
        expect(toAdminRoute('/event/x', '', '')).toBeNull();
        expect(toAdminRoute('/administrator', '', '')).toBeNull();
        expect(toAdminRoute('/admin/x', '', '/ctx')).toBeNull();
    });
});

describe('legacyHashToRoute', () => {
    it('turns an AngularJS hash into a route', () => {
        expect(legacyHashToRoute('#/events/x/detail')).toBe('/events/x/detail');
        expect(legacyHashToRoute('#/events/x/reservations/?search=abc')).toBe('/events/x/reservations/?search=abc');
        expect(legacyHashToRoute('#/')).toBe('/');
        expect(legacyHashToRoute('#!/users/')).toBe('/users/');
    });

    it('ignores hashes that are not routes', () => {
        expect(legacyHashToRoute('')).toBeNull();
        expect(legacyHashToRoute('#')).toBeNull();
        expect(legacyHashToRoute('#section-2')).toBeNull();
    });
});

describe('isOutsideAdminRoutes', () => {
    it('is false for admin pages', () => {
        expect(isOutsideAdminRoutes('/admin', '')).toBe(false);
        expect(isOutsideAdminRoutes('/admin/', '')).toBe(false);
        expect(isOutsideAdminRoutes('/admin/events/x/detail', '')).toBe(false);
        expect(isOutsideAdminRoutes('/ctx/admin/events/x/detail', '/ctx')).toBe(false);
        // a page whose name starts like the API
        expect(isOutsideAdminRoutes('/admin/api-keys/', '')).toBe(false);
    });

    it('is true for the API, the translations and anything outside the admin', () => {
        expect(isOutsideAdminRoutes('/admin/api/events/x/export', '')).toBe(true);
        expect(isOutsideAdminRoutes('/admin/api', '')).toBe(true);
        expect(isOutsideAdminRoutes('/admin/i18n/bundle/en', '')).toBe(true);
        expect(isOutsideAdminRoutes('/event/x', '')).toBe(true);
        expect(isOutsideAdminRoutes('/administrator', '')).toBe(true);
        expect(isOutsideAdminRoutes('/admin/events', '/ctx')).toBe(true);
    });
});

describe('decodeParam', () => {
    it('decodes URL-encoded values', () => {
        expect(decodeParam('my%20event')).toBe('my event');
        expect(decodeParam('plain')).toBe('plain');
    });

    it('returns malformed values and missing ones safely', () => {
        expect(decodeParam('%E0%A4%A')).toBe('%E0%A4%A');
        expect(decodeParam(undefined)).toBe('');
    });
});
