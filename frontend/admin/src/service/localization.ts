import { fetchJson } from "./helpers";

export type LocalizationServiceLocale = {
    locale: string,
    value: number,
    language: string,
    displayLanguage: string
};

export class LocalizationService {
    async getEventsSupportedLanguages() {
        const result = await fetchJson<LocalizationServiceLocale[]>(`/admin/api/events-supported-languages`);
        return result;
    }
}
