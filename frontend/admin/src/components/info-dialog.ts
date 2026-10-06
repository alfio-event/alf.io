import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, query, state} from 'lit/decorators.js';
import {when} from 'lit/directives/when.js';
import type {SlDialog} from '@shoelace-style/shoelace';
import {badges, base, detailList, dialog, modernLayout, modernTable, retroCompat, row, textColors} from '../styles.ts';
import {dispatchFeedback} from '../model/dom-events.ts';
import {dialogTitle} from './prompt-dialog.ts';

export interface InfoDialogContent {
    icon: string;
    title: string;
    description: string;
    // loads the body of the dialog. A spinner is displayed in the meantime
    body: () => Promise<TemplateResult> | TemplateResult;
    errorMessage?: string;
}

/**
 * Read-only dialog showing details provided by the caller, e.g. a list rendered with detailList() or a table.
 * Its styles include "detailList", "modernTable" and "badges".
 */
@customElement('alfio-info-dialog')
export class InfoDialog extends LitElement {
    @state() private content: InfoDialogContent | null = null;
    @state() private body: TemplateResult | null = null;
    @query('sl-dialog') private dialog!: SlDialog;

    static readonly styles = [
        base,
        retroCompat,
        textColors,
        badges,
        modernTable,
        modernLayout,
        detailList,
        dialog,
        row,
        css`
            sl-dialog {
                --alfio-dialog-max-width: 40rem;
            }
        `,
    ];

    async open(content: InfoDialogContent): Promise<void> {
        this.content = content;
        this.body = null;
        await this.updateComplete;
        // don't wait for the animation: show() never resolves when called right after the first render
        void this.dialog.show();
        try {
            this.body = await content.body();
        } catch {
            dispatchFeedback({ type: 'danger', message: content.errorMessage ?? 'Failed to load the details' }, this);
            await this.dialog.hide();
        }
    }

    render(): TemplateResult {
        const content = this.content;
        return html`
            <sl-dialog class="responsive-dialog" label=${content?.title ?? ''} placement="bottom">
                ${when(content, () => dialogTitle(content!.icon, content!.title, content!.description))}
                ${when(this.body,
                    () => this.body!,
                    () => html`<div class="dialog-loading"><sl-spinner></sl-spinner></div>`)}
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => this.dialog.hide()}>Close</sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-info-dialog': InfoDialog;
    }
}
