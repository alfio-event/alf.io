import { t } from '../service/i18n';
/**
 * The shape of a JSON setting, as the server reads it: keep each schema in sync with the Java class it describes.
 * null is accepted for every value, as the server does.
 */
export type JsonSchema =
    | 'text'
    | 'yes/no'
    | 'whole number'
    | { list: JsonSchema }
    | { object: Record<string, JsonSchema> };

/**
 * Checks a JSON setting against its schema: valid JSON, an object at the top, only known properties (a misspelled
 * one would be ignored by the server), and values of the right type. An empty value is accepted: the setting
 * isn't set.
 *
 * @return the message to display, or undefined if the value is valid
 */
export function checkJsonSetting(value: unknown, schema: Record<string, JsonSchema>): string | undefined {
    const text = String(value ?? '').trim();
    if (text === '') {
        return undefined;
    }
    let parsed: unknown;
    try {
        parsed = JSON.parse(text);
    } catch {
        return t('admin.json-check.invalid');
    }
    if (!isObject(parsed)) {
        return t('admin.json-check.not-object', '{', '}');
    }
    return checkObject(parsed, schema, '');
}

/**
 * The value indented on several lines, if it is valid JSON; otherwise as it is, so that it can be fixed
 */
export function prettyJson(value: string): string {
    if (value.trim() === '') {
        return value;
    }
    try {
        return format(JSON.parse(value), '');
    } catch {
        return value;
    }
}

/**
 * Like JSON.stringify(value, null, 2), but a list of plain values (e.g. the ids of the categories) stays on one line
 */
function format(value: unknown, indent: string): string {
    const inner = `${indent}  `;
    if (Array.isArray(value)) {
        if (value.every((item) => item === null || typeof item !== 'object')) {
            return `[${value.map((item) => JSON.stringify(item)).join(', ')}]`;
        }
        return `[\n${value.map((item) => inner + format(item, inner)).join(',\n')}\n${indent}]`;
    }
    if (value !== null && typeof value === 'object') {
        const entries = Object.entries(value);
        if (entries.length === 0) {
            return '{}';
        }
        return `{\n${entries.map(([key, item]) => `${inner}${JSON.stringify(key)}: ${format(item, inner)}`).join(',\n')}\n${indent}}`;
    }
    return JSON.stringify(value);
}

/**
 * The two values hold the same JSON, whatever their formatting
 */
export function sameJson(a: string, b: string): boolean {
    try {
        return JSON.stringify(JSON.parse(a)) === JSON.stringify(JSON.parse(b));
    } catch {
        return false;
    }
}

function checkObject(value: Record<string, unknown>, schema: Record<string, JsonSchema>, path: string): string | undefined {
    for (const [name, propertyValue] of Object.entries(value)) {
        const property = `${path}${name}`;
        const expected = schema[name];
        if (expected == null) {
            const known = Object.keys(schema).map((key) => `${path}${key}`).join(', ');
            return t('admin.json-check.unknown-property', property, known);
        }
        const error = checkValue(propertyValue, expected, property);
        if (error != null) {
            return error;
        }
    }
    return undefined;
}

function checkValue(value: unknown, schema: JsonSchema, path: string): string | undefined {
    if (value === null) {
        return undefined;
    }
    if (schema === 'text') {
        return check(typeof value === 'string', t('admin.json-check.text', path));
    }
    if (schema === 'yes/no') {
        return check(typeof value === 'boolean', t('admin.json-check.yes-no', path));
    }
    if (schema === 'whole number') {
        return check(Number.isInteger(value), t('admin.json-check.whole-number', path));
    }
    if ('list' in schema) {
        if (!Array.isArray(value)) {
            return t('admin.json-check.list', path, '[', ']');
        }
        for (let index = 0; index < value.length; index++) {
            const error = checkValue(value[index], schema.list, `${path}[${index}]`);
            if (error != null) {
                return error;
            }
        }
        return undefined;
    }
    if (!isObject(value)) {
        return t('admin.json-check.object', path, '{', '}');
    }
    return checkObject(value, schema.object, `${path}.`);
}

function check(valid: boolean, message: string): string | undefined {
    if (valid) {
        return undefined;
    }
    return message;
}

function isObject(value: unknown): value is Record<string, unknown> {
    return value != null && typeof value === 'object' && !Array.isArray(value);
}
