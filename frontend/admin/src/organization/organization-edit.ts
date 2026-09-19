import {customElement, query, state} from "lit/decorators.js";
import {css, html, LitElement, nothing, TemplateResult} from "lit";
import {SlDialog, SlRequestCloseEvent} from "@shoelace-style/shoelace";
import {TanStackFormController} from "@tanstack/lit-form";
import {classMap} from "lit/directives/class-map.js";
import {notifyChange, renderIf} from "../service/helpers.ts";
import {cardBgColors, dialog as dialogStyling, form, pageHeader, panelStyles, retroCompat, textColors} from "../styles.ts";
import {Organization, OrganizationModification} from "../model/organization.ts";
import {OrganizationService, ValidationResult} from "../service/organization.ts";
import {dispatchFeedback} from "../model/dom-events.ts";

interface OrganizationForm {
    id: number | null;
    name: string;
    email: string;
    description: string;
    externalId: string | null;
    slug: string | null;
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

static readonly styles = [dialogStyling, panelStyles, pageHeader, form, textColors, cardBgColors, retroCompat, css`
        :host {
            font-family: "Source Sans Pro", "Helvetica Neue", Helvetica, Arial, sans-serif;
            font-size: 16px;
        }

        sl-input, sl-textarea {
            display: block;
            width: 100%;
            font-size: 16px;
        }

        sl-input::part(input) {
            font-size: 16px;
            line-height: 1.42857143;
            height: 34px;
            padding: 6px 12px;
        }

        sl-textarea::part(textarea) {
            font-size: 16px;
            line-height: 1.42857143;
            padding: 6px 12px;
        }

        .slug-error-text {
            display: none;
        }

        sl-input.error .slug-error-text {
            display: inline-block;
        }

        .form-group {
            margin-bottom: 16px;
            margin-top: 16px;
        }

        .slug-loading {
            margin-left: 8px;
        }

 sl-input::part(form-control),
 sl-textarea::part(form-control) {
            margin-bottom: 0;
        }

 sl-input, sl-textarea {
            margin-top: 0;
            margin-bottom: 15px;
        }

        sl-input::part(form-control-label),
 sl-textarea::part(form-control-label) {
            font-size: 16px;
            line-height: 1.42857143;
           margin-top: 0;
            margin-bottom: 8px;
            display: block;
        }

         .page-header {
            padding-bottom: 10px;
            margin: 16px 0;
            border-bottom: 1px solid #eee;
        }

        .page-header h2 {
            font-family: "Source Sans Pro", "Helvetica Neue", Helvetica, Arial, sans-serif;
            font-size: 30px;
            font-weight: 700;
            line-height: 1.1;
            margin-top: 0;
            margin-bottom: 10px;
        }
    `];

    protected render(): TemplateResult {
        return html`
            <sl-dialog
                id="editDialog"
                style="--width: min(50rem, calc(100vw - 2rem)); --header-spacing:16px; --body-spacing: 16px; --sl-font-size-large: 1.5rem;"
                class="dialog"
                label=${this.dialogTitle}
                @sl-request-close=${this.preventAccidentalClose}>

                    ${renderIf(() => this.displayForm, () => this.renderForm())}

                    <div slot="footer">
                        <sl-divider></sl-divider>
                        <div style="display: flex; justify-content: space-between; width: 100%;">
                            <sl-button variant="default" size="large" @click=${() => this.close(false)}>Close</sl-button>
                            <div></div>
                            <sl-button variant="success" type="submit" size="large" form="form">Save</sl-button>
                        </div>
                    </div>

            </sl-dialog>
        `;
    }

    private renderForm(): TemplateResult {
        return html`
            <form id="form" @submit="${async (e: Event) => {e.preventDefault(); e.stopImmediatePropagation(); if (this.slugChecking || !this.slugValid) { return; } this.clearServerErrors(); this.formSubmitted = true; await this.#form.api.handleSubmit();}}">
                <div class="page-header">
                    <h4>Organizer Data</h4>
                </div>

                ${this.renderNameField()}
                ${this.renderEmailField()}
                ${this.renderDescriptionField()}

                ${renderIf(() => this.isAdmin, () => html`
                    <div class="page-header" style="margin-top: 24px;">
                        <h4>Advanced Configuration</h4>
                    </div>
                    ${this.renderSlugField()}
                    ${this.renderExternalIdField()}
                `)}

            </form>
        `;
    }

    private renderNameField() {
        const validate = ({value}: { value: any }) => {
            if (!value || value.length === 0) {
                return 'error.required';
            }
            if (value.length < 2) {
                return 'error.minLength';
            }
            return undefined;
        };
        return this.#form.field({
            name: 'name',
            validators: {
                onChange: validate,
                onSubmit: validate
            }
        }, (field) => html`
            <div class="form-group">
                <sl-input label="Name" class=${classMap({ error: this.hasError(field.state.meta) })} .value=${field.state.value} @sl-change=${(e: InputEvent) => notifyChange(e, field)}></sl-input>
            </div>
        `);
    }

    private renderEmailField() {
        const validate = ({value}: { value: any }) => {
            if (!value || value.length === 0) {
                return 'error.required';
            }
            const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
            if (!emailPattern.test(value)) {
                return 'error.emailFormat';
            }
            return undefined;
        };
        return this.#form.field({
            name: 'email',
            validators: {
                onChange: validate,
                onSubmit: validate
            }
        }, (field) => html`
            <div class="form-group">
                <sl-input label="Contact E-Mail" type="email" class=${classMap({ error: this.hasError(field.state.meta) })} .value=${field.state.value ?? nothing} @sl-change=${(e: InputEvent) => notifyChange(e, field)}></sl-input>
            </div>
        `);
    }

    private renderDescriptionField() {
        const validate = ({value}: { value: any }) => {
            if (!value || value.length === 0) {
                return 'error.required';
            }
            if (value.length < 3) {
                return 'error.minLength';
            }
            return undefined;
        };
        return this.#form.field({
            name: 'description',
            validators: {
                onChange: validate,
                onSubmit: validate
            }
        }, (field) => html`
            <div class="form-group">
                <sl-textarea label="Description" rows="2" class=${classMap({ error: this.hasError(field.state.meta) })} .value=${field.state.value ?? nothing} @sl-change=${(e: InputEvent) => notifyChange(e, field)}></sl-textarea>
            </div>
        `);
    }

    private renderSlugField() {
        const baseUrl = window.BASE_URL;
        const validate = ({value}: { value: any }) => {
            if (!value) {
                return undefined;
            }
            const slugPattern = /^[A-Za-z0-9]+([_-]+[A-Za-z0-9]+)*$/;
            if (!slugPattern.test(value)) {
                return 'error.slugPattern';
            }
            return undefined;
        };
        return this.#form.field({
            name: 'slug',
            validators: {
                onChange: validate,
                onSubmit: validate
            }
        }, (field) => html`
            <div class="form-group">
                <sl-input label="Slug"
                    type="text"
                    pattern="^[A-Za-z0-9]+([_-]+[A-Za-z0-9]+)*$"
                    class=${classMap({ error: !this.slugValid || this.hasError(field.state.meta) })}
                    .value=${field.state.value ?? ''}
                    @sl-change=${(e: InputEvent) => {
                        notifyChange(e, field);
                        this.validateSlug(e, field);
                    }}>
                    <span slot="prefix" class="text-muted">${baseUrl}/o/</span>
                    <sl-icon name="exclamation-triangle" class="slug-error-text" slot="suffix"></sl-icon>
                    ${renderIf(() => this.slugChecking, () => html`<sl-spinner slot="suffix" size="small"></sl-spinner>`)}
                    <span slot="suffix" class="text-muted">/</span>
                </sl-input>
            </div>
        `);
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
        }, (field) => html`
            <sl-input label="External ID (OpenID organization ID)"
                .value=${field.state.value ?? nothing}
                @sl-change=${(e: InputEvent) => notifyChange(e, field)}>
            </sl-input>
        `);
    }

    private clearServerErrors() {
        ['name', 'email', 'description', 'externalId', 'slug'].forEach((field: string) => {
            this.#form.api.setFieldMeta(field as any, (meta: any) => ({
                ...meta,
                errorMap: {
                    ...meta.errorMap,
                    onServer: undefined
                }
            }));
        });
    }

    private hasError(meta: any) {
        return (meta.isTouched || this.formSubmitted) && meta.errors.length > 0;
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
        this.dialogTitle = request.type === 'edit' ? 'Edit Organization' : 'Create new Organization';
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
                result.validationErrors.forEach(error => {
                    this.#form.api.setFieldMeta(error.fieldName as any, (meta: any) => ({
                        ...meta,
                        errorMap: {
                            ...meta.errorMap,
                            onServer: error.code
                        }
                    }));
                });
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
