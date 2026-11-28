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
import {badges, base, dialog as dialogStyling, modernLayout, modernTable, row, textColors} from '../styles.ts';
import {emptyState} from '../components/empty-state.ts';
import {taskContent} from '../components/task-content.ts';
import {sectionCard} from '../components/section-card.ts';
import {renderScope} from './extension-scope.ts';
import { navigate } from '../admin-router';
import { t, tHtml } from '../service/i18n';

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
        badges,
        textColors,
        modernLayout,
        modernTable,
        dialogStyling,
        row,
        css`
            :host {
                display: block;
            }
            sl-dialog {
                --alfio-dialog-max-width: 32rem;
            }
        `,
    ];

    render(): TemplateResult {
        return html`
            <div class="container">
                <div class="page-title-row">
                    <h1>${t('admin.menu.extensions')}</h1>
                </div>
                <hr class="page-separator"/>
                <div class="filter-toolbar actions-only">
                    <div class="filter-right">
                        <sl-button variant="default" size="large" @click=${() => this.navigateTo('log')}>
                            <sl-icon slot="prefix" name="journal-text"></sl-icon> ${t('admin.extensions.show-logs')}
                        </sl-button>
                        <sl-button variant="success" size="large" @click=${() => this.navigateTo('new')}>
                            <sl-icon slot="prefix" name="plus-circle"></sl-icon> ${t('admin.common.add-new-entity')}
                        </sl-button>
                    </div>
                </div>
                ${taskContent(this.loadTask, this.lastData, t('admin.extensions.load-failed'),
                    (data) => this.renderTable(data))}
            </div>
            ${this.renderDeleteDialog()}
        `;
    }

    private renderTable(data: ListData): TemplateResult {
        const count = html`<sl-badge variant="primary" pill>${data.extensions.length}</sl-badge>`;
        return sectionCard({icon: 'puzzle', title: t('admin.menu.extensions'), badge: count}, when(data.extensions.length === 0,
            () => emptyState(t('admin.extensions.none')),
            () => html`
                <div class="table-responsive">
                    <table class="table table-striped table-stacked">
                        <thead>
                            <tr>
                                <th>${t('admin.extensions.path')}</th>
                                <th>${t('admin.common.name')}</th>
                                <th>${t('admin.extensions.enabled')}</th>
                                <th><span class="sr-only">${t('admin.common.actions')}</span></th>
                            </tr>
                        </thead>
                        <tbody>
                            ${repeat(data.extensions,
                                (ext) => `${ext.path}/${ext.name}`,
                                (ext) => this.renderRow(ext, data))}
                        </tbody>
                    </table>
                </div>
            `));
    }

    private renderRow(ext: ExtensionSupport, data: ListData): TemplateResult {
        return html`
            <tr>
                <td data-label=${t('admin.extensions.path')}>${renderScope(ext.path, data.organizations, data.events)}</td>
                <td data-label=${t('admin.common.name')}>${ext.name}</td>
                <td data-label=${t('admin.extensions.enabled')}>${this.renderEnabledIcon(ext)}</td>
                <td>
                    <div class="actions-cell">
                        <!-- the hidden name tells screen reader users which extension the button acts on -->
                        <sl-button variant="default" size="small"
                                   @click=${() => this.navigateTo(`${ext.path}/${ext.name}/edit`)}>
                            <sl-icon slot="prefix" name="pencil"></sl-icon> ${t('admin.common.edit')}<span class="sr-only"> ${ext.name}</span>
                        </sl-button>
                        <sl-button variant="danger" size="small" @click=${() => this.openDeleteDialog(ext)}>
                            <sl-icon slot="prefix" name="trash"></sl-icon> ${t('admin.common.delete')}<span class="sr-only"> ${ext.name}</span>
                        </sl-button>
                    </div>
                </td>
            </tr>
        `;
    }

    private renderEnabledIcon(ext: ExtensionSupport): TemplateResult {
        if (ext.enabled) {
            return html`<sl-badge pill variant="success">${t('admin.extensions.enabled')}</sl-badge>`;
        }
        return html``;
    }

    private renderDeleteDialog(): TemplateResult {
        return html`
            <sl-dialog id="deleteDialog" class="responsive-dialog" label=${t('admin.extensions.delete.title')} @sl-request-close=${(e: CustomEvent) => {
                // ignore the close while the deletion is in progress
                if (this.deleting) {
                    e.preventDefault();
                }
            }}>
                ${when(this.deleteTarget != null, () => html`
                    <div class="confirm-dialog-body">
                        <p>${tHtml('admin.extensions.delete.text', html`<b>${this.deleteTarget!.name}</b>`)}</p>
                        <p>${tHtml('admin.extensions.delete.warning', html`<strong>${t('admin.extensions.delete.cannot-be-undone')}</strong>`)}</p>
                    </div>
                `)}
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => this.deleteDialog.hide()}>
                            ${t('admin.common.cancel')}
                        </sl-button>
                        <div></div>
                        <sl-button variant="warning" size="large" ?loading=${this.deleting} ?disabled=${this.deleting} @click=${() => this.confirmDelete()}>
                            ${t('admin.common.delete')}
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
            dispatchFeedback({type: 'success', message: t('admin.extensions.deleted')}, this);
            this.refresh();
        } catch (error) {
            dispatchFeedback({type: 'danger', message: t('admin.extensions.delete-failed')}, this);
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
        navigate(`/extension/${route}`);
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-extension-list': ExtensionList;
    }
}
