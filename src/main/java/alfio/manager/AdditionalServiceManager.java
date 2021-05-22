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
package alfio.manager;

import alfio.model.AdditionalService;
import alfio.model.AdditionalServiceItemExport;
import alfio.model.AdditionalServiceText;
import alfio.repository.AdditionalServiceItemRepository;
import alfio.repository.AdditionalServiceRepository;
import alfio.repository.AdditionalServiceTextRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@AllArgsConstructor
@Transactional
public class AdditionalServiceManager {

    private final AdditionalServiceRepository additionalServiceRepository;
    private final AdditionalServiceTextRepository additionalServiceTextRepository;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;


    public List<AdditionalService> loadAllForEvent(int eventId) {
        return additionalServiceRepository.loadAllForEvent(eventId);
    }

    public List<AdditionalServiceText> findAllTextByAdditionalServiceId(int additionalServiceId) {
        return additionalServiceTextRepository.findAllByAdditionalServiceId(additionalServiceId);
    }

    public Map<Integer, Integer> countUsageForEvent(int eventId) {
        return additionalServiceRepository.getCount(eventId);
    }

    public int update(int additionalServiceId,
                      boolean fixPrice,
                      int ordinal,
                      int availableQuantity,
                      int maxQtyPerOrder,
                      ZonedDateTime inception,
                      ZonedDateTime expiration,
                      BigDecimal vat,
                      AdditionalService.VatType vatType,
                      Integer price) {
        return additionalServiceRepository.update(additionalServiceId,
            fixPrice,
            ordinal,
            availableQuantity,
            maxQtyPerOrder,
            inception,
            expiration,
            vat,
            vatType,
            price);
    }

    public void updateText(Integer textId, String locale, AdditionalServiceText.TextType type, String value) {
        additionalServiceTextRepository.update(textId, locale, type, value);
    }

    public void insertText(Integer additionalServiceId, String locale, AdditionalServiceText.TextType type, String value) {
        additionalServiceTextRepository.insert(additionalServiceId, locale, type, value);
    }

    public Optional<AdditionalService> getOptionalById(int additionalServiceId, int eventId) {
        return additionalServiceRepository.getOptionalById(additionalServiceId, eventId);
    }


    public int deleteAdditionalServiceTexts(int additionalServiceId) {
        return additionalServiceTextRepository.deleteAdditionalServiceTexts(additionalServiceId);
    }

    public void delete(int additionalServiceId, int eventId) {
        additionalServiceRepository.delete(additionalServiceId, eventId);
    }

    public List<AdditionalServiceItemExport> exportItemsForEvent(AdditionalService.AdditionalServiceType type,
                                                                 int eventId,
                                                                 String locale) {
        return additionalServiceItemRepository.getAdditionalServicesOfTypeForEvent(eventId, type.name(), locale);
    }
}
