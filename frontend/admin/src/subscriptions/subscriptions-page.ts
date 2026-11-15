import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {Organization} from '../model/organization.ts';
import {OrganizationService} from '../service/organization.ts';
import {base, modernLayout, form, textColors} from '../styles.ts';
import {taskContent} from '../components/task-content.ts';

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
        window.addEventListener('hashchange', this.onHashChange);
    }

    disconnectedCallback(): void {
        super.disconnectedCallback();
        window.removeEventListener('hashchange', this.onHashChange);
    }

    private onHashChange = (): void => {
        this.formRoute = this.isFormRoute();
        // the routed content can change without this component being recreated (e.g. going
        // back from the edit page to #/subscriptions): re-sync the organization from the URL
        const orgs = this.loadOrganizationsTask.value;
        if (orgs != null) {
            this.syncSelectedOrganization(orgs);
        }
    };

    private isFormRoute(): boolean {
        return /^#\/subscriptions\/\d+\/(create|[^/]+\/edit)/.test(window.location.hash);
    }

    render(): TemplateResult {
        return html`
            <div class="container">
                ${when(!this.formRoute, () => html`
                    <div class="page-title-row">
                        <h1>Subscriptions</h1>
                    </div>
                    <hr class="page-separator">
                    <p class="page-description text-muted">Subscriptions give access to the events they are linked to.</p>
                `, () => nothing)}
                ${taskContent(this.loadOrganizationsTask, this.loadOrganizationsTask.value ?? null,
                    'Failed to load the organizations. Please try again.',
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
        if (/^#\/subscriptions\/?$/.test(window.location.hash)) {
            window.location.hash = `#/subscriptions/${orgs[0].id}/list`;
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-subscriptions-page': SubscriptionsPage;
    }
}
