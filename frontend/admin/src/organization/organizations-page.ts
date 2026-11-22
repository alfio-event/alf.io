import { html, LitElement, PropertyValues, TemplateResult } from 'lit';
import { customElement, property, query } from 'lit/decorators.js';
import { navigate } from '../admin-router';
import { OrganizationEdit } from './organization-edit';
import { OrganizationList } from './organization-list';

const MAX_ID = 2147483647;

/**
 * The organizations page: the list, and the edit dialog for the routes /organizations/new and
 * /organizations/{id}/edit. The list opens its own dialogs when the user clicks "New" or "Edit".
 */
@customElement('alfio-organizations-page')
export class OrganizationsPage extends LitElement {

    @property({ type: String, attribute: 'data-mode' }) mode: 'list' | 'new' | 'edit' = 'list';
    @property({ type: String, attribute: 'data-organization-id' }) organizationId = '';

    @query('alfio-organization-list') private list?: OrganizationList;
    @query('alfio-organization-edit') private editDialog?: OrganizationEdit;

    // light DOM, like the AngularJS page: the list and the dialog stay reachable from the document
    protected createRenderRoot(): HTMLElement {
        return this;
    }

    protected render(): TemplateResult {
        return html`
            <alfio-organization-list></alfio-organization-list>
            <alfio-organization-edit @alfio-dialog-closed=${this.onDialogClosed}></alfio-organization-edit>
        `;
    }

    protected updated(changed: PropertyValues<this>): void {
        if (changed.has('mode') || changed.has('organizationId')) {
            void this.syncDialog();
        }
    }

    private async syncDialog(): Promise<void> {
        await customElements.whenDefined('alfio-organization-edit');
        const dialog = this.editDialog;
        if (dialog == null) {
            return;
        }
        if (this.mode === 'new') {
            await dialog.open({ type: 'new' });
        } else if (this.mode === 'edit') {
            const id = Number(this.organizationId);
            if (!/^\d+$/.test(this.organizationId) || id < 1 || id > MAX_ID) {
                navigate('/organizations/', { replace: true });
                return;
            }
            await dialog.open({ type: 'edit', organizationId: id });
        } else {
            await dialog.hide();
        }
    }

    private onDialogClosed = (e: CustomEvent<{ success: boolean }>): void => {
        if (e.detail.success) {
            this.list?.refresh();
        }
        if (this.mode !== 'list') {
            navigate('/organizations/');
        }
    };
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-organizations-page': OrganizationsPage;
    }
}
