import { callDelete, postJson, putJson, responseErrorMessage, adminFetch } from './helpers.ts';
import {
    ConfigurationModification,
    EventBasicInfo,
    ExtensionSettingModification,
    ExtensionSettingsById,
    InheritedSetting,
    PlatformModeStatus,
    SettingsByCategory,
    SettingsLevel,
    TranslationsOverride,
} from '../model/configuration.ts';
import { t } from './i18n';

/**
 * The settings of the system, of an organization or of an event (the "Configuration" pages), and the tools
 * shown with them: translations, invoice regeneration, ticket generation for subscribers
 */
export class SettingsService {

    /**
     * The settings that can be set at a level, with the value set at that level (id -1 and value null if none),
     * by setting category. Empty if the user can't edit the settings of the level
     */
    /**
     * What applies to the settings that the level doesn't set, by key. Empty when the user may not know it
     * (e.g. the settings of the system, for an organization owner)
     */
    static async loadInheritedValues(level: SettingsLevel): Promise<Record<string, InheritedSetting>> {
        let url = '/admin/api/configuration/inherited-values';
        if (level.type === 'ORGANIZATION') {
            url = `/admin/api/configuration/organizations/${level.organizationId}/inherited-values`;
        } else if (level.type === 'EVENT') {
            url = `/admin/api/configuration/events/${level.eventId}/inherited-values`;
        }
        const response = await adminFetch(url);
        if (response.status === 403) {
            return {};
        }
        return SettingsService.json(response, t('admin.settings.error.load'));
    }

    static async loadSettings(level: SettingsLevel): Promise<SettingsByCategory> {
        let url = '/admin/api/configuration/load';
        if (level.type === 'ORGANIZATION') {
            url = `/admin/api/configuration/organizations/${level.organizationId}/load`;
        } else if (level.type === 'EVENT') {
            url = `/admin/api/configuration/events/${level.eventId}/load`;
        }
        const response = await adminFetch(url);
        if (response.status === 403) {
            return {};
        }
        return SettingsService.json(response, t('admin.settings.error.load'));
    }

    /**
     * Saves the given values at a level. A boolean setting without value is removed from the level
     */
    static async saveSettings(level: SettingsLevel, settings: Record<string, ConfigurationModification[]>): Promise<void> {
        let url = '/admin/api/configuration/update-bulk';
        if (level.type === 'ORGANIZATION') {
            url = `/admin/api/configuration/organizations/${level.organizationId}/update`;
        } else if (level.type === 'EVENT') {
            url = `/admin/api/configuration/organizations/${level.organizationId}/events/${level.eventId}/update`;
        }
        await SettingsService.ensureOk(postJson(url, settings), t('admin.settings.error.save'));
    }

    /**
     * Removes a setting from a level: the value of the level above applies again
     */
    static async deleteSetting(level: SettingsLevel, key: string): Promise<void> {
        let url = `/admin/api/configuration/key/${encodeURIComponent(key)}`;
        if (level.type === 'ORGANIZATION') {
            url = `/admin/api/configuration/organization/${level.organizationId}/key/${encodeURIComponent(key)}`;
        } else if (level.type === 'EVENT') {
            url = `/admin/api/configuration/event/${level.eventId}/key/${encodeURIComponent(key)}`;
        }
        await SettingsService.ensureOk(callDelete(url), t('admin.settings.error.remove'));
    }

    static async loadExtensionSettings(level: SettingsLevel): Promise<ExtensionSettingsById> {
        const response = await adminFetch(SettingsService.extensionSettingsUrl(level));
        if (response.status === 403) {
            return {};
        }
        return SettingsService.json(response, t('admin.settings.error.load-extensions'));
    }

    static async saveExtensionSettings(level: SettingsLevel, settings: ExtensionSettingModification[]): Promise<void> {
        await SettingsService.ensureOk(postJson(`${SettingsService.extensionSettingsUrl(level)}/bulk-update`, settings),
            t('admin.settings.error.save-extensions'));
    }

    static async deleteExtensionSetting(level: SettingsLevel, id: number): Promise<void> {
        await SettingsService.ensureOk(callDelete(`${SettingsService.extensionSettingsUrl(level)}/${id}`),
            t('admin.settings.error.remove-extension'));
    }

    /**
     * The countries of the European Union, by country code, for the VAT settings
     */
    static async loadEuCountries(): Promise<{ code: string; name: string }[]> {
        const response = await adminFetch('/admin/api/configuration/eu-countries');
        // each country is serialized as {"<code>": "<name>"}
        const countries = await SettingsService.json<Record<string, string>[]>(response, t('admin.load-error.countries'));
        return countries.flatMap((country) => Object.entries(country).map(([code, name]) => ({code, name})));
    }

    static async loadPlatformModeStatus(organizationId: number): Promise<PlatformModeStatus> {
        const response = await adminFetch(`/admin/api/configuration/platform-mode/status/${organizationId}`);
        return SettingsService.json(response, t('admin.settings.error.load-payment-providers'));
    }

    /**
     * All the events the user can access
     */
    static async loadEvents(): Promise<EventBasicInfo[]> {
        const response = await adminFetch('/admin/api/events/basic');
        return SettingsService.json(response, t('admin.load-error.events'));
    }

    /**
     * Schedules the generation of the tickets of the subscribers, for the events of the level
     */
    static async generateTicketsForSubscribers(level: SettingsLevel): Promise<void> {
        const params = new URLSearchParams();
        if (level.type !== 'SYSTEM') {
            params.set('organizationId', String(level.organizationId));
        }
        if (level.type === 'EVENT') {
            params.set('eventId', String(level.eventId));
        }
        await SettingsService.ensureOk(putJson(`/admin/api/configuration/generate-tickets-for-subscriptions?${params}`, {}),
            t('admin.settings.error.generate-tickets'));
    }

    // ---------- translations ----------

    /**
     * The translations customized at a level
     */
    static async loadTranslationsOverride(level: SettingsLevel): Promise<TranslationsOverride> {
        let url = '/admin/api/configuration/global-translations-override';
        if (level.type === 'ORGANIZATION') {
            url = `/admin/api/configuration/organizations/${level.organizationId}/translations-override`;
        } else if (level.type === 'EVENT') {
            url = `/admin/api/configuration/events/${level.eventId}/translations-override`;
        }
        const response = await adminFetch(url);
        return SettingsService.json(response, t('admin.settings.error.load-translations'));
    }

    static async saveTranslationsOverride(level: SettingsLevel, translations: TranslationsOverride): Promise<void> {
        await SettingsService.saveSettings(level, {
            TRANSLATIONS: [{id: -1, key: 'TRANSLATION_OVERRIDE', value: JSON.stringify(translations)}],
        });
    }

    /**
     * The default translations of a language, without the customizations
     */
    static async loadBundle(locale: string): Promise<Record<string, string>> {
        const response = await adminFetch(`/api/v2/public/i18n/bundle/${encodeURIComponent(locale)}?withSystemOverride=false`);
        return SettingsService.json(response, t('admin.settings.error.load-translations'));
    }

    // ---------- invoices ----------

    /**
     * The date of the first invoice of an event (now, if there is none)
     */
    static async loadFirstInvoiceDate(eventId: number): Promise<string> {
        const response = await adminFetch(`/admin/api/configuration/event/${eventId}/invoice-first-date`);
        return SettingsService.json(response, t('admin.settings.error.load-first-invoice-date'));
    }

    /**
     * The ids of the invoices of an event generated between two instants (epoch milliseconds)
     */
    static async findMatchingInvoices(eventId: number, from: number, to: number): Promise<number[]> {
        const response = await adminFetch(`/admin/api/configuration/event/${eventId}/matching-invoices?from=${from}&to=${to}`);
        return SettingsService.json(response, t('admin.settings.error.search-invoices'));
    }

    /**
     * Schedules the regeneration of the given invoices
     *
     * @return false if the request couldn't be scheduled
     */
    static async regenerateInvoices(eventId: number, invoiceIds: number[]): Promise<boolean> {
        const response = await postJson(`/admin/api/configuration/event/${eventId}/regenerate-invoices`, invoiceIds);
        return SettingsService.json(response, t('admin.settings.error.regenerate-invoices'));
    }

    // ---------- helpers ----------

    private static extensionSettingsUrl(level: SettingsLevel): string {
        if (level.type === 'ORGANIZATION') {
            return `/admin/api/extensions/setting/organization/${level.organizationId}`;
        }
        if (level.type === 'EVENT') {
            return `/admin/api/extensions/setting/organization/${level.organizationId}/event/${encodeURIComponent(level.eventName)}`;
        }
        return '/admin/api/extensions/setting/system';
    }

    private static async json<T>(response: Response, fallback: string): Promise<T> {
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, fallback));
        }
        return response.json();
    }

    private static async ensureOk(request: Promise<Response>, fallback: string): Promise<void> {
        const response = await request;
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, fallback));
        }
    }
}
