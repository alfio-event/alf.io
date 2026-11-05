import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {ContentLanguage} from '../model/purchase-context.ts';
import {ResourceScope, TemplateDescriptor, templateContextInfo, templateRecipientInfo, TemplateOverride} from '../model/template.ts';
import {TemplateService} from '../service/template.ts';
import {EventService} from '../service/event.ts';
import {dispatchFeedback} from '../model/dom-events.ts';
import {ConfirmationDialogService} from '../service/confirmation-dialog.ts';
import {downloadFile, errorMessage, responseErrorMessage} from '../service/helpers.ts';
import {badges, base, modernLayout, textColors} from '../styles.ts';
import {taskContent} from '../components/task-content.ts';
import {sectionCard} from '../components/section-card.ts';
import '../components/code-editor.ts';
import {mustacheLanguage} from './mustache-language.ts';

interface LocaleEdit {
    language: ContentLanguage;
    locale: string;
    // the default content of the template for this locale
    templateBody: string;
    // the content restored by Reset: the override if present, the default template otherwise
    original: string;
    // the current content of the editor
    current: string;
    override: TemplateOverride | null;
    previewMode: boolean;
    previewText: string;
    // a binary preview (e.g. a PDF) has been downloaded: saving is allowed
    binaryPreview: boolean;
}

interface LoadedData {
    scope: ResourceScope;
    locales: LocaleEdit[];
    // the template in the list of overridable templates, with its context; null if the name is unknown
    descriptor: TemplateDescriptor | null;
}

/**
 * Editor of an overridable template, with one tab per language.
 * Hosted by the "system-edit-resources", "organization-edit-resources" and "events.single.edit-resources"
 * routes of the AngularJS admin, through "data-system", "data-organization-id" or "data-event-name",
 * plus "data-resource-name".
 */
@customElement('alfio-template-edit')
export class TemplateEdit extends LitElement {

    @property({type: String, attribute: 'data-resource-name'}) resourceName = '';
    @property({type: Boolean, attribute: 'data-system'}) system = false;
    @property({type: Number, attribute: 'data-organization-id'}) organizationId?: number;
    @property({type: String, attribute: 'data-event-name'}) eventName = '';

    @state() private data: LoadedData | null = null;
    @state() private error: string | null = null;
    @state() private busyLocale: string | null = null;
    @state() private reloadCount = 0;

    private readonly loadTask = new Task(this, {
        task: async ([resourceName, system, organizationId, eventName]): Promise<LoadedData> => {
            if (!this.ready) {
                // AngularJS has not interpolated the attributes yet: the next run will have them
                return new Promise<LoadedData>(() => {});
            }
            const scope = await this.resolveScope(system, organizationId, eventName);
            let languagesPromise: Promise<ContentLanguage[]>;
            if (scope.type === 'event') {
                languagesPromise = TemplateService.eventLanguages(eventName);
            } else {
                languagesPromise = TemplateService.allLanguages();
            }
            const [languages, descriptors] = await Promise.all([languagesPromise, TemplateService.listTemplates()]);
            const locales = await Promise.all(languages.map((language) => this.loadLocale(scope, resourceName, language)));
            const descriptor = descriptors.find((d) => d.name === resourceName) ?? null;
            return {scope, locales, descriptor};
        },
        args: () => [this.resourceName, this.system, this.organizationId, this.eventName, this.reloadCount] as const,
        onComplete: (data) => {
            this.data = data;
        },
    });

    /**
     * False while AngularJS has not interpolated the attributes yet (e.g. data-resource-name="{{...}}")
     */
    private get ready(): boolean {
        if (this.resourceName === '' || this.resourceName.includes('{{') || this.eventName.includes('{{')) {
            return false;
        }
        if (!this.system && this.eventName === '' && (this.organizationId == null || Number.isNaN(this.organizationId))) {
            return false;
        }
        return true;
    }

    private async resolveScope(system: boolean, organizationId: number | undefined, eventName: string): Promise<ResourceScope> {
        if (system) {
            return {type: 'system'};
        }
        if (organizationId != null && !Number.isNaN(organizationId)) {
            return {type: 'organization', organizationId};
        }
        const {event} = await EventService.load(eventName);
        return {type: 'event', organizationId: event.organizationId, eventId: event.id};
    }

    private async loadLocale(scope: ResourceScope, resourceName: string, language: ContentLanguage): Promise<LocaleEdit> {
        const locale = language.locale;
        const fileName = TemplateService.fileName(resourceName, locale);
        const [templateBody, override] = await Promise.all([
            TemplateService.templateBody(resourceName, locale),
            TemplateService.loadOverride(scope, fileName),
        ]);
        let original = templateBody;
        if (override != null) {
            original = override.content;
        }
        return {
            language,
            locale,
            templateBody,
            original,
            current: original,
            override,
            previewMode: false,
            previewText: '',
            binaryPreview: false,
        };
    }

    static readonly styles = [
        base,
        badges,
        textColors,
        modernLayout,
        css`
            :host {
                display: block;
            }
            .error-alert {
                margin-bottom: var(--sl-spacing-medium);
            }
            .scope-description {
                margin: 0 0 var(--sl-spacing-medium);
            }
            sl-alert {
                margin-bottom: var(--sl-spacing-medium);
            }
            alfio-code-editor {
                --alfio-code-editor-height: max(24rem, calc(100vh - 30rem));
            }
            pre.preview {
                margin: 0;
                padding: var(--sl-spacing-medium);
                border: 1px solid var(--sl-input-border-color);
                border-radius: var(--sl-input-border-radius);
                background: var(--sl-color-neutral-0);
                font-family: var(--sl-font-mono);
                font-size: var(--sl-font-size-small);
                line-height: 1.5;
                max-height: 32rem;
                overflow: auto;
                white-space: pre-wrap;
            }
            .locale-actions {
                display: flex;
                flex-wrap: wrap;
                justify-content: space-between;
                gap: var(--sl-spacing-small);
                margin-top: var(--sl-spacing-medium);
            }
            .locale-actions .right {
                display: flex;
                flex-wrap: wrap;
                gap: var(--sl-spacing-x-small);
            }
            sl-tab sl-badge {
                margin-inline-start: var(--sl-spacing-2x-small);
            }
            .header-badge + .header-badge {
                margin-inline-start: var(--sl-spacing-2x-small);
            }
        `,
    ];

    render(): TemplateResult {
        return html`
            <div class="container">
                <div class="page-title-row">
                    <h1>${this.editTitle()}</h1>
                </div>
                <hr class="page-separator"/>
                ${when(this.error != null, () => html`
                    <sl-alert open variant="danger" class="error-alert">
                        <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                        ${this.error}
                    </sl-alert>
                `)}
                ${taskContent(this.loadTask, this.data, 'Failed to load the template. Please try again.',
                    (data) => this.renderEditor(data))}
            </div>
        `;
    }

    private renderEditor(data: LoadedData): TemplateResult {
        return sectionCard({icon: 'file-earmark-code', title: this.resourceName, badge: this.metadataBadges(data.descriptor)}, html`
            <p class="scope-description text-muted">${this.scopeDescription(data.scope)}</p>
            ${when(data.scope.type === 'system', () => html`
                <sl-alert open variant="primary">
                    <sl-icon slot="icon" name="info-circle"></sl-icon>
                    Preview is not available for system-level templates.
                </sl-alert>
            `)}
            <sl-tab-group>
                ${repeat(data.locales, (l) => l.locale, (l) => html`
                    <sl-tab slot="nav" panel=${l.locale}>
                        ${l.language.displayLanguage || l.locale}
                        ${when(l.override != null, () => html`<sl-badge pill variant="primary" class="override-badge">customized</sl-badge>`)}
                    </sl-tab>
                `)}
                ${repeat(data.locales, (l) => l.locale, (l) => html`
                    <sl-tab-panel name=${l.locale}>${this.renderLocale(data, l)}</sl-tab-panel>
                `)}
            </sl-tab-group>
        `);
    }

    /**
     * The page title: the human-readable description of the template when it is known,
     * the enum name otherwise. The card below keeps the enum name, which identifies the template.
     */
    private editTitle(): string {
        if (this.data?.descriptor != null && this.data.descriptor.description !== '') {
            return `Edit template: ${this.data.descriptor.description}`;
        }
        return `Edit template: ${this.resourceName}`;
    }

    /**
     * The badges of the template's metadata: the context (e.g. "Emails"), tying the editor to the
     * panel of the templates list the template was opened from, and the recipient (e.g. "attendee").
     */
    private metadataBadges(descriptor: TemplateDescriptor | null): TemplateResult | undefined {
        if (descriptor == null) {
            return undefined;
        }
        const badges = [html`<sl-badge pill variant="primary" class="header-badge">${templateContextInfo(descriptor.context).label}</sl-badge>`];
        if (descriptor.recipient != null) {
            const info = templateRecipientInfo(descriptor.recipient);
            badges.push(html`<sl-badge pill variant=${info.variant} class="header-badge">${info.label}</sl-badge>`);
        }
        return html`${badges}`;
    }

    private renderLocale(data: LoadedData, st: LocaleEdit): TemplateResult {
        const busy = this.busyLocale === st.locale;
        return html`
            ${when(st.previewMode,
                () => html`<pre class="preview">${st.previewText}</pre>`,
                () => html`
                    <alfio-code-editor
                        .language=${mustacheLanguage}
                        .value=${st.current}
                        @value-changed=${(e: CustomEvent<string>) => this.updateLocale(st.locale, {current: e.detail})}>
                    </alfio-code-editor>
                `)}
            <div class="locale-actions">
                <div>
                    ${when(this.showDelete(data.scope, st), () => html`
                        <sl-button variant="danger" data-action="delete" ?disabled=${busy} @click=${() => this.deleteFor(data, st)}>
                            <sl-icon slot="prefix" name="trash"></sl-icon>Delete
                        </sl-button>
                    `)}
                </div>
                <div class="right">
                    <sl-button variant="default" size="large" data-action="reset" ?disabled=${busy} @click=${() => this.resetFor(st)}>Reset</sl-button>
                    ${when(st.previewMode,
                        () => html`
                            <sl-button variant="default" size="large" data-action="close-preview" ?disabled=${busy}
                                       @click=${() => this.updateLocale(st.locale, {previewMode: false})}>Close preview</sl-button>
                        `,
                        () => html`
                            <sl-button variant="default" size="large" data-action="preview" ?disabled=${busy || data.scope.type === 'system'}
                                       @click=${() => this.previewFor(data, st)}>Preview</sl-button>
                        `)}
                    ${when(this.canSave(data.scope, st), () => html`
                        <sl-button variant="warning" size="large" data-action="save" ?loading=${busy} ?disabled=${busy}
                                   @click=${() => this.saveFor(data, st)}>Save</sl-button>
                    `)}
                </div>
            </div>
        `;
    }

    private updateLocale(locale: string, changes: Partial<LocaleEdit>): void {
        if (this.data == null) {
            return;
        }
        this.data = {
            ...this.data,
            locales: this.data.locales.map((l) => {
                if (l.locale === locale) {
                    return {...l, ...changes};
                }
                return l;
            }),
        };
    }

    /**
     * For organization and event templates, saving is allowed only after a successful preview,
     * which is also what reports a syntax error
     */
    private canSave(scope: ResourceScope, st: LocaleEdit): boolean {
        return scope.type === 'system' || st.previewMode || st.binaryPreview;
    }

    /**
     * The Delete button is shown only when the override was saved at this level:
     * an organization override inherited by an event cannot be deleted from the event
     */
    private showDelete(scope: ResourceScope, st: LocaleEdit): boolean {
        if (st.override == null) {
            return false;
        }
        if (scope.type === 'event') {
            return st.override.metadata.eventId === scope.eventId;
        }
        return true;
    }

    private async saveFor(data: LoadedData, st: LocaleEdit): Promise<void> {
        if (this.busyLocale != null) {
            return;
        }
        this.error = null;
        this.busyLocale = st.locale;
        try {
            const res = await TemplateService.save(data.scope, TemplateService.fileName(this.resourceName, st.locale), st.current);
            if (!res.ok) {
                throw new Error(await responseErrorMessage(res, `unexpected status ${res.status}`));
            }
            dispatchFeedback({type: 'success', message: `The ${st.locale} version of ${this.resourceName} has been saved`}, this);
            this.reloadCount++;
        } catch (e) {
            this.error = errorMessage(e, 'Failed to save the template');
        } finally {
            this.busyLocale = null;
        }
    }

    private async deleteFor(data: LoadedData, st: LocaleEdit): Promise<void> {
        if (this.busyLocale != null) {
            return;
        }
        const confirmed = await ConfirmationDialogService.requestConfirm(
            'Delete template override',
            `The ${st.locale} version of ${this.resourceName} will be deleted and the default template will be used again.`,
            'danger');
        if (!confirmed) {
            return;
        }
        this.error = null;
        this.busyLocale = st.locale;
        try {
            const res = await TemplateService.remove(data.scope, TemplateService.fileName(this.resourceName, st.locale));
            if (!res.ok) {
                throw new Error(await responseErrorMessage(res, `unexpected status ${res.status}`));
            }
            dispatchFeedback({type: 'success', message: `The ${st.locale} override of ${this.resourceName} has been deleted`}, this);
            this.reloadCount++;
        } catch (e) {
            this.error = errorMessage(e, 'Failed to delete the override');
        } finally {
            this.busyLocale = null;
        }
    }

    private resetFor(st: LocaleEdit): void {
        this.updateLocale(st.locale, {current: st.original, previewMode: false, previewText: ''});
    }

    private async previewFor(data: LoadedData, st: LocaleEdit): Promise<void> {
        if (this.busyLocale != null || data.scope.type === 'system') {
            return;
        }
        this.error = null;
        this.busyLocale = st.locale;
        try {
            const res = await TemplateService.preview(this.resourceName, st.locale, data.scope,
                TemplateService.fileName(this.resourceName, st.locale), st.current);
            if (!res.ok) {
                throw new Error(await responseErrorMessage(res, `unexpected status ${res.status}`));
            }
            const contentType = res.headers.get('content-type') ?? '';
            if (contentType.startsWith('text/plain')) {
                const text = await res.text();
                this.updateLocale(st.locale, {previewMode: true, previewText: text, binaryPreview: false});
            } else {
                // a binary document, e.g. a PDF: download it
                const blob = await res.blob();
                const disposition = res.headers.get('content-disposition') ?? '';
                const fileName = disposition.match(/filename=(.*)$/)?.[1] ?? `${this.resourceName}.bin`;
                downloadFile(fileName, blob);
                this.updateLocale(st.locale, {binaryPreview: true, previewMode: false});
            }
        } catch (e) {
            this.error = errorMessage(e, 'Failed to render the preview. Please check the syntax and retry');
        } finally {
            this.busyLocale = null;
        }
    }

    private scopeDescription(scope: ResourceScope): string {
        if (scope.type === 'system') {
            return 'System-level template: an override applies to all organizations and events.';
        }
        if (scope.type === 'organization') {
            return `Organization-level template: an override applies to all events of organization #${scope.organizationId}.`;
        }
        return `Event-level template: an override applies to ${this.eventName} only.`;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-template-edit': TemplateEdit;
    }
}
