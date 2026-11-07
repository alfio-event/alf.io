/**
 * An extension script, as returned by /admin/api/extensions
 */
export interface ExtensionSupport {
    id: number | null;
    // the scope of the extension: "-" (system), "-<organizationId>" or "-<organizationId>-<eventId>"
    path: string;
    name: string;
    hash: string | null;
    enabled: boolean;
    async: boolean;
    script: string | null;
    extensionMetadata: unknown;
}

/**
 * Payload of the create/update endpoints
 */
export interface ExtensionModification {
    path: string;
    name: string;
    script: string;
    enabled: boolean;
}

export type ExtensionLogType = 'SUCCESS' | 'ERROR' | 'INFO' | 'WARNING';

export interface ExtensionLog {
    id: number;
    // the scope where the extension was executed, e.g. "-1-2"
    effectivePath: string;
    path: string;
    name: string;
    description: string;
    type: ExtensionLogType;
    timestamp: string;
}

/**
 * Event data returned by GET /admin/api/events/basic, used by the scope selectors of the editor
 */
export interface ExtensionEvent {
    id: number;
    organizationId: number;
    displayName: string;
}