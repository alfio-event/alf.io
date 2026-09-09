import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task, TaskStatus} from '@lit/task';
import {UsersService} from './users-service.ts';
import {ConfirmationDialogService} from '../service/confirmation-dialog.ts';
import {Role, User, UserModification, UserType} from '../model/user.ts';
import {Organization} from '../model/organization.ts';
import {
    badges,
    base,
    dialog,
    form,
    modernLayout,
    modernTable,
    pageHeader,
    panelStyles,
    retroCompat,
    row,
    spacing,
    textColors
} from '../styles.ts';
import {dispatchFeedback} from '../model/dom-events.ts';
import {ErrorDescriptor} from '../model/validation.ts';
import Papa from 'papaparse';
import type {SlDialog} from '@shoelace-style/shoelace';
import {UtilService} from "../service/util.ts";
import {FileUploadChangeEvent} from "../components/file-upload.ts";

interface LoadResult {
    users: User[];
    roles: Role[];
    organizations: Organization[];
    isAdmin: boolean;
}

interface EditableUser extends UserModification {
    target?: string;
}

@customElement('alfio-users')
export class Users extends LitElement {
    @property({attribute: 'alfio-title'}) alfioTitle: string = 'Users';
    @property() type: UserType = 'user';

    @state() isAdmin: boolean = window.IS_ADMIN;
    @state() users: User[] = [];
    @state() roles: Role[] = [];
    @state() organizations: Organization[] = [];
    @state() selectedOrganization: number | null = null;
    @state() selectedRole: string | null = null;
    @state() systemApiKey: string | null = null;

    @state() qrCodeUser: User | null = null;
    @state() userDataReset: User | null = null;

    @query('sl-dialog#api-key-qr') apiQrDialog!: SlDialog;
    @query('sl-dialog#confirm-rotation') confirmRotationDialog!: SlDialog;
    @query('sl-dialog#show-user-data') showUserDataDialog!: SlDialog;
    @query('sl-dialog#user-edit') userEditDialog!: SlDialog;
    @query('sl-dialog#bulk-import') bulkImportDialog!: SlDialog;

    @state() private editingUser: EditableUser | null = null;
    @state() private validationErrors: Record<string, string> = {};

    @state() private bulkOrgId: number | null = null;
    @state() private bulkRole: string = '';
    @state() private bulkDescriptions: string[] = [];
    private bulkCsvInput: HTMLInputElement | null = null;

    @state() private refreshCount: number = 0;

    private readonly loadDataTask = new Task<[number], LoadResult>(this,
        async () => {
            const [allUsers, roles, currentUser, apiOrganizations] = await Promise.all([
                UsersService.loadAll(),
                UsersService.loadRoles(),
                UsersService.loadCurrentUser(),
                UsersService.loadOrganizations(),
            ]);

            const filteredUsers = allUsers.filter(user => (this.type === 'user') !== (user.type === 'API_KEY'));

            const sortedUsers = [...filteredUsers].sort((a, b) => {
                if (a.enabled !== b.enabled) {
                    return a.enabled ? -1 : 1;
                }
                return a.username.localeCompare(b.username);
            });

            const orgMap = new Map<number, Organization>();
            for (const org of apiOrganizations) {
                orgMap.set(org.id, org);
            }
            for (const user of allUsers) {
                for (const orgRef of user.memberOf) {
                    if (!orgMap.has(orgRef.id)) {
                        orgMap.set(orgRef.id, {
                            id: orgRef.id,
                            name: orgRef.name,
                            email: '',
                            description: '',
                            externalId: null,
                            slug: null,
                        });
                    }
                }
            }
            const organizations = Array.from(orgMap.values());

            this.users = sortedUsers;
            this.roles = roles;
            this.organizations = organizations;
            this.isAdmin = this.isAdmin || currentUser.role === 'ADMIN';

            return {
                users: sortedUsers,
                roles,
                organizations,
                isAdmin: this.isAdmin,
            };
        },
        () => [this.refreshCount]
    );

    static readonly styles = [
        base,
        retroCompat,
        panelStyles,
        spacing,
        pageHeader,
        form,
        dialog,
        row,
        badges,
        textColors,
        modernTable,
        modernLayout,
        css`
            .container {
                width: 100%;
                max-width: 1170px;
                margin-right: auto;
                margin-left: auto;
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

            .hidden { display: none !important; }
            .hidden-xs { display: none !important; }

            @media only screen and (min-width: 768px) {
                .hidden-xs { display: table-cell !important; }
            }

            .loading-spinner {
                display: flex;
                justify-content: center;
                padding: 2rem;
            }

            .ml-1 { margin-left: var(--sl-spacing-2x-small); }

            .form-group {
                margin-bottom: 16px;
                margin-top: 16px;
            }

            .form-group label {
                display: block;
                font-weight: bold;
                margin-bottom: 5px;
            }

            .api-key-cell {
                display: inline-flex;
                align-items: center;
                gap: var(--sl-spacing-2x-small);
                font-family: var(--sl-font-mono) monospace;
                font-size: var(--sl-font-size-small);
                color: var(--sl-color-gray-600);
            }

            .bulk-specs {
                background: var(--sl-color-gray-50);
                border: 1px solid var(--sl-color-gray-200);
                border-radius: var(--sl-input-border-radius);
                padding: var(--sl-spacing-medium);
            }

            .bulk-specs pre {
                background: var(--sl-color-neutral-5);
                border: 1px solid var(--sl-color-gray-300);
                border-radius: var(--sl-input-border-radius);
                padding: var(--sl-spacing-small) var(--sl-spacing-medium);
                font-size: var(--sl-font-size-small);
                margin-top: var(--sl-spacing-small);
                margin-bottom: 0;
            }
        `
    ];

render() {
        const data = this.loadDataTask.value;
        const enabledUsers = data?.users.filter(u => u.enabled) ?? [];
        const disabledUsers = data?.users.filter(u => !u.enabled) ?? [];
        const orgs = data?.organizations ?? [];
        const targetType = this.type === 'user' ? 'USER' : 'API_KEY';
        const availableRoles = new Set<string>();
        for (const user of (data?.users ?? [])) {
            for (const role of user.roles) {
                availableRoles.add(role);
            }
        }
        if (this.selectedRole !== null && !availableRoles.has(this.selectedRole)) {
            this.selectedRole = null;
        }

        return html`
            <div class="container">
                ${when(this.isAdmin && this.type === 'apikey',
                    () => html`
                        <div class="first-element featured-card">
                            <h1><sl-icon name="key-fill"></sl-icon> System API Key</h1>
                            <small class="text-muted">Used by external applications to create/modify/delete organizations and to create API Keys.</small>
                            ${when(this.systemApiKey,
                                () => html`
                                    <div class="secret-display">${this.systemApiKey}</div>
                                    <div class="api-key-actions">
                                        <sl-button variant="default" size="small" @click=${() => UtilService.copyValueToClipboard(() => this.systemApiKey!, 'System API Key', this)}>
                                            <sl-icon slot="prefix" name="clipboard"></sl-icon> Copy
                                        </sl-button>
                                        <sl-button variant="warning" size="small" @click=${() => this.openConfirmRotation()}>
                                            <sl-icon slot="prefix" name="arrow-clockwise"></sl-icon> Rotate
                                        </sl-button>
                                    </div>
                                `,
                                () => html`
                                    <div class="api-key-actions">
                                        <sl-button variant="success" size="small" @click=${() => this.revealSystemApiKey()}>
                                            <sl-icon slot="prefix" name="eye"></sl-icon> Reveal System API Key
                                        </sl-button>
                                    </div>
                                `)}
                        </div>
                    `,
                    () => nothing)}

                <div class="page-title-row">
                    <h1>${this.alfioTitle}</h1>
                </div>
                <hr class="page-separator">

                ${when(this.loadDataTask.status === TaskStatus.COMPLETE && (orgs.length > 1 || availableRoles.size > 0),
                    () => html`
                        <div class="filter-toolbar">
                            <div class="filter-left">
                                ${when(orgs.length > 1, () => html`
                                    <sl-select clearable class="filter-select" label="Show members of" size="medium"
                                        .value=${this.selectedOrganization !== null ? String(this.selectedOrganization) : ''}
                                        @sl-change=${(e: Event) => {
                                        const val = (e.target as any).value;
                                        this.selectedOrganization = val ? Number(val) : null;
                                    }}>
                                        <sl-option value="">All</sl-option>
                                        ${repeat(orgs, (org) => org.id, (org) => html`
                                            <sl-option value=${org.id}>${org.name}</sl-option>
                                        `)}
                                    </sl-select>
                                `, () => nothing)}
                                <sl-select class="filter-select" clearable label="Filter by role"
                                           .value=${this.selectedRole ?? ''}
                                           @sl-change=${(e: Event) => {
                                                this.selectedRole = (e.target as any).value;
                                            }}>
                                    <sl-option value="">All roles</sl-option>
                                    ${repeat(this.roles.filter(r => (r.target || []).includes(targetType) && availableRoles.has(r.role)), (r) => r.role, (r) => html`
                                    <sl-option value=${r.role}>${r.description}</sl-option>
                                `)}
                                </sl-select>
                            </div>
                            <div class="filter-right">
                                ${when(this.type === 'user',
                                    () => html`<sl-button variant="success" @click=${() => this.openNewUser()} size="large"><sl-icon slot="prefix" name="plus-circle"></sl-icon> Add new</sl-button>`,
                                    () => html`<sl-button variant="success" @click=${() => this.openNewApiKey()} size="large"><sl-icon slot="prefix" name="plus-circle"></sl-icon> Add new</sl-button>`)}
                                ${when(this.type === 'apikey',
                                    () => html`<sl-button variant="primary" @click=${() => this.openBulkImport()} size="large"><sl-icon slot="prefix" name="file-earmark-bar-graph"></sl-icon> Bulk creation</sl-button>`,
                                    () => nothing)}
                                ${when(this.type === 'apikey' && orgs.length === 1,
                                    () => html`<sl-button variant="default" @click=${() => this.downloadApiKeys(orgs[0].id)} size="small"><sl-icon slot="prefix" name="download"></sl-icon> Download all</sl-button>`,
                                    () => nothing)}

                                ${when(this.type === 'apikey' && orgs.length > 1,
                                    () => html`
                                        <sl-dropdown >
                                            <sl-button slot="trigger" variant="default" caret size="large"><sl-icon slot="prefix" name="download"></sl-icon> Download all</sl-button>
                                            <sl-menu>
                                                ${repeat(orgs, (org) => org.id, (org) => html`
                                                    <sl-menu-item @click=${() => this.downloadApiKeys(org.id)}>${org.name}</sl-menu-item>
                                                `)}
                                            </sl-menu>
                                        </sl-dropdown>
                                    `,
                                    () => nothing)}
                                </div>
                            </div>
                        </div>
                    `,
                    () => html`
                        <div class="filter-toolbar" style="justify-content: flex-end;">
                            <div class="filter-right">
                                ${when(this.type === 'user',
                                    () => html`<sl-button variant="success" @click=${() => this.openNewUser()} size="large"><sl-icon slot="prefix" name="plus-circle"></sl-icon> Add new</sl-button>`,
                                    () => html`<sl-button variant="success" @click=${() => this.openNewApiKey()} size="large"><sl-icon slot="prefix" name="plus-circle"></sl-icon> Add new</sl-button>`)}
                                ${when(this.type === 'apikey',
                                    () => html`<sl-button variant="primary" @click=${() => this.openBulkImport()} size="small"><sl-icon slot="prefix" name="file-earmark-bar-graph"></sl-icon> Bulk creation</sl-button>`,
                                    () => nothing)}
                                ${when(this.type === 'apikey' && orgs.length === 1,
                                    () => html`<sl-button variant="default" @click=${() => this.downloadApiKeys(orgs[0].id)} size="small"><sl-icon slot="prefix" name="download"></sl-icon> Download all</sl-button>`,
                                    () => nothing)}
                                ${when(this.type === 'apikey' && orgs.length > 1,
                                    () => html`
                                        <sl-dropdown sync="width">
                                            <sl-button slot="trigger" variant="default" caret size="small"><sl-icon slot="prefix" name="download"></sl-icon> Download all</sl-button>
                                            <sl-menu>
                                                ${repeat(orgs, (org) => org.id, (org) => html`
                                                    <sl-menu-item @click=${() => this.downloadApiKeys(org.id)}>${org.name}</sl-menu-item>
                                                `)}
                                            </sl-menu>
                                        </sl-dropdown>
                                    `,
                                    () => nothing)}
                            </div>
                        </div>
                    `)}

                ${this.loadDataTask.render({
                    initial: () => html`
                        <div class="loading-spinner">
                            <sl-spinner></sl-spinner>
                        </div>
                    `,
                    error: () => html`
                        <sl-alert variant="danger" open>
                            <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                            Failed to load ${this.title.toLowerCase()}
                        </sl-alert>
                    `,
                    complete: () => html`
                        <div class="section-card">
                            <div class="section-header">
                                <sl-icon name="check-circle" style="color: var(--sl-color-success-600)"></sl-icon>
                                <h3>Active</h3>
                                <sl-badge variant="success" pill>${this.countFilteredUsers(enabledUsers)}</sl-badge>
                            </div>
                            <div class="section-body">
                                <div class="table-responsive">
                                    ${this.renderUsersTable(enabledUsers, true)}
                                </div>
                            </div>
                        </div>
                    `
                })}

                <slot name="edit-user"></slot>

                ${this.loadDataTask.render({
                    initial: () => nothing,
                    error: () => nothing,
                    complete: () => html`
                        <div class="section-card">
                            <div class="section-header">
                                <sl-icon name="eye-slash" style="color: var(--sl-color-gray-500)"></sl-icon>
                                <h3>Inactive</h3>
                                <sl-badge variant="neutral" pill>${this.countFilteredUsers(disabledUsers)}</sl-badge>
                            </div>
                            <div class="section-body">
                                <div class="table-responsive">
                                    ${this.renderUsersTable(disabledUsers, false)}
                                </div>
                            </div>
                        </div>
                    `
                })}
             </div>

            ${this.renderUserEditDialog()}
            ${this.renderBulkImportDialog()}

            <sl-dialog id="api-key-qr" label=${this.qrCodeUser?.description ?? ''}>
                <div class="text-center">
                    <sl-qr-code error-correction="Q" size="200" value=${JSON.stringify({apiKey: this.qrCodeUser?.username, baseUrl: window.location.origin})}></sl-qr-code>
                </div>
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => this.apiQrDialog.hide()}>Close</sl-button>
                        <div></div>
                    </div>
                </div>
            </sl-dialog>

            <sl-dialog id="confirm-rotation" label="Confirm rotation">
                <div class="confirm-dialog-body">
                    <p>Regenerating the System API Key will invalidate the current key immediately.</p>
                    <p>All external applications must use the new API Key.</p>
                    <p>This operation <strong>cannot be undone</strong>.</p>
                </div>
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="warning" size="large" @click=${() => this.confirmRotateSystemApiKey()}>Confirm</sl-button>
                        <div></div>
                        <sl-button variant="default" size="large" @click=${() => this.confirmRotationDialog.hide()}>Cancel</sl-button>
                    </div>
                </div>
            </sl-dialog>

            <sl-dialog id="show-user-data" label="User credentials">
                <div class="credentials-card">
                    <div class="cred-row">
                        <span class="cred-label">Username</span>
                        <span class="cred-value">${this.userDataReset?.username ?? ''}</span>
                    </div>
                    <div class="cred-row">
                        <span class="cred-label">Password</span>
                        <span class="cred-value">
                            <sl-tooltip content="Copy Password">
                                <sl-button variant="text" size="small" @click=${() => UtilService.copyValueToClipboard(() => this.userDataReset!.password!, 'User Password', this)}><sl-icon name="clipboard"></sl-icon></sl-button>
                            </sl-tooltip>
                            ${this.userDataReset?.password ?? ''}
                        </span>
                    </div>
                </div>
                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => this.showUserDataDialog.hide()}>Close</sl-button>
                        <div></div>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    private renderUserEditDialog(): TemplateResult | typeof nothing {
        const user = this.editingUser;
        const target = user?.target ?? 'USER';
        const availableRoles = this.roles.filter(r => (r.target || []).includes(target));
        const dialogLabel = user?.id != null
            ? (target === 'API_KEY' ? 'Edit API Key' : 'Edit User')
            : this.dialogLabelForTarget(target);

        if (!user) return nothing;

        const errorFor = (field: string) =>
            this.validationErrors[field] ? html`<sl-alert variant="danger" open class="mt-2">${this.validationErrors[field]}</sl-alert>` : nothing;

        return html`
            <sl-dialog id="user-edit" label=${dialogLabel} size="large" placement="bottom">
                <div class="row" style="--alfio-row-cols: 2">
                    <div class="form-group">
                        <sl-select id="edit-org" label="Organization" required
                            .value=${String(user.organizationId)}
                            @sl-change=${(e: Event) => {
                                const val = (e.target as any).value;
                                user.organizationId = val ? Number(val) : 0;
                            }}>
                            <sl-option value="" disabled>Select an organization</sl-option>
                            ${repeat(this.organizations, (org) => org.id, (org) => html`
                                <sl-option value=${org.id}>${org.name}</sl-option>
                            `)}
                        </sl-select>
                        ${errorFor('organizationId')}
                    </div>
                    <div class="form-group">
                        <sl-select id="edit-role" label="Role" required
                            .value=${user.role}
                            @sl-change=${(e: Event) => {
                                user.role = (e.target as any).value;
                            }}>
                            <sl-option value="" disabled>Select a role</sl-option>
                            ${repeat(availableRoles, (r) => r.role, (r) => html`
                                <sl-option value=${r.role}>${r.description}</sl-option>
                            `)}
                        </sl-select>
                        ${errorFor('role')}
                    </div>
                </div>

                ${when(target === 'USER',
                    () => html`
                        <div class="form-group">
                            <sl-input id="edit-username" label="Username" required minlength="2"
                                .value=${user.username ?? ''}
                                @sl-input=${(e: InputEvent) => user.username = (e.target as any).value}>
                                <sl-icon name="person" slot="prefix"></sl-icon>
                            </sl-input>
                            ${errorFor('username')}
                        </div>
                        <div class="row" style="--alfio-row-cols: 2">
                            <div class="form-group">
                                <sl-input id="edit-firstName" label="First name" required minlength="2"
                                    .value=${user.firstName ?? ''}
                                    @sl-input=${(e: InputEvent) => user.firstName = (e.target as any).value}>
                                </sl-input>
                                ${errorFor('firstName')}
                            </div>
                            <div class="form-group">
                                <sl-input id="edit-lastName" label="Last name" required minlength="2"
                                    .value=${user.lastName ?? ''}
                                    @sl-input=${(e: InputEvent) => user.lastName = (e.target as any).value}>
                                </sl-input>
                                ${errorFor('lastName')}
                            </div>
                        </div>
                        <div class="form-group">
                            <sl-input id="edit-emailAddress" label="E-mail" type="email" required
                                .value=${user.emailAddress ?? ''}
                                @sl-input=${(e: InputEvent) => user.emailAddress = (e.target as any).value}>
                                <sl-icon name="envelope" slot="prefix"></sl-icon>
                            </sl-input>
                            ${errorFor('emailAddress')}
                        </div>
                    `,
                    () => html`
                        <div class="form-group">
                            <sl-input id="edit-description" label="Description" maxlength="128"
                                .value=${user.description ?? ''}
                                @sl-input=${(e: InputEvent) => user.description = (e.target as any).value}>
                                <sl-icon name="card-text" slot="prefix"></sl-icon>
                            </sl-input>
                            ${errorFor('description')}
                        </div>
                    `)}

                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => this.cancelEdit()}>Cancel</sl-button>
                        <div></div>
                        <sl-button variant="warning" size="large" @click=${() => this.saveUser()}>
                            <sl-icon slot="prefix" name="check2"></sl-icon> Save
                        </sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    private renderBulkImportDialog(): TemplateResult {
        const availableRoles = this.roles.filter(r => (r.target || []).includes('API_KEY'));

        return html`
            <sl-dialog id="bulk-import" label="Bulk create API Keys" style="--width: 80vw" placement="bottom">
                <div class="form-group">
                    <sl-select id="bulk-org" label="Organization" required
                        .value=${this.bulkOrgId != null ? String(this.bulkOrgId) : ''}
                        @sl-change=${(e: Event) => {
                            const val = (e.target as any).value;
                            this.bulkOrgId = val ? Number(val) : null;
                        }}>
                        <sl-option value="" disabled>Select an organization</sl-option>
                        ${repeat(this.organizations, (org) => org.id, (org) => html`
                            <sl-option value=${org.id}>${org.name}</sl-option>
                        `)}
                    </sl-select>
                </div>

                <div class="form-group">
                    <sl-select id="bulk-role" label="Role" required
                        .value=${this.bulkRole}
                        @sl-change=${(e: Event) => {
                            this.bulkRole = (e.target as any).value;
                        }}>
                        <sl-option value="" disabled>Select a role</sl-option>
                        ${repeat(availableRoles, (r) => r.role, (r) => html`
                            <sl-option value=${r.role}>${r.description}</sl-option>
                        `)}
                    </sl-select>
                </div>

                <div class="form-group">
                    <div class="bulk-specs">
                        <h4 style="margin-top: 0; margin-bottom: var(--sl-spacing-small);">File Specifications</h4>
                        <p class="text-muted" style="margin: 0 0 var(--sl-spacing-small);">
                            Create a CSV file <strong>without header</strong>, using commas (<strong>,</strong>) as separator and double quotes (<strong>"</strong>) as quote character.
                        </p>
                        <strong>Row specification:</strong>
                        <pre><span class="text-success">name</span>

where:

<span class="text-success">name</span> is the client name, displayed in the QR-Code and statistics</pre>
                    </div>
                    <alfio-file-upload class="mt-3" @change=${(e: FileUploadChangeEvent) => this.handleFileSelect(e)}></alfio-file-upload>
                </div>

                ${when(this.bulkDescriptions.length > 0, () => html`
                    <div class="form-group">
                        <table class="table table-striped">
                            <thead>
                                <tr><th colspan="2">Parsed entries</th></tr>
                            </thead>
                            <tbody>
                                ${repeat(this.bulkDescriptions, (_, i) => i, (desc) => html`<tr><td colspan="2">${desc}</td></tr>`)}
                            </tbody>
                            <tfoot>
                                <tr><th>Total</th><th><sl-badge variant="primary" pill>${this.bulkDescriptions.length}</sl-badge></th></tr>
                            </tfoot>
                        </table>
                    </div>
                `)}

                ${when(this.bulkDescriptions.length > 0, () => html`
                    <sl-alert variant="primary" open>
                        <sl-icon slot="icon" name="info-circle"></sl-icon>
                        <strong>${this.bulkDescriptions.length}</strong> API key${this.bulkDescriptions.length !== 1 ? 's' : ''} will be created.
                    </sl-alert>
                `)}

                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => this.cancelBulkImport()}>Cancel</sl-button>
                        <div></div>
                        <sl-button
                            variant="success"
                            size="large"
                            .disabled=${this.bulkDescriptions.length === 0}
                            @click=${() => this.saveBulkImport()}>
                            <sl-icon slot="prefix" name="check2"></sl-icon> Create
                        </sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

private countFilteredUsers(users: User[]): number {
        return users
            .filter(u => this.selectedOrganization !== null
                ? u.memberOf.some(org => org.id === this.selectedOrganization)
                : true)
            .filter(u => this.selectedRole !== null
                ? u.roles.includes(this.selectedRole)
                : true)
            .length;
    }

    private renderUsersTable(users: User[], _enabled: boolean): TemplateResult {
        const filtered = users
            .filter(u => this.selectedOrganization !== null
                ? u.memberOf.some(org => org.id === this.selectedOrganization)
                : true)
            .filter(u => this.selectedRole !== null
                ? u.roles.includes(this.selectedRole)
                : true);

        if (filtered.length === 0) {
            return html`
                <div class="empty-state">
                    <sl-icon name="inbox"></sl-icon>
                    <span>No ${this.type === 'user' ? 'users' : 'API keys'} found</span>
                </div>
            `;
        }

        return html`
            <table class="table table-striped">
                <thead>
                    <tr>
                        <th style="width: 12%">
                            <span class=${this.type === 'user' ? '' : 'hidden'}>Username</span>
                            <span class=${this.type === 'user' ? 'hidden' : ''}>API Key</span>
                        </th>
                        <th style="width: 33%">${this.type === 'apikey' ? 'Description' : 'Name'}</th>
                        <th style="width: 10%">Role</th>
                        <th style="width: 15%">Organization</th>
                        <th style="width: 30%"><span class="sr-only">Actions</span></th>
                    </tr>
                </thead>
                <tbody>
                    ${repeat(filtered, (user) => user.id, (user) => html`
                        <tr>
                            <td>
                                ${when(this.type === 'user',
                                    () => html`<a href="#" class="username-link" @click=${(e: Event) => this.onUsernameClick(e, user)}>${user.username}</a>`,
                                    () => html`
                                        <span class="api-key-cell">
                                            <sl-icon name="key"></sl-icon>
                                            <i>${user.username.substring(0, 8)}…</i>
                                            <sl-tooltip content="Copy API Key">
                                                <sl-button variant="text" size="small" @click=${() => UtilService.copyValueToClipboard(() => user.username, 'API Key', this)}><sl-icon name="clipboard"></sl-icon></sl-button>
                                            </sl-tooltip>
                                        </span>
                                    `)}
                            </td>
                            <td>${when(this.type === 'user', () => html`${user.firstName} ${user.lastName}`, () => html`${user.description}`)}</td>
                            <td>${repeat(user.roles, (role) => role, (role) => html`<sl-badge variant="primary" pill>${this.roleDesc(role)}</sl-badge>`)}</td>
                            <td>${repeat(user.memberOf, (org: any) => org.id, (org: any) => html`<sl-badge variant="neutral" pill>${org.name}</sl-badge>`)}</td>
                            <td>
                                <div class="actions-cell">
                                    ${when(user.enabled,
                                        () => html`
                                            <sl-button variant="default" size="small" @click=${() => this.editUser(user)}>
                                                <sl-icon slot="prefix" name="pencil"></sl-icon> Edit
                                            </sl-button>
                                            ${when(this.type === 'user',
                                                () => html`<sl-button variant="default" size="small" @click=${() => this.resetPassword(user)}>
                                                    <sl-icon slot="prefix" name="arrow-clockwise"></sl-icon> Reset
                                                </sl-button>`,
                                                () => html`<sl-button variant="default" size="small" @click=${() => this.viewApiKey(user)}>
                                                    <sl-icon slot="prefix" name="qr-code"></sl-icon> QR
                                                </sl-button>`)}
                                            <sl-button variant="warning" size="small" @click=${() => this.enableUser(user, false)}>
                                                <sl-icon slot="prefix" name="eye-slash"></sl-icon> Deactivate
                                            </sl-button>
                                        `,
                                        () => html`
                                            <sl-button variant="success" size="small" @click=${() => this.enableUser(user, true)}>
                                                <sl-icon slot="prefix" name="eye"></sl-icon> Reactivate
                                            </sl-button>
                                            <sl-button variant="danger" size="small" @click=${() => this.deleteUser(user)}>
                                                <sl-icon slot="prefix" name="trash"></sl-icon> Delete
                                            </sl-button>
                                        `)}
                                </div>
                            </td>
                        </tr>
                    `)}
                </tbody>
            </table>
        `;
    }

    private roleDesc(role: string): string {
        const match = this.roles.find(r => r.role === role);
        return match?.description ?? role;
    }

    private dialogLabelForTarget(target: string): string {
        return target === 'API_KEY' ? 'New API Key' : 'New User';
    }

    private onUsernameClick(e: Event, user: User): void {
        e.preventDefault();
        this.editUser(user);
    }

    private refresh(): void {
        this.refreshCount++;
    }

    connectedCallback(): void {
        super.connectedCallback();
        document.body.addEventListener('alfio-users-refresh', this.handleRefresh);
    }

    disconnectedCallback(): void {
        super.disconnectedCallback();
        document.body.removeEventListener('alfio-users-refresh', this.handleRefresh);
    }

    protected updated(changedProperties: Map<string, unknown>): void {
        super.updated(changedProperties);
        if (changedProperties.has('editingUser') && this.editingUser) {
            this.userEditDialog?.show();
        }
    }

    private readonly handleRefresh = (): void => {
        this.refresh();
    };

    public openNewUser(): void {
        this.editingUser = {
            id: null,
            organizationId: this.organizations.length === 1 ? this.organizations[0].id : 0,
            role: '',
            username: '',
            firstName: '',
            lastName: '',
            emailAddress: '',
            target: 'USER',
        };
        this.validationErrors = {};
    }

    public openNewApiKey(): void {
        this.editingUser = {
            id: null,
            organizationId: this.organizations.length === 1 ? this.organizations[0].id : 0,
            role: '',
            description: '',
            type: 'API_KEY',
            target: 'API_KEY',
        };
        this.validationErrors = {};
    }

    public async editUser(user: User): Promise<void> {
        const orgId = (user as any).organizationId ?? user.memberOf[0]?.id ?? 0;
        this.editingUser = {
            id: user.id,
            organizationId: orgId,
            role: user.roles[0] ?? '',
            username: user.username,
            firstName: user.firstName,
            lastName: user.lastName,
            emailAddress: user.emailAddress,
            description: user.description,
            type: user.type,
            target: user.type === 'API_KEY' ? 'API_KEY' : 'USER',
        };
        this.validationErrors = {};
    }

    public openBulkImport(): void {
        this.bulkOrgId = this.organizations.length === 1 ? this.organizations[0].id : null;
        this.bulkRole = '';
        this.bulkDescriptions = [];
        if (this.bulkCsvInput) {
            this.bulkCsvInput.value = '';
        }
        this.bulkImportDialog.show();
    }

    private cancelEdit(): void {
        this.editingUser = null;
        this.validationErrors = {};
        this.userEditDialog.hide();
    }

    private async saveUser(): Promise<void> {
        if (!this.editingUser) return;

        this.validationErrors = {};

        const missing = this.validateEditingUser();
        if (missing.length > 0) {
            dispatchFeedback({type: 'danger', message: `Please fill in all required fields: ${missing.join(', ')}`}, this);
            return;
        }

        const result = await UsersService.check(this.editingUser);
        if (!result.success) {
            this.validationErrors = this.mapValidationErrors(result.validationErrors);
            return;
        }

        const saved = await this.persistEditingUser();
        this.notifySaveResult(saved);
        this.cancelEdit();
    }

    private validateEditingUser(): string[] {
        if (!this.editingUser) return [];
        const missing: string[] = [];
        if (!this.editingUser.organizationId) missing.push('organization');
        if (!this.editingUser.role) missing.push('role');
        if (this.editingUser.target === 'USER') {
            if (!(this.editingUser.username?.length && this.editingUser.username.length >= 2)) missing.push('username');
            if (!(this.editingUser.firstName?.length && this.editingUser.firstName.length >= 2)) missing.push('firstName');
            if (!(this.editingUser.lastName?.length && this.editingUser.lastName.length >= 2)) missing.push('lastName');
            if (!(this.editingUser.emailAddress)) missing.push('emailAddress');
        }
        return missing;
    }

    private mapValidationErrors(errors: ErrorDescriptor[]): Record<string, string> {
        return errors.reduce((acc, err) => {
            acc[err.fieldName] = err.code;
            return acc;
        }, {} as Record<string, string>);
    }

    private async persistEditingUser(): Promise<User | void> {
        if (!this.editingUser) return;
        if (this.editingUser.id != null) {
            await UsersService.edit(this.editingUser as UserModification & { id: number });
            return;
        }
        return UsersService.edit(this.editingUser as UserModification & { id: null });
    }

    private notifySaveResult(saved: User | void): void {
        if (!this.editingUser) return;
        if (this.editingUser.id != null) {
            this.refresh();
            dispatchFeedback({type: 'success', message: 'User updated successfully'}, this);
        } else if (saved) {
            this.refresh();
            if (this.editingUser.target === 'USER') {
                this.userDataReset = saved;
                this.showUserDataDialog.show();
            }
        }
    }

    private async handleFileSelect(event: FileUploadChangeEvent): Promise<void> {
        const file = event.detail.file;
        if (file != null) {
            const content = await file.text();
            this.bulkDescriptions = this.parseCsv(content);
        } else {
            this.bulkDescriptions = [];
        }
    }

    private parseCsv(content: string): string[] {
        const results = Papa.parse<string>(content, {
            header: false,
            skipEmptyLines: true,
            delimiter: ',',
        });
        return results.data
            .filter(row => row.length >= 1 && row[0].trim().length > 0)
            .map(row => row[0]);
    }

    private async saveBulkImport(): Promise<void> {
        if (!this.bulkOrgId || !this.bulkRole || this.bulkDescriptions.length === 0) {
            dispatchFeedback({type: 'danger', message: 'Please select organization, role and upload a CSV file'}, this);
            return;
        }
        await UsersService.bulkImportApiKeys({
            organizationId: this.bulkOrgId,
            role: this.bulkRole,
            descriptions: this.bulkDescriptions,
        });
        this.refresh();
        await this.bulkImportDialog.hide();
        dispatchFeedback({type: 'success', message: 'API Keys imported successfully'}, this);
    }

    private cancelBulkImport(): void {
        this.bulkOrgId = null;
        this.bulkRole = '';
        this.bulkDescriptions = [];
        if (this.bulkCsvInput) this.bulkCsvInput.value = '';
        this.bulkImportDialog.hide();
    }

    private async deleteUser(user: User): Promise<void> {
        const confirmation = await ConfirmationDialogService.requestConfirm(
            `Delete user?`,
            `The ${this.type} ${user.username} will be deleted. Are you sure?`,
            'danger'
        );
        if (confirmation) {
            await UsersService.delete(user.id);
            this.refresh();
            dispatchFeedback({type: 'success', message: 'User deleted'}, this);
        }
    }

    private async resetPassword(user: User): Promise<void> {
        const confirmation = await ConfirmationDialogService.requestConfirm(
            `Reset password?`,
            `The password for the user ${user.username} will be reset. Are you sure?`,
            'warning'
        );
        if (confirmation) {
            this.userDataReset = await UsersService.resetPassword(user.id);
            this.showUserDataDialog.show();
        }
    }

    private async enableUser(user: User, status: boolean): Promise<void> {
        await UsersService.enable(user.id, status);
        this.refresh();
    }

    private downloadApiKeys(orgId: number): void {
        window.open(`/admin/api/api-keys/organization/${orgId}/all`);
    }

    private viewApiKey(user: User): void {
        this.qrCodeUser = user;
        this.apiQrDialog.show();
    }

    private revealSystemApiKey(): void {
        UsersService.retrieveSystemApiKey().then(key => {
            this.systemApiKey = key;
        });
    }

    private openConfirmRotation(): void {
        this.confirmRotationDialog.show();
    }

    private async confirmRotateSystemApiKey(): Promise<void> {
        await this.confirmRotationDialog.hide();
        this.systemApiKey = await UsersService.rotateSystemApiKey();
        dispatchFeedback({type: 'success', message: 'API Key successfully rotated.'}, this);
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-users': Users;
    }
}
