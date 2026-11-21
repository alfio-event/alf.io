import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import type {SlDialog, SlRequestCloseEvent} from '@shoelace-style/shoelace';
import {SettingsLevel} from '../model/configuration.ts';
import {base, dialog, textColors} from '../styles.ts';
import './settings-form.ts';

const SYSTEM: SettingsLevel = {type: 'SYSTEM'};

/**
 * Asks for the settings needed to run alf.io, when some of them are missing (first start).
 * The admin page renders it only in that case; it opens by itself, and reloads the page once the settings are saved.
 */
@customElement('alfio-basic-settings-dialog')
export class BasicSettingsDialog extends LitElement {

    @state() private opened = false;
    @query('sl-dialog') private dialog!: SlDialog;

    static readonly styles = [
        base,
        textColors,
        dialog,
        css`
            sl-dialog {
                --alfio-dialog-max-width: 60rem;
            }
            .intro {
                margin-top: 0;
            }
        `,
    ];

    async firstUpdated(): Promise<void> {
        this.opened = true;
        await this.updateComplete;
        await this.dialog.show();
    }

    render(): TemplateResult {
        return html`
            <sl-dialog class="responsive-dialog" label="Basic configuration"
                       @sl-request-close=${this.onRequestClose}
                       @sl-after-hide=${this.onAfterHide}>
                ${when(this.opened, () => html`
                    <p class="intro text-muted">
                        alf.io needs these settings to run. You can change them, and all the others, later in the configuration.
                    </p>
                    <alfio-settings-form data-basic .level=${SYSTEM}
                                         @alfio-settings-saved=${() => window.location.reload()}
                                         @alfio-settings-cancelled=${() => void this.dialog.hide()}></alfio-settings-form>
                `)}
            </sl-dialog>
        `;
    }

    // a click outside the dialog would discard the settings typed so far: only the close button and Cancel close it
    private onRequestClose = (event: SlRequestCloseEvent): void => {
        if (event.detail.source === 'overlay') {
            event.preventDefault();
        }
    };

    private onAfterHide = (event: Event): void => {
        // nested Shoelace components (e.g. selects) emit their own sl-after-hide
        if (event.target !== event.currentTarget) {
            return;
        }
        this.opened = false;
    };
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-basic-settings-dialog': BasicSettingsDialog;
    }
}
