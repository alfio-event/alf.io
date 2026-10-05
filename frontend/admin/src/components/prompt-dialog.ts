import {html, LitElement, TemplateResult} from 'lit';
import {query} from 'lit/decorators.js';
import type {SlDialog} from '@shoelace-style/shoelace';

/**
 * Label of a dialog: an icon, a title and a short description. Use with the "dialog" styles.
 */
export function dialogTitle(icon: string, title: string, description: string): TemplateResult {
    return html`
        <div slot="label" class="dialog-title">
            <sl-icon name=${icon}></sl-icon>
            <div>
                <strong>${title}</strong>
                <small>${description}</small>
            </div>
        </div>
    `;
}

/**
 * Base class for dialogs asking the user to make a choice.
 * Subclasses render a single sl-dialog wired to onAfterHide, call prompt() to display it and close() to choose.
 * The promise returned by prompt() is resolved once the dialog has been hidden, with null if it has been dismissed.
 */
export abstract class PromptDialog<T> extends LitElement {
    @query('sl-dialog') protected dialog!: SlDialog;
    private result: T | null = null;
    private resolve: ((result: T | null) => void) | null = null;

    protected async prompt(): Promise<T | null> {
        // a pending prompt is considered dismissed
        this.resolve?.(null);
        this.result = null;
        await this.updateComplete;
        return new Promise((resolve) => {
            this.resolve = resolve;
            void this.dialog.show();
        });
    }

    protected close(result: T | null): void {
        this.result = result;
        void this.dialog.hide();
    }

    protected onAfterHide = (e: Event): void => {
        // sl-after-hide bubbles from nested components (e.g. tooltips)
        if (e.target !== this.dialog) {
            return;
        }
        this.resolve?.(this.result);
        this.resolve = null;
    };
}
