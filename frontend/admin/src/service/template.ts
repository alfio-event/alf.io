import {callDelete, fetchJson, postJson, responseErrorMessage} from './helpers.ts';
import {ContentLanguage} from '../model/purchase-context.ts';
import {ResourceScope, TemplateDescriptor, TemplateOverride, UploadedResourceMetadata} from '../model/template.ts';

/**
 * API of the overridable templates ("customize templates") and of the uploaded resources that override them.
 */
export class TemplateService {

    /**
     * The templates that can be customized, with their context,
     * e.g. [{name: "CONFIRMATION_EMAIL", context: "EMAIL"}, ...]
     */
    static listTemplates(): Promise<TemplateDescriptor[]> {
        return fetchJson('/admin/api/overridable-template');
    }

    /**
     * The default content of a template for a locale, as text
     */
    static templateBody(name: string, locale: string): Promise<string> {
        return fetchText(`/admin/api/overridable-template/${encodeURIComponent(name)}/${encodeURIComponent(locale)}`);
    }

    /**
     * All the languages, for system and organization templates
     */
    static allLanguages(): Promise<ContentLanguage[]> {
        return fetchJson('/admin/api/events-all-languages');
    }

    /**
     * The languages selected for an event
     */
    static eventLanguages(eventName: string): Promise<ContentLanguage[]> {
        return fetchJson(`/admin/api/events/${encodeURIComponent(eventName)}/languages`);
    }

    /**
     * The name of the file an override is saved as, e.g. "CONFIRMATION_EMAIL_en.ms"
     */
    static fileName(resourceName: string, locale: string): string {
        return `${resourceName}_${locale}.ms`;
    }

    /**
     * The override of a template in the given scope, or null when only the default template exists.
     * For events, the organization-level override is used as a fallback.
     */
    static async loadOverride(scope: ResourceScope, fileName: string): Promise<TemplateOverride | null> {
        if (scope.type === 'event') {
            const organizationScope: ResourceScope = {type: 'organization', organizationId: scope.organizationId};
            const metadata = await this.metadata(this.baseUrl(scope), fileName)
                ?? await this.metadata(this.baseUrl(organizationScope), fileName);
            if (metadata == null) {
                return null;
            }
            const content = await this.resourceText(this.baseUrl(scope), fileName)
                ?? await this.resourceText(this.baseUrl(organizationScope), fileName);
            return {metadata, content: content ?? ''};
        }
        const baseUrl = this.baseUrl(scope);
        const metadata = await this.metadata(baseUrl, fileName);
        if (metadata == null) {
            return null;
        }
        const content = await this.resourceText(baseUrl, fileName);
        return {metadata, content: content ?? ''};
    }

    static save(scope: ResourceScope, fileName: string, content: string): Promise<Response> {
        return postJson(this.baseUrl(scope), {fileAsString: content, name: fileName, type: 'text/plain'});
    }

    static remove(scope: ResourceScope, fileName: string): Promise<Response> {
        return callDelete(`${this.baseUrl(scope)}/${encodeURIComponent(fileName)}`);
    }

    /**
     * Renders the given template content with a sample model.
     * The response is either the rendered text (text/plain) or a binary document, e.g. a PDF, to download.
     */
    static preview(name: string, locale: string, scope: Exclude<ResourceScope, { type: 'system' }>, fileName: string, content: string): Promise<Response> {
        const params = new URLSearchParams({organizationId: String(scope.organizationId)});
        if (scope.type === 'event') {
            params.set('eventId', String(scope.eventId));
        }
        return postJson(`/admin/api/overridable-template/${encodeURIComponent(name)}/${encodeURIComponent(locale)}/preview?${params}`, {
            fileAsString: content,
            name: fileName,
            type: 'text/plain',
        });
    }

    private static baseUrl(scope: ResourceScope): string {
        if (scope.type === 'system') {
            return '/admin/api/resource';
        }
        if (scope.type === 'organization') {
            return `/admin/api/resource-organization/${scope.organizationId}`;
        }
        return `/admin/api/resource-event/${scope.organizationId}/${scope.eventId}`;
    }

    private static async metadata(baseUrl: string, fileName: string): Promise<UploadedResourceMetadata | null> {
        const res = await fetch(`${baseUrl}/${encodeURIComponent(fileName)}/metadata`, {credentials: 'include'});
        if (res.status === 404) {
            return null;
        }
        if (!res.ok) {
            throw new Error(await responseErrorMessage(res, `unexpected status ${res.status}`));
        }
        return res.json();
    }

    private static async resourceText(baseUrl: string, fileName: string): Promise<string | null> {
        const res = await fetch(`${baseUrl}/${encodeURIComponent(fileName)}`, {credentials: 'include'});
        if (res.status === 404) {
            return null;
        }
        if (!res.ok) {
            throw new Error(await responseErrorMessage(res, `unexpected status ${res.status}`));
        }
        return res.text();
    }
}

async function fetchText(url: string): Promise<string> {
    const res = await fetch(url, {credentials: 'include'});
    if (!res.ok) {
        throw new Error(await responseErrorMessage(res, `unexpected status ${res.status}`));
    }
    return res.text();
}
