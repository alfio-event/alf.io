import { defineConfig } from "vite";
import { viteStaticCopy } from 'vite-plugin-static-copy'
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const EXTENSION_EVENTS_MODULE = 'virtual:extension-events';
const EXTENSION_EVENTS_SOURCE = fileURLToPath(
    new URL('../../src/main/java/alfio/manager/support/extension/ExtensionEvent.java', import.meta.url));

/**
 * Exposes the constants of the backend enum alfio.manager.support.extension.ExtensionEvent, with their dispatch mode,
 * as "virtual:extension-events", so that the script editor always proposes the events the backend supports.
 */
function extensionEvents() {
    const resolvedId = '\0' + EXTENSION_EVENTS_MODULE;
    return {
        name: 'alfio-extension-events',
        resolveId(id) {
            if (id === EXTENSION_EVENTS_MODULE) {
                return resolvedId;
            }
            return null;
        },
        load(id) {
            if (id !== resolvedId) {
                return null;
            }
            // reload the module in dev mode when the enum changes
            this.addWatchFile(EXTENSION_EVENTS_SOURCE);
            const events = parseEnumConstants(readFileSync(EXTENSION_EVENTS_SOURCE, 'utf8'));
            return `export const EXTENSION_EVENTS = ${JSON.stringify(events)};`;
        },
    };
}

/**
 * Reads the constants of the enum and how they are dispatched, e.g. "RESERVATION_CONFIRMED(Dispatch.ASYNC)"
 */
function parseEnumConstants(source) {
    const enumBody = source.substring(source.indexOf('enum ExtensionEvent'));
    const constantsBlock = enumBody.substring(enumBody.indexOf('{') + 1, enumBody.indexOf(';'))
        .replace(/\/\*[\s\S]*?\*\//g, '')
        .replace(/\/\/.*$/gm, '');
    const declarations = constantsBlock.split(',')
        .map((c) => c.trim())
        .filter((c) => c.length > 0);
    const constants = declarations
        .map((d) => /^([A-Z][A-Z0-9_]*)\(Dispatch\.(SYNC|ASYNC|ALL)\)$/.exec(d))
        .filter((match) => match !== null)
        .map((match) => ({name: match[1], dispatch: match[2]}));
    if (constants.length === 0 || constants.length !== declarations.length) {
        // the declaration of the constants changed: update this parser
        const invalid = declarations.filter((d) => !constants.some((c) => d.startsWith(c.name + '(')));
        throw new Error(`Cannot read the constants of ${EXTENSION_EVENTS_SOURCE}: ${invalid.join(', ')}`);
    }
    return constants;
}

export default defineConfig({
    plugins: [
        viteStaticCopy({
            targets: [
                {
                    src: 'node_modules/@shoelace-style/shoelace/dist/assets/icons/*.svg',
                    dest: 'shoelace/assets/icons'
                }
            ]
        }),
        extensionEvents(),
    ],
    build: {
        // generate .vite/manifest.json in outDir
        manifest: true,
        rollupOptions: {
            // overwrite default .html entry
            input: 'src/main.ts',
        },
    },
    server: {
        origin: 'http://127.0.0.1:8080',
    },
});
