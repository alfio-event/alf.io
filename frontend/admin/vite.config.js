import { defineConfig } from "vite";
import { viteStaticCopy } from 'vite-plugin-static-copy'
import legacy from '@vitejs/plugin-legacy';
import browserslistToEsbuild from 'browserslist-to-esbuild';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

/**
 * The admin must run on the same browsers as the public frontend: organizers use old laptops too.
 * Single source of truth: the public frontend's .browserslistrc.
 */
const SUPPORTED_BROWSERS = readFileSync(fileURLToPath(new URL('../public/.browserslistrc', import.meta.url)), 'utf8')
    .split('\n')
    .map((line) => line.replace(/#.*$/, '').trim())
    .filter((line) => line.length > 0)
    // esbuild cannot transpile destructuring for Safari 14.0 (a WebKit bug fixed in 14.1).
    // Every macOS / iOS version that got 14.0 was updated to 14.1.
    .concat('not safari < 14.1', 'not ios_saf < 14.1');

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
    // the build is served below {{contextPath}}/frontend-admin/: assets referenced by the CSS (fonts) are relative to it
    base: './',
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
        // sets build.target from the supported browsers and adds the polyfills they need (e.g. structuredClone).
        // All of them support ES modules, so no separate legacy bundle is needed.
        legacy({
            modernTargets: SUPPORTED_BROWSERS,
            modernPolyfills: true,
            renderLegacyChunks: false,
        }),
    ],
    build: {
        // CSS imported by main.ts. Styles inside Lit's css`` templates are not processed: see docs/branding-guidelines.md
        cssTarget: browserslistToEsbuild(SUPPORTED_BROWSERS),
        // generate .vite/manifest.json in outDir
        manifest: true,
        rollupOptions: {
            // overwrite default .html entry. The login page (login.ms) loads only its stylesheet
            input: ['src/main.ts', 'src/login.css'],
        },
    },
    server: {
        // the URLs of the assets referenced by the sources (e.g. the fonts in main.css) point to this dev server,
        // which the admin page loads its scripts from (admin-index.ms)
        origin: 'http://localhost:5173',
        fs: {
            // main.css uses the fonts of the backend (src/main/resources/font)
            allow: ['.', '../../src/main/resources/font'],
        },
    },
});
