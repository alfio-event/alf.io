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
import org.springframework.context.MessageSource;
import org.springframework.context.support.AbstractMessageSource;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


public class MessageSourceManager {

    private static final String[] EMPTY_ARRAY = new String[]{};

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

    public MessageSource getMessageSourceForEvent(EventAndOrganizationId eventAndOrganizationId) {
        var res = getEventMessageSourceOverride(eventAndOrganizationId);
        return new MessageSourceWithOverride(messageSource, res);
    }

    public MessageSource getRootMessageSource() {
        var res = configurationRepository.getSystemOverrideMessages();
        return new MessageSourceWithOverride(messageSource, res);
    }

    public Map<String, Map<String, String>> getEventMessageSourceOverride(EventAndOrganizationId eventAndOrganizationId) {
        return configurationRepository.getEventOverrideMessages(eventAndOrganizationId.getOrganizationId(), eventAndOrganizationId.getId());
    }

    private static final class MessageSourceWithOverride extends AbstractMessageSource {

        private final MessageSource messageSource;
        private final Map<String, Map<String, String>> override;

        MessageSourceWithOverride(MessageSource messageSource, Map<String, Map<String, String>> override) {
            this.messageSource = messageSource;
            this.override = override;
        }

        @Override
        protected MessageFormat resolveCode(String s, Locale locale) {
            var language = locale.getLanguage();
            if (override.containsKey(language) && override.get(language).containsKey(s)) {
                return new MessageFormat(override.get(language).get(s), locale);
            }
            return new MessageFormat(messageSource.getMessage(s, EMPTY_ARRAY, locale), locale);
        }
    }
}
