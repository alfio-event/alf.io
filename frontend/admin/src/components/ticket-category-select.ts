import {html, ReactiveController, ReactiveControllerHost, TemplateResult} from 'lit';
import {classMap} from 'lit/directives/class-map.js';
import {ifDefined} from 'lit/directives/if-defined.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import type {SlSelect} from '@shoelace-style/shoelace';
import {TicketCategory} from '../model/event.ts';
import {EventService} from '../service/event.ts';
import {errorMessage} from '../service/helpers.ts';

/**
 * Ticket categories of an event, which can be reloaded without reloading the page,
 * e.g. after adding a category in another tab
 */
export class TicketCategoriesController implements ReactiveController {
    categories: TicketCategory[] = [];
    refreshing = false;
    error: string | null = null;

    constructor(private readonly host: ReactiveControllerHost) {
        host.addController(this);
    }

    hostConnected(): void {
        // the categories are set by the host, once it has loaded the event
    }

    set(categories: TicketCategory[]): void {
        this.categories = categories;
        this.error = null;
        this.host.requestUpdate();
    }

    async refresh(eventName: string): Promise<void> {
        this.refreshing = true;
        this.error = null;
        this.host.requestUpdate();
        try {
            const { event } = await EventService.load(eventName);
            this.categories = event.ticketCategories;
        } catch (error) {
            this.error = errorMessage(error, 'Failed to reload the categories. Please try again.');
        } finally {
            this.refreshing = false;
            this.host.requestUpdate();
        }
    }

    contains(categoryId: string): boolean {
        return this.categories.some(category => String(category.id) === categoryId);
    }

    find(categoryId: string): TicketCategory | undefined {
        return this.categories.find(category => String(category.id) === categoryId);
    }
}

export interface TicketCategorySelectOptions {
    id?: string;
    className?: string;
    value: string;
    // validation error to display, see fieldError()
    error?: string;
    onChange: (categoryId: string) => void;
    onBlur?: () => void;
    onRefresh: () => void;
}

/**
 * Select of an existing category, with a button to reload the categories. Use with the "form" styles.
 */
export function ticketCategorySelect(controller: TicketCategoriesController, options: TicketCategorySelectOptions): TemplateResult {
    return html`
        <div class="input-with-action">
            <sl-select id=${ifDefined(options.id)} class=${classMap({ [options.className ?? '']: options.className != null, error: options.error != null })}
                       label="Category" placeholder="Select a category" required hoist
                       help-text=${options.error ?? ''}
                       .value=${options.value}
                       @sl-change=${(e: Event) => options.onChange(String((e.target as SlSelect).value))}
                       @sl-blur=${() => options.onBlur?.()}>
                ${repeat(controller.categories, category => category.id, category => html`
                    <sl-option value=${String(category.id)}>${category.name}</sl-option>
                `)}
            </sl-select>
            <sl-tooltip content="Reload the categories" placement="top-end">
                <sl-button class="refresh-categories" variant="default" ?loading=${controller.refreshing} @click=${options.onRefresh}>
                    <sl-icon slot="prefix" name="arrow-clockwise"></sl-icon>
                    Refresh
                </sl-button>
            </sl-tooltip>
        </div>
        ${when(controller.error, () => html`
            <sl-alert class="refresh-categories-error" variant="danger" open>
                <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                ${controller.error}
            </sl-alert>
        `)}
    `;
}
