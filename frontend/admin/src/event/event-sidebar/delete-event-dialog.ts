import {html, LitElement, TemplateResult} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import type {SlDialog, SlInput, SlRequestCloseEvent} from '@shoelace-style/shoelace';
import {EventService} from '../../service/event.ts';
import {errorMessage} from '../../service/helpers.ts';
import {dispatchFeedback} from '../../model/dom-events.ts';
import {dialog, form, row} from '../../styles.ts';
import { navigate } from '../../admin-router';
import { t, tHtml } from '../../service/i18n';

/**
 * Deletes an event with all its data. As a safety measure, the organizer types the short name of the event.
 * Goes back to the event list once the event has been deleted
 */
@customElement('alfio-delete-event-dialog')
export class DeleteEventDialog extends LitElement {

    @query('sl-dialog') private dialog!: SlDialog;

    @state() private opened = false;
    @state() private eventId = 0;
    @state() private shortName = '';
    @state() private typedName = '';
    @state() private deleting = false;
    @state() private error: string | null = null;

    static readonly styles = [dialog, form, row];

    async show(eventId: number, shortName: string): Promise<void> {
        this.eventId = eventId;
        this.shortName = shortName;
        this.typedName = '';
        this.deleting = false;
        this.error = null;
        this.opened = true;
        await this.updateComplete;
        await this.dialog.show();
    }

    render(): TemplateResult {
        const confirmed = this.typedName.trim() === this.shortName;
        return html`
            <sl-dialog class="responsive-dialog" label=${t('admin.event-menu.delete')} style="--alfio-dialog-max-width: 36rem"
                       @sl-request-close=${this.onRequestClose} @sl-after-hide=${this.onAfterHide}>
                ${when(this.opened, () => html`
                    <div class="form-stack">
                        <p>
                            ${tHtml('admin.event-menu.delete.text', html`<strong>${this.shortName}</strong>`)}
                        </p>
                        <sl-input id="delete-confirmation" label=${t('admin.event-menu.delete.type-to-confirm', this.shortName)} autocomplete="off"
                                  .value=${this.typedName}
                                  @sl-input=${(e: Event) => { this.typedName = (e.target as SlInput).value; }}></sl-input>
                        ${when(this.error != null, () => html`
                            <sl-alert variant="danger" open>
                                <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                                ${this.error}
                            </sl-alert>
                        `)}
                    </div>
                `)}
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => void this.dialog.hide()}>${t('admin.common.cancel')}</sl-button>
                        <div></div>
                        <sl-button id="confirm-delete-event" variant="danger" size="large"
                                   ?disabled=${!confirmed} ?loading=${this.deleting} @click=${this.delete}>
                            <sl-icon slot="prefix" name="trash"></sl-icon>
                            ${t('admin.common.delete')}
                        </sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    private delete = async (): Promise<void> => {
        if (this.typedName.trim() !== this.shortName || this.deleting) {
            return;
        }
        this.deleting = true;
        this.error = null;
        try {
            await EventService.deleteEvent(this.eventId);
            dispatchFeedback({type: 'success', message: t('admin.event-menu.delete.done', this.shortName)}, this);
            await this.dialog.hide();
            navigate('/');
        } catch (error) {
            this.error = errorMessage(error, t('admin.error.delete-event'));
        } finally {
            this.deleting = false;
        }
    };

    private onRequestClose = (e: SlRequestCloseEvent): void => {
        if (e.detail.source === 'overlay') {
            e.preventDefault();
        }
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
        'alfio-delete-event-dialog': DeleteEventDialog;
    }
}
