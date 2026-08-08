import {css, html, LitElement, nothing} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {fetchJson} from '../service/helpers.ts';
import type {Organization} from '../model/organization.ts';
import {pageHeader, retroCompat, panelStyles, spacing} from '../styles.ts';
import {AlfioDialogClosed, dispatchFeedback} from '../model/dom-events.ts';

@customElement('alfio-organization-list')
export class OrganizationList extends LitElement {

    @state()
    isAdmin: boolean = !!window.IS_ADMIN;

    @state()
    refreshCount: number = 0;

    private readonly loadOrganizationsTask = new Task<[number], ReadonlyArray<Organization>>(this,
        async () => {
            return await fetchJson<ReadonlyArray<Organization>>('/admin/api/organizations');
        },
        () => [this.refreshCount]
    );

    static readonly styles = [
        retroCompat,
        panelStyles,
        spacing,
        pageHeader,
        css`
            :host {
                display: block;
                font-family: "Source Sans Pro", "Helvetica Neue", Helvetica, Arial, sans-serif;
                font-size: 16px;
                line-height: 1.42857143;
                -webkit-font-smoothing: antialiased;
            }

            .container {
                width: 100%;
                max-width: 1170px;
                margin-right: auto;
                margin-left: auto;
                padding-right: 15px;
                padding-left: 15px;
            }

            h1 {
                font-family: "Source Sans Pro", "Helvetica Neue", Helvetica, Arial, sans-serif;
                font-size: 41px;
                font-weight: 700;
                line-height: 1.1;
                margin-top: 22px;
                margin-bottom: 11px;
                color: #333;
            }

            hr {
                height: 0;
                margin: 20px 0;
                border: 0;
                border-top: 1px solid #eee;
            }

            .table {
                width: 100%;
                max-width: 100%;
                margin-bottom: 20px;
                border-collapse: collapse;
            }

            .table > thead > tr > th {
                padding: 8px;
                line-height: 1.42857143;
                vertical-align: bottom;
                border-bottom: 2px solid #ddd;
                text-align: left;
                font-weight: bold;
            }

            .table > tbody > tr > td {
                padding: 8px;
                line-height: 1.42857143;
                vertical-align: top;
                border-top: 1px solid #ddd;
            }

            .table-striped > tbody > tr:nth-of-type(odd) {
                background-color: rgba(0, 0, 0, 0.05);
            }

            .sr-only {
                position: absolute;
                clip: rect(0, 0, 0, 0);
                width: 1px;
                height: 1px;
                padding: 0;
                margin: -1px;
                overflow: hidden;
                word-wrap: normal;
                border: 0;
            }

            .hidden-xs { display: none !important; }

            @media only screen and (min-width: 768px) {
                .hidden-xs { display: table-cell !important; }
            }

            .organization-description {
                max-width: 100%;
            }
         `
    ];

    render() {
        return html`
            <div class="container">
                <h1>Organizations</h1>
                <hr />
                ${when(this.isAdmin,
                    () => html`
                        <div style="text-align: right; margin-bottom: 12px;">
                            <sl-button variant="success" @click=${() => this.openNew()}>
                                <sl-icon name="plus" slot="prefix" style="font-size: larger"></sl-icon> Add new
                            </sl-button>
                        </div>
                    `,
                    () => nothing)}
            </div>
            <div class="container">
                <div>
                    <table class="table table-striped">
                        <thead>
                            <tr>
                                <th scope="col">Name</th>
                                <th scope="col" class="hidden-xs" style="width: 35%">Description</th>
                                <th scope="col" style="width: 30%">Email</th>
                                <th scope="col" style="width: 10%" class="col-edit"><span class="sr-only">Edit</span></th>
                            </tr>
                        </thead>
                        <tbody>
                            ${this.loadOrganizationsTask.render({
                                initial: () => html`
                                    <tr>
                                        <td colspan="4">
                                            <div class="loading-spinner">
                                                <sl-spinner></sl-spinner>
                                            </div>
                                        </td>
                                    </tr>
                                `,
                                error: () => html`
                                    <tr>
                                        <td colspan="4">
                                            <sl-alert variant="danger" open>
                                                <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                                                Failed to load organizations
                                            </sl-alert>
                                        </td>
                                    </tr>
                                `,
                                complete: (orgs: ReadonlyArray<Organization>) => html`
                                    ${when(orgs.length === 0,
                                        () => html`
                                            <tr>
                                                <td colspan="4">No organizations have been found. <sl-button variant="primary" size="small" @click=${() => this.openNew()}>Insert a new one</sl-button></td>
                                            </tr>
                                        `,
                                        () => html`
                                            ${repeat(orgs, (org) => org.id, (org) => html`
                                                <tr>
                                                    <td>${org.name}</td>
                                                    <td class="hidden-xs"><div class="organization-description" title=${org.description}>${org.description}</div></td>
                                                    <td>${org.email}</td>
                                                    <td>
                                                        <sl-button variant="default" @click=${() => this.edit(org)} size="small">
                                                            <sl-icon name="pencil" style="font-size: 18px;"></sl-icon><span class="sr-only">Edit</span>
                                                        </sl-button>
                                                    </td>
                                                </tr>
                                            `)}
                                        `)}
                                `
                            })}
                        </tbody>
                    </table>
                </div>
            </div>
        `;
    }

    private async openNew(): Promise<void> {
        const div = document.createElement('div');
        div.innerHTML = `<alfio-organization-edit></alfio-organization-edit>`;
        const component = div.querySelector('alfio-organization-edit')!;
        document.body.appendChild(div);
        await customElements.whenDefined('alfio-organization-edit');
        component.addEventListener('alfio-dialog-closed', async (e) => {
            await this.editDialogClosed(e as AlfioDialogClosed);
            setTimeout(() => div.remove());
        });
        await component.open({ type: 'new' });
    }

    private async edit(org: Organization): Promise<void> {
        const div = document.createElement('div');
        div.innerHTML = `<alfio-organization-edit></alfio-organization-edit>`;
        const component = div.querySelector('alfio-organization-edit')!;
        document.body.appendChild(div);
        await customElements.whenDefined('alfio-organization-edit');
        component.addEventListener('alfio-dialog-closed', async (e) => {
            await this.editDialogClosed(e as AlfioDialogClosed);
            setTimeout(() => div.remove());
        });
        await component.open({ type: 'edit', organizationId: org.id });
    }

    private async editDialogClosed(e: AlfioDialogClosed) {
        if (e.detail.success) {
            this.refresh();
            dispatchFeedback({
                type: 'success',
                message: 'Operation completed successfully'
            }, this);
        }
    }

    public refresh(): void {
        this.refreshCount++;
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-organization-list': OrganizationList;
    }
}
