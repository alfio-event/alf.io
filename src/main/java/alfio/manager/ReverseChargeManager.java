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
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.ReservationPriceCalculator;
import alfio.model.*;
import alfio.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ValidationUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static alfio.model.PriceContainer.VatStatus.*;
import static alfio.model.PriceContainer.VatStatus.NOT_INCLUDED_NOT_CHARGED;
import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.MonetaryUtil.unitToCents;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@AllArgsConstructor
@Component
public class ReverseChargeManager {

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

        boolean isEvent = purchaseContext.ofType(PurchaseContext.PurchaseContextType.event);
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

            Optional<VatDetail> vatDetail = optionalReservation
                .filter(e -> {
                    if(purchaseContext.ofType(PurchaseContext.PurchaseContextType.event) && (!reverseChargeInPerson || !reverseChargeOnline)) {
                        var eventFormat = purchaseContext.event().orElseThrow().getFormat();
                        // if we find at least one category matching the criteria, then we can proceed
                        return categoriesList.stream().anyMatch(findReverseChargeCategory(reverseChargeInPerson, reverseChargeOnline, eventFormat));
                    }
                    var vatStatus = purchaseContext.getVatStatus();
                    return vatStatus == INCLUDED || vatStatus == NOT_INCLUDED;
                })
                .filter(e -> vatChecker.isReverseChargeEnabledFor(purchaseContext))
                .flatMap(e -> vatChecker.checkVat(contactAndTicketsForm.getVatNr(), country, purchaseContext));


            if(vatDetail.isPresent()) {
                var vatValidation = vatDetail.get();
                if (!vatValidation.isValid()) {
                    bindingResult.rejectValue("vatNr", "error.STEP_2_INVALID_VAT");
                } else {
                    var reservation = ticketReservationManager.findById(reservationId).orElseThrow();
                    var currencyCode = reservation.getCurrencyCode();
                    PriceContainer.VatStatus vatStatus = determineVatStatus(purchaseContext.getVatStatus(), vatValidation.isVatExempt());
                    // standard case: Reverse Charge is applied to the entire reservation
                    var discount = reservation.getPromoCodeDiscountId() != null ? promoCodeDiscountRepository.findById(reservation.getPromoCodeDiscountId()) : null;
                    if(!isEvent || (reverseChargeOnline && reverseChargeInPerson)) {
                        if(isEvent) {
                            var event = purchaseContext.event().orElseThrow();
                            var priceContainers = mapPriceContainersByCategoryId(categoriesList,
                                (a) -> true,
                                currencyCode,
                                vatStatus,
                                discount,
                                event);
                            // update all tickets in reservation to match the VAT_STATUS
                            ticketRepository.updateVatStatusForReservation(reservationId, vatStatus);
                            updateTicketPricesByCategory(reservationId, currencyCode, vatStatus, event, priceContainers);
                        }
                        updateBillingData(reservationId, contactAndTicketsForm, purchaseContext, country, trimToNull(vatValidation.getVatNr()), reservation, vatStatus);
                    } else {
                        var event = purchaseContext.event().orElseThrow();
                        var eventFormat = event.getFormat();
                        var matchingCategories = mapPriceContainersByCategoryId(categoriesList,
                            findReverseChargeCategory(reverseChargeInPerson, reverseChargeOnline, eventFormat),
                            currencyCode,
                            vatStatus,
                            discount,
                            event);

                        updateTicketPricesByCategory(reservationId, currencyCode, vatStatus, event, matchingCategories);
                        // update billing data for the reservation, using the original VatStatus from reservation
                        updateBillingData(reservationId, contactAndTicketsForm, purchaseContext, country, trimToNull(vatValidation.getVatNr()), reservation, reservation.getVatStatus());
                    }
                    vatChecker.logSuccessfulValidation(vatValidation, reservationId, purchaseContext);
                }
            } else if(optionalReservation.isPresent() && contactAndTicketsForm.isItalyEInvoicingSplitPayment()) {
                var reservation = optionalReservation.get();
                var vatStatus = purchaseContext.getVatStatus() == INCLUDED ? INCLUDED_NOT_CHARGED : NOT_INCLUDED_NOT_CHARGED;
                updateBillingData(reservationId, contactAndTicketsForm, purchaseContext, country, trimToNull(contactAndTicketsForm.getVatNr()), reservation, vatStatus);
            }
        } catch (IllegalStateException ise) {//vat checker failure
            bindingResult.rejectValue("vatNr", "error.vatVIESDown");
        }
    }

    public void resetVat(PurchaseContext purchaseContext, String reservationId) {
        if(purchaseContext.ofType(PurchaseContext.PurchaseContextType.event)) {
            var reservation = ticketReservationRepository.findReservationById(reservationId);
            var categoriesList = ticketCategoryRepository.findCategoriesInReservation(reservationId);
            var discount = reservation.getPromoCodeDiscountId() != null ? promoCodeDiscountRepository.findById(reservation.getPromoCodeDiscountId()) : null;
            var event = purchaseContext.event().orElseThrow();
            var priceContainers = mapPriceContainersByCategoryId(categoriesList,
                (a) -> true,
                reservation.getCurrencyCode(),
                reservation.getVatStatus(),
                discount,
                event);
            // update all tickets in reservation to match the VAT_STATUS
            ticketRepository.updateVatStatusForReservation(reservationId, reservation.getVatStatus());
            updateTicketPricesByCategory(reservationId, reservation.getCurrencyCode(), reservation.getVatStatus(), event, priceContainers);
        }
    }

    private Map<Integer, TicketCategoryPriceContainer> mapPriceContainersByCategoryId(List<TicketCategory> categoriesList,
                                                                                      Predicate<TicketCategory> filter,
                                                                                      String currencyCode,
                                                                                      PriceContainer.VatStatus vatStatus,
                                                                                      PromoCodeDiscount discount,
                                                                                      Event event) {
        return categoriesList.stream()
            .filter(filter)
            .map(c -> new TicketCategoryPriceContainer(c.getId(), c.getSrcPriceCts(), currencyCode, event.getVat(), vatStatus, discount))
            .collect(toMap(o -> o.categoryId, Function.identity()));
    }

    private void updateTicketPricesByCategory(String reservationId,
                                              String currencyCode,
                                              PriceContainer.VatStatus vatStatus,
                                              Event event,
                                              Map<Integer, TicketCategoryPriceContainer> matchingCategories) {
        MapSqlParameterSource[] parameterSources = matchingCategories.entrySet().stream()
            .map(entry -> {
                    var value = entry.getValue();
                    return new MapSqlParameterSource()
                        .addValue("reservationId", reservationId)
                        .addValue("categoryId", entry.getKey())
                        .addValue("eventId", event.getId())
                        .addValue("srcPriceCts", value.getSrcPriceCts())
                        .addValue("finalPriceCts", value.getFinalPriceCts())
                        .addValue("vatCts", value.getVatCts())
                        .addValue("discountCts", value.getDiscountCts())
                        .addValue("currencyCode", currencyCode)
                        .addValue("vatStatus", vatStatus.name());
                }
            ).toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(ticketRepository.updateTicketPriceForCategoryInReservation(), parameterSources);
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

    private void updateBillingData(String reservationId, ContactAndTicketsForm contactAndTicketsForm, PurchaseContext purchaseContext, String country, String vatNr, TicketReservation reservation, PriceContainer.VatStatus vatStatus) {
        var discount = reservation.getPromoCodeDiscountId() != null ? promoCodeDiscountRepository.findById(reservation.getPromoCodeDiscountId()) : null;
        var additionalServiceItems = additionalServiceItemRepository.findByReservationUuid(reservation.getId());
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
