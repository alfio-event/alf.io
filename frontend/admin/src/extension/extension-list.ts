import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import {repeat} from 'lit/directives/repeat.js';
import {Task} from '@lit/task';
import {SlDialog} from '@shoelace-style/shoelace';
import {ExtensionEvent, ExtensionSupport} from '../model/extension.ts';
import type {Organization} from '../model/organization.ts';
import {ExtensionService} from '../service/extension.ts';
import {dispatchFeedback} from '../model/dom-events.ts';
import {base, dialog as dialogStyling, modernLayout, modernTable, row, textColors} from '../styles.ts';
import {emptyState} from '../components/empty-state.ts';
import {taskContent} from '../components/task-content.ts';
import {renderScope} from './extension-scope.ts';

interface ListData {
    extensions: ExtensionSupport[];
    organizations: ReadonlyArray<Organization>;
    events: ReadonlyArray<ExtensionEvent>;
}

/**
 * List of the extension scripts, with their scope (system, organization or event).
 * The page hosted by the "extension.list" route of the AngularJS admin.
 */
@customElement('alfio-extension-list')
export class ExtensionList extends LitElement {

    @query('#deleteDialog') private deleteDialog!: SlDialog;

    @state() private refreshCount = 0;
    @state() private deleteTarget: ExtensionSupport | null = null;
    @state() private deleting = false;
    @state() private lastData: ListData | null = null;

    private readonly loadTask = new Task(this, {
        task: async (): Promise<ListData> => {
            const [extensions, options] = await Promise.all([
                ExtensionService.listExtensions(),
                ExtensionService.loadScopeOptions(),
            ]);
            return { extensions, organizations: options.organizations, events: options.events };
        },
        args: () => [this.refreshCount] as const,
        // Task invokes this only for the latest request; the last data stays mounted during a refresh
        onComplete: (data) => {
            this.lastData = data;
        },
    });

    static readonly styles = [
        base,
        textColors,
        modernLayout,
        modernTable,
        dialogStyling,
        row,
        css`
            :host {
                display: block;
            }
            .enabled-icon {
                font-size: var(--sl-font-size-x-large);
            }
            .enabled-icon.disabled {
                color: var(--sl-color-gray-500);
            }
        `,
    ];

    render(): TemplateResult {
        return html`
            <div class="container">
                <div class="page-title-row">
                    <h1>Extensions</h1>
                </div>
                <hr class="page-separator"/>
                <div class="filter-toolbar" style="justify-content: flex-end;">
                    <div class="filter-right">
                        <sl-button variant="primary" size="large" @click=${() => this.navigateTo('log')}>
                            <sl-icon slot="prefix" name="journal-text"></sl-icon> Show logs
                        </sl-button>
                        <sl-button variant="success" size="large" @click=${() => this.navigateTo('new')}>
                            <sl-icon slot="prefix" name="plus-circle"></sl-icon> Add new
                        </sl-button>
                    </div>
                </div>
                ${taskContent(this.loadTask, this.lastData, 'Failed to load extensions. Please try again.',
                    (data) => this.renderTable(data))}
            </div>
            ${this.renderDeleteDialog()}
        `;
    }

    private renderTable(data: ListData): TemplateResult {
        if (data.extensions.length === 0) {
            return html`
                <section class="section-card">
                    ${emptyState('No extensions have been found')}
                </section>
            `;
        }
        return html`
            <div class="table-responsive">
                <table class="table table-striped">
                    <thead>
                        <tr>
                            <th>Path</th>
                            <th>Name</th>
                            <th>Enabled</th>
                            <th><span class="sr-only">Actions</span></th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(data.extensions,
                            (ext) => `${ext.path}/${ext.name}`,
                            (ext) => this.renderRow(ext, data))}
                    </tbody>
                </table>
            </div>
        `;
    }

    private renderRow(ext: ExtensionSupport, data: ListData): TemplateResult {
        return html`
            <tr>
                <td>${renderScope(ext.path, data.organizations, data.events)}</td>
                <td>${ext.name}</td>
                <td>${this.renderEnabledIcon(ext)}</td>
                <td>
                    <div class="actions-cell">
                        <!-- the hidden name tells screen reader users which extension the button acts on -->
                        <sl-button variant="default" size="small"
                                   @click=${() => this.navigateTo(`${ext.path}/${ext.name}/edit`)}>
                            <sl-icon slot="prefix" name="pencil"></sl-icon> Edit<span class="sr-only"> ${ext.name}</span>
                        </sl-button>
                        <sl-button variant="danger" size="small" @click=${() => this.openDeleteDialog(ext)}>
                            <sl-icon slot="prefix" name="trash"></sl-icon> Delete<span class="sr-only"> ${ext.name}</span>
                        </sl-button>
                    </div>
                </td>
            </tr>
        `;
    }

    private renderEnabledIcon(ext: ExtensionSupport): TemplateResult {
        if (ext.enabled) {
            return html`<sl-badge pill variant="success">enabled</sl-badge>`;
        }
        return html``;
    }

    private renderDeleteDialog(): TemplateResult {
        return html`
            <sl-dialog id="deleteDialog" label="Delete extension" @sl-request-close=${(e: CustomEvent) => {
                // ignore the close while the deletion is in progress
                if (this.deleting) {
                    e.preventDefault();
                }
            }}>
                ${when(this.deleteTarget != null, () => html`
                    <div class="confirm-dialog-body">
                        <p>The extension <b>${this.deleteTarget!.name}</b> will be removed.</p>
                        <p>This operation <strong>cannot be undone</strong>.</p>
                    </div>
                `)}
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => this.deleteDialog.hide()}>
                            Cancel
                        </sl-button>
                        <div></div>
                        <sl-button variant="warning" size="large" ?disabled=${this.deleting} @click=${() => this.confirmDelete()}>
                            ${when(this.deleting, () => html`<sl-spinner slot="prefix" size="small"></sl-spinner>`)}
                            Delete
                        </sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    private openDeleteDialog(extension: ExtensionSupport): void {
        this.deleteTarget = extension;
        this.deleteDialog.show();
    }

    private async confirmDelete(): Promise<void> {
        if (this.deleteTarget == null) {
            return;
        }
        const target = this.deleteTarget;
        this.deleting = true;
        try {
            await ExtensionService.delete(target.path, target.name);
            dispatchFeedback({type: 'success', message: 'Extension has been deleted'}, this);
            this.refresh();
        } catch (error) {
            dispatchFeedback({type: 'danger', message: 'Failed to delete the extension'}, this);
        } finally {
            this.deleting = false;
            this.deleteDialog.hide();
            this.deleteTarget = null;
        }
    }

    private refresh(): void {
        this.refreshCount++;
    }

    private navigateTo(route: string): void {
        window.location.hash = `#/extension/${route}`;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-extension-list': ExtensionList;
    }
}
