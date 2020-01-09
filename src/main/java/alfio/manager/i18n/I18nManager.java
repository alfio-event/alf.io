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

import alfio.manager.system.ConfigurationManager;
import alfio.model.ContentLanguage;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class I18nManager {

    private final EventRepository eventRepository;

    @Autowired
    public I18nManager(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public List<ContentLanguage> getAvailableLanguages() {
        return ContentLanguage.ALL_LANGUAGES;
    }

    public List<ContentLanguage> getEventLanguages(String eventName) {
        return eventRepository.findLocalesByShortName(eventName)
            .map(this::getEventLanguages)
            .orElse(Collections.emptyList());
    }

    public List<ContentLanguage> getEventLanguages(int eventLocales) {
        List<ContentLanguage> system = getAvailableLanguages();
        return ContentLanguage.findAllFor(eventLocales)
            .stream()
            .filter(system::contains)
            .collect(Collectors.toList());
    }
}
