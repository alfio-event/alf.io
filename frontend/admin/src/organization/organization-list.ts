import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {fetchJson} from '../service/helpers.ts';
import type {Organization} from '../model/organization.ts';
import {badges, base, modernLayout, modernTable, retroCompat} from '../styles.ts';
import {AlfioDialogClosed, dispatchFeedback} from '../model/dom-events.ts';
import {sectionCard} from '../components/section-card.ts';
import {taskContent} from '../components/task-content.ts';
import {emptyState} from '../components/empty-state.ts';

@customElement('alfio-organization-list')
export class OrganizationList extends LitElement {

    @state()
    isAdmin: boolean = !!window.IS_ADMIN;

    @state()
    refreshCount: number = 0;

    // the last loaded organizations, kept while a refresh is running
    @state()
    private organizations: ReadonlyArray<Organization> | null = null;

    private readonly loadOrganizationsTask = new Task(this, {
        task: async (): Promise<ReadonlyArray<Organization>> => fetchJson<ReadonlyArray<Organization>>('/admin/api/organizations'),
        args: () => [this.refreshCount] as const,
        onComplete: (organizations) => {
            this.organizations = organizations;
        },
    });

    static readonly styles = [
        base,
        retroCompat,
        badges,
        modernLayout,
        modernTable,
        css`
            :host {
                display: block;
            }

            .organization-description {
                overflow-wrap: anywhere;
            }

            @media (max-width: 767px) {
                .hidden-xs {
                    display: none;
                }
            }
        `
    ];

    render(): TemplateResult {
        return html`
            <div class="container">
                <div class="page-title-row">
                    <h1>Organizations</h1>
                </div>
                <hr class="page-separator">

                ${when(this.isAdmin, () => html`
                    <div class="filter-toolbar" style="justify-content: flex-end;">
                        <div class="filter-right">
                            <sl-button variant="success" size="large" @click=${() => this.openNew()}>
                                <sl-icon slot="prefix" name="plus-circle"></sl-icon> Add new
                            </sl-button>
                        </div>
                    </div>
                `)}

                ${taskContent(this.loadOrganizationsTask, this.organizations, 'Failed to load the organizations. Please try again.',
                    (orgs) => sectionCard({
                        icon: 'building',
                        title: 'Organizations',
                        badge: html`<sl-badge variant="primary" pill>${orgs.length}</sl-badge>`,
                    }, this.renderTable(orgs)))}
            </div>
        `;
    }

    private renderTable(orgs: ReadonlyArray<Organization>): TemplateResult {
        if (orgs.length === 0) {
            return emptyState('No organizations found');
        }
        return html`
            <div class="table-responsive">
                <table class="table table-striped">
                    <thead>
                        <tr>
                            <th scope="col">Name</th>
                            <th scope="col" class="hidden-xs" style="width: 35%">Description</th>
                            <th scope="col" style="width: 30%">E-mail</th>
                            <th scope="col" style="width: 10%"><span class="sr-only">Actions</span></th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(orgs, (org) => org.id, (org) => html`
                            <tr>
                                <td>${org.name}</td>
                                <td class="hidden-xs"><div class="organization-description" title=${org.description}>${org.description}</div></td>
                                <td>${org.email}</td>
                                <td>
                                    <div class="actions-cell">
                                        <sl-button variant="default" size="small" class="edit-organization" data-organization-id=${org.id}
                                                   @click=${() => this.edit(org)}>
                                            <sl-icon slot="prefix" name="pencil"></sl-icon>
                                            Edit<span class="sr-only"> ${org.name}</span>
                                        </sl-button>
                                    </div>
                                </td>
                            </tr>
                        `)}
                    </tbody>
                </table>
            </div>
        `;
    }

    private async openNew(): Promise<void> {
        const div = document.createElement('div');
        div.innerHTML = `<alfio-organization-edit></alfio-organization-edit>`;
        const component = div.querySelector('alfio-organization-edit')!;
        document.body.appendChild(div);
        await customElements.whenDefined('alfio-organization-edit');
        component.addEventListener('alfio-dialog-closed', async (e) => {
            await this.editDialogClosed(e as AlfioDialogClosed);
            setTimeout(() => div.remove());
        });
        await component.open({ type: 'new' });
    }

    private async edit(org: Organization): Promise<void> {
        const div = document.createElement('div');
        div.innerHTML = `<alfio-organization-edit></alfio-organization-edit>`;
        const component = div.querySelector('alfio-organization-edit')!;
        document.body.appendChild(div);
        await customElements.whenDefined('alfio-organization-edit');
        component.addEventListener('alfio-dialog-closed', async (e) => {
            await this.editDialogClosed(e as AlfioDialogClosed);
            setTimeout(() => div.remove());
        });
        await component.open({ type: 'edit', organizationId: org.id });
    }

    private async editDialogClosed(e: AlfioDialogClosed) {
        if (e.detail.success) {
            this.refresh();
            dispatchFeedback({
                type: 'success',
                message: 'Operation completed successfully'
            }, this);
        }
    }

    public refresh(): void {
        this.refreshCount++;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-organization-list': OrganizationList;
    }
}
