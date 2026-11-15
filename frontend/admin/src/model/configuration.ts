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
