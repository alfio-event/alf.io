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
package alfio.manager.system;

import alfio.manager.BillingDocumentManager;
import alfio.manager.TicketReservationManager;
import alfio.model.*;
import alfio.model.system.ConfigurationKeys;
import alfio.model.system.EventMigration;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.*;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.system.EventMigrationRepository;
import alfio.util.ClockProvider;
import alfio.util.MonetaryUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static alfio.model.PriceContainer.VatStatus.*;
import static alfio.repository.TicketRepository.RESET_TICKET;
import static alfio.util.Wrappers.optionally;
import static java.util.stream.Collectors.*;

@Component
@Transactional(readOnly = true)
@Log4j2
public class DataMigrator {

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d\\.)([0-9.]*)(-SNAPSHOT)?");
    private static final Map<String, String> PRICE_UPDATE_BY_KEY = new LinkedHashMap<>();
    private final EventMigrationRepository eventMigrationRepository;
    private final EventRepository eventRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final BigDecimal currentVersion;
    private final String currentVersionAsString;
    private final ZonedDateTime buildTimestamp;
    private final TransactionTemplate transactionTemplate;
    private final ConfigurationRepository configurationRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final TicketReservationManager ticketReservationManager;
    private final TicketSearchRepository ticketSearchRepository;
    private final PromoCodeDiscountRepository promoCodeDiscountRepository;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;
    private final AdditionalServiceRepository additionalServiceRepository;
    private final BillingDocumentManager billingDocumentManager;
    private final ClockProvider clockProvider;

    static {
        PRICE_UPDATE_BY_KEY.put("event", "update event set src_price_cts = :srcPriceCts, vat_status = :vatStatus where id = :eventId");
        PRICE_UPDATE_BY_KEY.put("category", "update ticket_category set src_price_cts = :srcPriceCts where id = :categoryId");
        PRICE_UPDATE_BY_KEY.put("ticket", "update ticket set src_price_cts = :srcPriceCts, final_price_cts = :finalPriceCts, vat_cts = :vatCts, discount_cts = :discountCts where id = :ticketId");
        PRICE_UPDATE_BY_KEY.put("additional_service", "update additional_service set src_price_cts = :srcPriceCts where id = :additionalServiceId");
        PRICE_UPDATE_BY_KEY.put("additional_service_item", "update additional_service_item set src_price_cts = :srcPriceCts, final_price_cts = :finalPriceCts, vat_cts = :vatCts where id = :additionalServiceItemId");
    }

    @Autowired
    public DataMigrator(EventMigrationRepository eventMigrationRepository,
                        EventRepository eventRepository,
                        TicketCategoryRepository ticketCategoryRepository,
                        @Value("${alfio.version}") String currentVersion,
                        @Value("${alfio.build-ts}") String buildTimestamp,
                        PlatformTransactionManager transactionManager,
                        ConfigurationRepository configurationRepository,
                        NamedParameterJdbcTemplate jdbc,
                        TicketReservationManager ticketReservationManager,
                        TicketSearchRepository ticketSearchRepository,
                        PromoCodeDiscountRepository promoCodeDiscountRepository,
                        AdditionalServiceItemRepository additionalServiceItemRepository,
                        AdditionalServiceRepository additionalServiceRepository,
                        BillingDocumentManager billingDocumentManager,
                        ClockProvider clockProvider) {
        this.eventMigrationRepository = eventMigrationRepository;
        this.eventRepository = eventRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.configurationRepository = configurationRepository;
        this.jdbc = jdbc;
        this.currentVersion = parseVersion(currentVersion);
        this.currentVersionAsString = currentVersion;
        this.buildTimestamp = ZonedDateTime.parse(buildTimestamp);
        this.transactionTemplate = new TransactionTemplate(transactionManager, new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        this.ticketReservationManager = ticketReservationManager;
        this.ticketSearchRepository = ticketSearchRepository;
        this.promoCodeDiscountRepository = promoCodeDiscountRepository;
        this.additionalServiceItemRepository = additionalServiceItemRepository;
        this.additionalServiceRepository = additionalServiceRepository;
        this.billingDocumentManager = billingDocumentManager;
        this.clockProvider = clockProvider;
    }

    public void migrateEventsToCurrentVersion() {
        List<Event> events = eventRepository.findAll();
        events.forEach(this::migrateEventToCurrentVersion);
        fillReservationsLanguage();
        fillDefaultOptions();
        fixReservationPrice(events);
        fixVatStatus();
    }

    private void fixVatStatus() {
        transactionTemplate.execute(ts -> {
            int rows = jdbc.update("update tickets_reservation set vat_status = e.vat_status from event e where tickets_reservation.vat_status is null and tickets_reservation.event_id_fk = e.id", Map.of());
            log.debug("update VAT/GST on {} reservations", rows);
            return null;
        });
    }

    private void fixReservationPrice(List<Event> events) {
        transactionTemplate.execute(ts -> {
            Map<Integer, List<String>> candidates = jdbc.queryForList("select id, event_id_fk from tickets_reservation where src_price_cts = 0 and payment_method <> 'NONE' and status not in ('CANCELLED', 'CREDIT_NOTE_ISSUED') order by 2", Map.of())
                .stream()
                .map(m -> Pair.of((Integer) m.get("event_id_fk"), (String) m.get("id")))
                .collect(groupingBy(Pair::getKey, mapping(Pair::getValue, toList())));

            for(var entry : candidates.entrySet()) {
                var event = events.stream().filter(e -> e.getId() == entry.getKey()).findFirst().orElseThrow();
                ListUtils.partition(entry.getValue(), 1000) // limit query size and batch update size
                    .forEach(reservations -> fixReservationsForEvent(event, reservations));
            }
            return null;
        });
    }

    private void fixReservationsForEvent(Event event, List<String> reservations) {
        var byReservationId = ticketSearchRepository.loadAllReservationsWithTickets(event.getId(), reservations).stream()
            .collect(groupingBy(trt -> trt.getTicketReservation().getId()));
        log.trace("found {} reservations to fix for event {}", byReservationId.size(), event.getShortName());
        if(!byReservationId.isEmpty()) {
            var additionalServices = additionalServiceRepository.loadAllForEvent(event.getId());
            var reservationsToUpdate = byReservationId.values().stream()
                .map(ticketsReservationAndTransactions -> {
                    var tickets = ticketsReservationAndTransactions.stream().map(TicketWithReservationAndTransaction::getTicket).collect(toList());
                    var ticketReservation = ticketsReservationAndTransactions.get(0).getTicketReservation();
                    var promoCodeDiscountId = ticketReservation.getPromoCodeDiscountId();
                    var discount = promoCodeDiscountId != null ? promoCodeDiscountRepository.findById(promoCodeDiscountId) : null;
                    var additionalServiceItems = additionalServiceItemRepository.findByReservationUuid(event.getId(), ticketReservation.getId());
                    var calculator = new ReservationPriceCalculator(ticketReservation, discount, tickets, additionalServiceItems, additionalServices, event, List.of(), Optional.empty());
                    var currencyCode = calculator.getCurrencyCode();
                    return new MapSqlParameterSource("reservationId", calculator.reservation.getId())
                        .addValue("srcPrice", calculator.getSrcPriceCts())
                        .addValue("finalPrice", MonetaryUtil.unitToCents(calculator.getFinalPrice(), currencyCode))
                        .addValue("discount", MonetaryUtil.unitToCents(calculator.getAppliedDiscount(), currencyCode))
                        .addValue("vat", MonetaryUtil.unitToCents(calculator.getVAT(), currencyCode))
                        .addValue("currencyCode", currencyCode);
                }).toArray(MapSqlParameterSource[]::new);
            log.trace("updating {} reservations", reservationsToUpdate.length);
            int[] results = jdbc.batchUpdate("update tickets_reservation set src_price_cts = :srcPrice, final_price_cts = :finalPrice, discount_cts = :discount, vat_cts = :vat, currency_code = :currencyCode where id = :reservationId", reservationsToUpdate);
            int sum = IntStream.of(results).sum();
            if(sum != reservationsToUpdate.length) {
                log.warn("Expected {} reservations to be affected, actual number: {}", reservationsToUpdate.length, sum);
            }
        }
    }

    private void fillDefaultOptions() {
        transactionTemplate.execute(ts -> {
            Integer count = jdbc.queryForObject("select count(*) from configuration where c_key = :key", new MapSqlParameterSource("key", ConfigurationKeys.GOOGLE_ANALYTICS_ANONYMOUS_MODE.getValue()), Integer.class);
            if(count == null || count == 0) {
                configurationRepository.insert(ConfigurationKeys.GOOGLE_ANALYTICS_ANONYMOUS_MODE.getValue(), "true", ConfigurationKeys.GOOGLE_ANALYTICS_ANONYMOUS_MODE.getDescription());
            }
            return null;
        });
    }

    private void migrateEventToCurrentVersion(Event event) {
        Optional<EventMigration> optional = optionally(() -> eventMigrationRepository.loadEventMigration(event.getId()));
        boolean alreadyDefined = optional.isPresent();
        if(!alreadyDefined || optional.filter(this::needsFixing).isPresent()) {
            transactionTemplate.execute(s -> {
                //optional.ifPresent(eventMigration -> eventMigrationRepository.lockEventMigrationForUpdate(eventMigration.getId()));
                if(event.now(clockProvider).isBefore(event.getEnd())) {
                    fixAvailableSeats(event);
                    fillDescriptions(event);
                    fixCategoriesSize(event);
                }

                //migrate prices to new structure. This should be done for all events, regardless of the expiration date.
                migratePrices(event.getId());
                fixStuckTickets(event.getId());
                createBillingDocuments(event);

                if(alreadyDefined) {
                    EventMigration eventMigration = optional.get();
                    int result = eventMigrationRepository.updateMigrationData(eventMigration.getId(), currentVersionAsString, buildTimestamp, EventMigration.Status.COMPLETE.name());
                    Validate.isTrue(result == 1, "error during update " + result);
                } else {
                    eventMigrationRepository.insertMigrationData(event.getId(), currentVersionAsString, buildTimestamp, EventMigration.Status.COMPLETE.name());
                }

                return null;
            });
        }
    }

    private void createBillingDocuments(Event event) {
        if(event.getEnd().isAfter(event.now(clockProvider))) {
            List<String> reservations = jdbc.queryForList("select id from tickets_reservation where event_id_fk = :eventId and status in ('OFFLINE_PAYMENT', 'COMPLETE') and invoice_number is not null and id not in(select distinct reservation_id_fk from billing_document where event_id_fk = :eventId)", new MapSqlParameterSource("eventId", event.getId()), String.class);
            if(reservations.isEmpty()) {
                return;
            }
            log.info("creating BillingDocument(s) for event {}", event.getDisplayName());
            for (String reservationId : reservations) {
                TicketReservation reservation = ticketReservationManager.findById(reservationId).orElseThrow(IllegalStateException::new);
                billingDocumentManager.getOrCreateBillingDocument(event, reservation, null, ticketReservationManager.orderSummaryForReservation(reservation, event));
            }
            log.info("checked {} BillingDocument(s) for event {}", reservations.size(), event.getDisplayName());
        }
    }

    void fixStuckTickets(int eventId) {
        List<Integer> ticketIds = jdbc.queryForList("select a.id from ticket a, tickets_reservation b where a.event_id = :eventId and a.status in('PENDING','TO_BE_PAID') and a.tickets_reservation_id = b.id and b.status = 'CANCELLED'", new MapSqlParameterSource("eventId", eventId), Integer.class);
        if(!ticketIds.isEmpty()) {
            int toBeFixed = ticketIds.size();
            log.warn("********* reverting {} stuck tickets ({}) for event id {}", toBeFixed, ticketIds, eventId);
            int[] results = jdbc.batchUpdate("update ticket set status = 'RELEASED'," + RESET_TICKET + " where id = :ticketId", ticketIds.stream().map(id -> new MapSqlParameterSource("ticketId", id)).toArray(MapSqlParameterSource[]::new));
            int result = Arrays.stream(results).sum();
            Validate.isTrue(result == toBeFixed, "Error while fixing stuck tickets: expected "+toBeFixed+", got "+result);
        }
    }

    void fixCategoriesSize(EventAndOrganizationId event) {
        ticketCategoryRepository.findAllTicketCategories(event.getId()).stream()
            .filter(TicketCategory::isBounded)
            .forEach(tc -> {
                Integer result = jdbc.queryForObject("select count(*) from ticket where event_id = :eventId and category_id = :categoryId and status <> 'INVALIDATED'", new MapSqlParameterSource("eventId", tc.getEventId()).addValue("categoryId", tc.getId()), Integer.class);
                if(result != null && result != tc.getMaxTickets()) {
                    log.warn("********* updating category size for {} from {} to {} tickets", tc.getName(), tc.getMaxTickets(), result);
                    ticketCategoryRepository.updateSeatsAvailability(tc.getId(), result);
                }
            });

    }

    void fillReservationsLanguage() {
        transactionTemplate.execute(s -> {
            jdbc.queryForList("select id from tickets_reservation where user_language is null", EmptySqlParameterSource.INSTANCE, String.class)
                    .forEach(id -> {
                        MapSqlParameterSource param = new MapSqlParameterSource("reservationId", id);
                        String language = optionally(() -> jdbc.queryForObject("select user_language from ticket where tickets_reservation_id = :reservationId limit 1", param, String.class)).orElse("en");
                        jdbc.update("update tickets_reservation set user_language = :userLanguage where id = :reservationId", param.addValue("userLanguage", language));
                    });
            return null;
        });
    }

    private void fillDescriptions(Event event) {
        int result = eventRepository.fillDisplayNameIfRequired(event.getId());
        if(result > 0) {
            log.debug("Event {} didn't have displayName, filled with shortName", event.getShortName());
        }
    }

    /*
     * even if we don't actively use the "available_seats" event property anymore, it makes sense to keep it synchronized
     * in order to ensure backward compatibility
     */
    private void fixAvailableSeats(Event event) {
        try {
            int availableSeats = eventRepository.countExistingTickets(event.getId());
            var paymentProxies = event.getAllowedPaymentProxies().stream().map(PaymentProxy::name).collect(joining(","));
            eventRepository.updatePrices(event.getCurrency(), availableSeats, event.isVatIncluded(),
                event.getVat(), paymentProxies, event.getId(), event.getVatStatus(), event.getSrcPriceCts());
        } catch (Exception ex) {
            log.trace("got exception while fixing available seats", ex);
        }
    }

    boolean needsFixing(EventMigration eventMigration) {
        return eventMigration.getBuildTimestamp().isBefore(buildTimestamp) || parseVersion(eventMigration.getCurrentVersion()).compareTo(currentVersion) < 0;
    }

    static BigDecimal parseVersion(String version) {
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if(!matcher.find()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(matcher.group(1) + matcher.group(2).replaceAll("\\.", ""));
    }

    private void migratePrices(final int eventId) {
        final Map<String, Integer> eventIdParam = Collections.singletonMap("eventId", eventId);
        final String srcPriceCtsParam = "srcPriceCts";
        Map<String, List<MapSqlParameterSource>> migrationData = jdbc.queryForList("select * from event where type = :type and id = :eventId and regular_price_cts > 0", new MapSqlParameterSource("type", "INTERNAL").addValue("eventId", eventId))
            .stream()
            .flatMap(event -> {
                //fill the event prices
                boolean eventVatIncluded = (boolean) event.get("vat_included");
                BigDecimal vatPercentage = Optional.ofNullable((BigDecimal) event.get("vat")).orElse(BigDecimal.ZERO);
                int price = (int) event.get("regular_price_cts");
                String currencyCode = (String) event.get("currency");
                int eventSrcPrice = eventVatIncluded ? MonetaryUtil.addVAT(price, vatPercentage) : price;

                List<Pair<String, MapSqlParameterSource>> modifications = new ArrayList<>();

                if (((int) event.get("src_price_cts")) == 0) {
                    modifications.add(Pair.of("event", new MapSqlParameterSource(srcPriceCtsParam, eventSrcPrice)
                        .addValue("vatStatus", eventVatIncluded ? INCLUDED.name() : NOT_INCLUDED.name())
                        .addValue("eventId", eventId)));
                }

                //ticket categories
                modifications.addAll(collectTicketCategoryMigrationData(srcPriceCtsParam, eventVatIncluded, vatPercentage, eventIdParam));

                //tickets
                modifications.addAll(collectTicketMigrationData(srcPriceCtsParam, eventVatIncluded, vatPercentage, currencyCode, eventId, eventIdParam));

                //additional_service
                modifications.addAll(collectASMigrationData(srcPriceCtsParam, eventVatIncluded, vatPercentage, eventIdParam));

                //additional_service_item
                modifications.addAll(collectASItemMigrationData(srcPriceCtsParam, eventVatIncluded, vatPercentage, currencyCode, eventIdParam));

                log.debug("Price migration: got {} modifications for event {}", modifications.size(), event.get("short_name"));
                return modifications.stream();
            }).collect(groupingBy(Pair::getKey, mapping(Pair::getValue, toList())));

        if(migrationData.size() > 0) {
            log.debug("Price migration: got modifications for: {}", migrationData.keySet());
            PRICE_UPDATE_BY_KEY.entrySet().stream()
                .filter(e -> migrationData.containsKey(e.getKey()))
                .map(e -> Pair.of(e, migrationData.get(e.getKey())))
                .forEach(p -> {
                    Map.Entry<String, String> entry = p.getLeft();
                    log.debug("migrating {} prices...", entry.getKey());
                    performPriceMigration(entry.getValue(), p.getRight(), jdbc);
                });
        }
    }

    private static void performPriceMigration(String updateStatement, List<MapSqlParameterSource> data, NamedParameterJdbcTemplate jdbc) {
        int size = data.size();
        jdbc.batchUpdate(updateStatement, data.toArray(new MapSqlParameterSource[size]));
        log.debug("{} records updated", size);
    }

    private List<Pair<String, MapSqlParameterSource>> collectASItemMigrationData(String srcPriceCtsParam, final boolean eventVatIncluded, final BigDecimal vatPercentage, final String currencyCode, Map<String, Integer> eventIdParam) {
        return jdbc.queryForList("select ai.id as id, ai.paid_price_cts as paid_price_cts, a.vat_type as vat_type from additional_service_item ai, additional_service a where ai.paid_price_cts > 0 and ai.src_price_cts = 0 and ai.event_id_fk = :eventId", eventIdParam).stream()
            .map(item -> {
                int oldPrice = (int) item.get("paid_price_cts");
                AdditionalService.VatType vatType = AdditionalService.VatType.valueOf((String) item.get("vat_type"));
                return Pair.of((Integer) item.get("id"), new PriceContainer() {
                    @Override
                    public int getSrcPriceCts() {
                        return eventVatIncluded ? MonetaryUtil.addVAT(oldPrice, vatPercentage) : oldPrice;
                    }

                    @Override
                    public String getCurrencyCode() {
                        return currencyCode;
                    }

                    @Override
                    public Optional<BigDecimal> getOptionalVatPercentage() {
                        return Optional.of(vatPercentage);
                    }

                    @Override
                    public VatStatus getVatStatus() {
                        if (vatType == AdditionalService.VatType.INHERITED) {
                            return eventVatIncluded ? INCLUDED : NOT_INCLUDED;
                        }
                        return NONE;//no need to check for other VatTypes. At the time of writing VAT override strategy has not yet been implemented.
                    }
                });
            })
            .map(p -> {
                PriceContainer priceContainer = p.getValue();
                return Pair.of("additional_service_item", new MapSqlParameterSource(srcPriceCtsParam, priceContainer.getSrcPriceCts())
                    .addValue("finalPriceCts", MonetaryUtil.unitToCents(priceContainer.getFinalPrice(), currencyCode))
                    .addValue("vatCts", MonetaryUtil.unitToCents(priceContainer.getVAT(), currencyCode))
                    .addValue("additionalServiceItemId", p.getKey()));
            }).collect(toList());
    }

    private List<Pair<String, MapSqlParameterSource>> collectASMigrationData(String srcPriceCtsParam, boolean eventVatIncluded, BigDecimal vatPercentage, Map<String, Integer> eventIdParam) {
        return jdbc.queryForList("select id, price_cts, vat_type from additional_service where event_id_fk = :eventId and fix_price = true and price_cts > 0 and src_price_cts = 0", eventIdParam)
            .stream()
            .map(as -> {
                int priceCts = (int) as.get("price_cts");
                AdditionalService.VatType vatType = AdditionalService.VatType.valueOf((String) as.get("vat_type"));
                int srcPrice = vatType == AdditionalService.VatType.INHERITED && eventVatIncluded ? MonetaryUtil.addVAT(priceCts, vatPercentage) : priceCts;
                return Pair.of("additional_service", new MapSqlParameterSource(srcPriceCtsParam, srcPrice).addValue("additionalServiceId", as.get("id")));
            }).collect(toList());
    }

    private List<Pair<String, MapSqlParameterSource>> collectTicketMigrationData(String srcPriceCtsParam, final boolean eventVatIncluded, final BigDecimal vatPercentage, final String currencyCode, final int eventId, Map<String, Integer> eventIdParam) {
        return jdbc.queryForList("select ticket.id as id, ticket.original_price_cts as original_price_cts, ticket.paid_price_cts as paid_price_cts, promo_code.discount_amount as discount_amount, promo_code.discount_type as discount_type from ticket join tickets_reservation on tickets_reservation.id = ticket.tickets_reservation_id left join promo_code on tickets_reservation.promo_code_id_fk = promo_code.id where ticket.event_id = :eventId and ticket.original_price_cts > 0 and ticket.src_price_cts = 0", eventIdParam)
            .stream()
            .map(ticket -> {
                int oldTicketPrice = (int) ticket.get("original_price_cts");
                return Pair.of((Integer) ticket.get("id"), new PriceContainer() {
                    @Override
                    public int getSrcPriceCts() {
                        return eventVatIncluded ? MonetaryUtil.addVAT(oldTicketPrice, vatPercentage) : oldTicketPrice;
                    }

                    @Override
                    public String getCurrencyCode() {
                        return currencyCode;
                    }

                    @Override
                    public Optional<BigDecimal> getOptionalVatPercentage() {
                        return Optional.of(vatPercentage);
                    }

                    @Override
                    public VatStatus getVatStatus() {
                        return eventVatIncluded ? INCLUDED : NOT_INCLUDED;
                    }

                    @Override
                    public Optional<PromoCodeDiscount> getDiscount() {
                        return Optional.ofNullable(ticket.get("discount_amount"))
                            .map(amount -> new PromoCodeDiscount(0, "", eventId, null, null, null, (int) amount, PromoCodeDiscount.DiscountType.valueOf((String) ticket.get("discount_type")), "", null, null, null, PromoCodeDiscount.CodeType.DISCOUNT, null, currencyCode));
                    }
                });
            })
            .map(p -> {
                    PriceContainer priceContainer = p.getValue();
                    return Pair.of("ticket", new MapSqlParameterSource(srcPriceCtsParam, priceContainer.getSrcPriceCts())
                        .addValue("finalPriceCts", MonetaryUtil.unitToCents(priceContainer.getFinalPrice(), currencyCode))
                        .addValue("vatCts", MonetaryUtil.unitToCents(priceContainer.getVAT(), currencyCode))
                        .addValue("discountCts", MonetaryUtil.unitToCents(priceContainer.getAppliedDiscount(), currencyCode))
                        .addValue("ticketId", p.getKey()));
                }
            ).collect(toList());
    }

    private List<Pair<String, MapSqlParameterSource>> collectTicketCategoryMigrationData(String srcPriceCtsParam, boolean eventVatIncluded, BigDecimal vatPercentage, Map<String, Integer> eventIdParam) {
        return jdbc.queryForList("select id, price_cts from ticket_category where event_id = :eventId and price_cts > 0 and src_price_cts = 0", eventIdParam)
            .stream()
            .map(category -> {
                int oldCategoryPrice = (int) category.get("price_cts");
                int categorySrcPrice = eventVatIncluded ? MonetaryUtil.addVAT(oldCategoryPrice, vatPercentage) : oldCategoryPrice;
                return Pair.of("category", new MapSqlParameterSource(srcPriceCtsParam, categorySrcPrice).addValue("categoryId", category.get("id")));
            })
            .collect(toList());
    }

}
