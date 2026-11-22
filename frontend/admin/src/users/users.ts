import {css, html, LitElement, nothing, TemplateResult} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {repeat} from 'lit/directives/repeat.js';
import {when} from 'lit/directives/when.js';
import {Task} from '@lit/task';
import {TanStackFormController} from '@tanstack/lit-form';
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
    retroCompat,
    row,
    spacing,
    textAlign,
    textColors
} from '../styles.ts';
import {dispatchFeedback} from '../model/dom-events.ts';
import {ErrorDescriptor} from '../model/validation.ts';
import Papa from 'papaparse';
import type {SlDialog, SlRequestCloseEvent, SlSelect} from '@shoelace-style/shoelace';
import {UtilService} from "../service/util.ts";
import {FileUploadChangeEvent} from "../components/file-upload.ts";
import {emptyState} from '../components/empty-state.ts';
import {sectionCard} from '../components/section-card.ts';
import {taskContent} from '../components/task-content.ts';
import {
    BoundField,
    emailAddress,
    fieldError,
    focusFirstError,
    FormMountController,
    minLength,
    requiredValue,
    setServerError,
    submitValidated,
    validatedInput,
    ValidatedInputOptions,
    validatedSelect,
    validateWith,
} from '../components/form-validation.ts';

import { adminConfig } from '../admin-config';
interface LoadResult {
    users: User[];
    roles: Role[];
    organizations: Organization[];
    isAdmin: boolean;
}

interface EditableUser extends UserModification {
    target?: string;
}

/**
 * The validated values of the user (or API key) being edited
 */
interface UserFormValues {
    // the ID of the organization, empty if none is selected
    organizationId: string;
    role: string;
    username: string;
    firstName: string;
    lastName: string;
    emailAddress: string;
    description: string;
}

interface BulkImportFormValues {
    organizationId: string;
    role: string;
    // the API keys read from the CSV file
    entries: number;
}

function emptyUserForm(organizationId: string): UserFormValues {
    return { organizationId, role: '', username: '', firstName: '', lastName: '', emailAddress: '', description: '' };
}

function validateEntries(value: unknown): string | undefined {
    if (Number(value ?? 0) === 0) {
        return 'Please upload a CSV file with at least one API key';
    }
    return undefined;
}

@customElement('alfio-users')
export class Users extends LitElement {
    @property({attribute: 'alfio-title'}) alfioTitle: string = 'Users';
    @property() type: UserType = 'user';

    @state() isAdmin: boolean = adminConfig().isAdmin;
    @state() users: User[] = [];
    @state() roles: Role[] = [];
    @state() organizations: Organization[] = [];
    @state() selectedOrganization: number | null = null;
    @state() selectedRole: string | null = null;
    @state() systemApiKey: string | null = null;

    @state() qrCodeUser: User | null = null;
    @state() userDataReset: User | null = null;

    @query('sl-dialog#api-key-qr') apiQrDialog!: SlDialog;
    @query('sl-dialog#show-user-data') showUserDataDialog!: SlDialog;
    @query('sl-dialog#user-edit') userEditDialog!: SlDialog;
    @query('sl-dialog#bulk-import') bulkImportDialog!: SlDialog;

    // the user (or API key) being edited: what is not in the form, e.g. its ID and type
    @state() private editingUser: EditableUser | null = null;

    readonly #userForm = new TanStackFormController(this, {
        defaultValues: emptyUserForm(''),
        onSubmit: async ({value}) => {
            await this.saveUser(value);
        },
    });

    readonly #bulkForm = new TanStackFormController(this, {
        defaultValues: { organizationId: '', role: '', entries: 0 } as BulkImportFormValues,
        onSubmit: async ({value}) => {
            await this.saveBulkImport(value);
        },
    });

    @state() private bulkDescriptions: string[] = [];
    private bulkCsvInput: HTMLInputElement | null = null;

    @state() private refreshCount: number = 0;
    // the last loaded data, kept while a refresh is running
    @state() private loadedData: LoadResult | null = null;
    @state() private savingUser: boolean = false;
    @state() private savingBulkImport: boolean = false;

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
                    if (a.enabled) {
                        return -1;
                    }
                    return 1;
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

            const result: LoadResult = {
                users: sortedUsers,
                roles,
                organizations,
                isAdmin: this.isAdmin,
            };
            this.loadedData = result;
            return result;
        },
        () => [this.refreshCount]
    );

    static readonly styles = [
        base,
        retroCompat,
        spacing,
        textAlign,
        form,
        dialog,
        row,
        badges,
        textColors,
        modernTable,
        modernLayout,
        css`
            .api-key-cell {
                display: inline-flex;
                align-items: center;
                gap: var(--sl-spacing-2x-small);
                font-family: var(--sl-font-mono);
                font-size: var(--sl-font-size-small);
                color: var(--sl-color-gray-600);
            }

            #user-edit {
                --alfio-dialog-max-width: 48rem;
            }

            #bulk-import {
                --alfio-dialog-max-width: 60rem;
            }

            #api-key-qr,
            #show-user-data {
                --alfio-dialog-max-width: 32rem;
            }

            .active-users .section-header > sl-icon {
                color: var(--sl-color-success-600);
            }

            .inactive-users .section-header > sl-icon {
                color: var(--sl-color-gray-500);
            }
        `
    ];

    render() {
        const data = this.loadedData;
        const orgs = data?.organizations ?? [];
        const availableRoles = new Set<string>();
        for (const user of (data?.users ?? [])) {
            for (const role of user.roles) {
                availableRoles.add(role);
            }
        }
        if (this.selectedRole !== null && !availableRoles.has(this.selectedRole)) {
            this.selectedRole = null;
        }
        const hasFilters = data != null && (orgs.length > 1 || availableRoles.size > 0);

        return html`
            <div class="container">
                <div class="page-title-row">
                    <h1>${this.alfioTitle}</h1>
                </div>
                <hr class="page-separator">

                ${when(this.isAdmin && this.type === 'apikey', () => this.renderSystemApiKey())}

                ${when(hasFilters,
                    () => html`
                        <div class="filter-toolbar">
                            <div class="filter-left">
                                ${this.renderFilters(orgs, availableRoles)}
                            </div>
                            <div class="filter-right">
                                ${this.renderToolbarActions(orgs)}
                            </div>
                        </div>
                    `,
                    () => html`
                        <div class="filter-toolbar" style="justify-content: flex-end;">
                            <div class="filter-right">
                                ${this.renderToolbarActions(orgs)}
                            </div>
                        </div>
                    `)}

                ${taskContent(this.loadDataTask, data, `Failed to load the ${this.entityPlural()}. Please try again.`,
                    (loaded) => this.renderSections(loaded))}
            </div>

            ${this.renderUserEditDialog()}
            ${this.renderBulkImportDialog()}

            <sl-dialog id="api-key-qr" class="responsive-dialog" label=${this.qrCodeUser?.description ?? ''}>
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

            <sl-dialog id="show-user-data" class="responsive-dialog" label="User credentials">
                <div class="credentials-card">
                    <div class="cred-row">
                        <span class="cred-label">Username</span>
                        <span class="cred-value">${this.userDataReset?.username ?? ''}</span>
                    </div>
                    <div class="cred-row">
                        <span class="cred-label">Password</span>
                        <span class="cred-value">
                            <sl-tooltip content="Copy password">
                                <sl-button variant="text" size="small" @click=${() => UtilService.copyValueToClipboard(() => this.userDataReset!.password!, 'User Password', this)}>
                                    <sl-icon name="clipboard" label="Copy password"></sl-icon>
                                </sl-button>
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

    private renderSystemApiKey(): TemplateResult {
        return html`
            <div class="featured-card">
                <h2><sl-icon name="key-fill"></sl-icon> System API Key</h2>
                <small class="text-muted">Used by external applications to create, modify and delete organizations, and to create API keys.</small>
                ${when(this.systemApiKey,
                    () => html`
                        <div class="secret-display">${this.systemApiKey}</div>
                        <div class="api-key-actions">
                            <sl-button variant="default" size="small" @click=${() => UtilService.copyValueToClipboard(() => this.systemApiKey!, 'System API Key', this)}>
                                <sl-icon slot="prefix" name="clipboard"></sl-icon> Copy
                            </sl-button>
                            <sl-button variant="warning" size="small" @click=${() => this.confirmRotateSystemApiKey()}>
                                <sl-icon slot="prefix" name="arrow-clockwise"></sl-icon> Rotate
                            </sl-button>
                        </div>
                    `,
                    () => html`
                        <div class="api-key-actions">
                            <sl-button variant="default" size="small" @click=${() => this.revealSystemApiKey()}>
                                <sl-icon slot="prefix" name="eye"></sl-icon> Reveal System API Key
                            </sl-button>
                        </div>
                    `)}
            </div>
        `;
    }

    private renderFilters(orgs: Organization[], availableRoles: Set<string>): TemplateResult {
        const targetType = this.targetType();
        let selectedOrganization = '';
        if (this.selectedOrganization !== null) {
            selectedOrganization = String(this.selectedOrganization);
        }
        return html`
            ${when(orgs.length > 1, () => html`
                <sl-select clearable hoist class="filter-select" label="Show members of"
                           .value=${selectedOrganization}
                           @sl-change=${(e: Event) => this.onOrganizationFilterChange((e.target as SlSelect).value)}>
                    <sl-option value="">All</sl-option>
                    ${repeat(orgs, (org) => org.id, (org) => html`
                        <sl-option value=${org.id}>${org.name}</sl-option>
                    `)}
                </sl-select>
            `)}
            <sl-select clearable hoist class="filter-select" label="Filter by role"
                       .value=${this.selectedRole ?? ''}
                       @sl-change=${(e: Event) => this.onRoleFilterChange((e.target as SlSelect).value)}>
                <sl-option value="">All roles</sl-option>
                ${repeat(this.roles.filter(r => (r.target || []).includes(targetType) && availableRoles.has(r.role)), (r) => r.role, (r) => html`
                    <sl-option value=${r.role}>${r.description}</sl-option>
                `)}
            </sl-select>
        `;
    }

    private renderToolbarActions(orgs: Organization[]): TemplateResult {
        return html`
            ${when(this.type === 'user',
                () => html`<sl-button variant="success" size="large" @click=${() => this.openNewUser()}><sl-icon slot="prefix" name="plus-circle"></sl-icon> Add new</sl-button>`,
                () => html`<sl-button variant="success" size="large" @click=${() => this.openNewApiKey()}><sl-icon slot="prefix" name="plus-circle"></sl-icon> Add new</sl-button>`)}
            ${when(this.type === 'apikey',
                () => html`<sl-button variant="primary" size="large" @click=${() => this.openBulkImport()}><sl-icon slot="prefix" name="file-earmark-bar-graph"></sl-icon> Bulk creation</sl-button>`)}
            ${when(this.type === 'apikey' && orgs.length === 1,
                () => html`<sl-button variant="default" size="large" @click=${() => this.downloadApiKeys(orgs[0].id)}><sl-icon slot="prefix" name="download"></sl-icon> Download all</sl-button>`)}
            ${when(this.type === 'apikey' && orgs.length > 1,
                () => html`
                    <sl-dropdown hoist>
                        <sl-button slot="trigger" variant="default" size="large" caret><sl-icon slot="prefix" name="download"></sl-icon> Download all</sl-button>
                        <sl-menu>
                            ${repeat(orgs, (org) => org.id, (org) => html`
                                <sl-menu-item @click=${() => this.downloadApiKeys(org.id)}>${org.name}</sl-menu-item>
                            `)}
                        </sl-menu>
                    </sl-dropdown>
                `)}
        `;
    }

    private renderSections(data: LoadResult): TemplateResult {
        const enabledUsers = this.applyFilters(data.users.filter(u => u.enabled));
        const disabledUsers = this.applyFilters(data.users.filter(u => !u.enabled));
        return html`
            ${sectionCard({
                icon: 'check-circle',
                title: 'Active',
                badge: html`<sl-badge variant="success" pill>${enabledUsers.length}</sl-badge>`,
                className: 'active-users',
            }, html`<div class="table-responsive">${this.renderUsersTable(enabledUsers)}</div>`)}

            <slot name="edit-user"></slot>

            ${sectionCard({
                icon: 'eye-slash',
                title: 'Inactive',
                badge: html`<sl-badge variant="neutral" pill>${disabledUsers.length}</sl-badge>`,
                className: 'inactive-users',
            }, html`<div class="table-responsive">${this.renderUsersTable(disabledUsers)}</div>`)}
        `;
    }

    private onOrganizationFilterChange(value: string | string[]): void {
        if (value) {
            this.selectedOrganization = Number(value);
        } else {
            this.selectedOrganization = null;
        }
    }

    private onRoleFilterChange(value: string | string[]): void {
        if (value) {
            this.selectedRole = String(value);
        } else {
            this.selectedRole = null;
        }
    }

    private renderUserEditDialog(): TemplateResult | typeof nothing {
        const user = this.editingUser;
        if (!user) {
            return nothing;
        }
        const target = user.target ?? 'USER';
        const availableRoles = this.roles.filter(r => (r.target || []).includes(target));

        const submitted = this.#userForm.api.state.submissionAttempts > 0;
        const input = (field: BoundField<string>, options: ValidatedInputOptions) => validatedInput(field, submitted, options);
        const ifUser = (validate: () => string | undefined) => {
            if (this.editingUser?.target !== 'USER') {
                return undefined;
            }
            return validate();
        };

        return html`
            <sl-dialog id="user-edit" class="responsive-dialog" label=${this.userDialogLabel(user)}
                       @sl-request-close=${this.preventOverlayClose}
                       @sl-after-hide=${this.onUserDialogAfterHide}>
                <div class="form-stack">
                    <div class="row" style="--alfio-row-cols: 2">
                        ${this.#userForm.field({ name: 'organizationId', validators: validateWith(value => requiredValue(value)) },
                            field => validatedSelect(field, submitted, { id: 'edit-org', label: 'Organization', placeholder: 'Select an organization' }, html`
                                ${repeat(this.organizations, (org) => org.id, (org) => html`
                                    <sl-option value=${String(org.id)}>${org.name}</sl-option>
                                `)}
                            `))}
                        ${this.#userForm.field({ name: 'role', validators: validateWith(value => requiredValue(value)) },
                            field => validatedSelect(field, submitted, { id: 'edit-role', label: 'Role', placeholder: 'Select a role' }, html`
                                ${repeat(availableRoles, (r) => r.role, (r) => html`
                                    <sl-option value=${r.role}>${r.description}</sl-option>
                                `)}
                            `))}
                    </div>

                    ${when(target === 'USER',
                        () => html`
                            ${this.#userForm.field({ name: 'username', validators: validateWith(value => ifUser(() => minLength(value, 2))) },
                                field => input(field, { id: 'edit-username', label: 'Username', prefixIcon: 'person' }))}
                            <div class="row" style="--alfio-row-cols: 2">
                                ${this.#userForm.field({ name: 'firstName', validators: validateWith(value => ifUser(() => minLength(value, 2))) },
                                    field => input(field, { id: 'edit-firstName', label: 'First name' }))}
                                ${this.#userForm.field({ name: 'lastName', validators: validateWith(value => ifUser(() => minLength(value, 2))) },
                                    field => input(field, { id: 'edit-lastName', label: 'Last name' }))}
                            </div>
                            ${this.#userForm.field({ name: 'emailAddress', validators: validateWith(value => ifUser(() => emailAddress(value))) },
                                field => input(field, { id: 'edit-emailAddress', label: 'E-mail', type: 'email', prefixIcon: 'envelope' }))}
                        `,
                        () => html`
                            ${this.#userForm.field({ name: 'description' },
                                field => input(field, { id: 'edit-description', label: 'Description', maxlength: 128, prefixIcon: 'card-text', optional: true }))}
                        `)}
                </div>

                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => this.cancelEdit()}>Cancel</sl-button>
                        <div></div>
                        <sl-button id="save-user" variant="warning" size="large" ?loading=${this.savingUser} @click=${() => this.submitUser()}>
                            Save
                        </sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    private renderBulkImportDialog(): TemplateResult {
        const availableRoles = this.roles.filter(r => (r.target || []).includes('API_KEY'));
        const bulkSubmitted = this.#bulkForm.api.state.submissionAttempts > 0;
        const count = this.bulkDescriptions.length;

        return html`
            <sl-dialog id="bulk-import" class="responsive-dialog" label="Bulk create API keys"
                       @sl-request-close=${this.preventOverlayClose}>
                <div class="form-stack">
                    ${this.#bulkForm.field({ name: 'organizationId', validators: validateWith(value => requiredValue(value)) },
                        field => validatedSelect(field, bulkSubmitted, { id: 'bulk-org', label: 'Organization', placeholder: 'Select an organization' }, html`
                            ${repeat(this.organizations, (org) => org.id, (org) => html`
                                <sl-option value=${String(org.id)}>${org.name}</sl-option>
                            `)}
                        `))}

                    ${this.#bulkForm.field({ name: 'role', validators: validateWith(value => requiredValue(value)) },
                        field => validatedSelect(field, bulkSubmitted, { id: 'bulk-role', label: 'Role', placeholder: 'Select a role' }, html`
                            ${repeat(availableRoles, (r) => r.role, (r) => html`
                                <sl-option value=${r.role}>${r.description}</sl-option>
                            `)}
                        `))}

                    <div class="bulk-specs">
                        <h4 style="margin-top: 0; margin-bottom: var(--sl-spacing-small);">File specifications</h4>
                        <p class="text-muted" style="margin: 0 0 var(--sl-spacing-small);">
                            Create a CSV file <strong>without header</strong>, using commas (<strong>,</strong>) as separator and double quotes (<strong>"</strong>) as quote character.
                        </p>
                        <strong>Row specification:</strong>
                        <pre><span class="text-success">name</span>

where:

<span class="text-success">name</span> is the client name, displayed in the QR-Code and statistics</pre>
                    </div>

                    <div>
                        <alfio-file-upload @change=${(e: FileUploadChangeEvent) => this.handleFileSelect(e)}></alfio-file-upload>
                        ${this.#bulkForm.field({ name: 'entries', validators: validateWith(value => validateEntries(value)) }, field => {
                            const error = fieldError(field.state.meta, bulkSubmitted);
                            return html`${when(error, () => html`
                                <sl-alert class="bulk-entries-error mt-2" variant="danger" open>
                                    <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                                    ${error}
                                </sl-alert>
                            `)}`;
                        })}
                    </div>

                    ${when(count > 0, () => html`
                        <div class="table-responsive">
                            <table class="table table-striped">
                                <thead>
                                    <tr><th colspan="2">Parsed entries</th></tr>
                                </thead>
                                <tbody>
                                    ${repeat(this.bulkDescriptions, (_, i) => i, (desc) => html`<tr><td colspan="2">${desc}</td></tr>`)}
                                </tbody>
                                <tfoot>
                                    <tr><th>Total</th><th><sl-badge variant="primary" pill>${count}</sl-badge></th></tr>
                                </tfoot>
                            </table>
                        </div>
                        <sl-alert variant="primary" open>
                            <sl-icon slot="icon" name="info-circle"></sl-icon>
                            <strong>${count}</strong> ${this.apiKeysLabel(count)} will be created.
                        </sl-alert>
                    `)}
                </div>

                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${() => this.cancelBulkImport()}>Cancel</sl-button>
                        <div></div>
                        <sl-button id="save-bulk-import" variant="warning" size="large" ?loading=${this.savingBulkImport}
                                   @click=${() => this.submitBulkImport()}>
                            Save
                        </sl-button>
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    private applyFilters(users: User[]): User[] {
        return users.filter(u => this.matchesFilters(u));
    }

    private matchesFilters(user: User): boolean {
        if (this.selectedOrganization !== null && !user.memberOf.some(org => org.id === this.selectedOrganization)) {
            return false;
        }
        if (this.selectedRole !== null && !user.roles.includes(this.selectedRole)) {
            return false;
        }
        return true;
    }

    private renderUsersTable(filtered: User[]): TemplateResult {
        if (filtered.length === 0) {
            return emptyState(`No ${this.entityPlural()} found`);
        }

        return html`
            <table class="table table-striped">
                <thead>
                    <tr>
                        <th style="width: 12%">
                            ${when(this.type === 'user', () => 'Username', () => 'API key')}
                        </th>
                        <th style="width: 33%">${when(this.type === 'apikey', () => 'Description', () => 'Name')}</th>
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
                                            <sl-tooltip content="Copy API key">
                                                <sl-button variant="text" size="small" @click=${() => UtilService.copyValueToClipboard(() => user.username, 'API Key', this)}>
                                                    <sl-icon name="clipboard" label="Copy API key"></sl-icon>
                                                </sl-button>
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
                                            <sl-button variant="default" size="small" @click=${() => this.enableUser(user, true)}>
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

    private targetType(): 'USER' | 'API_KEY' {
        if (this.type === 'user') {
            return 'USER';
        }
        return 'API_KEY';
    }

    // "users" or "API keys", for the messages of the page
    private entityPlural(): string {
        if (this.type === 'user') {
            return 'users';
        }
        return 'API keys';
    }

    private apiKeysLabel(count: number): string {
        if (count === 1) {
            return 'API key';
        }
        return 'API keys';
    }

    private userDialogLabel(user: EditableUser): string {
        const isApiKey = user.target === 'API_KEY';
        if (user.id != null) {
            if (isApiKey) {
                return 'Edit API key';
            }
            return 'Edit user';
        }
        if (isApiKey) {
            return 'New API key';
        }
        return 'New user';
    }

    // a click outside a form dialog would discard what the user typed: only the close button and Cancel close it
    private readonly preventOverlayClose = (event: SlRequestCloseEvent): void => {
        if (event.detail.source === 'overlay') {
            event.preventDefault();
        }
    };

    private readonly onUserDialogAfterHide = (event: Event): void => {
        // nested Shoelace components (e.g. selects, tooltips) emit their own sl-after-hide
        if (event.target !== event.currentTarget) {
            return;
        }
        this.editingUser = null;
    };

    private roleDesc(role: string): string {
        const match = this.roles.find(r => r.role === role);
        return match?.description ?? role;
    }

    private onUsernameClick(e: Event, user: User): void {
        e.preventDefault();
        this.editUser(user);
    }

    private refresh(): void {
        this.refreshCount++;
    }

    constructor() {
        super();
        new FormMountController(this, this.#userForm.api);
        new FormMountController(this, this.#bulkForm.api);
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
        this.editingUser = { id: null, organizationId: 0, role: '', target: 'USER' };
        this.#userForm.api.reset(emptyUserForm(this.singleOrganizationId()));
    }

    public openNewApiKey(): void {
        this.editingUser = { id: null, organizationId: 0, role: '', type: 'API_KEY', target: 'API_KEY' };
        this.#userForm.api.reset(emptyUserForm(this.singleOrganizationId()));
    }

    public async editUser(user: User): Promise<void> {
        const orgId = (user as any).organizationId ?? user.memberOf[0]?.id ?? 0;
        let target = 'USER';
        if (user.type === 'API_KEY') {
            target = 'API_KEY';
        }
        this.editingUser = { id: user.id, organizationId: orgId, role: '', type: user.type, target };
        let organizationId = '';
        if (orgId) {
            organizationId = String(orgId);
        }
        this.#userForm.api.reset({
            organizationId,
            role: user.roles[0] ?? '',
            username: user.username ?? '',
            firstName: user.firstName ?? '',
            lastName: user.lastName ?? '',
            emailAddress: user.emailAddress ?? '',
            description: user.description ?? '',
        });
    }

    public openBulkImport(): void {
        this.#bulkForm.api.reset({ organizationId: this.singleOrganizationId(), role: '', entries: 0 });
        this.bulkDescriptions = [];
        if (this.bulkCsvInput) {
            this.bulkCsvInput.value = '';
        }
        this.bulkImportDialog.show();
    }

    // preselected when there is no choice
    private singleOrganizationId(): string {
        if (this.organizations.length === 1) {
            return String(this.organizations[0].id);
        }
        return '';
    }

    // the dialog clears the user being edited once hidden (see onUserDialogAfterHide)
    private cancelEdit(): void {
        void this.userEditDialog?.hide();
    }

    private async submitUser(): Promise<void> {
        if (this.savingUser) {
            return;
        }
        this.savingUser = true;
        try {
            if (!await submitValidated(this.#userForm.api)) {
                await this.updateComplete;
                focusFirstError(this.userEditDialog);
            }
        } finally {
            this.savingUser = false;
        }
    }

    /**
     * Called by the user form once all the fields are valid
     */
    private async saveUser(values: UserFormValues): Promise<void> {
        if (!this.editingUser) return;
        this.editingUser = this.toModification(this.editingUser, values);

        const result = await UsersService.check(this.editingUser);
        if (!result.success) {
            const errors = this.mapValidationErrors(result.validationErrors);
            const fields = Object.keys(this.#userForm.api.fieldInfo);
            const others: string[] = [];
            Object.entries(errors).forEach(([field, message]) => {
                if (fields.includes(field)) {
                    setServerError(this.#userForm.api, field, message);
                } else {
                    others.push(message);
                }
            });
            if (others.length > 0) {
                dispatchFeedback({type: 'danger', message: others.join(' ')}, this);
            }
            return;
        }

        const saved = await this.persistEditingUser();
        this.notifySaveResult(saved);
        this.cancelEdit();
    }

    private toModification(user: EditableUser, values: UserFormValues): EditableUser {
        const modification: EditableUser = {
            ...user,
            organizationId: Number(values.organizationId),
            role: values.role,
        };
        if (user.target === 'USER') {
            modification.username = values.username.trim();
            modification.firstName = values.firstName.trim();
            modification.lastName = values.lastName.trim();
            modification.emailAddress = values.emailAddress.trim();
        } else {
            modification.description = values.description;
        }
        return modification;
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
            this.#bulkForm.api.setFieldValue('entries', this.bulkDescriptions.length);
        } else {
            this.#bulkForm.api.setFieldValue('entries', 0);
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

    private async submitBulkImport(): Promise<void> {
        if (this.savingBulkImport) {
            return;
        }
        this.savingBulkImport = true;
        try {
            if (!await submitValidated(this.#bulkForm.api)) {
                await this.updateComplete;
                focusFirstError(this.bulkImportDialog);
            }
        } finally {
            this.savingBulkImport = false;
        }
    }

    /**
     * Called by the bulk import form once all the fields are valid
     */
    private async saveBulkImport(values: BulkImportFormValues): Promise<void> {
        await UsersService.bulkImportApiKeys({
            organizationId: Number(values.organizationId),
            role: values.role,
            descriptions: this.bulkDescriptions,
        });
        this.refresh();
        await this.bulkImportDialog.hide();
        dispatchFeedback({type: 'success', message: 'API keys imported successfully'}, this);
    }

    private cancelBulkImport(): void {
        this.bulkDescriptions = [];
        if (this.bulkCsvInput) this.bulkCsvInput.value = '';
        this.bulkImportDialog.hide();
    }

    private async deleteUser(user: User): Promise<void> {
        let title = 'Delete user?';
        let message = `The user ${user.username} will be deleted. Are you sure?`;
        if (this.type === 'apikey') {
            title = 'Delete API key?';
            message = `The API key ${user.description ?? user.username} will be deleted. Are you sure?`;
        }
        const confirmation = await ConfirmationDialogService.requestConfirm(title, message, 'danger');
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

    private async confirmRotateSystemApiKey(): Promise<void> {
        const confirmation = await ConfirmationDialogService.requestConfirm(
            'Rotate the System API Key?',
            'The current key stops working immediately, and all external applications must use the new one. This cannot be undone.',
            'warning'
        );
        if (!confirmation) {
            return;
        }
        this.systemApiKey = await UsersService.rotateSystemApiKey();
        dispatchFeedback({type: 'success', message: 'API Key successfully rotated.'}, this);
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-users': Users;
    }
}
