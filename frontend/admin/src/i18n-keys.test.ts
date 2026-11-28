import { describe, expect, it } from 'vitest';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Every text of the admin UI comes from the English translation file: a key used with t() and missing there would
 * show the key itself to the user. See service/i18n.ts.
 */

const SOURCES = fileURLToPath(new URL('.', import.meta.url));
const ENGLISH_TRANSLATIONS = fileURLToPath(new URL('../../../src/main/resources/alfio/i18n/public.properties', import.meta.url));

// t('key') or i18nKey('key'), with ', " or `: the key is a literal
const LITERAL_KEY = /(?<![\w.$])(?:t|tHtml|tHtmlPlural|i18nKey)\(\s*(['"`])([^'"`$]+)\1/g;
// a key built from pieces: t(`admin.${x}`), t('admin.' + x), and any i18nKey() that is not a literal
const BUILT_KEY = [
    /(?<![\w.$])(?:t|tHtml|tHtmlPlural|i18nKey)\(\s*`[^`]*\$\{/,
    /(?<![\w.$])(?:t|tHtml|tHtmlPlural|i18nKey)\(\s*(['"])[^'"]*\1\s*\+/,
    /(?<![\w.$])(?:t|tHtml|tHtmlPlural|i18nKey)\(\s*[\w.$\[\]]+\s*\+/,
    /(?<![\w.$])i18nKey\(\s*(?!['"`])/,
];
// t('key', { count: … }) or tHtmlPlural('key', …): a text with plurals, stored as key_one, key_other…
const PLURAL_CALL = /(?<![\w.$])(?:t\(\s*(['"`])([^'"`$]+)\1\s*,\s*\{[^}]*\bcount\b|tHtmlPlural\(\s*(['"`])([^'"`$]+)\3)/g;

function sourceFiles(directory: string): string[] {
    return readdirSync(directory).flatMap(name => {
        const path = join(directory, name);
        if (statSync(path).isDirectory()) {
            return sourceFiles(path);
        }
        if (path.endsWith('.ts') && !path.endsWith('.test.ts') && !path.endsWith('.d.ts')) {
            return [path];
        }
        return [];
    });
}

function translationKeys(): Set<string> {
    const keys = new Set<string>();
    for (const line of readFileSync(ENGLISH_TRANSLATIONS, 'utf8').split('\n')) {
        const trimmed = line.trim();
        if (trimmed === '' || trimmed.startsWith('#') || trimmed.startsWith('!')) {
            continue;
        }
        const separator = trimmed.search(/[=:]/);
        if (separator > 0) {
            keys.add(trimmed.substring(0, separator).trim());
        }
    }
    return keys;
}

const FILES = sourceFiles(SOURCES).filter(file => !file.endsWith(join('service', 'i18n.ts')));
const KEYS = translationKeys();

describe('translation keys', () => {
    it('exist in the English translation file', () => {
        const missing: string[] = [];
        for (const file of FILES) {
            const source = readFileSync(file, 'utf8');
            const plurals = new Set([...source.matchAll(PLURAL_CALL)].map(match => match[2] ?? match[4]));
            for (const match of source.matchAll(LITERAL_KEY)) {
                const key = match[2];
                let found = KEYS.has(key);
                if (!found && plurals.has(key)) {
                    found = KEYS.has(`${key}_one`) && KEYS.has(`${key}_other`);
                }
                if (!found) {
                    missing.push(`${relative(SOURCES, file)}: ${key}`);
                }
            }
        }
        expect(missing, 'add these keys to src/main/resources/alfio/i18n/public.properties').toEqual([]);
    });

    it('are never built from pieces', () => {
        const built: string[] = [];
        for (const file of FILES) {
            const lines = readFileSync(file, 'utf8').split('\n');
            lines.forEach((line, index) => {
                if (BUILT_KEY.some(pattern => pattern.test(line))) {
                    built.push(`${relative(SOURCES, file)}:${index + 1}: ${line.trim()}`);
                }
            });
        }
        expect(built, 'choose among several keys with a map of i18nKey() values').toEqual([]);
    });
});
