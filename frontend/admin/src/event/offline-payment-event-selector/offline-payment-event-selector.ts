import { CustomPaymentMethodsService } from "../../service/custom-payment-methods";
import { SlCheckbox } from "@shoelace-style/shoelace";
import { TanStackFormController } from "@tanstack/lit-form";
import { customElement, property } from "lit/decorators.js";
import { html, LitElement, type TemplateResult } from "lit";
import { repeat } from "lit/directives/repeat.js";

type CustomPaymentCheckFormItem = {
    paymentMethod: CustomOfflinePayment,
    selected: boolean
};

@customElement('offline-payment-event-selector')
export class OfflinePaymentEventSelector extends LitElement {
    @property({attribute: "organization", type: Number})
    organization: number = -1;
    @property({attribute: "event", type: Number})
    event: number = -1;

    paymentMethodService?: CustomPaymentMethodsService = new CustomPaymentMethodsService();

    #form = new TanStackFormController(this, {
        defaultValues: {
            checkedMethods: [] as Array<CustomPaymentCheckFormItem>
        }
    });

    async updated(changedProperties: Map<string, unknown>) {
        if(changedProperties.has("organization") && this.organization > -1) {
            const orgPaymentMethods = await this.paymentMethodService?.getPaymentMethodsForOrganization(this.organization);

            let eventSelectedPaymentMethods: string[] = [];

            if(this.event > -1) {
                const response = await this.paymentMethodService?.getAllowedPaymentMethodsForEvent(this.event);
                if(response) {
                    eventSelectedPaymentMethods = response.map(pm => pm.paymentMethodId) as string[];
                }
            }

            const startingFormValues = orgPaymentMethods?.map(item => {
                return {
                    paymentMethod: item,
                    selected: eventSelectedPaymentMethods.includes(item.paymentMethodId!)
                }
            }) as CustomPaymentCheckFormItem[];
            this.#form.api.setFieldValue("checkedMethods", startingFormValues);
        }
    }

    connectedCallback(): void {
        super.connectedCallback();
        window.addEventListener("update-event-prices-form-submit", this.handleUpdatePricesModalSubmit);
        window.addEventListener("new-event-created", this.handleNewEventCreatedEvent)
    }

    disconnectedCallback(): void {
        window.removeEventListener("update-event-prices-form-submit", this.handleUpdatePricesModalSubmit);
        window.removeEventListener("new-event-created", this.handleNewEventCreatedEvent)
        super.disconnectedCallback();
    }

    handleUpdatePricesModalSubmit = (_event: Event) => {
        const eventId = this.event;

        const selectedIds = this.#form.api.state.values.checkedMethods
            .filter(method => method.selected && method.paymentMethod.paymentMethodId)
            .map(method => method.paymentMethod.paymentMethodId);

        this.paymentMethodService?.setPaymentMethodsForEvent(eventId, selectedIds as string[]);
    }

    handleNewEventCreatedEvent = (event: Event) => {
        const customEvent = event as CustomEvent;
        const eventId = customEvent.detail.data.event.id;

        const selectedIds = this.#form.api.state.values.checkedMethods
            .filter(method => method.selected && method.paymentMethod.paymentMethodId)
            .map(method => method.paymentMethod.paymentMethodId);

        this.paymentMethodService?.setPaymentMethodsForEvent(eventId, selectedIds as string[]);
    }

    protected render(): TemplateResult {
        return html`
            <form>
                ${this.#form.field(
                    {name: "checkedMethods"},
                    (paymentMethodsField) => {
                        return html`${repeat(
                            paymentMethodsField.state.value,
                            (_, index) => index,
                            (item, index) => {
                                return html`
                                    ${this.#form.field(
                                        {
                                            name: `checkedMethods[${index}].selected`,
                                        },
                                        (field) => {
                                            return html`
                                                <sl-checkbox
                                                    .checked=${field.state.value}
                                                    @sl-change=${(changed: Event) => {
                                                        const checkbox = changed.target as SlCheckbox;
                                                        field.handleChange(checkbox.checked);
                                                    }}
                                                >
                                                    ${item.paymentMethod.localizations["en"].paymentName}
                                                </sl-checkbox>
                                                <br/>
                                            `;
                                        }
                                    )}
                                `
                            }
                        )}`
                    }
                )}
            </form>
        `;
    }
}
