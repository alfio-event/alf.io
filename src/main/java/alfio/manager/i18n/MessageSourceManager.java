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

import alfio.model.EventAndOrganizationId;
import alfio.repository.system.ConfigurationRepository;
import alfio.util.CustomResourceBundleMessageSource;
import alfio.util.LocaleUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.MessageSource;
import org.springframework.context.support.AbstractMessageSource;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Log4j2
public class MessageSourceManager {

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

    public Pair<MessageSource, Map<String, Map<String, String>>> getMessageSourceForEventAndOverride(EventAndOrganizationId eventAndOrganizationId) {
        var override = configurationRepository.getEventOverrideMessages(eventAndOrganizationId.getOrganizationId(), eventAndOrganizationId.getId());
        return Pair.of(new MessageSourceWithOverride(messageSource, override), override);
    }

    public MessageSource getMessageSourceForEvent(EventAndOrganizationId eventAndOrganizationId) {
        return getMessageSourceForEventAndOverride(eventAndOrganizationId).getLeft();
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

    public Map<String, String> getBundleAsMap(String baseName, boolean withSystemOverride, String lang) {
        var locale = LocaleUtil.forLanguageTag(lang);
        var messageSource = getRootMessageSource(withSystemOverride);
        return getKeys(baseName, locale)
            .stream()
            .collect(Collectors.toMap(Function.identity(), k -> messageSource.getMessage(k, EMPTY_ARRAY, locale)
                //replace all placeholder {0} -> {{0}} so it can be consumed by ngx-translate
                .replaceAll("\\{(\\d+)\\}", "{{$1}}")));
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

    public static Map<String, String> cleanTranslationsForFrontend(Map<String, String> translations) {
        return translations.entrySet().stream()
            .map(entry -> Pair.of(entry.getKey(), cleanArguments(entry.getValue(), "{{$1}}").replaceAll("''", "'")))
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }
}
