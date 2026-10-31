import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {classMap} from 'lit/directives/class-map.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {SlInput, SlSelect, SlSwitch} from '@shoelace-style/shoelace';
import {ExtensionEvent, ExtensionModification, ExtensionSupport} from '../model/extension.ts';
import type {Organization} from '../model/organization.ts';
import {ExtensionService} from '../service/extension.ts';
import {dispatchFeedback} from '../model/dom-events.ts';
import {base, form, modernLayout, textColors} from '../styles.ts';
import {taskContent} from '../components/task-content.ts';
import {parseScope} from './extension-scope.ts';
import {extensionScriptLanguage} from './script-completions.ts';
import {syncMetadataId} from './script-metadata.ts';
import '../components/code-editor.ts';

const NAME_PATTERN = /^[A-Za-z0-9]+$/;

interface EditData {
    extension: ExtensionSupport;
    organizations: ReadonlyArray<Organization>;
    events: ReadonlyArray<ExtensionEvent>;
}

/**
 * Creates or updates an extension script.
 * The page hosted by the "extension.new" and "extension.edit" routes of the AngularJS admin;
 * "data-path"/"data-name" are set by the host template when updating.
 */
@customElement('alfio-extension-edit')
export class ExtensionEdit extends LitElement {

    @property({ type: String, attribute: 'data-path' }) editPath = '';
    @property({ type: String, attribute: 'data-name' }) editName = '';

    @state() private name = '';
    @state() private enabled = false;
    @state() private script = '';
    @state() private organizationId: number | null = null;
    @state() private eventId: number | null = null;
    @state() private busy = false;
    @state() private errorMessage: string | null = null;
    @state() private nameInvalid = false;

    @state() private data: EditData | null = null;
    // the name the metadata id was last copied from, see syncMetadataId
    private idSource = '';

    private get isEdit(): boolean {
        return this.editPath !== '' && this.editName !== '';
    }

    private readonly loadTask = new Task(this, {
        task: async (): Promise<EditData> => {
            let loadExtension: Promise<ExtensionSupport>;
            if (this.isEdit) {
                loadExtension = ExtensionService.loadSingle(this.editPath, this.editName);
            } else {
                loadExtension = ExtensionService.loadSample();
            }
            const [extension, options] = await Promise.all([
                loadExtension,
                ExtensionService.loadScopeOptions(),
            ]);
            return { extension, organizations: options.organizations, events: options.events };
        },
        args: () => [this.editPath, this.editName] as const,
        onComplete: (data) => {
            this.data = data;
            this.name = data.extension.name;
            this.idSource = data.extension.name;
            this.enabled = data.extension.enabled;
            this.script = data.extension.script ?? '';
            const scope = parseScope(data.extension.path);
            this.organizationId = scope.organizationId;
            this.eventId = scope.eventId;
        },
    });

    static readonly styles = [
        base,
        form,
        textColors,
        modernLayout,
        css`
            :host {
                display: block;
            }
            /* scope, event and name side by side, so that the script editor gets most of the page */
            .settings-row {
                display: flex;
                flex-wrap: wrap;
                gap: var(--sl-spacing-medium);
            }
            .settings-row > * {
                flex: 1 1 14rem;
            }
            /* the editor fills the viewport below the settings, but never gets shorter than the default */
            alfio-code-editor {
                --alfio-code-editor-height: max(24rem, calc(100vh - 32rem));
            }
            sl-alert {
                margin-bottom: var(--sl-spacing-medium);
            }
            .actions {
                display: flex;
                justify-content: flex-end;
                gap: var(--sl-spacing-x-small);
                margin-top: var(--sl-spacing-medium);
            }
        `,
    ];

    render(): TemplateResult {
        return html`
            <div class="container">
                <div class="page-title-row">
                    <h1>${this.pageTitle}</h1>
                </div>
                <hr class="page-separator"/>
                ${taskContent(this.loadTask, this.data, 'Failed to load the extension. Please try again.',
                    (data) => this.renderForm(data))}
            </div>
        `;
    }

    private get pageTitle(): string {
        if (this.isEdit) {
            return 'Update Extension';
        }
        return 'Add Extension';
    }

    private renderForm(data: EditData): TemplateResult {
        let events: ExtensionEvent[] = [];
        if (this.organizationId != null) {
            events = data.events.filter((e) => e.organizationId === this.organizationId);
        }
        return html`
            ${when(this.errorMessage != null, () => html`
                <sl-alert open variant="danger">
                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon> ${this.errorMessage}
                </sl-alert>
            `)}

            <form @submit=${this.save}>
                <section class="section-card">
                    <div class="section-header">
                        <sl-icon name="diagram-3" aria-hidden="true"></sl-icon>
                        <h3>Scope and name</h3>
                        <div class="section-actions">
                            <sl-switch
                                ?checked=${this.enabled}
                                @sl-change=${(e: Event) => {
                                    this.enabled = (e.target as SlSwitch).checked;
                                }}>Enabled</sl-switch>
                        </div>
                    </div>
                    <div class="section-body">
                        <div class="settings-row form-stack">
                            <sl-select
                                label="Scope"
                                hoist
                                placeholder="System"
                                .value=${String(this.organizationId ?? '')}
                                @sl-change=${this.onScopeChange}>
                                <sl-option value="">System</sl-option>
                                ${repeat(data.organizations, (org) => org.id, (org) => html`
                                    <sl-option value=${org.id}>${org.name}</sl-option>
                                `)}
                            </sl-select>
                            ${when(this.organizationId !== null, () => html`
                                <sl-select
                                    label="Event"
                                    hoist
                                    placeholder="All Events"
                                    .value=${String(this.eventId ?? '')}
                                    @sl-change=${this.onEventChange}>
                                    <sl-option value="">All Events</sl-option>
                                    ${repeat(events, (event) => event.id, (event) => html`
                                        <sl-option value=${event.id}>${event.displayName}</sl-option>
                                    `)}
                                </sl-select>
                            `)}
                            <sl-input
                                label="Name"
                                name="name"
                                placeholder="Letters and numbers only"
                                class=${classMap({error: this.nameInvalid})}
                                .value=${this.name}
                                @sl-input=${(e: Event) => this.onNameInput(String((e.target as SlInput).value))}></sl-input>
                        </div>
                    </div>
                </section>

                <section class="section-card">
                    <div class="section-header">
                        <sl-icon name="code-slash" aria-hidden="true"></sl-icon>
                        <h3>Script</h3>
                    </div>
                    <div class="section-body">
                        <alfio-code-editor
                            .language=${extensionScriptLanguage}
                            help-text="The script must define a getScriptMetadata() function, see the sample. In the events array, press Ctrl+Space or type a quote to list the supported events."
                            .value=${this.script}
                            @value-changed=${(e: CustomEvent<string>) => {
                                this.script = e.detail;
                            }}></alfio-code-editor>
                    </div>
                </section>

                <div class="actions">
                    <sl-button type="button" variant="default" size="large" ?disabled=${this.busy}
                               @click=${() => this.cancel()}>
                        Cancel
                    </sl-button>
                    <sl-button type="submit" variant="warning" size="large" ?disabled=${this.busy}>
                        ${when(this.busy, () => html`<sl-spinner slot="prefix" size="small"></sl-spinner>`)}
                        Save
                    </sl-button>
                </div>
            </form>
        `;
    }

    private onNameInput(name: string): void {
        this.name = name;
        this.nameInvalid = false;
        // an invalid name would break the script, e.g. with a quote: the id keeps following the last valid one
        if (name !== '' && !NAME_PATTERN.test(name)) {
            return;
        }
        this.script = syncMetadataId(this.script, this.idSource, name);
        this.idSource = name;
    }

    private onScopeChange = (event: Event): void => {
        const value = String((event.target as SlSelect).value);
        if (value === '') {
            this.organizationId = null;
            this.eventId = null;
            return;
        }
        const organizationId = Number.parseInt(value, 10);
        this.organizationId = organizationId;
        // drop the event when it doesn't belong to the selected organization
        let scopeEvent: ExtensionEvent | null = null;
        if (this.data != null) {
            scopeEvent = this.data.events.find((e) => e.id === this.eventId && e.organizationId === organizationId) ?? null;
        }
        if (scopeEvent != null) {
            this.eventId = scopeEvent.id;
        } else {
            this.eventId = null;
        }
    };

    private onEventChange = (event: Event): void => {
        const value = String((event.target as SlSelect).value);
        if (value === '') {
            this.eventId = null;
        } else {
            this.eventId = Number.parseInt(value, 10);
        }
    };

    /**
     * The scope, as stored by the backend: "-", "-<organizationId>" or "-<organizationId>-<eventId>"
     */
    private path(): string {
        if (this.organizationId === null) {
            return '-';
        }
        if (this.eventId === null) {
            return `-${this.organizationId}`;
        }
        return `-${this.organizationId}-${this.eventId}`;
    }

    private save = (event: SubmitEvent): void => {
        event.preventDefault();
        if (!NAME_PATTERN.test(this.name)) {
            this.nameInvalid = true;
            this.errorMessage = 'Name is mandatory and can only contain letters and numbers';
            this.scrollToTop();
            return;
        }
        const payload: ExtensionModification = {
            path: this.path(),
            name: this.name,
            script: this.script,
            enabled: this.enabled,
        };
        this.busy = true;
        this.errorMessage = null;
        let previous: { path: string, name: string } | undefined;
        if (this.isEdit) {
            previous = { path: this.editPath, name: this.editName };
        }
        ExtensionService.save(payload, previous)
            .then((result) => {
                if (result.success) {
                    dispatchFeedback({type: 'success', message: 'Extension has been saved'}, this);
                    this.gotoList();
                } else {
                    this.errorMessage = result.message ?? 'Failed to save the extension';
                    this.scrollToTop();
                }
            })
            .catch(() => {
                this.errorMessage = 'Failed to save the extension';
            })
            .finally(() => {
                this.busy = false;
            });
    };

    private cancel(): void {
        this.gotoList();
    }

    private gotoList(): void {
        window.location.hash = '#/extension/list';
    }

    private scrollToTop(): void {
        window.scrollTo(0, 0);
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-extension-edit': ExtensionEdit;
    }
}