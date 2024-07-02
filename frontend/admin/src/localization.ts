import {configureLocalization, LocaleModule} from '@lit/localize';
import {sourceLocale, targetLocales} from './generated/locale-codes.js';

import * as templates_it from './generated/locales/it';

const localizedTemplates: Map<string, LocaleModule> = new Map([
    ['it', templates_it],
]);

// now we can import getLocale and setLocale to get/set the locales at runtime

export const {getLocale, setLocale} = configureLocalization({
  sourceLocale,
  targetLocales,
  loadLocale: async (locale) => localizedTemplates.get(locale)!,
});