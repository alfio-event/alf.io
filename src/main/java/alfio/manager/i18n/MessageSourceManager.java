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
package alfio.manager.i18n;

import alfio.model.PurchaseContext;
import alfio.repository.system.ConfigurationRepository;
import alfio.util.CustomResourceBundleMessageSource;
import alfio.util.LocaleUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.MessageSource;
import org.springframework.context.support.AbstractMessageSource;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Log4j2
public class MessageSourceManager {

    /**
     * No-op filter used to load all keys in the resource bundle, to be used with the new Admin frontend
     */
    public static final Predicate<String> ADMIN_FRONTEND = key -> true;
    /**
     * Filters out all the "admin" keys which would not make sense to load on the public frontend
     */
    public static final Predicate<String> PUBLIC_FRONTEND = key -> !key.startsWith("admin.");

    private static final Pattern ARGUMENT_FINDER = Pattern.compile("\\{+(\\d+)}+");
    private final CustomResourceBundleMessageSource messageSource;
    private final ConfigurationRepository configurationRepository;

    public MessageSourceManager(CustomResourceBundleMessageSource messageSource,
                                ConfigurationRepository configurationRepository) {
        this.messageSource = messageSource;
        this.configurationRepository = configurationRepository;
    }

    public Set<String> getKeys(String basename, Locale locale) {
        return messageSource.getKeys(basename, locale);
    }

    public Pair<MessageSource, Map<String, Map<String, String>>> getMessageSourceForPurchaseContextAndOverride(PurchaseContext purchaseContext) {
        Map<String, Map<String, String>> override = purchaseContext.event()
            .map(event -> configurationRepository.getEventOverrideMessages(event.getOrganizationId(), event.getId()))
            .orElseGet(() -> configurationRepository.getOrganizationOverrideMessages(purchaseContext.getOrganizationId()));
        return Pair.of(new MessageSourceWithOverride(messageSource, override), override);
    }

    public MessageSource getMessageSourceFor(PurchaseContext purchaseContext) {
        return getMessageSourceForPurchaseContextAndOverride(purchaseContext).getLeft();
    }

    public MessageSource getMessageSourceFor(int orgId, int eventId) {
        var override = configurationRepository.getEventOverrideMessages(orgId, eventId);
        return new MessageSourceWithOverride(messageSource, override);
    }

    public MessageSource getRootMessageSource() {
        return getRootMessageSource(true);
    }

    public MessageSource getRootMessageSource(boolean withSystemOverride) {
        if (withSystemOverride) {
            return new MessageSourceWithOverride(messageSource, configurationRepository.getSystemOverrideMessages());
        } else {
            return messageSource;
        }
    }

    private static final String[] EMPTY_ARRAY = new String[]{};

    private static final Pattern PLACEHOLDER_TO_REPLACE = Pattern.compile("\\{(\\d+)\\}");

    private static String convertPlaceholder(String value) {
        return PLACEHOLDER_TO_REPLACE.matcher(value).replaceAll("{{$1}}").replace("\'\'", "\'");
    }

    public static Map<String, Map<String, String>> convertPlaceholdersForEachLanguage(Map<String, Map<String, String>> bundles) {
        Map<String, Map<String, String>> res = new HashMap<>(bundles.size());
        bundles.forEach((l, b) -> res.put(l, convertPlaceholders(b)));
        return res;
    }

    private static Map<String, String> convertPlaceholders(Map<String, String> bundle) {
        Map<String, String> res = new HashMap<>(bundle.size());
        bundle.forEach((k, v) -> res.put(k, convertPlaceholder(v)));
        return res;
    }

    public Map<String, String> getBundleAsMap(String baseName,
                                              boolean withSystemOverride,
                                              String lang,
                                              Predicate<String> keysFilter) {
        var locale = LocaleUtil.forLanguageTag(lang);
        var rootMessageSource = getRootMessageSource(withSystemOverride);
        return getKeys(baseName, locale)
            .stream()
            .filter(keysFilter)
            .collect(Collectors.toMap(Function.identity(), k -> convertPlaceholder(rootMessageSource.getMessage(k, EMPTY_ARRAY, locale))));
    }

    private static class MessageSourceWithOverride extends AbstractMessageSource {

        private final CustomResourceBundleMessageSource messageSource;
        private final Map<String, Map<String, String>> override;

        private MessageSourceWithOverride(CustomResourceBundleMessageSource messageSource, Map<String, Map<String, String>> override) {
            this.messageSource = messageSource;
            this.override = override;
        }

        @Override
        protected MessageFormat resolveCode(String s, Locale locale) {
            var language = locale.getLanguage();
            if (override.containsKey(language) && override.get(language).containsKey(s)) {
                var pattern = cleanArguments(override.get(language).get(s), "{$1}");
                return new MessageFormat(pattern, locale);
            }
            return messageSource.getMessageFormatFor(s, locale);
        }
    }

    static String cleanArguments(String translation, String replacement) {
        return ARGUMENT_FINDER.matcher(translation).replaceAll(replacement);
    }

    static Map<String, String> cleanTranslationsForFrontend(Map<String, String> translations) {
        return translations.entrySet().stream()
            .map(entry -> Pair.of(entry.getKey(), cleanArguments(entry.getValue(), "{{$1}}").replaceAll("''", "'")))
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }
}
