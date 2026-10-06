import {html, ReactiveController, ReactiveControllerHost, TemplateResult} from 'lit';
import {createRef, ref} from 'lit/directives/ref.js';
import type {SlInput} from '@shoelace-style/shoelace';

const SEARCH_DELAY_MS = 250;

/**
 * Search field of server-side filtered list pages: debounces the input and notifies the host
 * (onSearch) only when the search term actually changes. Use with the "purchaseContextListPage" styles.
 */
export class ListSearchController implements ReactiveController {
    // last term notified to the host
    private applied = '';
    private input = '';
    private timer: ReturnType<typeof setTimeout> | undefined;
    private readonly field = createRef<SlInput>();

    constructor(host: ReactiveControllerHost, private readonly onSearch: (search: string) => void) {
        host.addController(this);
    }

    /**
     * Sets the initial term (e.g. read from the route parameters) without notifying the host
     */
    init(search: string): void {
        this.applied = search;
        this.input = search;
    }

    hostDisconnected(): void {
        clearTimeout(this.timer);
    }

    /**
     * @param itemLabel plural label of the listed items, e.g. "payments"
     */
    render(itemLabel: string): TemplateResult {
        const capitalized = itemLabel.charAt(0).toUpperCase() + itemLabel.slice(1);
        return html`
            <sl-input
                ${ref(this.field)}
                class="list-search label-hidden"
                label="Filter ${itemLabel}"
                placeholder="Filter ${capitalized}"
                clearable
                .value=${this.input}
                @sl-input=${this.onInput}
                @sl-clear=${this.clear}
            >
                <sl-icon slot="prefix" name="search"></sl-icon>
            </sl-input>
        `;
    }

    private onInput = (event: Event): void => {
        this.input = (event.target as SlInput).value;
        clearTimeout(this.timer);
        this.timer = setTimeout(() => this.apply(), SEARCH_DELAY_MS);
    };

    private clear = (): void => {
        this.input = '';
        this.apply();
        this.field.value?.focus();
    };

    private apply(): void {
        clearTimeout(this.timer);
        if (this.applied === this.input) {
            return;
        }
        this.applied = this.input;
        this.onSearch(this.applied);
    }
}
