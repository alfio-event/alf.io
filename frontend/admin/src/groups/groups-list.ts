import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import type {Group} from '../model/group.ts';
import {GroupService} from '../service/group.ts';
import {badges, base, cardList, modernLayout, textColors} from '../styles.ts';
import {emptyState} from '../components/empty-state.ts';
import {taskContent} from '../components/task-content.ts';
import {sectionCard} from '../components/section-card.ts';
import {organizationFilter} from '../components/organization-filter.ts';
import {OrganizationService} from '../service/organization.ts';

/**
 * The groups of an organization, as a card with one row each: the row opens the edit page.
 */
@customElement('alfio-groups-list')
export class GroupsList extends LitElement {

    @property({type: Number, attribute: 'data-organization-id'})
    organizationId = 0;

    private readonly loadGroupsTask = new Task(this, {
        task: async ([organizationId]): Promise<readonly Group[] | null> => {
            if (!(organizationId > 0)) {
                // the organization id is still being interpolated by AngularJS
                return null;
            }
            return await GroupService.loadGroups(organizationId);
        },
        // the attribute is interpolated by AngularJS after the element is connected: wait for a real id
        args: () => [this.organizationId] as const,
    });

    private readonly loadOrganizationsTask = new Task(this, {
        task: async () => OrganizationService.loadAll(),
        args: () => [] as const,
    });

    static readonly styles = [
        base,
        badges,
        cardList,
        modernLayout,
        textColors,
        css`
            .list-card {
                --alfio-section-body-padding: 0;
            }

            .group-row {
                grid-template-columns: auto minmax(0, 1fr) auto;
            }

            .group-description {
                margin-top: var(--sl-spacing-2x-small);
                color: var(--sl-color-gray-600);
                font-size: var(--sl-font-size-small);
                display: -webkit-box;
                -webkit-line-clamp: 2;
                -webkit-box-orient: vertical;
                overflow: hidden;
            }

            .group-row .chevron {
                align-self: center;
                color: var(--sl-color-gray-400);
            }

            .group-row:hover .chevron {
                color: var(--sl-color-primary-700);
            }
        `,
    ];

    render(): TemplateResult {
        const groups = this.loadGroupsTask.value;
        let count: TemplateResult | undefined = undefined;
        if (groups != null) {
            count = html`<sl-badge variant="primary" pill>${groups.length}</sl-badge>`;
        }
        return html`
            <div class="filter-toolbar">
                <div class="filter-left">
                    ${organizationFilter(this.loadOrganizationsTask.value, this.organizationId,
                        (organizationId) => window.location.hash = `#/group/${organizationId}/all`)}
                </div>
                <div class="filter-right">
                    <sl-button id="add-group" href=${`#/group/${this.organizationId}/new`} variant="success" size="large">
                        <sl-icon name="plus-circle" slot="prefix"></sl-icon>
                        Add new
                    </sl-button>
                </div>
            </div>
            ${sectionCard({icon: 'people', title: 'Groups', badge: count, className: 'list-card'},
                taskContent(this.loadGroupsTask, this.loadGroupsTask.value ?? null,
                    'Failed to load the groups. Please try again.',
                    (loaded) => this.renderGroups(loaded)))}
        `;
    }

    private renderGroups(groups: readonly Group[] | null): TemplateResult {
        if (groups == null) {
            return html`<div class="state-message"><sl-spinner></sl-spinner></div>`;
        }
        if (groups.length === 0) {
            return emptyState('No groups have been found');
        }
        return html`
            ${repeat(groups, (group) => group.id, (group) => this.renderGroup(group))}
        `;
    }

    private renderGroup(group: Group): TemplateResult {
        return html`
            <a class="event-row group-row" href=${`#/group/${this.organizationId}/edit/${group.id}`}
               aria-label=${`Edit ${group.name}`}>
                <div class="event-logo placeholder">
                    <sl-icon name="people" aria-label="No image"></sl-icon>
                </div>
                <div class="event-main">
                    <div class="event-heading">
                        <span class="event-title group-name">${group.name}</span>
                    </div>
                    ${when(group.description != null && group.description !== '',
                        () => html`<div class="group-description">${group.description}</div>`,
                        () => nothing)}
                </div>
                <sl-icon class="chevron" name="chevron-right" aria-hidden="true"></sl-icon>
            </a>
        `;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-groups-list': GroupsList;
    }
}
