import {ExtensionEvent, ExtensionLog, ExtensionLogType, ExtensionModification, ExtensionSupport} from '../model/extension.ts';
import {Organization} from '../model/organization.ts';
import {PageAndContent} from '../model/reservation.ts';
import {callDelete, fetchJson, postJson} from './helpers.ts';

/**
 * Result of a create/update call: the backend answers with a pair (success, error message)
 */
export interface ExtensionSaveResult {
    success: boolean;
    message: string | null;
}

interface SerializablePair<T> {
    left: T;
    right: string | null;
}

export class ExtensionService {

    static listExtensions(): Promise<ExtensionSupport[]> {
        return fetchJson('/admin/api/extensions');
    }

    static loadSample(): Promise<ExtensionSupport> {
        return fetchJson('/admin/api/extensions/sample');
    }

    static loadSingle(path: string, name: string): Promise<ExtensionSupport> {
        return fetchJson(`${ExtensionService.baseUrl()}/${encodeURIComponent(path)}/${encodeURIComponent(name)}`);
    }

    static async save(extension: ExtensionModification, previous?: { path: string, name: string }): Promise<ExtensionSaveResult> {
        let url = ExtensionService.baseUrl();
        if (previous != null) {
            url = `${url}/${encodeURIComponent(previous.path)}/${encodeURIComponent(previous.name)}`;
        }
        const response = await postJson(url, extension);
        const pair = await response.json() as SerializablePair<boolean>;
        return { success: pair.left, message: pair.right };
    }

    static async delete(path: string, name: string): Promise<void> {
        // the endpoint answers with an empty body, so we check the status instead of using ensureOk (which parses JSON)
        const response = await callDelete(`${ExtensionService.baseUrl()}/${encodeURIComponent(path)}/${encodeURIComponent(name)}`);
        if (!response.ok) {
            throw new Error((await response.text()) || `unexpected status ${response.status}`);
        }
    }

    /**
     * @param page 1-based page number
     * @param name optional fragment of the extension name
     * @param type optional log type
     */
    static loadLogs(page: number, name: string, type: ExtensionLogType | null): Promise<PageAndContent<ExtensionLog[]>> {
        const params = new URLSearchParams({page: String(page - 1)});
        if (name !== '') {
            params.set('name', name);
        }
        if (type != null) {
            params.set('type', type);
        }
        return fetchJson(`${ExtensionService.baseUrl()}/log?${params}`);
    }

    /**
     * Organizations and events, to translate the path in the list and to fill the scope selectors
     */
    static async loadScopeOptions(): Promise<{ organizations: ReadonlyArray<Organization>, events: ReadonlyArray<ExtensionEvent> }> {
        const [organizations, events] = await Promise.all([
            fetchJson<ReadonlyArray<Organization>>('/admin/api/organizations'),
            fetchJson<ReadonlyArray<ExtensionEvent>>('/admin/api/events'),
        ]);
        return { organizations, events };
    }

    private static baseUrl(): string {
        return '/admin/api/extensions';
    }
}