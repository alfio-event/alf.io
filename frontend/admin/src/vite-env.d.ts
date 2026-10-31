/// <reference types="vite/client" />

declare module 'virtual:extension-events' {
    /**
     * How the backend fires an event: scripts handle it only if their "async" flag matches
     */
    export type ExtensionEventDispatch = 'SYNC' | 'ASYNC' | 'ALL';

    /**
     * Constants of the backend enum alfio.manager.support.extension.ExtensionEvent, read at build time (see vite.config.js)
     */
    export const EXTENSION_EVENTS: ReadonlyArray<{ name: string, dispatch: ExtensionEventDispatch }>;
}
