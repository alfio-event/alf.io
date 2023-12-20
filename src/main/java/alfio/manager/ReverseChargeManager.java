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

import alfio.controller.form.ContactAndTicketsForm;
import alfio.controller.support.CustomBindingResult;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.ReservationPriceCalculator;
import alfio.model.*;
import alfio.model.PurchaseContext.PurchaseContextType;
import alfio.model.decorator.TicketPriceContainer;
import alfio.model.extension.CustomTaxPolicy;
import alfio.repository.*;
import alfio.util.MonetaryUtil;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ValidationUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static alfio.model.Audit.EntityType.RESERVATION;
import static alfio.model.PriceContainer.VatStatus.*;
import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.MonetaryUtil.unitToCents;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@AllArgsConstructor
@Component
public class ReverseChargeManager {

    private static final Logger log = LoggerFactory.getLogger(ReverseChargeManager.class);
    private final PromoCodeDiscountRepository promoCodeDiscountRepository;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;
    private final AdditionalServiceRepository additionalServiceRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ConfigurationManager configurationManager;
    private final TicketReservationRepository ticketReservationRepository;
    private final EuVatChecker vatChecker;
    private final TicketReservationManager ticketReservationManager;
    private final TicketRepository ticketRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AuditingRepository auditingRepository;




    public void checkAndApplyVATRules(PurchaseContext purchaseContext,
                                       String reservationId,
                                       ContactAndTicketsForm contactAndTicketsForm,
                                       BindingResult bindingResult) {
        String country = contactAndTicketsForm.getVatCountryCode();

        // validate VAT presence if Reverse Charge is enabled
        var reverseChargeConfiguration = EuVatChecker.loadConfigurationForReverseChargeCheck(configurationManager, purchaseContext);

        if (EuVatChecker.reverseChargeEnabled(reverseChargeConfiguration) && (country == null || isEUCountry(country))) {
            ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "vatNr", "error.emptyField");
        }

        boolean isEvent = purchaseContext.ofType(PurchaseContextType.event);
        // we must take into account specific configuration only if the purchase context is an Event.
        // Otherwise, specific settings do not apply
        boolean reverseChargeInPerson = !isEvent || reverseChargeConfiguration.get(ENABLE_REVERSE_CHARGE_IN_PERSON).getValueAsBooleanOrDefault();
        boolean reverseChargeOnline = !isEvent || reverseChargeConfiguration.get(ENABLE_REVERSE_CHARGE_ONLINE).getValueAsBooleanOrDefault();

        try {
            var optionalReservation = ticketReservationRepository.findOptionalReservationById(reservationId);

            List<TicketCategory> categoriesList = optionalReservation
                .filter(res -> isEvent) //skip load if the current context is not "event"
                .map(res -> ticketCategoryRepository.findCategoriesInReservation(res.getId()))
                .orElse(List.of());

            List<Integer> noTaxCategories = new ArrayList<>();
            if (!categoriesList.isEmpty()) {
                noTaxCategories.addAll(configurationManager.getCategoriesWithNoTaxes(categoriesList.stream().map(TicketCategory::getId).collect(Collectors.toList())));
            }

            Optional<VatDetail> vatDetail = performReverseChargeCheck(purchaseContext, contactAndTicketsForm, country, reverseChargeInPerson, reverseChargeOnline, optionalReservation, categoriesList);

            if(vatDetail.isPresent()) {
                handleValidationResult(purchaseContext, reservationId, contactAndTicketsForm, bindingResult, country, isEvent, reverseChargeInPerson, reverseChargeOnline, categoriesList, noTaxCategories, vatDetail.get());
            } else if (optionalReservation.isPresent() && contactAndTicketsForm.isItalyEInvoicingSplitPayment()) {
                handleSplitPayment(purchaseContext, reservationId, contactAndTicketsForm, country, optionalReservation.get(), categoriesList, noTaxCategories);
            } else if (optionalReservation.isPresent() && !noTaxCategories.isEmpty()) {
                // if there is at least one category with custom tax policy, we need to update its tickets
                var reservation = optionalReservation.get();
                updateTicketsWithNoTaxSetting(purchaseContext, reservationId, reservation, categoriesList, noTaxCategories);
                updateBillingData(contactAndTicketsForm, purchaseContext, country, trimToNull(contactAndTicketsForm.getVatNr()), reservation, reservation.getVatStatus());
            }
        } catch (IllegalStateException ise) {//vat checker failure
            bindingResult.rejectValue("vatNr", "error.vatVIESDown");
        }
    }

    private Optional<VatDetail> performReverseChargeCheck(PurchaseContext purchaseContext, ContactAndTicketsForm contactAndTicketsForm, String country, boolean reverseChargeInPerson, boolean reverseChargeOnline, Optional<TicketReservation> optionalReservation, List<TicketCategory> categoriesList) {
        return optionalReservation
            .filter(e -> {
                if (purchaseContext.ofType(PurchaseContextType.event) && (!reverseChargeInPerson || !reverseChargeOnline)) {
                    var eventFormat = purchaseContext.event().orElseThrow().getFormat();
                    // if we find at least one category matching the criteria, then we can proceed
                    return categoriesList.stream().anyMatch(findReverseChargeCategory(reverseChargeInPerson, reverseChargeOnline, eventFormat));
                }
                var vatStatus = purchaseContext.getVatStatus();
                return vatStatus == INCLUDED || vatStatus == NOT_INCLUDED;
            })
            .filter(e -> vatChecker.isReverseChargeEnabledFor(purchaseContext))
            .flatMap(e -> vatChecker.checkVat(contactAndTicketsForm.getVatNr(), country, purchaseContext));
    }

    private void updateTicketsWithNoTaxSetting(PurchaseContext purchaseContext,
                                               String reservationId,
                                               TicketReservation reservation,
                                               List<TicketCategory> categoriesList,
                                               List<Integer> noTaxCategories) {
        if (purchaseContext.ofType(PurchaseContextType.event)) {
            var currencyCode = reservation.getCurrencyCode();
            var event = purchaseContext.event().orElseThrow();
            var priceContainers = mapPriceContainersByCategoryId(categoriesList,
                c -> noTaxCategories.contains(c.getId()),
                currencyCode,
                reservation.getVatStatus(),
                reservation.getDiscount().orElse(null),
                event,
                noTaxCategories);
            updateTicketPricesByCategory(reservationId, currencyCode, event, priceContainers);
        }
    }

    private void handleSplitPayment(PurchaseContext purchaseContext, String reservationId, ContactAndTicketsForm contactAndTicketsForm, String country, TicketReservation reservation, List<TicketCategory> categoriesList, List<Integer> noTaxCategories) {
        updateTicketsWithNoTaxSetting(purchaseContext, reservationId, reservation, categoriesList, noTaxCategories);
        var vatStatus = purchaseContext.getVatStatus() == INCLUDED ? INCLUDED_NOT_CHARGED : NOT_INCLUDED_NOT_CHARGED;
        updateBillingData(contactAndTicketsForm, purchaseContext, country, trimToNull(contactAndTicketsForm.getVatNr()), reservation, vatStatus);
    }

    private void handleValidationResult(PurchaseContext purchaseContext, String reservationId, ContactAndTicketsForm contactAndTicketsForm, BindingResult bindingResult, String country, boolean isEvent, boolean reverseChargeInPerson, boolean reverseChargeOnline, List<TicketCategory> categoriesList, List<Integer> noTaxCategories, VatDetail vatValidation) {
        if (!vatValidation.isValid()) {
            bindingResult.rejectValue("vatNr", "error.STEP_2_INVALID_VAT");
        } else {
            var reservation = ticketReservationManager.findById(reservationId).orElseThrow();
            var currencyCode = reservation.getCurrencyCode();
            PriceContainer.VatStatus vatStatus = determineVatStatus(purchaseContext.getVatStatus(), vatValidation.isVatExempt());
            // standard case: Reverse Charge is applied to the entire reservation
            var discount = getDiscountOrNull(reservation);
            if(!isEvent || (reverseChargeOnline && reverseChargeInPerson)) {
                if(isEvent) {
                    var event = purchaseContext.event().orElseThrow();
                    var priceContainers = mapPriceContainersByCategoryId(categoriesList,
                        a -> true,
                        currencyCode,
                        vatStatus,
                        discount,
                        event,
                        noTaxCategories);
                    // update all tickets in reservation to match the VAT_STATUS
                    ticketRepository.updateVatStatusForReservation(reservationId, vatStatus);
                    updateTicketPricesByCategory(reservationId, currencyCode, event, priceContainers);
                }
                updateBillingData(contactAndTicketsForm, purchaseContext, country, trimToNull(vatValidation.getVatNr()), reservation, vatStatus);
            } else {
                var event = purchaseContext.event().orElseThrow();
                var eventFormat = event.getFormat();
                var matchingCategories = mapPriceContainersByCategoryId(categoriesList,
                    findReverseChargeCategory(reverseChargeInPerson, reverseChargeOnline, eventFormat),
                    currencyCode,
                    vatStatus,
                    discount,
                    event,
                    noTaxCategories);

                updateTicketPricesByCategory(reservationId, currencyCode, event, matchingCategories);
                // update billing data for the reservation, using the original VatStatus from reservation
                updateBillingData(contactAndTicketsForm, purchaseContext, country, trimToNull(vatValidation.getVatNr()), reservation, reservation.getVatStatus());
            }
            vatChecker.logSuccessfulValidation(vatValidation, reservationId, purchaseContext);
        }
    }

    private PromoCodeDiscount getDiscountOrNull(TicketReservation reservation) {
        if (reservation.getPromoCodeDiscountId() != null) {
            return promoCodeDiscountRepository.findById(reservation.getPromoCodeDiscountId());
        }
        return null;
    }

    public void applyCustomTaxPolicy(PurchaseContext purchaseContext,
                                     CustomTaxPolicy customTaxPolicy,
                                     String reservationId,
                                     ContactAndTicketsForm contactAndTicketsForm,
                                     CustomBindingResult bindingResult) {

        if (!purchaseContext.ofType(PurchaseContextType.event)) {
            throw new IllegalStateException("Custom tax policy is only supported for events");
        }

        var event = (Event) purchaseContext;
        // first, validate that categories in CustomTaxPolicy are actually present in the form
        var reservation = ticketReservationManager.findById(reservationId).orElseThrow();
        var currencyCode = reservation.getCurrencyCode();
        var ticketsInReservation = ticketRepository.findTicketsInReservation(reservationId).stream()
            .collect(toMap(Ticket::getUuid, Function.identity()));
        var ticketIds = ticketsInReservation.keySet();
        if (customTaxPolicy.getTicketPolicies().stream().anyMatch(tp -> !ticketIds.contains(tp.getUuid()))) {
            log.warn("Error in custom tax policy: some tickets are not included in reservation {}", reservationId);
            bindingResult.reject("error.generic");
        } else {
            // log the received policy to the auditing
            auditingRepository.insert(reservationId, null, purchaseContext, Audit.EventType.VAT_CUSTOM_CONFIGURATION_APPLIED, new Date(), RESERVATION, reservationId, List.of(Map.of("policy", customTaxPolicy)));
            var priceMapping = customTaxPolicy.getTicketPolicies().stream()
                .map(tcp -> toTicketPriceContainer(ticketsInReservation.get(tcp.getUuid()), tcp, getDiscountOrNull(reservation), event))
                .collect(Collectors.toList());
            updateTicketPrices(priceMapping, currencyCode, event);
            // update billing data for the reservation, using the original VatStatus from reservation
            updateBillingData(contactAndTicketsForm, purchaseContext, reservation.getVatCountryCode(), trimToNull(reservation.getVatNr()), reservation, reservation.getVatStatus());
        }
    }

    private static TicketPriceContainer toTicketPriceContainer(Ticket ticket,
                                                               CustomTaxPolicy.TicketTaxPolicy categoryTaxPolicy,
                                                               PromoCodeDiscount discount,
                                                               Event event) {
        return TicketPriceContainer.from(
            ticket.withVatStatus(categoryTaxPolicy.getTaxPolicy()),
            categoryTaxPolicy.getTaxPolicy(),
            event.getVat(),
            event.getVatStatus(),
            discount
        );
    }

    public void resetVat(PurchaseContext purchaseContext, String reservationId) {
        if(purchaseContext.ofType(PurchaseContextType.event)) {
            var reservation = ticketReservationRepository.findReservationById(reservationId);
            var categoriesList = ticketCategoryRepository.findCategoriesInReservation(reservationId);
            var discount = getDiscountOrNull(reservation);
            var event = purchaseContext.event().orElseThrow();
            var priceContainers = mapPriceContainersByCategoryId(categoriesList,
                (a) -> true,
                reservation.getCurrencyCode(),
                reservation.getVatStatus(),
                discount,
                event,
                List.of());
            // update all tickets in reservation to match the VAT_STATUS
            ticketRepository.updateVatStatusForReservation(reservationId, reservation.getVatStatus());
            updateTicketPricesByCategory(reservationId, reservation.getCurrencyCode(), event, priceContainers);
        }
    }

    private Map<Integer, TicketCategoryPriceContainer> mapPriceContainersByCategoryId(List<TicketCategory> categoriesList,
                                                                                      Predicate<TicketCategory> filter,
                                                                                      String currencyCode,
                                                                                      PriceContainer.VatStatus vatStatus,
                                                                                      PromoCodeDiscount discount,
                                                                                      Event event,
                                                                                      List<Integer> noTaxCategories) {
        return categoriesList.stream()
            .filter(filter)
            .map(c -> {
                var categoryVatStatus = vatStatus;
                if (noTaxCategories.contains(c.getId())) {
                    categoryVatStatus = PriceContainer.VatStatus.forceExempt(vatStatus);
                }
                return new TicketCategoryPriceContainer(c.getId(), c.getSrcPriceCts(), currencyCode, event.getVat(), categoryVatStatus, discount);
            })
            .collect(toMap(o -> o.categoryId, Function.identity()));
    }

    private void updateTicketPricesByCategory(String reservationId,
                                              String currencyCode,
                                              Event event,
                                              Map<Integer, TicketCategoryPriceContainer> matchingCategories) {
        MapSqlParameterSource[] parameterSources = matchingCategories.entrySet().stream()
            .map(entry -> buildParameterSourceForPriceUpdate(entry.getValue(), event.getId(), entry.getKey(), currencyCode,
                m -> m.addValue("reservationId", reservationId)))
            .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(ticketRepository.updateTicketPriceForCategoryInReservation(), parameterSources);
    }

    private void updateTicketPrices(List<TicketPriceContainer> ticketPrices, String currencyCode, Event event) {
        MapSqlParameterSource[] parameterSources = ticketPrices.stream()
            .map(entry -> buildParameterSourceForPriceUpdate(entry, event.getId(), entry.getCategoryId(), currencyCode,
                m -> m.addValue("uuid", entry.getUuid())))
            .toArray(MapSqlParameterSource[]::new);
        var updateResult = jdbcTemplate.batchUpdate(ticketRepository.bulkUpdateTicketPrice(), parameterSources);
        Validate.isTrue(Arrays.stream(updateResult).allMatch(i -> i == 1), "Error while updating ticket prices");
    }

    private static MapSqlParameterSource buildParameterSourceForPriceUpdate(PriceContainer value,
                                                                            int eventId,
                                                                            int categoryId,
                                                                            String currencyCode,
                                                                            UnaryOperator<MapSqlParameterSource> modifier) {
        return modifier.apply(new MapSqlParameterSource()
            .addValue("categoryId", categoryId)
            .addValue("eventId", eventId)
            .addValue("srcPriceCts", value.getSrcPriceCts())
            .addValue("finalPriceCts", MonetaryUtil.unitToCents(value.getFinalPrice(), currencyCode))
            .addValue("vatCts", MonetaryUtil.unitToCents(value.getVAT(), currencyCode))
            .addValue("discountCts", MonetaryUtil.unitToCents(value.getAppliedDiscount(), currencyCode))
            .addValue("currencyCode", currencyCode)
            .addValue("vatStatus", value.getVatStatus().name()));
    }

    private Predicate<TicketCategory> findReverseChargeCategory(boolean reverseChargeInPerson, boolean reverseChargeOnline, Event.EventFormat eventFormat) {
        return tc -> {
            if (eventFormat == Event.EventFormat.HYBRID) {
                return tc.getTicketAccessType() == TicketCategory.TicketAccessType.IN_PERSON ? reverseChargeInPerson : reverseChargeOnline;
            } else {
                return eventFormat == Event.EventFormat.IN_PERSON ? reverseChargeInPerson : reverseChargeOnline;
            }
        };
    }

    private void updateBillingData(ContactAndTicketsForm contactAndTicketsForm, PurchaseContext purchaseContext, String country, String vatNr, TicketReservation reservation, PriceContainer.VatStatus vatStatus) {
        var reservationId = reservation.getId();
        var discount = getDiscountOrNull(reservation);
        List<AdditionalServiceItem> additionalServiceItems = purchaseContext.ofType(PurchaseContextType.event) ?
            additionalServiceItemRepository.findByReservationUuid(purchaseContext.event().orElseThrow().getId(), reservation.getId())
            : List.of();
        var tickets = ticketReservationManager.findTicketsInReservation(reservation.getId());
        var additionalServices = purchaseContext.event().map(event -> additionalServiceRepository.loadAllForEvent(event.getId())).orElse(List.of());
        var subscriptions = subscriptionRepository.findSubscriptionsByReservationId(reservationId);
        var appliedSubscription = subscriptionRepository.findAppliedSubscriptionByReservationId(reservationId);
        var calculator = new ReservationPriceCalculator(reservation.withVatStatus(vatStatus), discount, tickets, additionalServiceItems, additionalServices, purchaseContext, subscriptions, appliedSubscription);
        var currencyCode = reservation.getCurrencyCode();
        ticketReservationRepository.updateBillingData(vatStatus, reservation.getSrcPriceCts(),
            unitToCents(calculator.getFinalPrice(), currencyCode), unitToCents(calculator.getVAT(), currencyCode), unitToCents(calculator.getAppliedDiscount(), currencyCode),
            reservation.getCurrencyCode(), vatNr,
            country, contactAndTicketsForm.isInvoiceRequested(), reservationId);
    }

    private boolean isEUCountry(String countryCode) {
        return configurationManager.getForSystem(EU_COUNTRIES_LIST).getRequiredValue().contains(countryCode);
    }

    private static PriceContainer.VatStatus determineVatStatus(PriceContainer.VatStatus current, boolean isVatExempt) {
        if(!isVatExempt) {
            return current;
        }
        return current == NOT_INCLUDED ? NOT_INCLUDED_EXEMPT : INCLUDED_EXEMPT;
    }

    private static class TicketCategoryPriceContainer implements PriceContainer {

        private final int categoryId;
        private final int srcPriceCts;
        private final String currencyCode;
        private final BigDecimal vatPercentage;
        private final VatStatus vatStatus;
        private final PromoCodeDiscount promoCodeDiscount;

        private TicketCategoryPriceContainer(int categoryId,
                                             int srcPriceCts,
                                             String currencyCode,
                                             BigDecimal vatPercentage,
                                             VatStatus vatStatus,
                                             PromoCodeDiscount promoCodeDiscount) {
            this.categoryId = categoryId;
            this.srcPriceCts = srcPriceCts;
            this.currencyCode = currencyCode;
            this.vatPercentage = vatPercentage;
            this.vatStatus = vatStatus;
            this.promoCodeDiscount = promoCodeDiscount;
        }

        @Override
        public int getSrcPriceCts() {
            return srcPriceCts;
        }

        @Override
        public String getCurrencyCode() {
            return currencyCode;
        }

        @Override
        public Optional<BigDecimal> getOptionalVatPercentage() {
            return Optional.ofNullable(vatPercentage);
        }

        @Override
        public VatStatus getVatStatus() {
            return vatStatus;
        }

        public int getFinalPriceCts() {
            return unitToCents(getFinalPrice(), currencyCode);
        }

        public int getVatCts() {
            return unitToCents(getVAT(), currencyCode);
        }

        public int getDiscountCts() {
            return unitToCents(getAppliedDiscount(), currencyCode);
        }

        @Override
        public Optional<PromoCodeDiscount> getDiscount() {
            return Optional.ofNullable(promoCodeDiscount);
        }
    }
}
