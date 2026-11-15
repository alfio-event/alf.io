import {html, LitElement, TemplateResult} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {choose} from 'lit/directives/choose.js';
import type {SlDialog} from '@shoelace-style/shoelace';
import {EventService} from '../../service/event.ts';
import {errorMessage} from '../../service/helpers.ts';
import {dialog, row} from '../../styles.ts';

type Step = 'loading' | 'ready' | 'error';

/**
 * Opens an application linked to the event by an extension (LINK_EXTERNAL_APPLICATION capability):
 * the extension generates the link, which the organizer then opens in a new tab
 */
@customElement('alfio-linked-application-dialog')
export class LinkedApplicationDialog extends LitElement {

    @query('sl-dialog') private dialog!: SlDialog;

    @state() private label = '';
    @state() private step: Step = 'loading';
    @state() private link = '';
    @state() private error = '';
    // discards the answer of a previous opening
    private request = 0;

    static readonly styles = [dialog, row];

    async show(eventName: string, capability: string, label: string): Promise<void> {
        const request = ++this.request;
        this.label = label;
        this.step = 'loading';
        this.link = '';
        this.error = '';
        await this.updateComplete;
        void this.dialog.show();
        try {
            const link = await EventService.executeCapability(eventName, capability, {});
            if (request === this.request) {
                this.link = link;
                this.step = 'ready';
            }
        } catch (error) {
            if (request === this.request) {
                this.error = errorMessage(error, 'The link could not be generated');
                this.step = 'error';
            }
        }
    }

    render(): TemplateResult {
        return html`
            <sl-dialog class="responsive-dialog" label=${this.label} style="--alfio-dialog-max-width: 32rem">
                ${choose(this.step, [
                    ['loading', () => html`<p><sl-spinner></sl-spinner> Generating the link…</p>`],
                    ['ready', () => html`
                        <p>The link is ready.</p>
                        <sl-button id="open-linked-application" variant="primary" href=${this.link} target="_blank"
                                   @click=${() => void this.dialog.hide()}>
                            <sl-icon slot="suffix" name="box-arrow-up-right"></sl-icon>
                            Open ${this.label}
                        </sl-button>
                    `],
                    ['error', () => html`
                        <sl-alert variant="danger" open>
                            <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                            ${this.error}
                        </sl-alert>
                    `],
                ])}
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => void this.dialog.hide()}>Close</sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-linked-application-dialog': LinkedApplicationDialog;
    }
}
