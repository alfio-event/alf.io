import { callDelete, fetchJson, postJson, responseErrorMessage, adminFetch } from './helpers';
import {ConfigurationModification, SettingsByCategory} from "../model/configuration.ts";

export class ConfigurationService {

    /**
     * The settings that can be set for a ticket category, with their current value at the most specific level
     */
    static loadCategorySettings(eventId: number, categoryId: number): Promise<SettingsByCategory> {
        return fetchJson(`/admin/api/configuration/events/${eventId}/categories/${categoryId}/load`);
    }

    /**
     * Saves settings at the level of a ticket category, grouped by setting category
     */
    static async updateCategorySettings(eventId: number, categoryId: number,
                                        settings: Record<string, ConfigurationModification[]>): Promise<void> {
        const response = await postJson(`/admin/api/configuration/events/${eventId}/categories/${categoryId}/update`, settings);
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, 'Failed to save the options'));
        }
    }

    /**
     * Removes a setting from a ticket category: the value set for the event (or above) applies again
     */
    static async deleteCategorySetting(eventId: number, categoryId: number, key: string): Promise<void> {
        const response = await callDelete(`/admin/api/configuration/event/${eventId}/category/${categoryId}/key/${key}`);
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, 'Failed to reset the option'));
        }
    }

    static update(kv: { key: string, value: string }): Promise<Response> {
        return postJson('/admin/api/configuration/update', kv);
    }

    /**
     * Whether reservations should be identified by their invoice number. Falls back to false if the setting cannot be loaded
     */
    static async useInvoiceNumberAsId(eventName: string): Promise<boolean> {
        try {
            return (await ConfigurationService.loadSingleConfig(eventName, 'USE_INVOICE_NUMBER_AS_ID')) === 'true';
        } catch {
            return false;
        }
    }

    /**
     * The badge colors of the ticket categories of an event (the color shown by the check-in app on a successful
     * scan), by category id. Categories without a color are missing
     */
    static async loadBadgeColors(eventName: string): Promise<Map<number, string>> {
        const response = await adminFetch(`/admin/api/configuration/events/${encodeURIComponent(eventName)}/single/CHECK_IN_COLOR_CONFIGURATION`, {
            credentials: 'include',
        });
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, 'Failed to load the badge colors'));
        }
        const colors = new Map<number, string>();
        const text = await response.text();
        if (response.status === 204 || text.trim() === '') {
            return colors;
        }
        // the value is returned as a JSON string, which contains the JSON of the configuration
        let configuration: unknown = JSON.parse(text);
        if (typeof configuration === 'string') {
            configuration = JSON.parse(configuration);
        }
        for (const entry of (configuration as CheckInColorConfiguration).configurations ?? []) {
            for (const categoryId of entry.categories ?? []) {
                colors.set(categoryId, entry.colorName);
            }
        }
        return colors;
    }

    /**
     * The badge color of a ticket category, or '' when none is set
     */
    static async loadBadgeColor(eventName: string, categoryId: number): Promise<string> {
        const colors = await ConfigurationService.loadBadgeColors(eventName);
        return colors.get(categoryId) ?? '';
    }

    static loadSingleConfig(publicIdentifier: string, key: string): Promise<string> {
        return fetchJson(`/admin/api/configuration/events/${publicIdentifier}/single/${key}`);
    }

    static loadSingleConfigForOrganization(organizationId: number, key: string): Promise<string> {
        return fetchJson(`/admin/api/configuration/organizations/${organizationId}/single/${key}`);
    }

    static loadInstanceSettings(): Promise<InstanceSettings> {
        return fetchJson('/admin/api/configuration/instance-settings');
    }
}

/**
 * CHECK_IN_COLOR_CONFIGURATION: the colors assigned to the ticket categories of an event
 */
interface CheckInColorConfiguration {
    defaultColorName?: string;
    configurations?: Array<{ colorName: string; categories?: number[] }>;
}

export interface InstanceSettings {
    descriptionMaxLength: number;
    baseUrl: string;
}
