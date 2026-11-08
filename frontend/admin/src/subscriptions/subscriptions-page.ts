import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {repeat} from 'lit/directives/repeat.js';
import {Task} from '@lit/task';
import type {SlSelect} from '@shoelace-style/shoelace';
import {Organization} from '../model/organization.ts';
import {OrganizationService} from '../service/organization.ts';
import {base, modernLayout, form, textColors} from '../styles.ts';
import {taskContent} from '../components/task-content.ts';

/**
 * The container of the subscriptions screens: page title, organization selector and the
 * routed content (list, detail, edit), projected through the default slot.
 */
@customElement('alfio-subscriptions-page')
export class SubscriptionsPage extends LitElement {

    @state()
    private selectedOrganizationId: number | null = null;

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

            .page-title-row small {
                color: var(--sl-color-gray-600);
            }

            .organization-select {
                max-width: 24rem;
                margin-bottom: var(--sl-spacing-medium);
            }
        `,
    ];

    connectedCallback(): void {
        super.connectedCallback();
        window.addEventListener('hashchange', this.onHashChange);
    }

    disconnectedCallback(): void {
        super.disconnectedCallback();
        window.removeEventListener('hashchange', this.onHashChange);
    }

    private onHashChange = (): void => {
        // the routed content can change without this component being recreated (e.g. going
        // back from the edit page to #/subscriptions): re-sync the organization from the URL
        const orgs = this.loadOrganizationsTask.value;
        if (orgs != null) {
            this.syncSelectedOrganization(orgs);
        }
    };

    render(): TemplateResult {
        return html`
            <div class="container">
                <div class="page-title-row">
                    <h1>Subscriptions</h1>
                    <small>Manage your subscriptions</small>
                </div>
                <hr class="page-separator">
                ${taskContent(this.loadOrganizationsTask, this.loadOrganizationsTask.value ?? null,
                    'Failed to load the organizations. Please try again.',
                    (orgs) => this.renderOrganizationSelector(orgs))}
                <slot></slot>
            </div>
        `;
    }

    private renderOrganizationSelector(orgs: readonly Organization[]): TemplateResult {
        if (orgs.length === 0) {
            return html``;
        }
        return html`
            ${when(orgs.length > 1,
                () => html`
                    <sl-select class="organization-select" label="Organization" hoist
                               .value=${String(this.selectedOrganizationId ?? orgs[0].id)}
                               @sl-change=${this.onOrganizationChange}>
                        ${repeat(orgs, (org) => org.id, (org) => html`
                            <sl-option value=${org.id}>${org.name}</sl-option>
                        `)}
                    </sl-select>
                `,
                () => nothing)}
        `;
    }

    private onOrganizationChange = (event: Event): void => {
        const organizationId = Number((event.target as SlSelect).value);
        window.location.hash = `#/subscriptions/${organizationId}/list`;
    };

    /**
     * Reads the organization of the current route, and when the route has none (e.g. after
     * clicking "Subscriptions" in the menu) moves to the list of the first organization
     */
    private syncSelectedOrganization(orgs: readonly Organization[]): void {
        if (orgs.length === 0) {
            return;
        }
        const match = window.location.hash.match(/#\/subscriptions\/(\d+)/);
        if (match != null) {
            this.selectedOrganizationId = Number(match[1]);
        } else {
            this.selectedOrganizationId = orgs[0].id;
            window.location.hash = `#/subscriptions/${orgs[0].id}/list`;
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-subscriptions-page': SubscriptionsPage;
    }
}
