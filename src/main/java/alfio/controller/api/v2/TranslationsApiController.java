/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.controller.api.v2;

import alfio.controller.api.support.TicketHelper;
import alfio.controller.api.v2.model.Language;
import alfio.controller.api.v2.model.LocalizedCountry;
import alfio.manager.i18n.I18nManager;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.system.ConfigurationKeys;
import alfio.util.LocaleUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.web.bind.annotation.*;

import java.text.Collator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v2/")
public class TranslationsApiController {

    private final MessageSourceManager messageSourceManager;
    private final ConfigurationManager configurationManager;
    private final I18nManager i18nManager;

    public TranslationsApiController(MessageSourceManager messageSourceManager, ConfigurationManager configurationManager, I18nManager i18nManager) {
        this.messageSourceManager = messageSourceManager;
        this.configurationManager = configurationManager;
        this.i18nManager = i18nManager;
    }

    @GetMapping("/public/i18n/bundle/{lang}")
    public Map<String, String> getPublicTranslations(@PathVariable("lang") String lang,
                                                     @RequestParam(value = "withSystemOverride", defaultValue = "true", required = false) boolean withSystemOverride) {
        return messageSourceManager.getBundleAsMap("alfio.i18n.public", withSystemOverride, lang, MessageSourceManager.PUBLIC_FRONTEND);
    }

    @GetMapping("/admin/i18n/bundle/{lang}")
    public Map<String, String> getAdminTranslations(@PathVariable("lang") String lang,
                                                    @RequestParam(value = "withSystemOverride", defaultValue = "true", required = false) boolean withSystemOverride) {
        return messageSourceManager.getBundleAsMap("alfio.i18n.public", withSystemOverride, lang, MessageSourceManager.ADMIN_FRONTEND);
    }

    @GetMapping("/public/i18n/countries/{lang}")
    public List<LocalizedCountry> getCountries(@PathVariable("lang") String lang) {
        return fromPair(TicketHelper.getLocalizedCountries(LocaleUtil.forLanguageTag(lang)));
    }

    @GetMapping("/public/i18n/countries-vat/{lang}")
    public List<LocalizedCountry> getCountriesForVat(@PathVariable("lang") String lang) {
        return fromPair(TicketHelper.getLocalizedCountriesForVat(LocaleUtil.forLanguageTag(lang)));
    }

    @GetMapping("/public/i18n/eu-countries-vat/{lang}")
    public List<LocalizedCountry> getEuCountriesForVat(@PathVariable("lang") String lang) {
        var countries = TicketHelper.getLocalizedEUCountriesForVat(LocaleUtil.forLanguageTag(lang),
            configurationManager.getForSystem(ConfigurationKeys.EU_COUNTRIES_LIST).getRequiredValue());
        return fromPair(countries);
    }

    private static List<LocalizedCountry> fromPair(List<Pair<String, String>> countries) {
        var collator = Collator.getInstance(Locale.FRENCH); //<- gives the better sorting experience...
        return countries.stream().map(p-> new LocalizedCountry(p.getKey(), p.getValue()))
            .sorted((lc1, lc2) -> collator.compare(lc1.getName(), lc2.getName()))
            .collect(Collectors.toList());
    }

    @GetMapping("/public/i18n/languages")
    public List<Language> getSupportedLanguages() {
        return i18nManager.getAvailableLanguages()
            .stream()
            .map(cl -> new Language(cl.getLocale().getLanguage(), cl.getDisplayLanguage()))
            .collect(Collectors.toList());
    }
}
