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
import alfio.manager.support.reservation.ReservationCostCalculator;
import alfio.model.*;
import alfio.model.decorator.AdditionalServicePriceContainer;
import alfio.model.modification.ASReservationWithOptionalCodeModification;
import alfio.model.modification.AdditionalServiceReservationModification;
import alfio.model.modification.EventModification;
import alfio.repository.*;
import alfio.util.MonetaryUtil;
import ch.digitalfondue.npjt.AffectedRowCountAndKey;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.iterators.LoopingIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.model.AdditionalService.SupplementPolicy.*;
import static alfio.util.MonetaryUtil.*;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.groupingBy;

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
    private final PurchaseContextFieldRepository purchaseContextFieldRepository;
    private final ReservationCostCalculator reservationCostCalculator;


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
            Optional.ofNullable(additionalService.getPrice()).map(p -> MonetaryUtil.unitToCents(p, event.getCurrency())).orElse(0),
            additionalService.getSupplementPolicy().name(),
            Optional.ofNullable(additionalService.getMinPrice()).map(p -> MonetaryUtil.unitToCents(p, event.getCurrency())).orElse(null),
            Optional.ofNullable(additionalService.getMaxPrice()).map(p -> MonetaryUtil.unitToCents(p, event.getCurrency())).orElse(null));
        preGenerateItems(additionalServiceId, event, additionalService);
        return result;
    }

    public void updateText(Integer textId, String locale, AdditionalServiceText.TextType type, String value, Integer additionalServiceId) {
        Assert.isTrue(additionalServiceTextRepository.update(textId, locale, type, value, additionalServiceId) == 1,
            "Error while updating the text with id " + textId + " and additionalServiceId " + additionalServiceId + ", should have affected 1 row"
        );
    }

    public void insertText(Integer additionalServiceId, String locale, AdditionalServiceText.TextType type, String value) {
        additionalServiceTextRepository.insert(additionalServiceId, locale, type, value);
    }

    public Optional<AdditionalService> getOptionalById(int additionalServiceId, int eventId) {
        return additionalServiceRepository.getOptionalById(additionalServiceId, eventId);
    }

    public void deleteAdditionalService(int additionalServiceId, int eventId) {
        int deletedTexts = deleteAdditionalServiceTexts(additionalServiceId);
        LOGGER.debug("deleted {} texts", deletedTexts);
        int deletedItems = additionalServiceItemRepository.deleteByAdditionalServiceId(eventId, additionalServiceId);
        LOGGER.debug("deleted {} items", deletedItems);
        delete(additionalServiceId, eventId);
        LOGGER.debug("additional service #{} successfully deleted", additionalServiceId);
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
            evaluateAdditionalServicePriceCts(additionalService, event.getCurrency()),
            additionalService.isFixPrice(),
            additionalService.getOrdinal(),
            additionalService.getAvailableQuantity(),
            additionalService.getMaxQtyPerOrder(),
            additionalService.getInception().toZonedDateTime(event.getZoneId()),
            additionalService.getExpiration().toZonedDateTime(event.getZoneId()),
            additionalService.getVat(),
            additionalService.getVatType(),
            additionalService.getType(),
            Objects.requireNonNullElse(additionalService.getSupplementPolicy(), OPTIONAL_UNLIMITED_AMOUNT),
            additionalService.getMinPrice() != null ? MonetaryUtil.unitToCents(additionalService.getMinPrice(), event.getCurrency()) : null,
            additionalService.getMaxPrice() != null ? MonetaryUtil.unitToCents(additionalService.getMaxPrice(), event.getCurrency()) : null);
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
                    evaluateAdditionalServicePriceCts(as, currencyCode),
                    as.isFixPrice(),
                    as.getOrdinal(),
                    as.getAvailableQuantity(),
                    as.getMaxQtyPerOrder(),
                    as.getInception().toZonedDateTime(zoneId),
                    as.getExpiration().toZonedDateTime(zoneId),
                    as.getVat(),
                    as.getVatType(),
                    as.getType(),
                    as.getSupplementPolicy(),
                    as.getMinPrice() != null ? MonetaryUtil.unitToCents(as.getMinPrice(), currencyCode) : null,
                    as.getMaxPrice() != null ? MonetaryUtil.unitToCents(as.getMaxPrice(), currencyCode) : null);
                if (as.getAvailableQuantity() > 0) {
                    preGenerateItems(service.getKey(), event, as);
                }
                as.getTitle().forEach(insertAdditionalServiceDescription(service.getKey()));
                as.getDescription().forEach(insertAdditionalServiceDescription(service.getKey()));
            });
        }
    }

    private static int evaluateAdditionalServicePriceCts(EventModification.AdditionalService as, String currencyCode) {
        if (AdditionalService.SupplementPolicy.isMandatoryPercentage(as.getSupplementPolicy())) {
            var decimalAwarePrice = Objects.requireNonNullElse(as.getPrice(), BigDecimal.ZERO)
                .multiply(HUNDRED)
                .setScale(0, HALF_UP);
            return decimalAwarePrice.intValueExact();
        } else {
            return as.getPrice() != null ? MonetaryUtil.unitToCents(as.getPrice(), currencyCode) : 0;
        }
    }

    private void preGenerateItems(int serviceId, Event event, EventModification.AdditionalService as) {
        if (!event.supportsLinkedAdditionalServices()) {
            LOGGER.trace("Event does not support linked additional services");
            return;
        }
        if (as.getAvailableQuantity() == -1) {
            // nothing to do here
            return;
        }
        // check how many are already defined
        int count = additionalServiceItemRepository.countItemsForService(serviceId);
        int requested = Math.max(0, as.getAvailableQuantity());
        if (count > requested) {
            LOGGER.debug("Requested {} items, found {}", requested, count);
            int result = additionalServiceItemRepository.invalidateItems(serviceId, count - requested);
            Validate.isTrue(result == count - requested, "Cannot reduce available items to "+requested);
        } else if(count < requested) {
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
        if (as.availableQuantity() > 0) {
            queryTemplate = additionalServiceItemRepository.batchUpdate();
            var ids = additionalServiceItemRepository.lockExistingItems(as.id(), quantity);
            if (ids.size() != quantity) {
                throw new NotEnoughItemsException();
            }
            for (int i = 0; i < quantity; i++) {
                batchReserveParameters.add(new MapSqlParameterSource("uuid", UUID.randomUUID().toString())
                    .addValue("ticketsReservationUuid", reservationId)
                    .addValue("additionalServiceId", as.id())
                    .addValue("status", AdditionalServiceItem.AdditionalServiceItemStatus.PENDING.name())
                    .addValue("id", ids.get(i))
                    .addValue("srcPriceCts", pc.getSrcPriceCts())
                    .addValue("finalPriceCts", unitToCents(pc.getFinalPrice(), currencyCode))
                    .addValue("vatCts", unitToCents(pc.getVAT(), currencyCode))
                    .addValue("discountCts", unitToCents(pc.getAppliedDiscount(), currencyCode))
                    .addValue("currencyCode", event.getCurrency()));
            }
        } else {
            queryTemplate = additionalServiceItemRepository.batchInsert();
            for (int i = 0; i < quantity; i++) {
                batchReserveParameters.add(buildInsertItemParameterSource(
                    as.id(),
                    reservationId,
                    AdditionalServiceItem.AdditionalServiceItemStatus.PENDING,
                    event,
                    pc.getSrcPriceCts(),
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

    public Map<Integer, AdditionalService.AdditionalServiceType> getTypeByIds(Collection<Integer> additionalServiceIds) {
        if (additionalServiceIds.isEmpty()) {
            return Map.of();
        }
        return additionalServiceRepository.getTypeByIds(additionalServiceIds);
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

    int updateStatusForReservationId(int eventId, String reservationId, AdditionalServiceItem.AdditionalServiceItemStatus additionalServiceItemStatus) {
        return additionalServiceItemRepository.updateItemsStatusWithReservationUUID(eventId, reservationId, additionalServiceItemStatus);
    }

    public List<AdditionalServiceItem> findItemsInReservation(int eventId, String reservationId) {
        return additionalServiceItemRepository.findByReservationUuid(eventId, reservationId);
    }

    public List<AdditionalServiceItem> findItemsForTicket(Ticket ticket) {
        return additionalServiceItemRepository.findByTicketId(ticket.getEventId(), ticket.getTicketsReservationId(), ticket.getId());
    }

    public List<AdditionalServiceItem> findItemsInReservation(PurchaseContext purchaseContext, String reservationId) {
        if (purchaseContext.ofType(PurchaseContext.PurchaseContextType.event)) {
            return findItemsInReservation(((Event)purchaseContext).getId(), reservationId);
        }
        return List.of();
    }

    public int countItemsInReservation(PurchaseContext purchaseContext, String reservationId) {
        if (purchaseContext.ofType(PurchaseContext.PurchaseContextType.event)) {
            return additionalServiceItemRepository.countByReservationUuid(((Event) purchaseContext).getId(), reservationId);
        }
        return 0;
    }

    Optional<AdditionalServiceText> loadItemTitle(AdditionalServiceItem asi, Locale locale) {
        return additionalServiceTextRepository.findByLocaleAndType(asi.getAdditionalServiceId(), locale.getLanguage(), AdditionalServiceText.TextType.TITLE);
    }

    boolean hasPaidSupplements(int eventId, String reservationId) {
        return additionalServiceItemRepository.hasPaidSupplements(eventId, reservationId);
    }

    List<AdditionalService> findAllInEventWithPolicy(int eventId, AdditionalService.SupplementPolicy supplementPolicy) {
        return additionalServiceRepository.findAllInEventWithPolicy(eventId, supplementPolicy);
    }

    public List<AdditionalService> loadAllForReservation(String reservationId, int eventId) {
        return additionalServiceRepository.loadAllForReservation(reservationId, eventId);
    }

    public void linkItemsToTickets(String reservationId,
                                      Map<String, List<AdditionalServiceLinkForm>> links,
                                      List<Ticket> tickets) {
        if (links == null || links.isEmpty()) {
            return;
        }
        var parameterSources = links.entrySet().stream()
            .flatMap(entry -> {
                var asl = entry.getValue();
                Integer ticketId = tickets.stream()
                    .filter(t -> StringUtils.isNotEmpty(entry.getKey()) && t.getPublicUuid().toString().equals(entry.getKey()))
                    .findFirst()
                    .map(Ticket::getId)
                    .orElse(null);
                return asl.stream().map(form -> batchLinkSource(reservationId, form.getAdditionalServiceItemId(), ticketId));
            }).toArray(MapSqlParameterSource[]::new);
        var results = jdbcTemplate.batchUpdate(additionalServiceItemRepository.batchLinkToTicket(), parameterSources);
        Validate.isTrue(Arrays.stream(results).allMatch(i -> i == 1));
    }

    private static MapSqlParameterSource batchLinkSource(String reservationId, int itemId, Integer ticketId) {
        return new MapSqlParameterSource("ticketId", ticketId)
            .addValue("itemId", itemId)
            .addValue("reservationId", reservationId);
    }


    public void bookAdditionalServicesForReservation(Event event,
                                                     String reservationId,
                                                     List<ASReservationWithOptionalCodeModification> additionalServices,
                                                     Optional<PromoCodeDiscount> discount) {
        var ticketIds = ticketRepository.findTicketIdsInReservation(reservationId);
        int ticketCount = ticketIds.size();
        // apply valid additional service with supplement policy mandatory one for ticket
        var additionalServicesForEvent = loadAllForEvent(event.getId());

        var automatic = additionalServicesForEvent.stream().filter(as -> as.supplementPolicy().isMandatory() && as.getSaleable())
            .map(as -> {
                AdditionalServiceReservationModification asrm = new AdditionalServiceReservationModification();
                asrm.setAdditionalServiceId(as.id());
                asrm.setQuantity(as.supplementPolicy() == MANDATORY_ONE_FOR_TICKET ? ticketCount : 1);
                return new ASReservationWithOptionalCodeModification(asrm, Optional.empty());
            }).toList();

        if (automatic.isEmpty() && additionalServices.isEmpty()) {
            // skip additional queries
            return;
        }
        var items = new ArrayList<>(automatic);
        items.addAll(additionalServices);
        reserveAdditionalServicesForReservation(event, reservationId, items, discount.orElse(null), additionalServicesForEvent, ticketIds);
    }

    public void persistFieldsForAdditionalItems(int eventId,
                                                int organizationId,
                                                Map<String, List<AdditionalServiceLinkForm>> additionalServices,
                                                List<Ticket> tickets) {
        var ticketIds = tickets.stream().map(Ticket::getId).collect(Collectors.toSet());
        int res = purchaseContextFieldRepository.deleteAllValuesForAdditionalItems(ticketIds, eventId);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Deleted {} field values", res);
        }
        var fields = purchaseContextFieldRepository.findAdditionalFieldsForEvent(eventId)
            .stream()
            .filter(c -> c.getContext() == PurchaseContextFieldConfiguration.Context.ADDITIONAL_SERVICE)
            .toList();

        var sources = additionalServices.entrySet().stream()
            .flatMap(entry -> entry.getValue().stream().flatMap(form -> form.getAdditional().entrySet().stream()
                .filter(e2 -> !e2.getValue().isEmpty())
                .map(e2 -> {
                    long configurationId = fields.stream().filter(f -> f.getName().equals(e2.getKey()))
                        .findFirst()
                        .orElseThrow()
                        .getId();
                    return new MapSqlParameterSource("additionalServiceItemId", form.getAdditionalServiceItemId())
                        .addValue("fieldConfigurationId", configurationId)
                        .addValue("value", purchaseContextFieldRepository.getFieldValueJson(e2.getValue()))
                        .addValue("organizationId", organizationId);
                }))).toArray(MapSqlParameterSource[]::new);
        var results = jdbcTemplate.batchUpdate(purchaseContextFieldRepository.batchInsertAdditionalItemsFields(), sources);
        Validate.isTrue(Arrays.stream(results).allMatch(r -> r == 1), "error while persisting additional fields");
    }

    private void reserveAdditionalServicesForReservation(Event event,
                                                         String reservationId,
                                                         List<ASReservationWithOptionalCodeModification> additionalServiceReservationList,
                                                         PromoCodeDiscount discount,
                                                         List<AdditionalService> additionalServicesForEvent,
                                                         List<Integer> ticketIds) {


        var allAdditionalItems = additionalServiceReservationList.stream()
            .filter(ar -> ar.getAdditionalServiceId() != null)
            .map(requested -> {
                var optionalAs = additionalServicesForEvent.stream()
                    .filter(as -> as.id() == requested.getAdditionalServiceId() && as.supplementPolicy() != null)
                    .findFirst();
                return new MappedRequestedService(requested, optionalAs.orElse(null));
            })
            .filter(o -> Objects.nonNull(o.additionalService))
            .collect(groupingBy(o -> o.additionalService.supplementPolicy()));

        // first handle MANDATORY_PERCENTAGE_FOR_TICKET, if any.
        // this way only ticket costs will be included in the percentage calculation
        handleMandatoryPercentage(MANDATORY_PERCENTAGE_FOR_TICKET, event, reservationId, discount, allAdditionalItems);

        // then apply all non-mandatory (i.e. user-selected)
        var nonMandatoryPolicies = AdditionalService.SupplementPolicy.userSelected();
        nonMandatoryPolicies.stream()
            .filter(allAdditionalItems::containsKey)
            .flatMap(p -> allAdditionalItems.get(p).stream().filter(as -> as.requested.getQuantity() > 0 && (as.additionalService.fixPrice() || requireNonNullElse(as.requested.getAmount(), BigDecimal.ZERO).compareTo(BigDecimal.ZERO) > 0)))
            .forEach(mapped -> {
                var as = mapped.additionalService;
                var additionalServiceReservation = mapped.requested;
                bookAdditionalServiceItems(additionalServiceReservation.getQuantity(), additionalServiceReservation.getAmount(), as, event, discount, reservationId);
            });

        // as last step, we apply all remaining mandatory
        handleMandatoryPercentage(MANDATORY_PERCENTAGE_RESERVATION, event, reservationId, discount, allAdditionalItems);

        allAdditionalItems.getOrDefault(MANDATORY_ONE_FOR_TICKET, List.of()).forEach(mrs -> {
            BigDecimal amount = mrs.requested.getAmount();
            bookAdditionalServiceItems(mrs.requested.getQuantity(), amount, mrs.additionalService, event, discount, reservationId);
        });

        // link additional services to tickets
        var bookedItems = additionalServiceItemRepository.findByReservationUuid(event.getId(), reservationId);
        //we skip donation as they don't have a supplement policy

        var parameterSources = allAdditionalItems.entrySet().stream()
            .flatMap(entry -> {
                var values = entry.getValue();
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Processing {} items with policy {}", values.size(), entry.getKey());
                }
                return values.stream()
                    .flatMap(m -> linkWithEveryTicket(reservationId, additionalServiceReservationList, bookedItems, ticketIds, m.additionalService));
            }).toArray(MapSqlParameterSource[]::new);
        var results = jdbcTemplate.batchUpdate(additionalServiceItemRepository.batchLinkToTicket(), parameterSources);
        Validate.isTrue(Arrays.stream(results).allMatch(i -> i == 1));

        // we attach all those without policy to the first ticket (donations)
        var firstTicketId = ticketIds.stream().findFirst().map(List::of).orElseThrow();
        var noPoliciesParameterSources = additionalServicesForEvent.stream()
            .filter(as -> as.supplementPolicy() == null && additionalServiceReservationList.stream().anyMatch(findAdditionalServiceRequest(as)))
            .flatMap(as -> linkWithEveryTicket(reservationId, additionalServiceReservationList, bookedItems, firstTicketId, as))
            .toArray(MapSqlParameterSource[]::new);
        var noPolicyResults = jdbcTemplate.batchUpdate(additionalServiceItemRepository.batchLinkToTicket(), noPoliciesParameterSources);
        Validate.isTrue(Arrays.stream(noPolicyResults).allMatch(i -> i == 1));
    }

    private void handleMandatoryPercentage(AdditionalService.SupplementPolicy supplementPolicy,
                                           Event event,
                                           String reservationId,
                                           PromoCodeDiscount discount,
                                           Map<AdditionalService.SupplementPolicy, List<MappedRequestedService>> allMapped) {
        if (allMapped.containsKey(supplementPolicy)) {
            final TotalPrice reservationPrice = reservationCostCalculator.totalReservationCostWithVAT(reservationId).getKey();
            allMapped.get(supplementPolicy).forEach(mrs -> {
                int basePrice = reservationPrice.getPriceWithVAT();
                var vatStatus = event.getVatStatus();
                if (PriceContainer.VatStatus.isVatNotIncluded(vatStatus)) {
                    basePrice -= reservationPrice.getVAT();
                }
                var percentage = new BigDecimal(String.valueOf(mrs.additionalService.srcPriceCts())).divide(HUNDRED, HALF_UP);
                int amountCts = adjustUsingMinMaxPrice(calcPercentage(basePrice, percentage, BigDecimal::intValueExact), mrs.additionalService);
                BigDecimal amount = centsToUnit(amountCts, reservationPrice.getCurrencyCode());
                bookAdditionalServiceItems(mrs.requested.getQuantity(), amount, mrs.additionalService, event, discount, reservationId);
            });
        }
    }

    private static int adjustUsingMinMaxPrice(int amountCts, AdditionalService additionalService) {
        if (additionalService.minPriceCts() != null && additionalService.minPriceCts() > amountCts) {
            // if calculated price is below minimum, we return the minimum price
            return additionalService.minPriceCts();
        }
        if (additionalService.maxPriceCts() != null && additionalService.maxPriceCts() < amountCts) {
            // if calculated price is over maximum, we return the maximum price
            return additionalService.maxPriceCts();
        }
        return amountCts;
    }

    private static Stream<MapSqlParameterSource> linkWithEveryTicket(String reservationId, List<ASReservationWithOptionalCodeModification> additionalServiceReservationList, List<AdditionalServiceItem> bookedItems, List<Integer> ticketIds, AdditionalService m) {
        var additionalServiceRequest = additionalServiceReservationList.stream()
            .filter(findAdditionalServiceRequest(m))
            .findFirst()
            .orElseThrow();
        var ticketIterator = new LoopingIterator<>(ticketIds); // using looping iterator to handle potential overflow
        return bookedItems.stream()
            .filter(i -> i.getAdditionalServiceId() == additionalServiceRequest.getAdditionalServiceId())
            .map(i -> batchLinkSource(reservationId, i.getId(), ticketIterator.next()));
    }

    private static Predicate<ASReservationWithOptionalCodeModification> findAdditionalServiceRequest(AdditionalService as) {
        return asr -> as.id() == asr.getAdditionalServiceId();
    }

    public void swapAdditionalServicesPosition(int eventId, int id1, int id2) {
        int id1Ordinal = additionalServiceRepository.getServiceOrdinal(id1, eventId).orElseThrow();
        int id2Ordinal = additionalServiceRepository.getServiceOrdinal(id2, eventId).orElseThrow();
        additionalServiceRepository.updateOrdinal(id1, id2Ordinal);
        additionalServiceRepository.updateOrdinal(id2, id1Ordinal);
    }

    record MappedRequestedService(ASReservationWithOptionalCodeModification requested, AdditionalService additionalService) {
    }
}
