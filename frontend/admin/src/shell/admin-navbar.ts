import { css, html, LitElement, nothing, TemplateResult } from 'lit';
import { customElement, query, state } from 'lit/decorators.js';
import { consume } from '@lit/context';
import { repeat } from 'lit/directives/repeat.js';
import type SlDrawer from '@shoelace-style/shoelace/dist/components/drawer/drawer.js';
import { AdminConfig, adminConfigContext } from '../admin-config';
import { adminRoute, adminUrl, navigate, ROUTE_CHANGED_EVENT } from '../admin-router';
import { postJson, supportedLanguages } from '../service/helpers';
import { currentLanguage, languageName, t } from '../service/i18n';
import { badges, base } from '../styles';

interface NavLink {
    label: string;
    route: string;
    /** the routes on which the link is the current page */
    current: RegExp;
    beta?: boolean;
}

/** dispatched on window before logging out: pages asking for confirmation before unload must not block it */
export const LOGOUT_EVENT = 'alfio-logout';

/**
 * The top bar of the admin: the sections, the account, the language and the logout. Below 992px the links move into
 * a drawer.
 */
@customElement('alfio-admin-navbar')
export class AdminNavbar extends LitElement {

    @consume({ context: adminConfigContext })
    private config!: AdminConfig;

    @state() private route = adminRoute();
    @query('sl-drawer') private drawer?: SlDrawer;

    static readonly styles = [
        base,
        badges,
        css`
            :host {
                display: block;
                background: var(--sl-color-neutral-0);
                border-bottom: 1px solid var(--sl-color-gray-200);
            }
            /* the background spans the window; the content lines up with the widest content below it, the
               workspace of an event (see "Page width and alignment" in docs/admin-style-guide.md) */
            .bar {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-medium);
                box-sizing: border-box;
                max-width: calc(var(--alfio-workspace-width, 100%) + 2 * var(--sl-spacing-medium));
                margin-inline: auto;
                padding: 0 var(--sl-spacing-medium);
                min-height: 3.5rem;
            }
            .logo {
                display: flex;
                flex: 0 0 auto;
            }
            .logo img {
                display: block;
                height: 2.5rem;
                width: auto;
            }
            nav {
                flex: 1 1 auto;
                min-width: 0;
            }
            ul {
                list-style: none;
                margin: 0;
                padding: 0;
            }
            .links {
                display: flex;
                flex-wrap: wrap;
                column-gap: var(--sl-spacing-small);
            }
            .links a {
                display: inline-flex;
                align-items: center;
                gap: var(--sl-spacing-2x-small);
                padding: var(--sl-spacing-medium) var(--sl-spacing-2x-small);
                border-bottom: 2px solid transparent;
                color: var(--sl-color-gray-700);
                text-decoration: none;
                white-space: nowrap;
            }
            .links a:hover {
                color: var(--sl-color-primary-700);
            }
            .links a[aria-current='page'] {
                color: var(--sl-color-primary-600);
                border-bottom-color: var(--sl-color-primary-600);
                font-weight: var(--sl-font-weight-semibold);
            }
            .account {
                flex: 0 0 auto;
            }
            .account-trigger::part(label) {
                display: inline-flex;
                align-items: center;
                gap: var(--sl-spacing-x-small);
            }
            .account-trigger sl-icon {
                font-size: var(--sl-font-size-large);
            }
            .username {
                display: inline-flex;
                align-items: center;
                gap: var(--sl-spacing-2x-small);
                max-width: 16rem;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
            }
            .menu-button {
                display: none;
                margin-left: auto;
                font-size: var(--sl-font-size-x-large);
            }
            /* the drawer */
            .drawer-links a {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-x-small);
                padding: var(--sl-spacing-small) var(--sl-spacing-medium);
                border-left: 3px solid transparent;
                color: var(--sl-color-gray-700);
                text-decoration: none;
            }
            .drawer-links a[aria-current='page'] {
                color: var(--sl-color-primary-600);
                border-left-color: var(--sl-color-primary-600);
                background: var(--sl-color-primary-50);
                font-weight: var(--sl-font-weight-semibold);
            }
            .drawer-links button {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-x-small);
                width: 100%;
                padding: var(--sl-spacing-small) var(--sl-spacing-medium);
                border: none;
                border-left: 3px solid transparent;
                background: none;
                font: inherit;
                color: var(--sl-color-gray-700);
                text-align: left;
                cursor: pointer;
            }
            .drawer-links a:hover,
            .drawer-links button:hover {
                color: var(--sl-color-primary-700);
            }
            .drawer-links button[aria-pressed='true'] {
                color: var(--sl-color-primary-600);
                font-weight: var(--sl-font-weight-semibold);
            }
            .drawer-heading {
                padding: var(--sl-spacing-small) var(--sl-spacing-medium) 0;
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-small);
            }
            .drawer-user {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-x-small);
                padding: var(--sl-spacing-small) var(--sl-spacing-medium);
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-small);
            }
            sl-drawer::part(body) {
                padding: var(--sl-spacing-small) 0;
            }
            @media (max-width: 991px) {
                .bar nav, .bar .account {
                    display: none;
                }
                .menu-button {
                    display: inline-flex;
                }
            }
        `,
    ];

    connectedCallback(): void {
        super.connectedCallback();
        window.addEventListener(ROUTE_CHANGED_EVENT, this.onRouteChange);
    }

    disconnectedCallback(): void {
        window.removeEventListener(ROUTE_CHANGED_EVENT, this.onRouteChange);
        super.disconnectedCallback();
    }

    protected render(): TemplateResult {
        const links = this.links();
        return html`
            <div class="bar">
                <a class="logo" href=${adminUrl('/')}>
                    <img alt="alf.io" src="${this.config.contextPath}/resources/images/alfio-logo.svg" width="100" height="50">
                </a>
                <nav aria-label=${t('admin.navbar.main-navigation')}>
                    <ul class="links">
                        ${repeat(links, link => link.route, link => html`<li>${this.renderLink(link)}</li>`)}
                    </ul>
                </nav>
                <sl-dropdown class="account" placement="bottom-end" @sl-select=${this.onAccountSelect}>
                    <sl-button slot="trigger" class="account-trigger" variant="text" caret title=${t('admin.navbar.logged-in-as', this.config.username)}>
                        <sl-icon name="person-circle" label=${t('admin.navbar.account')}></sl-icon>
                        <span class="username">${this.config.username}</span>
                    </sl-button>
                    <sl-menu>
                        ${this.renderAccountMenuItem()}
                        ${this.renderLanguageMenuItem()}
                        <sl-divider></sl-divider>
                        <sl-menu-item class="logout" value="logout">
                            <sl-icon slot="prefix" name="box-arrow-right"></sl-icon>
                            ${t('admin.navbar.log-out')}
                        </sl-menu-item>
                    </sl-menu>
                </sl-dropdown>
                <sl-icon-button class="menu-button" name="list" label=${t('admin.navbar.menu')} @click=${this.openDrawer}></sl-icon-button>
            </div>
            <sl-drawer label="alf.io" placement="end">
                <nav aria-label=${t('admin.navbar.main-navigation')}>
                    <ul class="drawer-links">
                        ${repeat(links, link => link.route, link => html`<li>${this.renderLink(link)}</li>`)}
                    </ul>
                </nav>
                <sl-divider></sl-divider>
                <div class="drawer-user">
                    <sl-icon name="person-circle"></sl-icon>
                    <span class="username" title=${t('admin.navbar.logged-in-as', this.config.username)}>${this.config.username}</span>
                </div>
                <ul class="drawer-links">
                    ${this.renderAccountLink()}
                    <li>
                        <button type="button" class="logout" @click=${this.logout}>
                            <sl-icon name="box-arrow-right"></sl-icon>
                            ${t('admin.navbar.log-out')}
                        </button>
                    </li>
                </ul>
                <sl-divider></sl-divider>
                <div class="drawer-heading" id="drawer-language">${t('admin.navbar.language')}</div>
                <ul class="drawer-links language-links" aria-labelledby="drawer-language">
                    ${repeat(this.languages(), language => language.code, language => html`
                        <li>
                            <button type="button" lang=${language.code} aria-pressed=${String(language.current)}
                                    @click=${() => this.switchLanguage(language.code)}>
                                ${language.name}
                            </button>
                        </li>
                    `)}
                </ul>
            </sl-drawer>
        `;
    }

    private renderLink(link: NavLink): TemplateResult {
        let current: string | typeof nothing = nothing;
        if (link.current.test(this.route)) {
            current = 'page';
        }
        let beta: TemplateResult | typeof nothing = nothing;
        if (link.beta === true) {
            beta = html`<sl-badge variant="neutral" pill>${t('admin.common.beta')}</sl-badge>`;
        }
        return html`<a href=${adminUrl(link.route)} aria-current=${current}>${link.label} ${beta}</a>`;
    }

    private renderAccountMenuItem(): TemplateResult | typeof nothing {
        if (!this.config.isDBAuthentication) {
            return nothing;
        }
        return html`
            <sl-menu-item class="edit-account" value="edit-account">
                <sl-icon slot="prefix" name="pencil"></sl-icon>
                ${t('admin.navbar.edit-account')}
            </sl-menu-item>
        `;
    }

    /**
     * The languages of the admin, each one named in its own language, so that a user who landed on a language they
     * don't read can still find theirs. Names only: a flag stands for a country, not for a language.
     */
    private renderLanguageMenuItem(): TemplateResult {
        return html`
            <sl-menu-item class="language">
                <sl-icon slot="prefix" name="translate"></sl-icon>
                ${t('admin.navbar.language')}
                <sl-menu slot="submenu">
                    ${repeat(this.languages(), language => language.code, language => html`
                        <sl-menu-item type="checkbox" value=${`lang:${language.code}`} lang=${language.code}
                                      ?checked=${language.current}>
                            ${language.name}
                        </sl-menu-item>
                    `)}
                </sl-menu>
            </sl-menu-item>
        `;
    }

    private languages(): Array<{ code: string; name: string; current: boolean }> {
        const current = currentLanguage();
        return supportedLanguages()
            .map(language => ({ code: language.language, name: languageName(language.language), current: language.language === current }))
            .sort((a, b) => a.name.localeCompare(b.name, current));
    }

    /**
     * The server renders the page in the chosen language and keeps it for the session (MvcConfiguration): this is the
     * one navigation that reloads the page
     */
    private switchLanguage(code: string): void {
        if (code === currentLanguage()) {
            return;
        }
        const url = new URL(window.location.href);
        url.searchParams.set('lang', code);
        window.location.assign(url.toString());
    }

    private onAccountSelect = (e: CustomEvent<{ item: { value: string } }>): void => {
        const action = e.detail.item.value;
        if (action.startsWith('lang:')) {
            this.switchLanguage(action.substring('lang:'.length));
        } else if (action === 'edit-account') {
            navigate('/profile/edit');
        } else if (action === 'logout') {
            void this.logout();
        }
    };

    private renderAccountLink(): TemplateResult | typeof nothing {
        if (!this.config.isDBAuthentication) {
            return nothing;
        }
        let current: string | typeof nothing = nothing;
        if (this.route.startsWith('/profile/')) {
            current = 'page';
        }
        return html`
            <li>
                <a class="edit-account" href=${adminUrl('/profile/edit')} aria-current=${current}>
                    <sl-icon name="pencil"></sl-icon>
                    ${t('admin.navbar.edit-account')}
                </a>
            </li>
        `;
    }

    private links(): NavLink[] {
        const links: NavLink[] = [
            { label: t('admin.menu.events'), route: '/', current: /^\/($|\?|events\/)/ },
        ];
        if (!this.config.isOwner) {
            return links;
        }
        links.push(
            { label: t('admin.menu.subscriptions'), route: '/subscriptions', current: /^\/subscriptions/, beta: true },
            { label: t('admin.menu.system.organizations'), route: '/organizations/', current: /^\/organizations/ },
        );
        if (this.config.isDBAuthentication) {
            links.push({ label: t('admin.user.list.title'), route: '/users/', current: /^\/users/ });
        }
        links.push(
            { label: t('admin.menu.api-keys'), route: '/api-keys/', current: /^\/api-keys/ },
            { label: t('admin.menu.groups'), route: '/group', current: /^\/group/ },
            { label: t('admin.menu.settings'), route: '/configuration/system', current: /^\/(configuration|show-resources)/ },
        );
        if (this.config.isAdmin) {
            links.push({ label: t('admin.menu.extensions'), route: '/extension/list', current: /^\/extension/ });
        }
        return links;
    }

    private onRouteChange = (): void => {
        this.route = adminRoute();
        void this.drawer?.hide();
    };

    private openDrawer = (): void => {
        void this.drawer?.show();
    };

    private logout = async (): Promise<void> => {
        const config = this.config;
        try {
            await postJson(`${config.contextPath}/logout`, {});
        } finally {
            clearSessionStorage();
            window.dispatchEvent(new CustomEvent(LOGOUT_EVENT));
            if (config.idpLogoutRedirectionUrl != null && config.idpLogoutRedirectionUrl !== '') {
                window.location.replace(config.idpLogoutRedirectionUrl);
            } else {
                window.location.reload();
            }
        }
    };
}

export function clearSessionStorage(): void {
    try {
        window.sessionStorage.clear();
    } catch {
        // session storage can be unavailable
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-admin-navbar': AdminNavbar;
    }
}
