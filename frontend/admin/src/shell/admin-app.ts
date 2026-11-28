import { html, LitElement, nothing, TemplateResult } from 'lit';
import { customElement, query, state } from 'lit/decorators.js';
import { provide } from '@lit/context';
import { when } from 'lit/directives/when.js';
import type SlDialog from '@shoelace-style/shoelace/dist/components/dialog/dialog.js';
import { AdminConfig, adminConfig, adminConfigContext } from '../admin-config';
import {
    adminRoot,
    adminRoute,
    createAdminRouter,
    legacyHashToRoute,
    navigate,
    PAGE_NAME_EVENT,
    ROUTE_CHANGED_EVENT,
    RouteChangedDetail,
    toAdminUrl,
} from '../admin-router';
import { ADMIN_ROUTES, routeTitles } from '../admin-routes';
import { NOT_AUTHORIZED_EVENT, NOT_LOGGED_IN_EVENT } from '../service/helpers';
import { clearSessionStorage } from './admin-navbar';
import { t } from '../service/i18n';

const APP_TITLE = t('admin.shell.app-title');
/** kept across reloads: the draft of the event being created (see event-create.ts) */
const EVENT_DRAFT_KEY = 'new_event';

/**
 * The admin: the navbar, the page of the current route, the footer and the dialogs every page can need.
 *
 * Rendered in light DOM, so that the pages stay reachable from the document (E2E tests, document.querySelector);
 * its styles are in main.css ("alfio-admin-app").
 */
@customElement('alfio-admin-app')
export class AdminApp extends LitElement {

    @provide({ context: adminConfigContext })
    private readonly config: AdminConfig = adminConfig();

    @state() private devNoticeDismissed = false;
    @query('.main-content') private main?: HTMLElement;
    @query('.not-authorized-dialog') private notAuthorizedDialog?: SlDialog;
    @query('alfio-admin-navbar') private navbar?: HTMLElement;

    private readonly router;
    private navbarObserver: ResizeObserver | null = null;
    /** the name of what the current page is about, e.g. the event, for the title */
    private pageName = '';
    /** scroll position of each history entry, by its key */
    private readonly scrollPositions = new Map<string, number>();
    private firstNavigation = true;

    constructor() {
        super();
        // links to the AngularJS admin (/admin/#/events/x/detail) and their bookmarks keep working
        const legacyRoute = legacyHashToRoute(window.location.hash);
        if (legacyRoute != null) {
            window.history.replaceState(window.history.state, '', toAdminUrl(legacyRoute, this.config.contextPath));
        }
        const root = adminRoot(this.config.contextPath);
        this.router = createAdminRouter(this, ADMIN_ROUTES.map(route => {
            let path = `${root}${route.path}`;
            if (route.path === '/') {
                path = `${root}{/}?`;
            }
            return { path, render: route.render };
        }));
        window.history.scrollRestoration = 'manual';
    }

    // light DOM, see the class comment
    protected createRenderRoot(): HTMLElement {
        return this;
    }

    connectedCallback(): void {
        super.connectedCallback();
        window.addEventListener(ROUTE_CHANGED_EVENT, this.onRouteChanged);
        window.addEventListener(PAGE_NAME_EVENT, this.onPageName);
        window.addEventListener(NOT_LOGGED_IN_EVENT, this.onNotLoggedIn);
        window.addEventListener(NOT_AUTHORIZED_EVENT, this.onNotAuthorized);
        window.addEventListener('scroll', this.onScroll, { passive: true });
        window.addEventListener('beforeunload', this.onBeforeUnload);
        window.addEventListener('hashchange', this.onHashChange);
    }

    disconnectedCallback(): void {
        window.removeEventListener(ROUTE_CHANGED_EVENT, this.onRouteChanged);
        window.removeEventListener(PAGE_NAME_EVENT, this.onPageName);
        window.removeEventListener(NOT_LOGGED_IN_EVENT, this.onNotLoggedIn);
        window.removeEventListener(NOT_AUTHORIZED_EVENT, this.onNotAuthorized);
        window.removeEventListener('scroll', this.onScroll);
        window.removeEventListener('beforeunload', this.onBeforeUnload);
        window.removeEventListener('hashchange', this.onHashChange);
        this.navbarObserver?.disconnect();
        super.disconnectedCallback();
    }

    protected firstUpdated(): void {
        // the event menu sticks below the navbar, whose height changes when its links wrap
        const navbar = this.navbar;
        if (navbar != null) {
            this.navbarObserver = new ResizeObserver(() => {
                this.style.setProperty('--admin-navbar-height', `${Math.round(navbar.getBoundingClientRect().height)}px`);
            });
            this.navbarObserver.observe(navbar);
        }
    }

    protected render(): TemplateResult {
        const config = this.config;
        const eventPage = adminRoute().startsWith('/events/') && !adminRoute().startsWith('/events/new');
        return html`
            <a class="skip-link" href="#main-content" @click=${this.skipToContent}>${t('admin.shell.skip-to-content')}</a>
            <alfio-admin-navbar></alfio-admin-navbar>
            <main class="main-content" id="main-content" tabindex="-1">
                ${when(config.errorMessage != null && config.errorMessage !== '', () => html`
                    <div class="container">
                        <sl-alert variant="danger" open>
                            <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                            <strong>${config.errorMessage}</strong>
                        </sl-alert>
                    </div>
                `)}
                ${this.router.outlet()}
            </main>
            ${when(!eventPage, () => html`
                <footer class="admin-footer">
                    <alfio-project-banner data-full-banner=${String(config.displayProjectBanner)} data-alfio-version=${config.alfioVersion}></alfio-project-banner>
                </footer>
            `)}
            ${this.renderModeNotice()}
            <sl-dialog class="not-authorized-dialog responsive-dialog" label=${t('admin.shell.not-allowed.title')}>
                <p>${t('admin.shell.not-allowed.text')}</p>
                <sl-button slot="footer" variant="default" size="large" @click=${this.closeNotAuthorized}>${t('admin.common.close')}</sl-button>
            </sl-dialog>
            <alfio-feedback-visualizer></alfio-feedback-visualizer>
            ${when(config.basicConfigurationNeeded, () => html`<alfio-basic-settings-dialog></alfio-basic-settings-dialog>`)}
        `;
    }

    private renderModeNotice(): TemplateResult | typeof nothing {
        if (this.config.devModeEnabled && !this.devNoticeDismissed) {
            return html`
                <sl-alert class="mode-notice" variant="primary" open closable @sl-after-hide=${this.dismissDevNotice}>
                    <sl-icon slot="icon" name="info-circle"></sl-icon>
                    ${t('admin.shell.development-mode')}
                </sl-alert>
            `;
        }
        if (this.config.prodModeEnabled && window.location.protocol !== 'https:') {
            return html`
                <sl-alert class="mode-notice" variant="danger" open>
                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                    ${t('admin.shell.production-without-https')}
                </sl-alert>
            `;
        }
        return nothing;
    }

    private onRouteChanged = async (e: Event): Promise<void> => {
        const { popped } = (e as CustomEvent<RouteChangedDetail>).detail;
        const first = this.firstNavigation;
        this.firstNavigation = false;
        this.pageName = '';
        const key = this.historyKey();
        // the event footer depends on the route
        this.requestUpdate();
        await this.updateComplete;
        this.updateTitle();
        if (first) {
            return;
        }
        if (popped) {
            this.restoreScroll(key);
        } else {
            window.scrollTo(0, 0);
        }
        // screen readers announce the new page
        this.main?.focus({ preventScroll: true });
    };

    private onPageName = (e: Event): void => {
        this.pageName = (e as CustomEvent<{ name: string }>).detail.name;
        this.updateTitle();
    };

    private updateTitle(): void {
        const parts = routeTitles(adminRoute());
        if (this.pageName !== '') {
            parts.push(this.pageName);
        }
        parts.push(APP_TITLE);
        document.title = parts.join(' · ');
    }

    /** every history entry gets a key, under which its scroll position is kept */
    private historyKey(): string {
        const state = window.history.state;
        let key: string | undefined = state?.alfioKey;
        if (key == null) {
            key = `${Date.now()}-${Math.random().toString(36).substring(2)}`;
            window.history.replaceState({ ...(state ?? {}), alfioKey: key }, '');
        }
        return key;
    }

    /** a link to the AngularJS admin opened while the admin is already loaded */
    private onHashChange = (): void => {
        const legacyRoute = legacyHashToRoute(window.location.hash);
        if (legacyRoute != null) {
            navigate(legacyRoute, { replace: true });
        }
    };

    private onScroll = (): void => {
        const key = window.history.state?.alfioKey;
        if (key != null) {
            this.scrollPositions.set(key, window.scrollY);
        }
    };

    private restoreScroll(key: string): void {
        const position = this.scrollPositions.get(key) ?? 0;
        // the page may still be loading its data: try again once it had time to render
        window.scrollTo(0, position);
        setTimeout(() => {
            if (window.history.state?.alfioKey === key) {
                window.scrollTo(0, position);
            }
        }, 300);
    }

    private skipToContent = (e: Event): void => {
        e.preventDefault();
        this.main?.focus();
    };

    private dismissDevNotice = (): void => {
        this.devNoticeDismissed = true;
    };

    private onNotLoggedIn = (): void => {
        // the server sends to the login page, and back here after it
        window.location.reload();
    };

    private onNotAuthorized = (): void => {
        void this.notAuthorizedDialog?.show();
    };

    private closeNotAuthorized = (): void => {
        void this.notAuthorizedDialog?.hide();
    };

    /** keeps the unfinished event across reloads; logout still clears it */
    private onBeforeUnload = (): void => {
        try {
            const eventDraft = window.sessionStorage.getItem(EVENT_DRAFT_KEY);
            clearSessionStorage();
            if (eventDraft != null) {
                window.sessionStorage.setItem(EVENT_DRAFT_KEY, eventDraft);
            }
        } catch {
            // session storage can be unavailable
        }
    };
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-admin-app': AdminApp;
    }
}
