import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {Task} from '@lit/task';
import {TemplateService} from '../service/template.ts';
import {groupTemplatesByContext, TemplateContextGroup, TemplateDescriptor, templateContextInfo, templateRecipientInfo} from '../model/template.ts';
import {badges, base, modernLayout, modernTable, textColors} from '../styles.ts';
import {emptyState} from '../components/empty-state.ts';
import {taskContent} from '../components/task-content.ts';

/**
 * The list of the overridable templates ("customize templates"), grouped by context
 * (emails, tickets, ...) in collapsible panels, each row with an "Edit" button opening the editor.
 * Hosted by the "events.single.show-resources" route of the AngularJS admin, with "data-event-name",
 * and embedded in the "Templates" tab of the system and organization configuration pages,
 * with "data-system" or "data-organization-id".
 */
@customElement('alfio-template-list')
export class TemplateList extends LitElement {

    @property({type: Boolean, attribute: 'data-system'}) system = false;
    @property({type: Number, attribute: 'data-organization-id'}) organizationId?: number;
    @property({type: String, attribute: 'data-event-name'}) eventName = '';

    @state() private templates: TemplateDescriptor[] | null = null;

    private readonly loadTask = new Task(this, {
        task: async (): Promise<TemplateDescriptor[]> => TemplateService.listTemplates(),
        // a task without args never runs (see @lit/task): an empty args array runs it once
        args: () => [] as const,
        onComplete: (templates) => {
            this.templates = templates;
        },
    });

    static readonly styles = [
        base,
        badges,
        textColors,
        modernLayout,
        modernTable,
        css`
            :host {
                display: block;
            }
            sl-alert {
                margin-bottom: var(--sl-spacing-medium);
            }
            sl-details.template-context {
                margin-bottom: var(--sl-spacing-small);
            }
            .context-summary {
                display: inline-flex;
                align-items: center;
                gap: var(--sl-spacing-x-small);
                font-weight: var(--sl-font-weight-semibold);
            }
            .context-summary sl-badge {
                margin-inline-start: var(--sl-spacing-2x-small);
            }
            .template-name {
                display: inline-flex;
                align-items: center;
                gap: var(--sl-spacing-x-small);
            }
        `,
    ];

    render(): TemplateResult {
        const content = html`
            <sl-alert open variant="warning">
                <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                <strong>This is an advanced configuration.</strong>
                Please be careful, as a wrong edit could lead to a loss of functionality.
            </sl-alert>
            ${taskContent(this.loadTask, this.templates, 'Failed to load the templates. Please try again.',
                (templates) => this.renderList(templates))}
        `;
        // as a page (event scope) the component renders its own title; embedded in a configuration tab, the tab already has one
        if (this.eventName !== '') {
            return html`
                <div class="container">
                    <div class="page-title-row">
                        <h1>Customize templates</h1>
                    </div>
                    <hr class="page-separator"/>
                    ${content}
                </div>
            `;
        }
        return content;
    }

    private renderList(templates: TemplateDescriptor[]): TemplateResult {
        if (templates.length === 0) {
            return emptyState('No templates found');
        }
        return html`
            ${repeat(groupTemplatesByContext(templates), (group) => group.context, (group) => this.renderContextGroup(group))}
        `;
    }

    private renderContextGroup(group: TemplateContextGroup): TemplateResult {
        const info = templateContextInfo(group.context);
        return html`
            <sl-details class="template-context" data-context=${group.context}>
                <span slot="summary" class="context-summary">
                    <sl-icon name=${info.icon} aria-hidden="true"></sl-icon>
                    ${info.label}
                    <sl-badge pill variant="primary">${group.templates.length}</sl-badge>
                </span>
                <div class="table-responsive">
                    <table class="table table-striped">
                        <thead>
                            <tr>
                                <th>Template</th>
                                <th>Recipient</th>
                                <th><span class="sr-only">Actions</span></th>
                            </tr>
                        </thead>
                        <tbody>
                            ${repeat(group.templates, (template) => template.name, (template) => html`
                                <tr>
                                    <td>
                                        <span class="template-name">
                                            <sl-icon name=${this.templateIcon(template.name)} aria-hidden="true"></sl-icon>
                                            ${template.description || template.name}
                                        </span>
                                    </td>
                                    <td>${this.renderRecipientBadge(template)}</td>
                                    <td class="actions-cell">
                                        <!-- the enum name is the data attribute and the accessible label of the button -->
                                        <sl-button variant="default" size="small" class="edit-template" data-template=${template.name}
                                                   title=${template.name} type="button" @click=${() => this.openEditor(template.name)}>
                                            <sl-icon slot="prefix" name="pencil"></sl-icon>
                                            Edit<span class="sr-only"> ${template.name}</span>
                                        </sl-button>
                                    </td>
                                </tr>
                            `)}
                        </tbody>
                    </table>
                </div>
            </sl-details>
        `;
    }

    private templateIcon(name: string): string {
        if (name.endsWith('_PDF')) {
            return 'file-earmark-pdf';
        }
        return 'file-earmark-code';
    }

    /**
     * The label telling who receives the template: an empty cell for documents, e.g. PDFs
     */
    private renderRecipientBadge(template: TemplateDescriptor): TemplateResult {
        if (template.recipient == null) {
            return html``;
        }
        const info = templateRecipientInfo(template.recipient);
        return html`<sl-badge pill variant=${info.variant}>${info.label}</sl-badge>`;
    }

    private editUrl(name: string): string {
        if (this.system) {
            return `#/show-resources/${name}/`;
        }
        if (this.hasOrganization()) {
            return `#/organizations/${this.organizationId}/show-resources/${name}/`;
        }
        return `#/events/${this.eventName}/show-resources/${name}/`;
    }

    /**
     * System and organization templates are edited on a page of their own, opened in a new tab,
     * like in the previous admin; event templates navigate within the event section.
     */
    private openEditor(name: string): void {
        const url = this.editUrl(name);
        if (this.system || this.hasOrganization()) {
            window.open(url, '_blank', 'noopener,noreferrer');
            return;
        }
        window.location.hash = url;
    }

    private hasOrganization(): boolean {
        return this.organizationId != null && !Number.isNaN(this.organizationId);
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-template-list': TemplateList;
    }
}
