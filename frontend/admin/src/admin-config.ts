import { createContext } from '@lit/context';

/**
 * What the server tells the admin shell about the current user and installation.
 * Rendered by AdminIndexController as the data-config attribute of <alfio-admin-app>.
 */
export interface AdminConfig {
    /** the servlet context path: the admin's pages are below `${contextPath}/admin`, its static files below `${contextPath}/resources` */
    contextPath: string;
    isOwner: boolean;
    isAdmin: boolean;
    isDBAuthentication: boolean;
    username: string;
    idpLogoutRedirectionUrl: string | null;
    errorMessage: string | null;
    displayProjectBanner: boolean;
    alfioVersion: string;
    basicConfigurationNeeded: boolean;
    supportedLanguages: unknown[];
    baseUrl: string;
    devModeEnabled: boolean;
    prodModeEnabled: boolean;
}

export const adminConfigContext = createContext<AdminConfig>('alfio-admin-config');

const DEFAULT_CONFIG: AdminConfig = {
    contextPath: '',
    isOwner: false,
    isAdmin: false,
    isDBAuthentication: true,
    username: '',
    idpLogoutRedirectionUrl: null,
    errorMessage: null,
    displayProjectBanner: false,
    alfioVersion: '',
    basicConfigurationNeeded: false,
    supportedLanguages: [],
    baseUrl: '',
    devModeEnabled: false,
    prodModeEnabled: false,
};

let config: AdminConfig | null = null;

/**
 * The configuration, read once from the page. Code outside the shell's element tree (services, helpers) uses this;
 * elements below <alfio-admin-app> can consume adminConfigContext instead.
 */
export function adminConfig(): AdminConfig {
    if (config == null) {
        config = readConfig();
    }
    return config;
}

function readConfig(): AdminConfig {
    const json = document.querySelector('alfio-admin-app')?.getAttribute('data-config');
    if (json == null || json === '') {
        return DEFAULT_CONFIG;
    }
    try {
        return { ...DEFAULT_CONFIG, ...JSON.parse(json) };
    } catch {
        return DEFAULT_CONFIG;
    }
}
