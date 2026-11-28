import i18next, { type TOptions } from 'i18next';

/**
 * The texts of the admin UI, in the language chosen by the server (see MvcConfiguration#localeResolver).
 *
 * The translations come from src/main/resources/alfio/i18n/public*.properties, resolved by the server: a key missing
 * in a language already has its English text, and the placeholders are converted from `{0}` to `{{0}}`.
 * The shell loads them once, before the first render (main.ts): `t()` can be called anywhere, also at module level.
 */

export type Translations = Record<string, string>;

/**
 * Positional values of the placeholders of a text, `{0}`, `{1}`, … in the properties files
 */
export type TranslationValue = string | number;

let formattingLocale = 'en-US';

/**
 * Downloads the translations. The page preloads the same URL (admin-index.ms), so this usually takes no time.
 */
export async function loadTranslations(url: string): Promise<Translations> {
    // same mode and credentials as the <link rel="preload" crossorigin>, otherwise the browser downloads it again
    const response = await fetch(url, { credentials: 'same-origin' });
    if (!response.ok) {
        throw new Error(`Cannot load the translations: status ${response.status}`);
    }
    return response.json();
}

export async function initTranslations(language: string, translations: Translations): Promise<void> {
    await i18next.init({
        lng: language,
        // the server already filled the missing keys with the English texts
        fallbackLng: false,
        resources: { [language]: { translation: translations } },
        // the keys contain dots and colons ("admin.event.detail.title"): they are flat, not nested
        keySeparator: false,
        nsSeparator: false,
        interpolation: {
            // Lit escapes what it renders
            escapeValue: false,
        },
        initAsync: false,
    });
    formattingLocale = validLocale(translations['locale'], language);
}

/**
 * The text of a key, with the values of its placeholders: `t('admin.event.sold', 3, 10)` for "{0} of {1} sold".
 * For a text with plurals, pass the count as an option: `t('admin.event.tickets', { count: 3 })`, which picks
 * `admin.event.tickets_one`, `admin.event.tickets_other`, … according to the rules of the language. In those texts
 * `{0}` is the count (`admin.event.tickets_other={0} tickets`): the server reads every text with Java's
 * MessageFormat, which doesn't accept i18next's `{{count}}`.
 *
 * Keys are literal strings, so that the test can check them (i18n-keys.test.ts). Choose among several keys with
 * an explicit map of {@link i18nKey} values, never by building the key (`'admin.status.' + status`).
 */
export function t(key: string, ...values: TranslationValue[]): string;
export function t(key: string, options: TOptions): string;
export function t(key: string, ...args: Array<TranslationValue | TOptions>): string {
    const first = args[0];
    if (first != null && typeof first === 'object') {
        return i18next.t(key, withCountPlaceholder(first));
    }
    const options: Record<string, TranslationValue> = {};
    args.forEach((value, index) => {
        options[String(index)] = value as TranslationValue;
    });
    return i18next.t(key, options);
}

/**
 * The count of a text with plurals is its first placeholder, `{0}`, formatted for the language ("1,234")
 */
function withCountPlaceholder(options: TOptions): TOptions {
    const count = options.count;
    if (typeof count !== 'number' || options['0'] !== undefined) {
        return options;
    }
    return { ...options, '0': formatNumber(count) };
}

/**
 * A text with markup in its placeholders, for Lit: `tHtml('admin.banner.powered-by', html\`<a href="…">alf.io</a>\`)`
 * for "Powered by {0}". The translation is split at its placeholders and the values go in between, so a translation
 * never contains HTML: a sentence stays whole for the translators, without unsafeHTML.
 */
export function tHtml(key: string, ...values: unknown[]): unknown[] {
    let text = key;
    const resource = i18next.getResource(i18next.language, 'translation', key);
    if (typeof resource === 'string') {
        text = resource;
    }
    return splitAtPlaceholders(text, values);
}

/**
 * {@link tHtml} for a text with plurals: `tHtmlPlural('admin.pending-payments.count', 3, html\`<strong>3</strong>\`)`
 * picks `admin.pending-payments.count_other` ("{0} payments pending"), as `t()` does with `{ count }`
 */
export function tHtmlPlural(key: string, count: number, ...values: unknown[]): unknown[] {
    const language = i18next.language;
    const form = new Intl.PluralRules(language).select(count);
    let text = key;
    for (const candidate of [`${key}_${form}`, `${key}_other`]) {
        const resource = i18next.getResource(language, 'translation', candidate);
        if (typeof resource === 'string') {
            text = resource;
            break;
        }
    }
    return splitAtPlaceholders(text, values);
}

function splitAtPlaceholders(text: string, values: unknown[]): unknown[] {
    const parts: unknown[] = [];
    let last = 0;
    for (const match of text.matchAll(/\{\{(\d+)\}\}/g)) {
        parts.push(text.substring(last, match.index));
        parts.push(values[Number(match[1])]);
        last = (match.index ?? 0) + match[0].length;
    }
    parts.push(text.substring(last));
    return parts;
}

/**
 * Marks a translation key kept in a constant or a map, so that the test checks it exists (i18n-keys.test.ts):
 * `const STATUS_LABELS = { ACTIVE: i18nKey('admin.status.active') }`, then `t(STATUS_LABELS[status])`
 */
export function i18nKey(key: string): string {
    return key;
}

/**
 * Whether a key has a text, e.g. for the code of an error returned by the server
 */
export function hasTranslation(key: string): boolean {
    return i18next.exists(key);
}

/**
 * The text of an error code sent by the server (e.g. "error.shortname"), or the fallback if the code has no text
 */
export function translateErrorCode(code: string | null | undefined, fallback: string, values: TranslationValue[] = []): string {
    if (code != null && code !== '' && hasTranslation(code)) {
        return t(code, ...values);
    }
    return fallback;
}

/**
 * The language of the admin UI ("de")
 */
export function currentLanguage(): string {
    return i18next.language ?? 'en';
}

/**
 * The locale for formatting dates and numbers ("de-DE"), the "locale" key of the translations
 */
export function currentFormattingLocale(): string {
    return formattingLocale;
}

/**
 * A number formatted for the language of the admin UI: "1,234.5" in English, "1.234,5" in German
 */
export function formatNumber(value: number, options?: Intl.NumberFormatOptions): string {
    return new Intl.NumberFormat(formattingLocale, options).format(value);
}

/**
 * The name of a language in that language, capitalized as a menu entry: "Deutsch", "Français", "Italiano"
 */
export function languageName(code: string): string {
    let name = code;
    try {
        name = new Intl.DisplayNames([code], { type: 'language' }).of(code) ?? code;
    } catch {
        // an unknown code: show it as it is
    }
    return name.charAt(0).toLocaleUpperCase(code) + name.slice(1);
}

/**
 * The value if it is a locale the browser understands, otherwise the language: a wrong value in a translation file
 * must not break the formatting of every date and number
 */
export function validLocale(value: string | undefined, language: string): string {
    if (value != null) {
        try {
            const canonical = Intl.getCanonicalLocales(value.trim());
            if (canonical.length > 0) {
                return canonical[0];
            }
        } catch {
            // e.g. "de_DE": not a BCP 47 tag
        }
    }
    return language;
}
