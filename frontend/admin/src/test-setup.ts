import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { initTranslations, Translations } from './service/i18n';

/**
 * The unit tests see the English texts, as the server sends them (TranslationsApiController): "{0}" becomes "{{0}}"
 * and the doubled apostrophe of MessageFormat a single one
 */
function englishTranslations(): Translations {
    const file = fileURLToPath(new URL('../../../src/main/resources/alfio/i18n/public.properties', import.meta.url));
    const translations: Translations = {};
    for (const line of readFileSync(file, 'utf8').split('\n')) {
        const trimmed = line.trim();
        if (trimmed === '' || trimmed.startsWith('#') || trimmed.startsWith('!')) {
            continue;
        }
        const separator = trimmed.search(/[=:]/);
        if (separator > 0) {
            const text = trimmed.substring(separator + 1).trim();
            translations[trimmed.substring(0, separator).trim()] = text.replace(/\{+(\d+)\}+/g, '{{$1}}').replace(/''/g, "'");
        }
    }
    return translations;
}

await initTranslations('en', englishTranslations());
