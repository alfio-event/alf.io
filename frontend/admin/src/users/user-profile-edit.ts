import {customElement, query, state} from "lit/decorators.js";
import {css, html, LitElement, nothing, TemplateResult} from "lit";
import {SlButton, SlDialog} from "@shoelace-style/shoelace";
import {when} from "lit/directives/when.js";
import {classMap} from "lit/directives/class-map.js";
import {badges, base, dialog, form, modernLayout, pageHeader, panelStyles, retroCompat, row, spacing, textColors} from "../styles.ts";
import {UsersService} from "./users-service.ts";
import {dispatchFeedback} from "../model/dom-events.ts";
import {ErrorDescriptor} from "../model/validation.ts";

const PASSWORD_REGEX = /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[\x21-\x2F\x3A-\x40\x5B-\x60\x7B-\x7E])(?!.*\s).{10,}$/;

interface ProfileForm {
    firstName: string;
    lastName: string;
    emailAddress: string;
}

interface PasswordForm {
    oldPassword: string;
    newPassword: string;
    newPasswordConfirm: string;
}

@customElement('alfio-user-profile-edit')
export class UserProfileEdit extends LitElement {

    @query('sl-dialog#change-password')
    changePasswordDialog?: SlDialog;
    @query('sl-button#save-profile')
    saveProfileBtn?: SlButton;
    @query('sl-button#change-password-btn')
    changePasswordBtn?: SlButton;

    @state() loading: boolean = true;
    @state() profileUsername: string = '';
    @state() isAdminProfile: boolean = false;
    @state() passwordSuccess: boolean = false;

    @state() profileValidationErrors: Record<string, string> = {};
    @state() passwordValidationErrors: Record<string, string> = {};

    @state() profileForm: ProfileForm = {
        firstName: '',
        lastName: '',
        emailAddress: ''
    };

    @state() passwordForm: PasswordForm = {
        oldPassword: '',
        newPassword: '',
        newPasswordConfirm: ''
    };

    @state() saving: boolean = false;
    @state() changingPassword: boolean = false;
    @state() passwordFormSubmitted: boolean = false;

    static readonly styles = [
        base,
        retroCompat,
        panelStyles,
        spacing,
        pageHeader,
        form,
        textColors,
        badges,
        row,
        dialog,
        modernLayout,
        css`
            .container {
                max-width: 640px;
                margin: 0 auto;
                padding: var(--sl-spacing-medium);
            }

            .first-element {
                margin-top: var(--alfio-page-top-margin, 2rem);
            }

            .form-group {
                margin-bottom: 16px;
                margin-top: 16px;
            }

            .actions {
                display: flex;
                justify-content: flex-end;
                gap: var(--sl-spacing-small);
                margin-top: var(--sl-spacing-medium);
            }

            .section-card {
                background: var(--sl-color-neutral-5);
                border: 1px solid var(--sl-color-gray-200);
                border-radius: var(--sl-input-border-radius-large);
                margin-bottom: var(--sl-spacing-medium);
                overflow: hidden;
            }

            .section-header {
                display: flex;
                align-items: center;
                gap: var(--sl-spacing-small);
                padding: var(--sl-spacing-medium);
                background: var(--sl-color-gray-50);
                border-bottom: 1px solid var(--sl-color-gray-200);
            }

            .section-header h3 {
                margin-top: 0;
                margin-bottom: 0;
                font-size: var(--sl-font-size-large);
            }

            .section-body {
                padding: var(--sl-spacing-medium);
            }

            .warning-admin {
                margin-top: 0;
                margin-bottom: var(--sl-spacing-medium);
            }
        `
    ];

    constructor() {
        super();
        void this.loadProfile();
    }

    protected render(): TemplateResult {
        return html`
            <div class="container">
                <div class="first-element page-title-row">
                    <h1>Edit your account</h1>
                </div>
                <hr class="page-separator">

                ${when(this.loading,
                    () => html`
                        <div class="loading-spinner">
                            <sl-spinner></sl-spinner>
                        </div>
                    `,
                    () => html`
                        <div class="section-card">
                            <div class="section-header">
                                <sl-icon name="person-fill"></sl-icon>
                                <h3>Personal Information</h3>
                            </div>
                            <div class="section-body">
                                <div class="text-muted mb-3">
                                    <sl-icon name="person"></sl-icon> ${this.profileUsername}
                                </div>

                                ${this.renderProfileForm()}

                                <div class="actions" style="justify-content: space-between;">
                                    ${when(this.saving,
                                        () => html`<sl-spinner></sl-spinner>`,
                                        () => html`
                                            <sl-button variant="default" size="large" @click=${this.resetProfileForm}>Cancel</sl-button>
                                            <sl-button id="save-profile" variant="warning" size="large" @click=${this.saveProfile}>
                                                <sl-icon slot="prefix" name="check2"></sl-icon> Save
                                            </sl-button>
                                        `)}
                                </div>
                            </div>
                        </div>

                        <div class="section-card">
                            <div class="section-header">
                                <sl-icon name="lock-fill"></sl-icon>
                                <h3>Change Password</h3>
                            </div>
                            <div class="section-body">
                                ${when(this.isAdminProfile,
                                    () => html`
                                        <sl-alert variant="warning" open>
                                            <sl-icon slot="icon" name="exclamation-triangle"></sl-icon>
                                            <div>
                                                <strong>With great power comes great responsibility.</strong> You are the administrator,
                                                therefore <strong>nobody could reset your password if you lose it</strong>. Be careful!
                                            </div>
                                        </sl-alert>
                                    `,
                                    () => nothing)}

                                <div class="actions">
                                    <sl-button id="change-password-btn" variant="primary" size="large" @click=${this.openChangePasswordDialog}>
                                        <sl-icon slot="prefix" name="key"></sl-icon> Change Password
                                    </sl-button>
                                </div>
                            </div>
                        </div>
                    `)}
            </div>

            ${this.renderChangePasswordDialog()}
        `;
    }

    private renderProfileForm(): TemplateResult {
        const errorFor = (field: string) =>
            this.profileValidationErrors[field]
                ? html`<sl-alert variant="danger" open class="mt-2"><sl-icon slot="icon" name="exclamation-triangle"></sl-icon> ${this.profileValidationErrors[field]}</sl-alert>`
                : nothing;

        return html`
            <div class="row" style="--alfio-row-cols: 2">
                <div class="form-group">
                    <sl-input
                        id="firstName"
                        label="First Name"
                        required
                        minlength="2"
                        class=${classMap({error: this.profileValidationErrors['firstName'] != null})}
                        .value=${this.profileForm.firstName}
                        @sl-input=${(e: InputEvent) => {
                            this.profileForm.firstName = (e.target as HTMLInputElement).value;
                            this.clearProfileError('firstName');
                        }}>
                        <sl-icon name="person" slot="prefix"></sl-icon>
                    </sl-input>
                    ${errorFor('firstName')}
                </div>
                <div class="form-group">
                    <sl-input
                        id="lastName"
                        label="Last Name"
                        required
                        minlength="2"
                        class=${classMap({error: this.profileValidationErrors['lastName'] != null})}
                        .value=${this.profileForm.lastName}
                        @sl-input=${(e: InputEvent) => {
                            this.profileForm.lastName = (e.target as HTMLInputElement).value;
                            this.clearProfileError('lastName');
                        }}>
                        <sl-icon name="person" slot="prefix"></sl-icon>
                    </sl-input>
                    ${errorFor('lastName')}
                </div>
            </div>
            <div class="form-group">
                <sl-input
                    id="emailAddress"
                    label="Email Address"
                    type="email"
                    required
                    class=${classMap({error: this.profileValidationErrors['emailAddress'] != null})}
                    .value=${this.profileForm.emailAddress}
                    @sl-input=${(e: InputEvent) => {
                        this.profileForm.emailAddress = (e.target as HTMLInputElement).value;
                        this.clearProfileError('emailAddress');
                    }}>
                    <sl-icon name="envelope" slot="prefix"></sl-icon>
                </sl-input>
                ${errorFor('emailAddress')}
            </div>
        `;
    }

    private renderChangePasswordDialog(): TemplateResult | typeof nothing {
        const errorFor = (field: string) => {
            if (!this.passwordValidationErrors[field]) return nothing;
            const msg = this.getPasswordErrorMessage(this.passwordValidationErrors[field] ?? '');
            return html`<sl-alert variant="danger" open class="mt-2"><sl-icon slot="icon" name="exclamation-triangle"></sl-icon> ${msg}</sl-alert>`;
        };

        return html`
            <sl-dialog
                id="change-password"
                label="Change Password"
                style="--width: min(40rem, calc(100vw - 2rem)); --header-spacing:16px; --body-spacing: 16px;"
                @sl-request-close=${(e: any) => {
                    if (e.detail.source === 'overlay') {
                        e.preventDefault();
                    } else {
                        this.closeChangePasswordDialog();
                    }
                }}>
                ${when(this.passwordSuccess,
                    () => html`
                        <sl-alert variant="success" open>
                            <sl-icon slot="icon" name="check-circle"></sl-icon>
                            Password changed successfully.
                        </sl-alert>
                    `,
                    () => nothing)}

                <sl-alert variant="primary" open>
                    <sl-icon slot="icon" name="info-circle"></sl-icon>
                    <div>
                        <strong>Password requirements:</strong> at least 10 characters with:
                        <ul style="margin: 4px 0 0; padding-left: 20px;">
                            <li>at least one uppercase letter <code>A-Z</code></li>
                            <li>at least one lowercase letter <code>a-z</code></li>
                            <li>at least one digit <code>0-9</code></li>
                            <li>at least one punctuation character <code>!"#$%&'()*+,-./:;<=>?@[\]^_\`{|}~</code></li>
                            <li>no spaces</li>
                        </ul>
                    </div>
                </sl-alert>

                <div class="form-group">
                    <sl-input
                        id="oldPassword"
                        label="Current Password"
                        type="password"
                        required
                        class=${classMap({error: this.passwordValidationErrors['oldPassword'] != null})}
                        .value=${this.passwordForm.oldPassword}
                        @sl-input=${(e: InputEvent) => {
                            this.passwordForm.oldPassword = (e.target as HTMLInputElement).value;
                            this.clearPasswordError('oldPassword');
                            this.passwordSuccess = false;
                        }}
                        @sl-clear=${() => this.clearPasswordError('oldPassword')}>
                        <sl-icon name="lock" slot="prefix"></sl-icon>
                    </sl-input>
                    ${errorFor('oldPassword')}
                </div>

                <div class="form-group">
                    <sl-input
                        id="newPassword"
                        label="New Password"
                        type="password"
                        required
                        class=${classMap({error: this.passwordValidationErrors['newPassword'] != null})}
                        .value=${this.passwordForm.newPassword}
                        @sl-input=${(e: InputEvent) => {
                            this.passwordForm.newPassword = (e.target as HTMLInputElement).value;
                            this.clearPasswordError('newPassword');
                            this.passwordSuccess = false;
                        }}
                        @sl-clear=${() => this.clearPasswordError('newPassword')}>
                        <sl-icon name="lock-fill" slot="prefix"></sl-icon>
                    </sl-input>
                    ${errorFor('newPassword')}
                </div>

                <div class="form-group">
                    <sl-input
                        id="newPasswordConfirm"
                        label="Confirm New Password"
                        type="password"
                        required
                        class=${classMap({error: this.passwordValidationErrors['newPasswordConfirm'] != null})}
                        .value=${this.passwordForm.newPasswordConfirm}
                        @sl-input=${(e: InputEvent) => {
                            this.passwordForm.newPasswordConfirm = (e.target as HTMLInputElement).value;
                            this.clearPasswordError('newPasswordConfirm');
                            this.passwordSuccess = false;
                        }}
                        @sl-clear=${() => this.clearPasswordError('newPasswordConfirm')}>
                        <sl-icon name="lock-fill" slot="prefix"></sl-icon>
                    </sl-input>
                    ${errorFor('newPasswordConfirm')}
                </div>

                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${this.closeChangePasswordDialog}>Cancel</sl-button>
                        <div></div>
                        <sl-button
                            variant="success"
                            size="large"
                            ?disabled=${this.changingPassword}
                            @click=${this.changePassword}>
                            ${when(this.changingPassword,
                                () => nothing,
                                () => html`<sl-icon slot="prefix" name="check2"></sl-icon>`)}
                            ${this.changingPassword ? '' : 'Update'}
                        </sl-button>
                        ${when(this.changingPassword,
                            () => html`<sl-spinner style="--sl-spinner-size: 1.5rem;"></sl-spinner>`,
                            () => nothing)}
                    </div>
                </div>
            </sl-dialog>
        `;
    }

    private getPasswordErrorMessage(field: string): string {
        switch (field) {
            case 'alfio.new-password-invalid':
                return 'The new password does not meet the requirements listed above.';
            case 'alfio.new-password-does-not-match':
                return '"New password" and "Confirm new password" do not match.';
            case 'alfio.old-password-invalid':
                return 'The current password is not correct.';
            case 'newPassword':
                return 'The new password does not meet the requirements listed above.';
            case 'newPasswordConfirm':
                return '"New password" and "Confirm new password" do not match.';
            case 'oldPassword':
                return 'The current password is not correct.';
            default:
                return 'An error occurred.';
        }
    }

    private async loadProfile(): Promise<void> {
        this.loading = true;
        try {
            const user = await UsersService.loadCurrentUser();
            this.profileUsername = user.username ?? '';
            this.isAdminProfile = this.profileUsername === 'admin';
            this.profileForm = {
                firstName: user.firstName ?? '',
                lastName: user.lastName ?? '',
                emailAddress: user.emailAddress ?? ''
            };
        } catch {
            dispatchFeedback({type: 'danger', message: 'Failed to load profile.'}, this);
        } finally {
            this.loading = false;
        }
    }

    private resetProfileForm(): void {
        this.profileValidationErrors = {};
        this.loadProfile();
    }

    private clearProfileError(field: keyof ProfileForm): void {
        if (this.profileValidationErrors[field]) {
            this.profileValidationErrors = {...this.profileValidationErrors};
            delete this.profileValidationErrors[field];
        }
    }

    private clearPasswordError(field: keyof PasswordForm): void {
        if (this.passwordValidationErrors[field]) {
            this.passwordValidationErrors = {...this.passwordValidationErrors};
            delete this.passwordValidationErrors[field];
        }
    }

    private openChangePasswordDialog(): void {
        this.passwordSuccess = false;
        this.passwordFormSubmitted = false;
        this.passwordValidationErrors = {};
        this.passwordForm = {oldPassword: '', newPassword: '', newPasswordConfirm: ''};
        this.changePasswordDialog?.show();
    }

    private closeChangePasswordDialog(): void {
        this.passwordSuccess = false;
        this.passwordFormSubmitted = false;
        this.passwordValidationErrors = {};
        this.changingPassword = false;
        this.passwordForm = {oldPassword: '', newPassword: '', newPasswordConfirm: ''};
        this.changePasswordDialog?.hide();
    }

    private async saveProfile(): Promise<void> {
        this.profileValidationErrors = {};

        const clientErrors = this.validateProfileForm();
        if (Object.keys(clientErrors).length > 0) {
            this.profileValidationErrors = clientErrors;
            return;
        }

        this.saving = true;
        try {
            const checkResult = await UsersService.check({
                id: null,
                organizationId: 0,
                role: '',
                firstName: this.profileForm.firstName,
                lastName: this.profileForm.lastName,
                emailAddress: this.profileForm.emailAddress
            });

            if (!checkResult.success) {
                this.profileValidationErrors = this.mapValidationErrors(checkResult.validationErrors);
                return;
            }

            await UsersService.updateCurrentUserContactInfo({
                id: null,
                organizationId: 0,
                role: '',
                firstName: this.profileForm.firstName,
                lastName: this.profileForm.lastName,
                emailAddress: this.profileForm.emailAddress
            });

            this.profileUsername = this.profileUsername;
            dispatchFeedback({type: 'success', message: 'Profile updated successfully.'}, this);
        } catch {
            dispatchFeedback({type: 'danger', message: 'Failed to update profile. Please try again.'}, this);
        } finally {
            this.saving = false;
        }
    }

    private async changePassword(): Promise<void> {
        this.passwordSuccess = false;
        this.passwordValidationErrors = {};
        this.passwordFormSubmitted = true;

        const clientErrors = this.validatePasswordForm();
        if (Object.keys(clientErrors).length > 0) {
            this.passwordValidationErrors = clientErrors;
            return;
        }

        this.changingPassword = true;
        try {
            const result = await UsersService.updateCurrentUserPassword({
                oldPassword: this.passwordForm.oldPassword,
                newPassword: this.passwordForm.newPassword,
                newPasswordConfirm: this.passwordForm.newPasswordConfirm
            });

            if (result.success) {
                this.passwordSuccess = true;
                this.passwordForm = {oldPassword: '', newPassword: '', newPasswordConfirm: ''};
                this.passwordValidationErrors = {};
                dispatchFeedback({type: 'success', message: 'Password changed successfully.'}, this);
            } else {
                this.passwordValidationErrors = this.mapPasswordValidationErrors(result.validationErrors);
            }
        } catch {
            dispatchFeedback({type: 'danger', message: 'Failed to change password. Please try again.'}, this);
        } finally {
            this.changingPassword = false;
        }
    }

    private validateProfileForm(): Record<string, string> {
        const errors: Record<string, string> = {};
        if (!this.profileForm.firstName || this.profileForm.firstName.length < 2) {
            errors.firstName = 'First name must be at least 2 characters.';
        }
        if (!this.profileForm.lastName || this.profileForm.lastName.length < 2) {
            errors.lastName = 'Last name must be at least 2 characters.';
        }
        if (!this.profileForm.emailAddress || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.profileForm.emailAddress)) {
            errors.emailAddress = 'A valid email address is required.';
        }
        return errors;
    }

    private validatePasswordForm(): Record<string, string> {
        const errors: Record<string, string> = {};
        if (!this.passwordForm.oldPassword) {
            errors.oldPassword = 'Current password is required.';
        }
        if (!this.passwordForm.newPassword || this.passwordForm.newPassword.length < 10) {
            errors.newPassword = 'New password must be at least 10 characters.';
        } else if (!PASSWORD_REGEX.test(this.passwordForm.newPassword)) {
            errors.newPassword = 'New password does not meet the requirements.';
        }
        if (this.passwordForm.newPassword !== this.passwordForm.newPasswordConfirm) {
            errors.newPasswordConfirm = 'Passwords do not match.';
        }
        return errors;
    }

    private mapValidationErrors(errors: ErrorDescriptor[]): Record<string, string> {
        return errors.reduce((acc, err) => {
            acc[err.fieldName] = err.code;
            return acc;
        }, {} as Record<string, string>);
    }

    private mapPasswordValidationErrors(errors: ErrorDescriptor[]): Record<string, string> {
        const fieldMap: Record<string, string> = {
            'alfio.old-password-invalid': 'oldPassword',
            'alfio.new-password-invalid': 'newPassword',
            'alfio.new-password-does-not-match': 'newPasswordConfirm',
        };
        return errors.reduce((acc, err) => {
            const field = fieldMap[err.fieldName] ?? err.fieldName;
            acc[field] = err.fieldName;
            return acc;
        }, {} as Record<string, string>);
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-user-profile-edit': UserProfileEdit;
    }
}