import {html, nothing, TemplateResult} from 'lit';
import {when} from 'lit/directives/when.js';
import {Task, TaskStatus} from '@lit/task';

/**
 * Renders the data loaded by a page's task:
 * - an error alert if the last load failed
 * - the last loaded data, which stays mounted while a refresh is running
 * - a spinner on first load
 * Use with the "purchaseContextListPage" styles.
 */
export function taskContent<T>(task: Task<any, any>,
                               lastData: T | null,
                               errorMessage: string,
                               renderContent: (data: T) => TemplateResult): TemplateResult {
    const failed = task.status === TaskStatus.ERROR;
    return html`
        ${when(failed, () => html`
            <sl-alert open variant="danger">
                <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                ${errorMessage}
            </sl-alert>
        `)}
        ${when(lastData != null,
            () => renderContent(lastData!),
            () => when(!failed, () => html`<div class="loading"><sl-spinner></sl-spinner></div>`, () => nothing))}
    `;
}
