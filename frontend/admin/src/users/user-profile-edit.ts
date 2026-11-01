import {customElement, query, state} from "lit/decorators.js";
import {css, html, LitElement, nothing, TemplateResult} from "lit";
import {SlButton, SlDialog} from "@shoelace-style/shoelace";
import {when} from "lit/directives/when.js";
import {TanStackFormController} from "@tanstack/lit-form";
import {badges, base, dialog, form, modernLayout, pageHeader, panelStyles, retroCompat, row, spacing, textColors} from "../styles.ts";
import {UsersService} from "./users-service.ts";
import {dispatchFeedback} from "../model/dom-events.ts";
import {ErrorDescriptor} from "../model/validation.ts";
import {
    BoundField,
    emailAddress,
    focusFirstError,
    FormMountController,
    minLength,
    requiredValue,
    setServerError,
    submitValidated,
    validatedInput,
    ValidatedInputOptions,
    validateWith,
} from "../components/form-validation.ts";

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

    readonly #profileForm = new TanStackFormController(this, {
        defaultValues: { firstName: '', lastName: '', emailAddress: '' } as ProfileForm,
        onSubmit: async ({value}) => {
            await this.saveProfile(value);
        },
    });

    readonly #passwordForm = new TanStackFormController(this, {
        defaultValues: { oldPassword: '', newPassword: '', newPasswordConfirm: '' } as PasswordForm,
        onSubmit: async ({value}) => {
            await this.changePassword(value);
        },
    });

    @state() saving: boolean = false;
    @state() changingPassword: boolean = false;

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
            :host {
                --alfio-section-body-padding: var(--sl-spacing-medium);
            }

            .container {
                max-width: 640px;
                padding: var(--sl-spacing-medium);
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

            .warning-admin {
                margin-top: 0;
                margin-bottom: var(--sl-spacing-medium);
            }
        `
    ];

    constructor() {
        super();
        new FormMountController(this, this.#profileForm.api);
        new FormMountController(this, this.#passwordForm.api);
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
                                            <sl-button id="save-profile" variant="warning" size="large" @click=${this.submitProfile}>
                                                Save
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
        const submitted = this.#profileForm.api.state.submissionAttempts > 0;
        const input = (field: BoundField<string>, options: ValidatedInputOptions) => validatedInput(field, submitted, options);
        return html`
            <div class="row" style="--alfio-row-cols: 2">
                <div class="form-group">
                    ${this.#profileForm.field({ name: 'firstName', validators: validateWith(value => minLength(value, 2)) },
                        field => input(field, { id: 'firstName', label: 'First Name', prefixIcon: 'person' }))}
                </div>
                <div class="form-group">
                    ${this.#profileForm.field({ name: 'lastName', validators: validateWith(value => minLength(value, 2)) },
                        field => input(field, { id: 'lastName', label: 'Last Name', prefixIcon: 'person' }))}
                </div>
            </div>
            <div class="form-group">
                ${this.#profileForm.field({ name: 'emailAddress', validators: validateWith(value => emailAddress(value)) },
                    field => input(field, { id: 'emailAddress', label: 'Email Address', type: 'email', prefixIcon: 'envelope' }))}
            </div>
        `;
    }

    private renderPasswordFields(): TemplateResult {
        const submitted = this.#passwordForm.api.state.submissionAttempts > 0;
        const input = (field: BoundField<string>, options: ValidatedInputOptions) => validatedInput({
            state: field.state,
            handleChange: (value: string) => {
                field.handleChange(value);
                this.passwordSuccess = false;
            },
            handleBlur: () => field.handleBlur(),
        }, submitted, { ...options, type: 'password' });
        const validateNewPassword = (value: unknown) => {
            const missing = requiredValue(value);
            if (missing != null) {
                return missing;
            }
            if (!PASSWORD_REGEX.test(String(value))) {
                return 'The new password does not meet the requirements listed above.';
            }
            return undefined;
        };
        const validateConfirmation = (value: unknown) => {
            if (value !== this.#passwordForm.api.state.values.newPassword) {
                return '"New password" and "Confirm new password" do not match.';
            }
            return undefined;
        };
        return html`
            <div class="form-group">
                ${this.#passwordForm.field({ name: 'oldPassword', validators: validateWith(value => requiredValue(value)) },
                    field => input(field, { id: 'oldPassword', label: 'Current Password', prefixIcon: 'lock' }))}
            </div>
            <div class="form-group">
                ${this.#passwordForm.field({ name: 'newPassword', validators: validateWith(validateNewPassword) },
                    field => input(field, { id: 'newPassword', label: 'New Password', prefixIcon: 'lock-fill' }))}
            </div>
            <div class="form-group">
                ${this.#passwordForm.field({
                    name: 'newPasswordConfirm',
                    // checked again when the new password changes
                    validators: { ...validateWith(validateConfirmation), onChangeListenTo: ['newPassword'] },
                }, field => input(field, { id: 'newPasswordConfirm', label: 'Confirm New Password', prefixIcon: 'lock-fill' }))}
            </div>
        `;
    }

    private renderChangePasswordDialog(): TemplateResult | typeof nothing {
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

                ${this.renderPasswordFields()}

                <div slot="footer">
                    <sl-divider></sl-divider>
                    <div class="row" style="--alfio-row-cols: 3">
                        <sl-button variant="default" size="large" @click=${this.closeChangePasswordDialog}>Cancel</sl-button>
                        <div></div>
                        <sl-button
                            variant="success"
                            size="large"
                            ?disabled=${this.changingPassword}
                            @click=${this.submitPassword}>
                            ${when(this.changingPassword,
                                () => nothing,
                                () => html`<sl-icon slot="prefix" name="check2"></sl-icon>`)}
                            ${when(!this.changingPassword, () => 'Update')}
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
            this.#profileForm.api.reset({
                firstName: user.firstName ?? '',
                lastName: user.lastName ?? '',
                emailAddress: user.emailAddress ?? ''
            });
        } catch {
            dispatchFeedback({type: 'danger', message: 'Failed to load profile.'}, this);
        } finally {
            this.loading = false;
        }
    }

    private resetProfileForm(): void {
        this.loadProfile();
    }

    private openChangePasswordDialog(): void {
        this.passwordSuccess = false;
        this.#passwordForm.api.reset({oldPassword: '', newPassword: '', newPasswordConfirm: ''});
        this.changePasswordDialog?.show();
    }

    private closeChangePasswordDialog(): void {
        this.passwordSuccess = false;
        this.changingPassword = false;
        this.#passwordForm.api.reset({oldPassword: '', newPassword: '', newPasswordConfirm: ''});
        this.changePasswordDialog?.hide();
    }

    private submitProfile = async (): Promise<void> => {
        if (this.saving) {
            return;
        }
        if (!await submitValidated(this.#profileForm.api)) {
            await this.updateComplete;
            focusFirstError(this.renderRoot);
        }
    };

    private submitPassword = async (): Promise<void> => {
        if (this.changingPassword) {
            return;
        }
        this.passwordSuccess = false;
        if (!await submitValidated(this.#passwordForm.api)) {
            await this.updateComplete;
            focusFirstError(this.renderRoot.querySelector('#change-password') ?? this.renderRoot);
        }
    };

    /**
     * Called by the profile form once all the fields are valid
     */
    private async saveProfile(profile: ProfileForm): Promise<void> {
        this.saving = true;
        try {
            const checkResult = await UsersService.check({
                id: null,
                organizationId: 0,
                role: '',
                firstName: profile.firstName,
                lastName: profile.lastName,
                emailAddress: profile.emailAddress
            });

            if (!checkResult.success) {
                this.showServerErrors(this.#profileForm.api, this.mapValidationErrors(checkResult.validationErrors));
                return;
            }

            await UsersService.updateCurrentUserContactInfo({
                id: null,
                organizationId: 0,
                role: '',
                firstName: profile.firstName,
                lastName: profile.lastName,
                emailAddress: profile.emailAddress
            });

            this.profileUsername = this.profileUsername;
            dispatchFeedback({type: 'success', message: 'Profile updated successfully.'}, this);
        } catch {
            dispatchFeedback({type: 'danger', message: 'Failed to update profile. Please try again.'}, this);
        } finally {
            this.saving = false;
        }
    }

    /**
     * Called by the password form once all the fields are valid
     */
    private async changePassword(passwords: PasswordForm): Promise<void> {
        this.changingPassword = true;
        try {
            const result = await UsersService.updateCurrentUserPassword(passwords);

            if (result.success) {
                this.passwordSuccess = true;
                this.#passwordForm.api.reset({oldPassword: '', newPassword: '', newPasswordConfirm: ''});
                dispatchFeedback({type: 'success', message: 'Password changed successfully.'}, this);
            } else {
                const errors = this.mapPasswordValidationErrors(result.validationErrors);
                const messages: Record<string, string> = {};
                Object.entries(errors).forEach(([field, code]) => {
                    messages[field] = this.getPasswordErrorMessage(code);
                });
                this.showServerErrors(this.#passwordForm.api, messages);
            }
        } catch {
            dispatchFeedback({type: 'danger', message: 'Failed to change password. Please try again.'}, this);
        } finally {
            this.changingPassword = false;
        }
    }

    /**
     * Displays the errors returned by the server on their fields, the others as feedback
     */
    private showServerErrors(api: Parameters<typeof setServerError>[0], errors: Record<string, string>): void {
        const fields = Object.keys(api.fieldInfo);
        const others: string[] = [];
        Object.entries(errors).forEach(([field, message]) => {
            if (fields.includes(field)) {
                setServerError(api, field, message);
            } else {
                others.push(message);
            }
        });
        if (others.length > 0) {
            dispatchFeedback({type: 'danger', message: others.join(' ')}, this);
        }
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