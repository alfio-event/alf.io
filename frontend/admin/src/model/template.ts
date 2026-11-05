/**
 * The context a template belongs to, as defined by the backend (TemplateResource.Context):
 * it groups the templates in the "customize templates" list.
 */
export type TemplateContext = 'EMAIL' | 'TICKET' | 'BILLING' | 'SUBSCRIPTION';

/**
 * Who receives a template sent as an email, as defined by the backend (TemplateResource.Recipient).
 */
export type TemplateRecipient = 'ATTENDEE' | 'ORGANIZER';

/**
 * A template that can be customized,
 * e.g. {name: "CONFIRMATION_EMAIL", description: "Reservation confirmation", recipient: "ATTENDEE", context: "EMAIL"}.
 */
export interface TemplateDescriptor {
    name: string;
    // human-readable text shown in the admin instead of the enum name
    description: string;
    // null for documents that are not sent as an email, e.g. PDFs
    recipient: TemplateRecipient | null;
    context: TemplateContext;
}

export interface TemplateRecipientInfo {
    label: string;
    // the badge variant: the organizer is the exception among the recipients, so it stands out
    variant: 'primary' | 'warning';
}

/**
 * How a recipient is presented as a label in the templates list. A recipient added by a newer
 * backend falls back to its raw key as the label.
 */
export function templateRecipientInfo(recipient: string): TemplateRecipientInfo {
    if (recipient === 'ORGANIZER') {
        return {label: 'organizer', variant: 'warning'};
    }
    if (recipient === 'ATTENDEE') {
        return {label: 'attendee', variant: 'primary'};
    }
    return {label: recipient.toLowerCase(), variant: 'primary'};
}

export interface TemplateContextInfo {
    label: string;
    icon: string;
}

/**
 * The order of the context panels in the templates list.
 */
export const TEMPLATE_CONTEXT_ORDER: TemplateContext[] = ['EMAIL', 'TICKET', 'BILLING', 'SUBSCRIPTION'];

export const TEMPLATE_CONTEXTS: Record<TemplateContext, TemplateContextInfo> = {
    EMAIL: {label: 'Emails', icon: 'envelope'},
    TICKET: {label: 'Tickets', icon: 'ticket-perforated'},
    BILLING: {label: 'Invoices and receipts', icon: 'receipt'},
    SUBSCRIPTION: {label: 'Subscriptions', icon: 'calendar2-check'},
};

/**
 * How a context is presented in the templates list. A context added by a newer backend
 * falls back to its raw key as the label.
 */
export function templateContextInfo(context: string): TemplateContextInfo {
    const info = (TEMPLATE_CONTEXTS as Record<string, TemplateContextInfo>)[context];
    if (info != null) {
        return info;
    }
    return {label: context, icon: 'file-earmark-text'};
}

export interface TemplateContextGroup {
    context: TemplateContext;
    templates: TemplateDescriptor[];
}

/**
 * Groups the templates by context, in the order of TEMPLATE_CONTEXT_ORDER.
 */
export function groupTemplatesByContext(templates: TemplateDescriptor[]): TemplateContextGroup[] {
    const byContext = new Map<TemplateContext, TemplateDescriptor[]>();
    for (const template of templates) {
        const group = byContext.get(template.context);
        if (group != null) {
            group.push(template);
        } else {
            byContext.set(template.context, [template]);
        }
    }
    const ordered = TEMPLATE_CONTEXT_ORDER.filter((context) => byContext.has(context));
    for (const context of byContext.keys()) {
        if (!ordered.includes(context)) {
            ordered.push(context);
        }
    }
    return ordered.map((context) => ({context, templates: byContext.get(context) ?? []}));
}

/**
 * Where an uploaded template override lives: system-wide, for an organization or for an event.
 * The backend exposes one REST resource per scope (/admin/api/resource, -organization, -event).
 */
export type ResourceScope =
    | { type: 'system' }
    | { type: 'organization'; organizationId: number }
    | { type: 'event'; organizationId: number; eventId: number };

/**
 * Metadata of an uploaded resource, as returned by the /metadata endpoints.
 * eventId tells whether the override was saved for the event itself or inherited from the organization.
 */
export interface UploadedResourceMetadata {
    name: string;
    organizationId: number | null;
    eventId: number | null;
    contentType: string;
    contentSize: number;
    creationTime: number;
    attributes: Record<string, string>;
}

/**
 * An existing override of an overridable template, with its metadata.
 */
export interface TemplateOverride {
    metadata: UploadedResourceMetadata;
    content: string;
}
