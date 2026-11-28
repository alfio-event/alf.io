import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import type {SlCheckbox, SlDialog, SlRadioGroup} from '@shoelace-style/shoelace';
import {EventService, ExportField} from '../../service/event.ts';
import {errorMessage} from '../../service/helpers.ts';
import {dialog, row} from '../../styles.ts';
import { t } from '../../service/i18n';

export type ExportKind = 'attendees' | 'waiting-list';

type ExportFormat = 'excel' | 'csv';

/**
 * Downloads the attendees' data (the organizer picks the fields) or the waiting list of an event,
 * as an Excel or CSV file
 */
@customElement('alfio-event-export-dialog')
export class EventExportDialog extends LitElement {

    @query('sl-dialog') private dialog!: SlDialog;

    // the content is rendered only while the dialog is open, so that every opening starts from scratch
    @state() private opened = false;
    @state() private kind: ExportKind = 'attendees';
    @state() private eventName = '';
    @state() private format: ExportFormat = 'excel';
    @state() private fields: ExportField[] | null = null;
    @state() private selected = new Set<string>();
    @state() private loadError: string | null = null;

    static readonly styles = [
        dialog,
        row,
        css`
            .fields {
                display: grid;
                grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr));
                gap: var(--sl-spacing-x-small) var(--sl-spacing-medium);
                margin: var(--sl-spacing-small) 0 var(--sl-spacing-large);
            }
            .fields-header {
                display: flex;
                align-items: center;
                justify-content: space-between;
                gap: var(--sl-spacing-small);
                flex-wrap: wrap;
            }
            .fields-header h3 {
                margin: 0;
                font-size: var(--sl-font-size-medium);
            }
            .select-buttons {
                display: flex;
                gap: var(--sl-spacing-x-small);
            }
        `,
    ];

    async show(eventName: string, kind: ExportKind): Promise<void> {
        this.eventName = eventName;
        this.kind = kind;
        this.format = 'excel';
        this.fields = null;
        this.selected = new Set();
        this.loadError = null;
        this.opened = true;
        await this.updateComplete;
        await this.dialog.show();
        if (kind === 'attendees') {
            try {
                this.fields = await EventService.loadExportFields(eventName);
            } catch (error) {
                this.loadError = errorMessage(error, t('admin.load-error.fields'));
            }
        }
    }

    render(): TemplateResult {
        let label = t('admin.event-menu.export.waiting-list');
        if (this.kind === 'attendees') {
            label = t('admin.event-menu.export.attendees');
        }
        return html`
            <sl-dialog class="responsive-dialog" label=${label} style="--alfio-dialog-max-width: 48rem"
                       @sl-after-hide=${this.onAfterHide}>
                ${when(this.opened, () => this.renderBody())}
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => void this.dialog.hide()}>${t('admin.common.close')}</sl-button>
                        <div></div>
                        <sl-button id="export-download" variant="warning" size="large"
                                   ?disabled=${this.kind === 'attendees' && this.selected.size === 0}
                                   @click=${this.download}>
                            <sl-icon slot="prefix" name="download"></sl-icon>
                            ${t('admin.event.menu.download')}
                        </sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    private renderBody(): TemplateResult {
        return html`
            ${when(this.kind === 'attendees', () => this.renderFields(), () => nothing)}
            <sl-radio-group id="export-format" label=${t('admin.event-menu.export.format')} .value=${this.format}
                            @sl-change=${(e: Event) => { this.format = (e.target as SlRadioGroup).value as ExportFormat; }}>
                <sl-radio-button value="excel">Excel</sl-radio-button>
                <sl-radio-button value="csv">CSV</sl-radio-button>
            </sl-radio-group>
        `;
    }

    private renderFields(): TemplateResult {
        if (this.loadError != null) {
            return html`
                <sl-alert variant="danger" open>
                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                    ${this.loadError}
                </sl-alert>
            `;
        }
        const fields = this.fields;
        if (fields == null) {
            return html`<div class="dialog-loading"><sl-spinner></sl-spinner></div>`;
        }
        return html`
            <div class="fields-header">
                <h3>${t('admin.event-menu.export.fields')}</h3>
                <div class="select-buttons">
                    <sl-button size="small" id="select-all-fields" @click=${() => { this.selected = new Set(fields.map(f => f.key)); }}>${t('admin.common.select-all')}</sl-button>
                    <sl-button size="small" id="select-no-fields" @click=${() => { this.selected = new Set(); }}>${t('admin.common.select-none')}</sl-button>
                </div>
            </div>
            <div class="fields">
                ${repeat(fields, f => f.key, f => html`
                    <sl-checkbox class="export-field" data-key=${f.key} .checked=${this.selected.has(f.key)}
                                 @sl-change=${(e: Event) => this.toggle(f.key, (e.target as SlCheckbox).checked)}>${f.value}</sl-checkbox>
                `)}
            </div>
        `;
    }

    private toggle(key: string, checked: boolean): void {
        const selected = new Set(this.selected);
        if (checked) {
            selected.add(key);
        } else {
            selected.delete(key);
        }
        this.selected = selected;
    }

    private download = (): void => {
        const params = new URLSearchParams({ format: this.format });
        const eventName = encodeURIComponent(this.eventName);
        let url = `/admin/api/event/${eventName}/waiting-queue/download`;
        if (this.kind === 'attendees') {
            url = `/admin/api/events/${eventName}/export`;
            // in the order of the list
            for (const field of this.fields ?? []) {
                if (this.selected.has(field.key)) {
                    params.append('fields', field.key);
                }
            }
        }
        window.open(`${url}?${params.toString()}`);
    };

    private onAfterHide = (e: Event): void => {
        if (e.target !== e.currentTarget) {
            return;
        }
        this.opened = false;
    };
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-event-export-dialog': EventExportDialog;
    }
}
