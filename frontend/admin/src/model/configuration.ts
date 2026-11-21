/**
 * A setting as loaded by the configuration endpoints (alfio.model.system.Configuration).
 * The value is the one set at the most specific level: configurationPathLevel tells which one.
 * A setting that isn't set anywhere has id -1 and value null
 */
export interface ConfigurationSetting {
    id: number;
    key: string;
    value: string | null;
    configurationPathLevel: ConfigurationPathLevel;
    componentType: 'TEXT' | 'TEXTAREA' | 'BOOLEAN' | 'LIST';
    description: string;
    internal?: boolean;
    basic?: boolean;
    // a secret is never sent with its value: secretValueSet tells whether one is stored at this level
    secret?: boolean;
    secretValueSet?: boolean;
}

/**
 * What applies to a setting that a level doesn't set: whether the level above sets it (never its value)
 * and, for a yes/no setting, the value that applies
 */
export interface InheritedSetting {
    set: boolean;
    value: string | null;
}

export type ConfigurationPathLevel = 'SYSTEM' | 'ORGANIZATION' | 'PURCHASE_CONTEXT' | 'TICKET_CATEGORY';

/**
 * The settings, by setting category (e.g. GENERAL, PAYMENT)
 */
export type SettingsByCategory = Record<string, ConfigurationSetting[]>;

/**
 * A change to save (alfio.model.modification.ConfigurationModification): the id of the setting at the
 * level being edited, or -1 to create it
 */
export interface ConfigurationModification {
    id: number;
    key: string;
    value: string;
}

/**
 * The level whose settings are edited. A setting that isn't set at a level takes the value of the level above
 * (event → organization → system → default)
 */
export type SettingsLevel =
    | { type: 'SYSTEM' }
    | { type: 'ORGANIZATION'; organizationId: number; organizationName: string }
    | { type: 'EVENT'; organizationId: number; eventId: number; eventName: string; eventDisplayName: string };

/**
 * A parameter of an extension, with its value at the level being edited
 * (alfio.model.ExtensionSupport.ExtensionParameterMetadataAndValue). The id identifies the parameter
 */
export interface ExtensionSetting {
    id: number;
    name: string;
    description: string;
    extensionId: number;
    extensionDisplayName: string;
    componentType: string;
    mandatory: boolean;
    // the value set at the level being edited, null if none
    value: string | null;
}

/**
 * The parameters of the extensions, by extension id
 */
export type ExtensionSettingsById = Record<string, ExtensionSetting[]>;

/**
 * A change to an extension parameter (alfio.model.ExtensionSupport.ExtensionMetadataValue)
 */
export interface ExtensionSettingModification {
    id: number;
    value: string;
}

/**
 * An event, as listed by /admin/api/events/basic (alfio.model.EventBasicInfo)
 */
export interface EventBasicInfo {
    id: number;
    shortName: string;
    displayName: string;
    organizationId: number;
    expired: boolean;
}

/**
 * Whether the payment providers are connected through the platform (Stripe Connect, Mollie Connect)
 */
export interface PlatformModeStatus {
    enabled: boolean;
    stripeConnected?: boolean;
    mollieConnected?: boolean;
}

/**
 * The customized translations, by locale and by key
 */
export type TranslationsOverride = Record<string, Record<string, string>>;
