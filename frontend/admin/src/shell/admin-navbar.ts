import { css, html, LitElement, nothing, TemplateResult } from 'lit';
import { customElement, query, state } from 'lit/decorators.js';
import { consume } from '@lit/context';
import { repeat } from 'lit/directives/repeat.js';
import type SlDrawer from '@shoelace-style/shoelace/dist/components/drawer/drawer.js';
import { AdminConfig, adminConfigContext } from '../admin-config';
import { adminRoute, adminUrl, navigate, ROUTE_CHANGED_EVENT } from '../admin-router';
import { postJson } from '../service/helpers';
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
 * The top bar of the admin: the sections, the account and the logout. Below 992px the links move into a drawer.
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
            .bar {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-medium);
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
                <nav aria-label="Main">
                    <ul class="links">
                        ${repeat(links, link => link.route, link => html`<li>${this.renderLink(link)}</li>`)}
                    </ul>
                </nav>
                <sl-dropdown class="account" placement="bottom-end" @sl-select=${this.onAccountSelect}>
                    <sl-button slot="trigger" class="account-trigger" variant="text" caret title="Logged in as ${this.config.username}">
                        <sl-icon name="person-circle" label="Account"></sl-icon>
                        <span class="username">${this.config.username}</span>
                    </sl-button>
                    <sl-menu>
                        ${this.renderAccountMenuItem()}
                        <sl-menu-item class="logout" value="logout">
                            <sl-icon slot="prefix" name="box-arrow-right"></sl-icon>
                            Log out
                        </sl-menu-item>
                    </sl-menu>
                </sl-dropdown>
                <sl-icon-button class="menu-button" name="list" label="Menu" @click=${this.openDrawer}></sl-icon-button>
            </div>
            <sl-drawer label="alf.io" placement="end">
                <nav aria-label="Main">
                    <ul class="drawer-links">
                        ${repeat(links, link => link.route, link => html`<li>${this.renderLink(link)}</li>`)}
                    </ul>
                </nav>
                <sl-divider></sl-divider>
                <div class="drawer-user">
                    <sl-icon name="person-circle"></sl-icon>
                    <span class="username" title="Logged in as ${this.config.username}">${this.config.username}</span>
                </div>
                <ul class="drawer-links">
                    ${this.renderAccountLink()}
                    <li>
                        <button type="button" class="logout" @click=${this.logout}>
                            <sl-icon name="box-arrow-right"></sl-icon>
                            Log out
                        </button>
                    </li>
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
            beta = html`<sl-badge variant="neutral" pill>beta</sl-badge>`;
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
                Edit account
            </sl-menu-item>
            <sl-divider></sl-divider>
        `;
    }

    private onAccountSelect = (e: CustomEvent<{ item: { value: string } }>): void => {
        const action = e.detail.item.value;
        if (action === 'edit-account') {
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
                    Edit account
                </a>
            </li>
        `;
    }

    private links(): NavLink[] {
        const links: NavLink[] = [
            { label: 'Events', route: '/', current: /^\/($|\?|events\/)/ },
        ];
        if (!this.config.isOwner) {
            return links;
        }
        links.push(
            { label: 'Subscriptions', route: '/subscriptions', current: /^\/subscriptions/, beta: true },
            { label: 'Organizations', route: '/organizations/', current: /^\/organizations/ },
        );
        if (this.config.isDBAuthentication) {
            links.push({ label: 'Users', route: '/users/', current: /^\/users/ });
        }
        links.push(
            { label: 'API keys', route: '/api-keys/', current: /^\/api-keys/ },
            { label: 'Groups', route: '/group', current: /^\/group/ },
            { label: 'Configuration', route: '/configuration/system', current: /^\/(configuration|show-resources)/ },
        );
        if (this.config.isAdmin) {
            links.push({ label: 'Extensions', route: '/extension/list', current: /^\/extension/ });
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
