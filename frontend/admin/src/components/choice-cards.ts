import {html, TemplateResult} from 'lit';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';

export interface ChoiceCardOption<T extends string> {
    value: T;
    icon: string;
    title: string;
    description: string;
}

export interface ChoiceCardsOptions<T extends string> {
    // radio group name, unique within the component
    name: string;
    label: string;
    value: T;
    options: ChoiceCardOption<T>[];
    // additional classes, e.g. to identify the group in tests
    className?: string;
    // inputs of the selected option, shown in a tinted panel right below the cards
    panel?: TemplateResult;
}

/**
 * Single choice between a few options, each one described by an icon, a title and a short description.
 * Based on native radio inputs (keyboard navigation, screen readers). Use with the "choiceCards" styles.
 * The panel is part of the fieldset, so that its inputs belong to the group.
 */
export function choiceCards<T extends string>(options: ChoiceCardsOptions<T>, onChange: (value: T) => void): TemplateResult {
    return html`
        <fieldset class="choice-cards ${options.className ?? ''}">
            <legend>${options.label}</legend>
            <div class="choice-cards-grid">
                ${repeat(options.options, option => option.value, option => html`
                    <label class="choice-card" data-value=${option.value}>
                        <input type="radio" name=${options.name} value=${option.value} .checked=${option.value === options.value}
                               @change=${() => onChange(option.value)} />
                        <span class="choice-card-icon"><sl-icon name=${option.icon}></sl-icon></span>
                        <span class="choice-card-text">
                            <span class="choice-card-title">${option.title}</span>
                            <span class="choice-card-description">${option.description}</span>
                        </span>
                        <sl-icon class="choice-card-check" name="check-circle-fill" aria-hidden="true"></sl-icon>
                    </label>
                `)}
            </div>
            ${when(options.panel, () => html`<div class="choice-panel">${options.panel}</div>`)}
        </fieldset>
    `;
}
