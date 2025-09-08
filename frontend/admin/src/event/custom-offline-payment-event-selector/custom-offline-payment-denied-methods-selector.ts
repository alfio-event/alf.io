import { CustomPaymentMethodsService } from "../../service/custom-payment-methods";
import { customElement, property, state } from "lit/decorators.js";
import { html, LitElement } from "lit";
import { Task } from '@lit/task';

@customElement('custom-offline-payment-denied-methods-selector')
export class CustomOfflinePaymentDeniedMethodsSelector extends LitElement {
    @property({ attribute: "organization", type: Number })
    organization: number = -1;
    @property({ attribute: "event", type: Number })
    event: number = -1;
    @property({ attribute: "category", type: Number })
    category: number = -1;

    @state()
    selectedPaymentMethods: Array<{ paymentMethod: CustomOfflinePayment; selected: boolean }> = [];

    paymentMethodService?: CustomPaymentMethodsService = new CustomPaymentMethodsService();

    private _paymentMethodsTask = new Task(this, {
        task: async ([organization]) => {
            if (organization <= 0) return [];

            const orgPaymentMethods = await this.paymentMethodService?.getPaymentMethodsForOrganization(organization);
            const deniedPaymentMethods = await this.paymentMethodService?.getDeniedPaymentMethodsForCategory(
                this.event,
                this.category
            );

            return orgPaymentMethods?.map(item => ({
                paymentMethod: item,
                selected: deniedPaymentMethods?.includes(item.paymentMethodId!)
            })) || [];
        },
        args: () => [this.organization]
    });

    connectedCallback(): void {
        super.connectedCallback();
        window.addEventListener("update-payment-method-category-denied-list", this.handleUpdateDeniedListSubmit);
    }

    disconnectedCallback(): void {
        window.removeEventListener("update-payment-method-category-denied-list", this.handleUpdateDeniedListSubmit);
        super.disconnectedCallback();
    }

    handleSelectionChange = (selectedMethods: Array<{ paymentMethod: CustomOfflinePayment; selected: boolean }>) => {
        this.selectedPaymentMethods = selectedMethods;
    }

    handleUpdateDeniedListSubmit = async (_event: Event) => {
        const deniedPaymentMethodIds: string[] = this.selectedPaymentMethods
            .filter(pm => pm.selected)
            .map(pm => pm.paymentMethod.paymentMethodId)
            .filter((pmid): pmid is string => pmid !== null);

        if(!deniedPaymentMethodIds)
            return;

        await this.paymentMethodService?.setDeniedPaymentMethodsForCategory(
            this.event,
            this.category,
            deniedPaymentMethodIds
        );
    }

    render() {
        return this._paymentMethodsTask.render({
            pending: () => html`<p>Loading payment methods...</p>`,
            complete: (paymentMethods) => html`
                <custom-offline-payment-selector
                    .paymentMethods=${paymentMethods}
                    .strikeThroughSelected=${true}
                    @selection-change=${(e: CustomEvent) => this.handleSelectionChange(e.detail)}
                ></custom-offline-payment-selector>
            `,
            error: (e) => html`<p>Error: ${e}</p>`
        });
    }
}
