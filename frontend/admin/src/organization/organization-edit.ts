import {customElement, query, state} from "lit/decorators.js";
import {css, html, LitElement, TemplateResult} from "lit";
import {when} from "lit/directives/when.js";
import {classMap} from "lit/directives/class-map.js";
import {SlDialog, SlRequestCloseEvent, SlTextarea} from "@shoelace-style/shoelace";
import {TanStackFormController} from "@tanstack/lit-form";
import {dialog as dialogStyling, form, modernLayout, retroCompat, row, textColors} from "../styles.ts";
import {Organization, OrganizationModification} from "../model/organization.ts";
import {OrganizationService, ValidationResult} from "../service/organization.ts";
import {dispatchFeedback} from "../model/dom-events.ts";
import {
    emailAddress,
    fieldError,
    focusFirstError,
    FormMountController,
    minLength,
    setServerError,
    submitValidated,
    validatedInput,
    validateWith,
} from "../components/form-validation.ts";

interface OrganizationForm {
    id: number | null;
    name: string;
    email: string;
    description: string;
    externalId: string | null;
    slug: string | null;
}

const SLUG_PATTERN = /^[A-Za-z0-9]+([_-]+[A-Za-z0-9]+)*$/;

function validateSlugFormat(value: unknown): string | undefined {
    if (!value) {
        return undefined;
    }
    if (!SLUG_PATTERN.test(String(value))) {
        return 'Please use only letters and digits, separated by "-" or "_"';
    }
    return undefined;
}

@customElement('alfio-organization-edit')
export class OrganizationEdit extends LitElement {

    @query("sl-dialog#editDialog")
    dialog?: SlDialog;

    @state()
    dialogTitle?: string;

    @state()
    displayForm: boolean = false;

    @state()
    isAdmin: boolean = false;

    @state()
    originalSlug: string | null = null;

    @state()
    slugChecking: boolean = false;

    @state()
    slugValid: boolean = true;

    slugCheckGeneration: number = 0;

    private openGeneration: number = 0;

    @state()
    formSubmitted: boolean = false;

    @state()
    saving: boolean = false;

    readonly #form = new TanStackFormController(this, {
        defaultValues: {
            id: null,
            name: '',
            email: '',
            description: '',
            externalId: null,
            slug: null
        } as OrganizationForm
    });

    constructor() {
        super();
        new FormMountController(this, this.#form.api);
    }

    static readonly styles = [dialogStyling, form, row, textColors, retroCompat, modernLayout, css`
        sl-dialog {
            --alfio-dialog-max-width: 50rem;
        }

        .slug-error-text {
            display: none;
        }

        sl-input.error .slug-error-text {
            display: inline-block;
        }
    `];

    protected render(): TemplateResult {
        return html`
            <sl-dialog
                id="editDialog"
                class="responsive-dialog"
                label=${this.dialogTitle ?? ''}
                @sl-request-close=${this.preventAccidentalClose}>

                    ${when(this.displayForm, () => this.renderForm())}

                    <div slot="footer">
                        <sl-divider></sl-divider>
                        <div class="row" style="--alfio-row-cols: 3">
                            <sl-button variant="default" size="large" @click=${() => this.close(false)}>Cancel</sl-button>
                            <div></div>
                            <sl-button id="save-organization" variant="warning" type="submit" size="large" form="form" ?loading=${this.saving}>Save</sl-button>
                        </div>
                    </div>

            </sl-dialog>
        `;
    }

    private renderForm(): TemplateResult {
        return html`
            <form id="form" class="dialog-form form-stack" @submit=${this.onSubmit}>
                <div class="section-card">
                    <div class="dialog-section-header"><sl-icon name="building"></sl-icon>Organizer data</div>
                    <div class="section-body form-stack">
                        ${this.renderNameField()}
                        ${this.renderEmailField()}
                        ${this.renderDescriptionField()}
                    </div>
                </div>

                ${when(this.isAdmin, () => html`
                    <div class="section-card">
                        <div class="dialog-section-header"><sl-icon name="gear"></sl-icon>Advanced configuration</div>
                        <div class="section-body form-stack">
                            ${this.renderSlugField()}
                            ${this.renderExternalIdField()}
                        </div>
                    </div>
                `)}
            </form>
        `;
    }

    private readonly onSubmit = async (e: Event): Promise<void> => {
        e.preventDefault();
        e.stopImmediatePropagation();
        if (this.slugChecking || !this.slugValid || this.saving) {
            return;
        }
        this.formSubmitted = true;
        this.saving = true;
        try {
            if (!await submitValidated(this.#form.api)) {
                await this.updateComplete;
                focusFirstError(this.renderRoot);
            }
        } finally {
            this.saving = false;
        }
    };

    private renderNameField() {
        return this.#form.field({
            name: 'name',
            validators: validateWith(value => minLength(value, 2))
        }, (field) => validatedInput(field, this.formSubmitted, { id: 'organization-name', label: 'Name' }));
    }

    private renderEmailField() {
        return this.#form.field({
            name: 'email',
            validators: validateWith(value => emailAddress(value))
        }, (field) => validatedInput(field, this.formSubmitted, { id: 'organization-email', label: 'Contact e-mail', type: 'email', prefixIcon: 'envelope' }));
    }

    private renderDescriptionField() {
        return this.#form.field({
            name: 'description',
            validators: validateWith(value => minLength(value, 3))
        }, (field) => {
            const error = fieldError(field.state.meta, this.formSubmitted);
            return html`
                <sl-textarea id="organization-description" label="Description" rows="2" required
                             class=${classMap({ error: error != null })}
                             help-text=${error ?? ''}
                             .value=${field.state.value ?? ''}
                             @sl-input=${(e: Event) => field.handleChange((e.target as SlTextarea).value)}
                             @sl-blur=${() => field.handleBlur()}></sl-textarea>
            `;
        });
    }

    private renderSlugField() {
        const baseUrl = window.BASE_URL;
        return this.#form.field({
            name: 'slug',
            validators: validateWith(value => validateSlugFormat(value))
        }, (field) => {
            let error = fieldError(field.state.meta, this.formSubmitted);
            if (!this.slugValid) {
                error = 'This slug is already in use. Please choose a different one.';
            }
            return html`
                <sl-input id="organization-slug" label="Slug"
                    type="text"
                    pattern="^[A-Za-z0-9]+([_-]+[A-Za-z0-9]+)*$"
                    class=${classMap({ error: error != null })}
                    help-text=${error ?? ''}
                    .value=${field.state.value ?? ''}
                    @sl-input=${(e: Event) => field.handleChange((e.target as HTMLInputElement).value)}
                    @sl-change=${(e: InputEvent) => this.validateSlug(e, field)}>
                    <span slot="prefix" class="text-muted">${baseUrl}/o/</span>
                    <sl-icon name="exclamation-triangle" class="slug-error-text" slot="suffix"></sl-icon>
                    ${when(this.slugChecking, () => html`<sl-spinner slot="suffix"></sl-spinner>`)}
                    <span slot="suffix" class="text-muted">/</span>
                </sl-input>
            `;
        });
    }

    private async validateSlug(e: InputEvent, field: { handleChange: (m: any) => void; handleBlur: () => void }) {
        field.handleBlur();
        const slug = (e.currentTarget as HTMLInputElement).value;
        if (slug && slug !== this.originalSlug) {
            const gen = ++this.slugCheckGeneration;
            this.slugChecking = true;
            try {
                const result: ValidationResult = await OrganizationService.checkSlug(this.buildOrganizationModification());
                if (gen !== this.slugCheckGeneration) {
                    return;
                }
                this.slugValid = result.success;
                if (!result.success) {
                    dispatchFeedback({
                        type: "danger",
                        message: "This slug is already in use. Please choose a different one."
                    }, this);
                }
            } catch {
                if (gen === this.slugCheckGeneration) {
                    this.slugValid = true;
                }
            } finally {
                if (gen === this.slugCheckGeneration) {
                    this.slugChecking = false;
                }
            }
        } else {
            this.slugCheckGeneration++;
            this.slugChecking = false;
            this.slugValid = true;
        }
    }

    private renderExternalIdField() {
        return this.#form.field({
            name: 'externalId',
        }, (field) => validatedInput(field, this.formSubmitted, { id: 'organization-external-id', label: 'External ID (OpenID organization ID)', optional: true }));
    }

    public async hide(): Promise<void> {
        this.openGeneration++;
        this.displayForm = false;
        if (this.dialog?.open) {
            await this.dialog.hide();
        }
    }

    private async close(success: boolean): Promise<void> {
        if (this.dialog != null) {
            await this.dialog.hide();
        }
        this.displayForm = false;
        this.dispatchEvent(new CustomEvent('alfio-dialog-closed', { detail: { success }, bubbles: true, composed: true }));
    }

    private preventAccidentalClose(e: SlRequestCloseEvent): void {
        if (e.detail.source === 'overlay') {
            e.preventDefault();
        } else {
            this.displayForm = false;
            this.dispatchEvent(new CustomEvent('alfio-dialog-closed', { detail: { success: false }, bubbles: true, composed: true }));
        }
    }

    public async open(request: { type: 'new' | 'edit', organizationId?: number }): Promise<void> {
        const myGeneration = ++this.openGeneration;
        if (request.type === 'edit') {
            this.dialogTitle = 'Edit organization';
        } else {
            this.dialogTitle = 'New organization';
        }
        this.isAdmin = window.IS_ADMIN;
        this.slugCheckGeneration++;
        this.slugChecking = false;
        this.slugValid = true;
        this.formSubmitted = false;

        let org: Organization | null = null;
        if (request.organizationId != null) {
            org = await OrganizationService.load(request.organizationId);
        }

        if (myGeneration !== this.openGeneration) {
            return;
        }

        this.originalSlug = org?.slug ?? null;

        this.#form.api.update({
            defaultValues: this.buildDefaultValues(org),
            onSubmit: async (state) => {
                await this.save(state.value);
            }
        });

        this.displayForm = true;
        await this.updateComplete;
        if (myGeneration !== this.openGeneration) {
            return;
        }
        await this.dialog?.show();

        setTimeout(() => {
            const input = this.renderRoot.querySelector('sl-input') as any;
            if (input && typeof input.focus === 'function') {
                input.focus();
            }
        }, 0);
    }

    private buildDefaultValues(org: Organization | null): OrganizationForm {
        if (org != null) {
            return {
                id: org.id,
                name: org.name,
                email: org.email,
                description: org.description,
                externalId: org.externalId,
                slug: org.slug
            };
        }
        return {
            id: null,
            name: '',
            email: '',
            description: '',
            externalId: null,
            slug: null
        };
    }

    private buildOrganizationModification(): OrganizationModification {
        const values = this.#form.api.state.values;
        return {
            id: values.id,
            name: values.name,
            email: values.email,
            description: values.description,
            externalId: values.externalId,
            slug: values.slug
        };
    }

    private async save(form: OrganizationForm) {
        if (!this.slugValid || this.slugChecking) {
            return;
        }

        const orgModification: OrganizationModification = {
            id: form.id,
            name: form.name,
            email: form.email,
            description: form.description,
            externalId: form.externalId,
            slug: form.slug
        };

        try {
            const result: ValidationResult = await OrganizationService.check(orgModification);
            if (!result.success) {
                result.validationErrors.forEach(error => setServerError(this.#form.api, error.fieldName, error.code));
                dispatchFeedback({
                    type: "danger",
                    message: "Please correct the errors in the form"
                }, this);
                return;
            }

            let response: Response;
            if (orgModification.id == null) {
                response = await OrganizationService.create(orgModification);
            } else {
                response = await OrganizationService.update(orgModification);
            }

            if (response.ok) {
                await this.close(true);
            } else {
                dispatchFeedback({
                    type: "danger",
                    message: "Unexpected error. Please retry."
                }, this);
            }
        } catch {
            dispatchFeedback({
                type: "danger",
                message: "Unexpected error. Please retry."
            }, this);
        }
    }
}

declare global {
    interface HTMLElementTagNameMap {
        'alfio-organization-edit': OrganizationEdit
    }
}
