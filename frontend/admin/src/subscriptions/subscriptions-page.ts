import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {Organization} from '../model/organization.ts';
import {OrganizationService} from '../service/organization.ts';
import {base, modernLayout, form, textColors} from '../styles.ts';
import {taskContent} from '../components/task-content.ts';
import { adminRoute, navigate, ROUTE_CHANGED_EVENT } from '../admin-router';
import { t } from '../service/i18n';

/**
 * The container of the subscriptions screens: page title and the routed content (list, detail,
 * edit), projected through the default slot. The list shows the organization filter.
 */
@customElement('alfio-subscriptions-page')
export class SubscriptionsPage extends LitElement {

    // the create/edit pages have their own title
    @state()
    private formRoute = false;

    private readonly loadOrganizationsTask = new Task(this, {
        task: async (): Promise<readonly Organization[]> => {
            const orgs = await OrganizationService.loadAll();
            this.syncSelectedOrganization(orgs);
            return orgs;
        },
        args: () => [] as const,
    });

    static readonly styles = [
        base,
        textColors,
        form,
        modernLayout,
        css`
            :host {
                display: block;
            }
        `,
    ];

    connectedCallback(): void {
        super.connectedCallback();
        this.formRoute = this.isFormRoute();
        window.addEventListener(ROUTE_CHANGED_EVENT, this.onRouteChange);
    }

    disconnectedCallback(): void {
        super.disconnectedCallback();
        window.removeEventListener(ROUTE_CHANGED_EVENT, this.onRouteChange);
    }

    private onRouteChange = (): void => {
        this.formRoute = this.isFormRoute();
        // the routed content can change without this component being recreated (e.g. going
        // back from the edit page to /admin/subscriptions): re-sync the organization from the URL
        const orgs = this.loadOrganizationsTask.value;
        if (orgs != null) {
            this.syncSelectedOrganization(orgs);
        }
    };

    private isFormRoute(): boolean {
        return /^\/subscriptions\/\d+\/(create|[^/]+\/edit)/.test(adminRoute());
    }

    render(): TemplateResult {
        return html`
            <div class="container">
                ${when(!this.formRoute, () => html`
                    <div class="page-title-row">
                        <h1>${t('admin.menu.subscriptions')}</h1>
                    </div>
                    <hr class="page-separator">
                    <p class="page-description text-muted">${t('admin.subscriptions.description')}</p>
                `, () => nothing)}
                ${taskContent(this.loadOrganizationsTask, this.loadOrganizationsTask.value ?? null,
                    t('admin.load-error.organizations'),
                    () => html``)}
                <slot></slot>
            </div>
        `;
    }

    /**
     * On the bare route, without an organization (e.g. after clicking "Subscriptions" in the menu),
     * moves to the list of the first organization
     */
    private syncSelectedOrganization(orgs: readonly Organization[]): void {
        if (orgs.length === 0) {
            return;
        }
        if (/^\/subscriptions\/?$/.test(adminRoute().split('?')[0])) {
            navigate(`/subscriptions/${orgs[0].id}/list`, { replace: true });
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-subscriptions-page': SubscriptionsPage;
    }
}
