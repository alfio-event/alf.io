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

import alfio.controller.form.AdditionalServiceLinkForm;
import alfio.manager.support.reservation.NotEnoughItemsException;
import alfio.model.*;
import alfio.model.decorator.AdditionalServicePriceContainer;
import alfio.model.modification.EventModification;
import alfio.repository.AdditionalServiceItemRepository;
import alfio.repository.AdditionalServiceRepository;
import alfio.repository.AdditionalServiceTextRepository;
import alfio.repository.TicketRepository;
import alfio.util.MonetaryUtil;
import ch.digitalfondue.npjt.AffectedRowCountAndKey;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static alfio.util.MonetaryUtil.unitToCents;

@Component
@AllArgsConstructor
@Transactional
public class AdditionalServiceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdditionalServiceManager.class);
    private final AdditionalServiceRepository additionalServiceRepository;
    private final AdditionalServiceTextRepository additionalServiceTextRepository;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TicketRepository ticketRepository;


    public List<AdditionalService> loadAllForEvent(int eventId) {
        return additionalServiceRepository.loadAllForEvent(eventId);
    }

    public List<AdditionalServiceText> findAllTextByAdditionalServiceId(int additionalServiceId) {
        return additionalServiceTextRepository.findAllByAdditionalServiceId(additionalServiceId);
    }

    public Map<Integer, Map<AdditionalServiceItem.AdditionalServiceItemStatus, Integer>> countUsageForEvent(int eventId) {
        return additionalServiceRepository.getCount(eventId);
    }

    public int update(int additionalServiceId,
                      Event event,
                      EventModification.AdditionalService additionalService) {
        int result = additionalServiceRepository.update(additionalServiceId,
            additionalService.isFixPrice(),
            additionalService.getOrdinal(),
            additionalService.getAvailableQuantity(),
            additionalService.getMaxQtyPerOrder(),
            additionalService.getInception().toZonedDateTime(event.getZoneId()),
            additionalService.getExpiration().toZonedDateTime(event.getZoneId()),
            additionalService.getVat(),
            additionalService.getVatType(),
            Optional.ofNullable(additionalService.getPrice()).map(p -> MonetaryUtil.unitToCents(p, event.getCurrency())).orElse(0));
        preGenerateItems(additionalServiceId, event, additionalService);
        return result;
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


    public EventModification.AdditionalService insertAdditionalService(Event event, EventModification.AdditionalService additionalService) {
        int eventId = event.getId();
        AffectedRowCountAndKey<Integer> result = additionalServiceRepository.insert(eventId,
            Optional.ofNullable(additionalService.getPrice()).map(p -> MonetaryUtil.unitToCents(p, event.getCurrency())).orElse(0),
            additionalService.isFixPrice(),
            additionalService.getOrdinal(),
            additionalService.getAvailableQuantity(),
            additionalService.getMaxQtyPerOrder(),
            additionalService.getInception().toZonedDateTime(event.getZoneId()),
            additionalService.getExpiration().toZonedDateTime(event.getZoneId()),
            additionalService.getVat(),
            additionalService.getVatType(),
            additionalService.getType(),
            additionalService.getSupplementPolicy());
        Validate.isTrue(result.getAffectedRowCount() == 1, "too many records updated");
        int id = result.getKey();
        Stream.concat(additionalService.getTitle().stream(), additionalService.getDescription().stream()).
            forEach(t -> additionalServiceTextRepository.insert(id, t.getLocale(), t.getType(), t.getValue()));
        if (additionalService.getAvailableQuantity() > 0) {
            preGenerateItems(result.getKey(), event, additionalService);
        }

        return EventModification.AdditionalService.from(additionalServiceRepository.getById(result.getKey(), eventId))
            .withText(additionalServiceTextRepository.findAllByAdditionalServiceId(result.getKey()))
            .withZoneId(event.getZoneId())
            .build();
    }

    void createAllAdditionalServices(Event event, List<EventModification.AdditionalService> additionalServices) {
        if (!CollectionUtils.isEmpty(additionalServices)) {
            int eventId = event.getId();
            var currencyCode = event.getCurrency();
            var zoneId = event.getZoneId();
            additionalServices.forEach(as -> {
                AffectedRowCountAndKey<Integer> service = additionalServiceRepository.insert(eventId,
                    Optional.ofNullable(as.getPrice()).map(p -> MonetaryUtil.unitToCents(p, currencyCode)).orElse(0),
                    as.isFixPrice(),
                    as.getOrdinal(),
                    as.getAvailableQuantity(),
                    as.getMaxQtyPerOrder(),
                    as.getInception().toZonedDateTime(zoneId),
                    as.getExpiration().toZonedDateTime(zoneId),
                    as.getVat(),
                    as.getVatType(),
                    as.getType(),
                    as.getSupplementPolicy());
                if (as.getAvailableQuantity() > 0) {
                    preGenerateItems(service.getKey(), event, as);
                }
                as.getTitle().forEach(insertAdditionalServiceDescription(service.getKey()));
                as.getDescription().forEach(insertAdditionalServiceDescription(service.getKey()));
            });
        }
    }

    private void preGenerateItems(int serviceId, Event event, EventModification.AdditionalService as) {
        // check how many are already defined
        int count = additionalServiceItemRepository.countItemsForService(serviceId);
        int requested = as.getAvailableQuantity();
        if (count >= requested) {
            LOGGER.debug("Requested {} items, found {}", requested, count);
            int result = additionalServiceItemRepository.invalidateItems(serviceId, count - requested);
            Validate.isTrue(result == count - requested, "Cannot reduce available items to "+requested);
        } else {
            var batchReserveParameters = new ArrayList<MapSqlParameterSource>();
            for (int i = 0; i < requested - count; i++) {
                batchReserveParameters.add(buildInsertItemParameterSource(
                    serviceId,
                    null,
                    AdditionalServiceItem.AdditionalServiceItemStatus.FREE,
                    event,
                    0,
                    0,
                    0,
                    0
                ));
            }
            int result = (int) Arrays.stream(jdbcTemplate.batchUpdate(additionalServiceItemRepository.batchInsert(), batchReserveParameters.toArray(MapSqlParameterSource[]::new)))
                .asLongStream()
                .sum();
            Validate.isTrue(result + count == requested, "Error while pre-generating items");
        }
    }

    void bookAdditionalServiceItems(int quantity,
                                    BigDecimal amount,
                                    AdditionalService as,
                                    Event event,
                                    PromoCodeDiscount discount,
                                    String reservationId) {
        Validate.isTrue(quantity > 0);
        AdditionalServicePriceContainer pc = AdditionalServicePriceContainer.from(amount, as, event, discount);
        var currencyCode = pc.getCurrencyCode();
        String queryTemplate;
        var batchReserveParameters = new ArrayList<MapSqlParameterSource>();
        if (as.getAvailableQuantity() > 0) {
            queryTemplate = additionalServiceItemRepository.batchUpdate();
            var ids = additionalServiceItemRepository.lockExistingItems(as.getId(), quantity);
            if (ids.size() != quantity) {
                throw new NotEnoughItemsException();
            }
            for (int i = 0; i < quantity; i++) {
                batchReserveParameters.add(new MapSqlParameterSource("uuid", UUID.randomUUID().toString())
                    .addValue("ticketsReservationUuid", reservationId)
                    .addValue("additionalServiceId", as.getId())
                    .addValue("status", AdditionalServiceItem.AdditionalServiceItemStatus.PENDING.name())
                    .addValue("id", ids.get(i))
                    .addValue("srcPriceCts", as.getSrcPriceCts())
                    .addValue("finalPriceCts", unitToCents(pc.getFinalPrice(), currencyCode))
                    .addValue("vatCts", unitToCents(pc.getVAT(), currencyCode))
                    .addValue("discountCts", unitToCents(pc.getAppliedDiscount(), currencyCode))
                    .addValue("currencyCode", event.getCurrency()));
            }
        } else {
            queryTemplate = additionalServiceItemRepository.batchInsert();
            for (int i = 0; i < quantity; i++) {
                batchReserveParameters.add(buildInsertItemParameterSource(
                    as.getId(),
                    reservationId,
                    AdditionalServiceItem.AdditionalServiceItemStatus.PENDING,
                    event,
                    as.getSrcPriceCts(),
                    unitToCents(pc.getFinalPrice(), currencyCode),
                    unitToCents(pc.getVAT(), currencyCode),
                    unitToCents(pc.getAppliedDiscount(), currencyCode)
                ));
            }
        }
        int result = (int) Arrays.stream(jdbcTemplate.batchUpdate(queryTemplate, batchReserveParameters.toArray(MapSqlParameterSource[]::new)))
            .asLongStream()
            .sum();
        Validate.isTrue(result == quantity, "Cannot book additional services");
    }

    private Consumer<EventModification.AdditionalServiceText> insertAdditionalServiceDescription(int serviceId) {
        return t -> additionalServiceTextRepository.insert(serviceId, t.getLocale(), t.getType(), t.getValue());
    }

    public AdditionalService getAdditionalServiceById(int id, int eventId) {
        return additionalServiceRepository.getById(id, eventId);
    }

    public Map<Integer, Map<AdditionalServiceText.TextType, Map<String, String>>> getDescriptionsByAdditionalServiceIds(Collection<Integer> additionalServiceIds) {
        return additionalServiceTextRepository.getDescriptionsByAdditionalServiceIds(additionalServiceIds);
    }

    private MapSqlParameterSource buildInsertItemParameterSource(int serviceId,
                                                                 String reservationId,
                                                                 AdditionalServiceItem.AdditionalServiceItemStatus status,
                                                                 Event event,
                                                                 int srcPriceCts,
                                                                 int finalPriceCts,
                                                                 int vatCts,
                                                                 int discountCts) {
        return new MapSqlParameterSource("uuid", UUID.randomUUID().toString())
            .addValue("ticketsReservationUuid", reservationId)
            .addValue("additionalServiceId", serviceId)
            .addValue("status", status.name())
            .addValue("eventId", event.getId())
            .addValue("srcPriceCts", srcPriceCts)
            .addValue("finalPriceCts", finalPriceCts)
            .addValue("vatCts", vatCts)
            .addValue("discountCts", discountCts)
            .addValue("currencyCode", event.getCurrency());
    }

    int updateStatusForReservationId(String reservationId, AdditionalServiceItem.AdditionalServiceItemStatus additionalServiceItemStatus) {
        return additionalServiceItemRepository.updateItemsStatusWithReservationUUID(reservationId, additionalServiceItemStatus);
    }

    public List<AdditionalServiceItem> findItemsInReservation(String reservationId) {
        return additionalServiceItemRepository.findByReservationUuid(reservationId);
    }

    Optional<AdditionalServiceText> loadItemTitle(AdditionalServiceItem asi, Locale locale) {
        return additionalServiceTextRepository.findByLocaleAndType(asi.getAdditionalServiceId(), locale.getLanguage(), AdditionalServiceText.TextType.TITLE);
    }

    boolean hasPaidSupplements(String reservationId) {
        return additionalServiceItemRepository.hasPaidSupplements(reservationId);
    }

    List<AdditionalService> findAllInEventWithPolicy(int eventId, AdditionalService.SupplementPolicy supplementPolicy) {
        return additionalServiceRepository.findAllInEventWithPolicy(eventId, supplementPolicy);
    }

    public List<AdditionalService> loadAllForReservation(String reservationId, int eventId) {
        return additionalServiceRepository.loadAllForReservation(reservationId, eventId);
    }

    public void linkItemsToTickets(String reservationId,
                                   AdditionalServiceLinkForm additionalServiceLinkForm,
                                   List<Ticket> tickets) {
        if (additionalServiceLinkForm == null || CollectionUtils.isEmpty(additionalServiceLinkForm.getAdditionalServiceLinks())) {
            return;
        }
        var parameterSources = additionalServiceLinkForm.getAdditionalServiceLinks().stream()
            .map(asl -> {
                Integer ticketId = tickets.stream()
                    .filter(t -> StringUtils.isNotEmpty(asl.getTicketUUID()) && t.getUuid().equals(asl.getTicketUUID()))
                    .findFirst()
                    .map(Ticket::getId)
                    .orElse(null);
                return new MapSqlParameterSource("ticketId", ticketId)
                    .addValue("itemId", asl.getAdditionalServiceItemId())
                    .addValue("reservationId", reservationId);
            }).toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(additionalServiceItemRepository.batchLinkToTicket(), parameterSources);
    }


}
