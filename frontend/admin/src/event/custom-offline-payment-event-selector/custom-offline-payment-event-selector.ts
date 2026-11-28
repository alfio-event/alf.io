import { CustomPaymentMethodsService } from "../../service/custom-payment-methods";
import { customElement, property, state } from "lit/decorators.js";
import { html, LitElement } from "lit";
import { Task } from '@lit/task';
import { t } from '../../service/i18n';

@customElement('custom-offline-payment-event-selector')
export class CustomOfflinePaymentEventSelector extends LitElement {
    @property({ attribute: "organization", type: Number })
    organization: number = -1;

    @property({ attribute: "event", type: Number })
    event: number = -1;

    @state()
    selectedPaymentMethods: Array<{ paymentMethod: CustomOfflinePayment; selected: boolean }> = [];

    paymentMethodService?: CustomPaymentMethodsService = new CustomPaymentMethodsService();

    private _paymentMethodsTask = new Task(this, {
        task: async ([organization, event]) => {
            if (organization <= 0) return [];

            const orgPaymentMethods = await this.paymentMethodService?.getPaymentMethodsForOrganization(organization);
            let eventSelectedPaymentMethods: string[] = [];

            if (event > -1) {
                const response = await this.paymentMethodService?.getAllowedPaymentMethodsForEvent(event);
                if (response) {
                    eventSelectedPaymentMethods = response.map(pm => pm.paymentMethodId) as string[];
                }
            }

            return orgPaymentMethods?.map(item => ({
                paymentMethod: item,
                selected: eventSelectedPaymentMethods.includes(item.paymentMethodId!)
            })) || [];
        },
        args: () => [this.organization, this.event]
    });

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

    handleSelectionChange = (selectedMethods: Array<{ paymentMethod: CustomOfflinePayment; selected: boolean }>) => {
        this.selectedPaymentMethods = selectedMethods;
        this.dispatchEvent(new CustomEvent("selection-changed", {
            detail: selectedMethods.filter(pm => pm.selected).map(pm => pm.paymentMethod)
        }));
    }

    handleUpdatePricesModalSubmit = (_event: Event) => {
        const eventId = this.event;

        const selectedIds = this.selectedPaymentMethods
            .filter(method => method.selected && method.paymentMethod.paymentMethodId)
            .map(method => method.paymentMethod.paymentMethodId);

        this.paymentMethodService?.setPaymentMethodsForEvent(eventId, selectedIds as string[]);
    }

    handleNewEventCreatedEvent = (event: Event) => {
        const customEvent = event as CustomEvent;
        const eventId = customEvent.detail.data.event.id;

        const selectedIds = this.selectedPaymentMethods
            .filter(method => method.selected && method.paymentMethod.paymentMethodId)
            .map(method => method.paymentMethod.paymentMethodId);

        this.paymentMethodService?.setPaymentMethodsForEvent(eventId, selectedIds as string[]);
    }

    render() {
        return this._paymentMethodsTask.render({
            pending: () => html`<sl-spinner></sl-spinner>`,
            complete: (paymentMethods) => html`
                <custom-offline-payment-selector
                    .paymentMethods=${paymentMethods}
                    @selection-change=${(e: CustomEvent) => this.handleSelectionChange(e.detail)}
                ></custom-offline-payment-selector>
            `,
            error: (e) => html`
                <sl-alert variant="danger" open>
                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                    ${t('admin.payment-method.load-failed', String(e))}
                </sl-alert>
            `
        });
    }
}
