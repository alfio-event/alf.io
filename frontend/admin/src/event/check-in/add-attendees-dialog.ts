import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {choose} from 'lit/directives/choose.js';
import type {SlDialog, SlRequestCloseEvent} from '@shoelace-style/shoelace';
import {ReservationCreatedDetail} from '../../model/admin-reservation.ts';
import {ReservationService} from '../../service/reservation.ts';
import {dialog} from '../../styles.ts';
import {ReservationChangedDetail} from '../../reservation-detail/reservation-detail.ts';
import '../reservation-create/reservation-create.ts';
import '../../reservation-detail/reservation-detail.ts';

type Step = 'create' | 'confirm' | 'fix';

export interface AttendeesAddedDetail {
    reservationId: string;
}

/**
 * Adds attendees on site: creates a reservation whose contact is the first attendee, then confirms it right away.
 * If the reservation can't be confirmed, its details are displayed, so that the operator can fix it.
 * Fires "alfio-attendees-added" once the reservation is confirmed.
 */
@customElement('alfio-add-attendees-dialog')
export class AddAttendeesDialog extends LitElement {
    @property({ type: String, attribute: 'data-event-name' }) eventName = '';
    // the content is rendered only while the dialog is open, so that every opening starts from an empty form
    @state() private opened = false;
    @state() private step: Step = 'create';
    @state() private reservationId = '';
    @query('sl-dialog') private dialog!: SlDialog;

    static readonly styles = [
        dialog,
        css`
            sl-dialog {
                --alfio-dialog-max-width: 75rem;
            }
            sl-dialog::part(body) {
                padding-top: 0;
            }
        `,
    ];

    async show(): Promise<void> {
        this.step = 'create';
        this.reservationId = '';
        this.opened = true;
        await this.updateComplete;
        await this.dialog.show();
    }

    render(): TemplateResult {
        return html`
            <sl-dialog class="responsive-dialog" label="Add new attendees"
                       @sl-request-close=${this.onRequestClose}
                       @sl-after-hide=${this.onAfterHide}>
                ${choose(this.step, [
                    ['create', () => this.renderCreate()],
                    ['confirm', () => html`<div class="dialog-loading"><sl-spinner></sl-spinner></div>`],
                    ['fix', () => this.renderFix()],
                ])}
            </sl-dialog>
        `;
    }

    private renderCreate(): TemplateResult {
        if (!this.opened) {
            return html``;
        }
        return html`
            <alfio-reservation-create data-fast-creation data-event-name=${this.eventName}
                                      @alfio-reservation-created=${this.onCreated}
                                      @alfio-reservation-create-cancelled=${this.close}></alfio-reservation-create>
        `;
    }

    private renderFix(): TemplateResult {
        return html`
            <alfio-reservation-detail data-embedded data-purchase-context-type="event"
                                      data-public-identifier=${this.eventName} data-reservation-id=${this.reservationId}
                                      @alfio-reservation-changed=${this.onReservationChanged}
                                      @alfio-reservation-close=${this.close}></alfio-reservation-detail>
        `;
    }

    private onCreated = async (event: CustomEvent<ReservationCreatedDetail>): Promise<void> => {
        this.reservationId = event.detail.reservationId;
        this.step = 'confirm';
        try {
            await new ReservationService('event', this.eventName, this.reservationId).confirm();
            this.onConfirmed();
        } catch (error) {
            console.error(error);
            this.step = 'fix';
        }
    };

    private onReservationChanged = (event: CustomEvent<ReservationChangedDetail>): void => {
        if (event.detail.confirmed) {
            this.onConfirmed();
        }
    };

    private onConfirmed(): void {
        this.dispatchEvent(new CustomEvent<AttendeesAddedDetail>('alfio-attendees-added', {
            detail: { reservationId: this.reservationId },
            bubbles: true,
            composed: true,
        }));
        this.close();
    }

    private close = (): void => {
        void this.dialog.hide();
    };

    // a click outside the dialog would discard the attendees typed so far: only the close button and Cancel close it
    private onRequestClose = (event: SlRequestCloseEvent): void => {
        if (event.detail.source === 'overlay') {
            event.preventDefault();
        }
    };

    private onAfterHide = (event: Event): void => {
        // nested Shoelace components (e.g. tooltips, selects) emit their own sl-after-hide
        if (event.target !== event.currentTarget) {
            return;
        }
        this.opened = false;
        this.step = 'create';
    };
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-add-attendees-dialog': AddAttendeesDialog;
    }
    interface GlobalEventHandlersEventMap {
        'alfio-attendees-added': CustomEvent<AttendeesAddedDetail>;
    }
}
