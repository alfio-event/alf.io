import { customElement, property } from "lit/decorators.js";
import { html, LitElement, type TemplateResult } from "lit";
import { repeat } from "lit/directives/repeat.js";
import { SlCheckbox } from "@shoelace-style/shoelace";

@customElement('custom-offline-payment-selector')
export class CustomOfflinePaymentSelector extends LitElement {
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
            <form>
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
                                ${this.strikeThroughSelected && item.selected ?
                                    html`<del>${paymentMethodName}</del>`
                                    : paymentMethodName
                                }
                            </sl-checkbox>
                            <br/>
                        `;
                    }
                )}
            </form>
        `;
    }
}
