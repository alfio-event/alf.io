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
package alfio.util;

import alfio.controller.decorator.SaleableTicketCategory;
import alfio.controller.form.ReservationCreate;
import alfio.manager.AdditionalServiceManager;
import alfio.manager.EventManager;
import alfio.manager.PromoCodeRequestManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.support.reservation.*;
import alfio.manager.support.response.ValidatedResponse;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.modification.ASReservationWithOptionalCodeModification;
import alfio.model.modification.AdditionalServiceReservationModification;
import alfio.model.modification.ReservationRequest;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.repository.TicketCategoryRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

public class ReservationUtil {

    private static final Logger log = LoggerFactory.getLogger(ReservationUtil.class);

    private ReservationUtil() {
    }

    public static <T extends ReservationRequest> Optional<String> checkPromoCode(ReservationCreate<T> createRequest,
                                                  Event event,
                                                  PromoCodeRequestManager promoCodeRequestManager,
                                                  BindingResult bindingResult) {
        Optional<ValidatedResponse<Pair<Optional<SpecialPrice>, Optional<PromoCodeDiscount>>>> codeCheck = Optional.empty();

        if(StringUtils.trimToNull(createRequest.getPromoCode()) != null) {
            var resCheck = promoCodeRequestManager.checkCode(event, createRequest.getPromoCode());
            if(!resCheck.isSuccess()) {
                bindingResult.reject(ErrorsCode.STEP_1_CODE_NOT_FOUND, ErrorsCode.STEP_1_CODE_NOT_FOUND);
            }
            codeCheck = Optional.of(resCheck);
        }

        return codeCheck.map(ValidatedResponse::getValue)
            .flatMap(Pair::getRight)
            .map(PromoCodeDiscount::getPromoCode);
    }


    public static Optional<Pair<List<TicketReservationWithOptionalCodeModification>, List<ASReservationWithOptionalCodeModification>>> validateCreateRequest(ReservationCreate<? extends ReservationRequest> request,
                                                                                                                                                      Errors bindingResult,
                                                                                                                                                      TicketReservationManager tickReservationManager,
                                                                                                                                                      EventManager eventManager,
                                                                                                                                                      AdditionalServiceManager additionalServiceManager,
                                                                                                                                                      String validatedPromoCodeDiscount,
                                                                                                                                                      Event event) {



        int selectionCount = ticketSelectionCount(request.getTickets());

        if (selectionCount <= 0) {
            bindingResult.reject(ErrorsCode.STEP_1_SELECT_AT_LEAST_ONE);
            return Optional.empty();
        }

        List<Pair<ReservationRequest, Integer>> maxTicketsByTicketReservation = selected(request.getTickets()).stream()
            .map(r -> Pair.of((ReservationRequest) r, tickReservationManager.maxAmountOfTicketsForCategory(event, r.getTicketCategoryId(), validatedPromoCodeDiscount)))
            .toList();
        Optional<Pair<ReservationRequest, Integer>> error = maxTicketsByTicketReservation.stream()
            .filter(p -> p.getKey().getQuantity() > p.getValue())
            .findAny();

        if(error.isPresent()) {
            bindingResult.reject(ErrorsCode.STEP_1_OVER_MAXIMUM, new Object[] { error.get().getValue() }, null);
            return Optional.empty();
        }


        final var selectedCategoryIds = selected(request.getTickets()).stream().map(ReservationRequest::getTicketCategoryId).collect(Collectors.toSet());
        final var availableCategoryIds = eventManager.findCategoriesById(selectedCategoryIds, event).stream().map(TicketCategory::getId).collect(Collectors.toSet());
        final boolean validCategorySelection = availableCategoryIds.equals(selectedCategoryIds);

        final List<AdditionalServiceReservationModification> additionalServices = selectedAdditionalServices(request.getAdditionalServices());
        final boolean validAdditionalServiceSelected = additionalServices.stream().allMatch(asm -> {
            AdditionalService as = additionalServiceManager.getAdditionalServiceById(asm.getAdditionalServiceId(), event.getId());
            ZonedDateTime now = event.now(ClockProvider.clock());
            return as.getInception(event.getZoneId()).isBefore(now) &&
                as.getExpiration(event.getZoneId()).isAfter(now) &&
                asm.getQuantity() >= 0 &&
                ((as.fixPrice() && asm.isQuantityValid(as, selectionCount)) || (!as.fixPrice() && asm.getAmount() != null && asm.getAmount().compareTo(BigDecimal.ZERO) >= 0)) &&
                eventManager.eventExistsById(as.eventId());
        });

        if(!validCategorySelection || !validAdditionalServiceSelected) {
            bindingResult.reject(ErrorsCode.STEP_1_TICKET_CATEGORY_MUST_BE_SALEABLE);
            return Optional.empty();
        }

        List<TicketReservationWithOptionalCodeModification> res = new ArrayList<>();
        //
        Optional<SpecialPrice> specialCode = Optional.ofNullable(StringUtils.trimToNull(request.getPromoCode()))
            .flatMap(tickReservationManager::getSpecialPriceByCode);
        //
        final ZonedDateTime now = event.now(ClockProvider.clock());
        maxTicketsByTicketReservation.forEach(pair -> validateCategory(bindingResult, tickReservationManager, eventManager, event, pair.getRight(), res, specialCode, now, pair.getLeft()));
        return bindingResult.hasErrors() ? Optional.empty() : Optional.of(Pair.of(res, additionalServices.stream().map(as -> new ASReservationWithOptionalCodeModification(as, specialCode)).toList()));
    }

    private static <T extends ReservationRequest> int ticketSelectionCount(List<T> tickets) {
        return selected(tickets).stream().mapToInt(ReservationRequest::getQuantity).sum();
    }

    private static <T extends ReservationRequest> void validateCategory(Errors bindingResult, TicketReservationManager tickReservationManager, EventManager eventManager,
                                         Event event, int maxAmountOfTicket, List<TicketReservationWithOptionalCodeModification> res,
                                         Optional<SpecialPrice> specialCode, ZonedDateTime now, T r) {
        TicketCategory tc = eventManager.getTicketCategoryById(r.getTicketCategoryId(), event.getId());
        var categoryAvailability = tickReservationManager.countAvailableTickets(event, tc);
        SaleableTicketCategory ticketCategory = new SaleableTicketCategory(tc, now, event, categoryAvailability, maxAmountOfTicket, null);

        if (!ticketCategory.getSaleable()) {
            bindingResult.reject(ErrorsCode.STEP_1_TICKET_CATEGORY_MUST_BE_SALEABLE);
            return;
        }

        res.add(new TicketReservationWithOptionalCodeModification(r, ticketCategory.isAccessRestricted() ? specialCode : Optional.empty()));
    }

    private static <T extends ReservationRequest> List<T> selected(List<T> reservation) {
        return ofNullable(reservation)
            .orElse(emptyList())
            .stream()
            .filter(e -> e != null && e.getQuantity() != null && e.getTicketCategoryId() != null && e.getQuantity() > 0)
            .toList();
    }

    private static List<AdditionalServiceReservationModification> selectedAdditionalServices(List<AdditionalServiceReservationModification> additionalServices) {
        return ofNullable(additionalServices)
            .orElse(emptyList())
            .stream()
            .filter(e -> e != null && e.getQuantity() != null && e.getAdditionalServiceId() != null && e.getQuantity() > 0)
            .toList();
    }

    public static boolean hasPrivacyPolicy(PurchaseContext event) {
        return StringUtils.isNotBlank(event.getPrivacyPolicyLinkOrNull());
    }

    public static String ticketUpdateUrl(Event event, Ticket ticket, ConfigurationManager configurationManager) {
        return configurationManager.baseUrl(event) + "/event/" + event.getShortName() + "/ticket/" + ticket.getPublicUuid().toString() + "/update?lang=" + ticket.getUserLanguage();
    }

    public static String reservationUrl(String baseUrl, String reservationId,
                                        PurchaseContext purchaseContext,
                                        String userLanguage,
                                        String additionalParams) {
        var cleanParams = StringUtils.trimToNull(additionalParams);
        return StringUtils.removeEnd(baseUrl, "/")
            + "/" + purchaseContext.getType()
            + "/" + purchaseContext.getPublicIdentifier()
            + "/reservation/" + reservationId
            + "?lang="+userLanguage
            + (cleanParams != null ? "&" + cleanParams : "");
    }
    public static String reservationUrl(String baseUrl, String reservationId, PurchaseContext purchaseContext, String userLanguage) {
        return reservationUrl(baseUrl, reservationId, purchaseContext, userLanguage, null);
    }

    public static String reservationUrl(TicketReservation reservation, PurchaseContext purchaseContext, ConfigurationManager configurationManager) {
        return reservationUrl(configurationManager.baseUrl(purchaseContext), reservation.getId(), purchaseContext, reservation.getUserLanguage());
    }

    public static List<TicketWithCategory> collectTicketsWithCategory(Map<Integer, List<Ticket>> ticketsByCategory, TicketCategoryRepository ticketCategoryRepository) {
        final List<TicketWithCategory> ticketsWithCategory;
        if(!ticketsByCategory.isEmpty()) {
            ticketsWithCategory = ticketCategoryRepository.findByIds(ticketsByCategory.keySet())
                .stream()
                .flatMap(tc -> ticketsByCategory.get(tc.getId()).stream().map(t -> new TicketWithCategory(t, tc)))
                .toList();
        } else {
            ticketsWithCategory = Collections.emptyList();
        }
        return ticketsWithCategory;
    }

    public static Locale getReservationLocale(TicketReservation reservation) {
        return StringUtils.isEmpty(reservation.getUserLanguage()) ? Locale.ENGLISH : LocaleUtil.forLanguageTag(reservation.getUserLanguage());
    }

    public static Optional<String> handleReservationCreationErrors(Supplier<Optional<String>> reservationCreationCall,
                                                                   BindingResult bindingResult,
                                                                   PurchaseContext.PurchaseContextType purchaseContextType) {
        try {
            return reservationCreationCall.get();
        } catch (NotEnoughTicketsException | NotEnoughItemsException nete) {
            if (purchaseContextType == PurchaseContext.PurchaseContextType.event) {
                bindingResult.reject(ErrorsCode.STEP_1_NOT_ENOUGH_TICKETS);
            } else {
                log.error("cannot acquire subscription", nete);
                bindingResult.reject("show-subscription.sold-out.message");
            }
        } catch (MissingSpecialPriceTokenException missing) {
            bindingResult.reject(ErrorsCode.STEP_1_ACCESS_RESTRICTED);
        } catch (InvalidSpecialPriceTokenException invalid) {
            bindingResult.reject(ErrorsCode.STEP_1_CODE_NOT_FOUND);
        } catch (TooManyTicketsForDiscountCodeException tooMany) {
            bindingResult.reject(ErrorsCode.STEP_2_DISCOUNT_CODE_USAGE_EXCEEDED);
        } catch (CannotProceedWithPayment cannotProceedWithPayment) {
            bindingResult.reject(ErrorsCode.STEP_1_CATEGORIES_NOT_COMPATIBLE);
            log.error("missing payment methods", cannotProceedWithPayment);
        }
        return Optional.empty();
    }
}
