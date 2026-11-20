import {css, html, LitElement, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {Task} from '@lit/task';
import {CustomPaymentMethodsService} from '../service/custom-payment-methods.ts';
import {LocalizationService} from '../service/localization.ts';
import {ConfirmationDialogService} from '../service/confirmation-dialog.ts';
import {responseErrorMessage} from '../service/helpers.ts';
import {dispatchFeedback} from '../model/dom-events.ts';
import {ContentLanguage} from '../model/purchase-context.ts';
import {emptyState} from '../components/empty-state.ts';
import {taskContent} from '../components/task-content.ts';
import {badges, base, modernLayout, modernTable, textColors} from '../styles.ts';
import type {OfflinePaymentDialog, OfflinePaymentSaveDetail} from './offline-payment-dialog.ts';
import './offline-payment-dialog.ts';

interface PaymentMethodsData {
    methods: CustomOfflinePayment[];
    languages: ContentLanguage[];
}

/**
 * The custom offline payment methods of an organization, with Edit and Delete. The host card offers
 * "Add payment method", which calls add().
 */
@customElement('offline-payment-config-block')
export class OfflinePaymentConfigBlock extends LitElement {
    @property({attribute: 'organization', type: Number}) organization = -1;

    @state() private lastData: PaymentMethodsData | null = null;
    @state() private deleting: string | null = null;
    @state() private refreshCount = 0;
    @query('offline-payment-dialog') private dialog!: OfflinePaymentDialog;

    private readonly service = new CustomPaymentMethodsService();
    private readonly localizationService = new LocalizationService();

    private readonly loadTask = new Task(this, {
        task: async ([organization]): Promise<PaymentMethodsData> => {
            if (organization <= 0) {
                return {methods: [], languages: []};
            }
            const [methods, languages] = await Promise.all([
                this.service.getPaymentMethodsForOrganization(organization),
                this.localizationService.getEventsSupportedLanguages(),
            ]);
            return {methods, languages};
        },
        args: () => [this.organization, this.refreshCount] as const,
        onComplete: (data) => {
            this.lastData = data;
        },
    });

    static readonly styles = [
        base,
        textColors,
        badges,
        modernTable,
        modernLayout,
        css`
            :host {
                display: block;
            }
            .payment-method-name {
                font-weight: var(--sl-font-weight-semibold);
                overflow-wrap: anywhere;
            }
            .payment-method-languages {
                display: flex;
                flex-wrap: wrap;
                gap: var(--sl-spacing-3x-small);
            }
            .actions-cell {
                white-space: nowrap;
                text-align: right;
            }
            .actions-cell sl-button + sl-button {
                margin-inline-start: var(--sl-spacing-2x-small);
            }
            @media (max-width: 700px) {
                .actions-cell {
                    white-space: normal;
                }
                .actions-cell sl-button {
                    margin: var(--sl-spacing-3x-small) 0;
                }
            }
        `,
    ];

    /**
     * Opens the dialog that creates a new payment method
     */
    add(): void {
        this.edit(null);
    }

    render(): TemplateResult {
        return html`
            ${taskContent(this.loadTask, this.lastData, 'Failed to load the payment methods. Please try again.',
                (data) => this.renderList(data))}
            <offline-payment-dialog @alfio-offline-payment-save=${this.onSave}></offline-payment-dialog>
        `;
    }

    private renderList(data: PaymentMethodsData): TemplateResult {
        if (data.methods.length === 0) {
            return emptyState('No payment methods yet.');
        }
        return html`
            <div class="table-responsive">
                <table class="table">
                    <thead>
                        <tr>
                            <th>Name</th>
                            <th>Languages</th>
                            <th><span class="sr-only">Actions</span></th>
                        </tr>
                    </thead>
                    <tbody>
                        ${repeat(data.methods, (method) => method.paymentMethodId ?? '', (method) => this.renderRow(data, method))}
                    </tbody>
                </table>
            </div>
        `;
    }

    private renderRow(data: PaymentMethodsData, method: CustomOfflinePayment): TemplateResult {
        const locales = Object.keys(method.localizations);
        const id = method.paymentMethodId ?? '';
        return html`
            <tr class="payment-method" data-id=${id}>
                <td class="payment-method-name">${displayName(method)}</td>
                <td>
                    <span class="payment-method-languages">
                        ${repeat(locales, (locale) => locale, (locale) => html`
                            <sl-badge variant="neutral" pill>${languageName(data.languages, locale)}</sl-badge>
                        `)}
                    </span>
                </td>
                <td class="actions-cell">
                    <sl-button class="edit-payment-method" variant="default" size="small" @click=${() => this.edit(method)}>
                        <sl-icon slot="prefix" name="pencil"></sl-icon>
                        Edit
                    </sl-button>
                    <sl-button class="delete-payment-method" variant="danger" size="small" outline
                               ?loading=${this.deleting === id} ?disabled=${this.deleting != null}
                               @click=${() => void this.delete(method)}>
                        <sl-icon slot="prefix" name="trash"></sl-icon>
                        Delete
                    </sl-button>
                </td>
            </tr>
        `;
    }

    private edit(method: CustomOfflinePayment | null): void {
        const data = this.lastData;
        if (data == null) {
            return;
        }
        const others = data.methods.filter((other) => method == null || other.paymentMethodId !== method.paymentMethodId);
        void this.dialog.show(method, others, data.languages);
    }

    private onSave = async (event: CustomEvent<OfflinePaymentSaveDetail>): Promise<void> => {
        const {paymentMethodId, payment} = event.detail;
        let request: Promise<Response>;
        let message = 'The payment method has been created';
        if (paymentMethodId != null) {
            request = this.service.updatePaymentMethod(this.organization, paymentMethodId, payment);
            message = 'The payment method has been saved';
        } else {
            request = this.service.createPaymentMethod(this.organization, payment);
        }
        try {
            const response = await request;
            if (!response.ok) {
                this.dialog.showError(await responseErrorMessage(response, 'Failed to save the payment method. Please try again.'));
                return;
            }
            this.dialog.close();
            dispatchFeedback({type: 'success', message}, this);
            this.refreshCount++;
        } catch {
            this.dialog.showError('Failed to save the payment method. Please try again.');
        }
    };

    private async delete(method: CustomOfflinePayment): Promise<void> {
        const id = method.paymentMethodId;
        if (id == null) {
            return;
        }
        const confirmed = await ConfirmationDialogService.requestConfirm('Delete payment method',
            `"${displayName(method)}" will no longer be offered to the buyers.`, 'danger');
        if (!confirmed) {
            return;
        }
        this.deleting = id;
        try {
            const response = await this.service.deletePaymentMethod(this.organization, id);
            if (!response.ok) {
                dispatchFeedback({type: 'danger', message: await responseErrorMessage(response, 'Failed to delete the payment method')}, this);
                return;
            }
            dispatchFeedback({type: 'success', message: 'The payment method has been deleted'}, this);
            this.refreshCount++;
        } catch {
            dispatchFeedback({type: 'danger', message: 'Failed to delete the payment method'}, this);
        } finally {
            this.deleting = null;
        }
    }
}

// the English name, or the first one
function displayName(method: CustomOfflinePayment): string {
    const localizations = method.localizations ?? {};
    const localization = localizations['en'] ?? Object.values(localizations)[0];
    return localization?.paymentName ?? '';
}

function languageName(languages: ContentLanguage[], locale: string): string {
    return languages.find((language) => language.locale === locale)?.displayLanguage ?? locale;
}

declare global {
    interface HTMLElementTagNameMap {
        'offline-payment-config-block': OfflinePaymentConfigBlock;
    }
}
