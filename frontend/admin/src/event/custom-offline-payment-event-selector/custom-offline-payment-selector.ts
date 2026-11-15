import { customElement, property } from "lit/decorators.js";
import { css, html, LitElement, type TemplateResult } from "lit";
import { repeat } from "lit/directives/repeat.js";
import { when } from "lit/directives/when.js";
import { SlCheckbox } from "@shoelace-style/shoelace";

@customElement('custom-offline-payment-selector')
export class CustomOfflinePaymentSelector extends LitElement {
    static styles = css`
        .payment-methods {
            display: grid;
            gap: var(--sl-spacing-x-small);
        }
    `;

    @property({ type: Array })
    paymentMethods: Array<{ paymentMethod: CustomOfflinePayment; selected: boolean }> = [];
    @property({ type: Boolean })
    strikeThroughSelected: boolean = false;

    updated(changedProperties: Map<string, unknown>) {
        if (changedProperties.has("paymentMethods")) {
            this.dispatchSelectionChange();
        }
    }

    dispatchSelectionChange() {
        const selectedMethods = this.paymentMethods.map(method => ({
            paymentMethod: method.paymentMethod,
            selected: method.selected
        }));
        this.dispatchEvent(new CustomEvent('selection-change', { detail: selectedMethods }));
    }

    protected render(): TemplateResult {
        return html`
            <form class="payment-methods">
                ${repeat(
                    this.paymentMethods,
                    (_, index) => index,
                    (item, index) => {
                        const paymentMethod = item.paymentMethod;
                        const localizationKeys = Object.keys(paymentMethod.localizations);
                        const paymentMethodName =
                            paymentMethod.localizations?.["en"]?.paymentName
                            ?? paymentMethod.localizations[localizationKeys[0]].paymentName;
                        return html`
                            <sl-checkbox
                                .checked=${item.selected}
                                @sl-change=${(changed: Event) => {
                                    const checkbox = changed.target as SlCheckbox;
                                    this.paymentMethods[index].selected = checkbox.checked;
                                    this.requestUpdate("paymentMethods");
                                    this.dispatchSelectionChange();
                                }}
                            >
                                ${when(this.strikeThroughSelected && item.selected,
                                    () => html`<del>${paymentMethodName}</del>`,
                                    () => paymentMethodName)}
                            </sl-checkbox>
                        `;
                    }
                )}
            </form>
        `;
    }
}
