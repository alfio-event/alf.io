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

import alfio.controller.form.ReservationForm;
import alfio.manager.support.response.ValidatedResponse;
import alfio.model.Event;
import alfio.model.PromoCodeDiscount;
import alfio.model.PromoCodeUsageResult;
import alfio.model.SpecialPrice;
import alfio.model.modification.TicketReservationModification;
import alfio.model.result.ValidationResult;
import alfio.repository.EventRepository;
import alfio.repository.PromoCodeDiscountRepository;
import alfio.repository.SpecialPriceRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.util.ClockProvider;
import alfio.util.ErrorsCode;
import alfio.util.RequestUtils;
import alfio.util.ReservationUtil;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.ServletWebRequest;

import java.security.Principal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static alfio.model.PromoCodeDiscount.categoriesOrNull;

@Component
@AllArgsConstructor
public class PromoCodeRequestManager {

    private final SpecialPriceRepository specialPriceRepository;
    private final PromoCodeDiscountRepository promoCodeRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final EventManager eventManager;
    private final EventRepository eventRepository;
    private final TicketReservationManager ticketReservationManager;
    private final ClockProvider clockProvider;
    private final AdditionalServiceManager additionalServiceManager;

    enum PromoCodeType {
        SPECIAL_PRICE, PROMO_CODE_DISCOUNT, TICKET_CATEGORY_CODE, NOT_FOUND
    }

    public Optional<String> createReservationFromPromoCode(String eventName,
                                                           String code,
                                                           BiConsumer<String, String> queryStringHandler,
                                                           Function<Pair<Optional<String>, BindingResult>, Optional<String>> handleErrors,
                                                           ServletWebRequest request,
                                                           Principal principal) {

        String trimmedCode = StringUtils.trimToNull(code);

        if(trimmedCode == null) {
            return Optional.empty();
        }

        return eventRepository.findOptionalByShortName(eventName).flatMap(e -> {

            var checkedCode = checkCode(e, trimmedCode);

            var codeType = checkPromoCodeType(e.getId(), trimmedCode);

            var maybePromoCodeDiscount = checkedCode.getValue().getRight();

            if(checkedCode.isSuccess() && codeType == PromoCodeType.PROMO_CODE_DISCOUNT) {
                queryStringHandler.accept("code", trimmedCode);
                return Optional.empty();
            } else if(codeType == PromoCodeType.TICKET_CATEGORY_CODE) {
                var category = ticketCategoryRepository.findCodeInEvent(e.getId(), trimmedCode).orElseThrow();
                if(!category.isAccessRestricted()) {
                    var res = makeSimpleReservation(e, category.getId(), trimmedCode, request, maybePromoCodeDiscount, principal);
                    return handleErrors.apply(res);
                } else {
                    var specialPrice = specialPriceRepository.findActiveNotAssignedByCategoryId(category.getId(), 1).stream().findFirst();
                    if(specialPrice.isEmpty()) {
                        queryStringHandler.accept("errors", ErrorsCode.STEP_1_CODE_NOT_FOUND);
                        return Optional.empty();
                    }
                    var res = makeSimpleReservation(e, category.getId(), specialPrice.get().getCode(), request, maybePromoCodeDiscount, principal);
                    return handleErrors.apply(res);
                }
            } else if (checkedCode.isSuccess() && codeType == PromoCodeType.SPECIAL_PRICE) {
                int ticketCategoryId = specialPriceRepository.getByCode(trimmedCode).orElseThrow().getTicketCategoryId();
                var res = makeSimpleReservation(e, ticketCategoryId, trimmedCode, request, maybePromoCodeDiscount, principal);
                return handleErrors.apply(res);
            } else {
                queryStringHandler.accept("errors", ErrorsCode.STEP_1_CODE_NOT_FOUND);
                return Optional.empty();
            }
        });
    }

    public ValidatedResponse<Triple<Optional<SpecialPrice>, Event, Optional<PromoCodeDiscount>>> checkCode(String eventName, String promoCode) {
        var eventOptional = eventRepository.findOptionalByShortName(eventName);
        if(eventOptional.isEmpty()) {
            return new ValidatedResponse<>(ValidationResult.failed(new ValidationResult.ErrorDescriptor("eventName", "Event not found.")), null);
        }
        var event = eventOptional.get();
        var response = checkCode(event, promoCode);
        if(response.isSuccess()) {
            var value = response.getValue();
            return new ValidatedResponse<>(ValidationResult.success(), Triple.of(value.getLeft(), event, value.getRight()));
        } else {
            return new ValidatedResponse<>(ValidationResult.failed(new ValidationResult.ErrorDescriptor("promoCode", ErrorsCode.STEP_1_CODE_NOT_FOUND, ErrorsCode.STEP_1_CODE_NOT_FOUND)), null);
        }
    }

    public ValidatedResponse<Pair<Optional<SpecialPrice>, Optional<PromoCodeDiscount>>> checkCode(Event event, String promoCode) {
        ZoneId eventZoneId = event.getZoneId();
        ZonedDateTime now = ZonedDateTime.now(clockProvider.withZone(eventZoneId));
        Optional<String> maybeSpecialCode = Optional.ofNullable(StringUtils.trimToNull(promoCode));
        Optional<SpecialPrice> specialCode = maybeSpecialCode.flatMap(specialPriceRepository::getByCode);
        Optional<PromoCodeDiscount> promotionCodeDiscount = maybeSpecialCode.flatMap(trimmedCode -> promoCodeRepository.findPublicPromoCodeInEventOrOrganization(event.getId(), trimmedCode));

        var result = Pair.of(specialCode, promotionCodeDiscount);

        var errorResponse = new ValidatedResponse<>(ValidationResult.failed(new ValidationResult.ErrorDescriptor("promoCode", ErrorsCode.STEP_1_CODE_NOT_FOUND, ErrorsCode.STEP_1_CODE_NOT_FOUND)), result);

        //
        if(specialCode.isPresent()) {
            if (eventManager.getOptionalByIdAndActive(specialCode.get().getTicketCategoryId(), event.getId()).isEmpty()) {
                return errorResponse;
            }

            if (specialCode.get().getStatus() != SpecialPrice.Status.FREE) {
                return errorResponse;
            }

        } else if(promotionCodeDiscount.isPresent()) {
            var pcd = promotionCodeDiscount.get();
            if (!pcd.isCurrentlyValid(eventZoneId, now)
                || isDiscountCodeUsageExceeded(pcd)
                || (pcd.hasCurrencyCode() && !pcd.getCurrencyCode().equals(event.getCurrency()))) {
                return errorResponse;
            }
        } else {
            return errorResponse;
        }

        return new ValidatedResponse<>(ValidationResult.success(), result);
    }

    private PromoCodeType checkPromoCodeType(int eventId, String trimmedCode) {
        if(trimmedCode == null) {
            return PromoCodeType.NOT_FOUND;
        }  else if(specialPriceRepository.getByCode(trimmedCode).isPresent()) {
            return PromoCodeType.SPECIAL_PRICE;
        } else if (promoCodeRepository.findPublicPromoCodeInEventOrOrganization(eventId, trimmedCode).isPresent()) {
            return PromoCodeType.PROMO_CODE_DISCOUNT;
        } else if (ticketCategoryRepository.findCodeInEvent(eventId, trimmedCode).isPresent()) {
            return PromoCodeType.TICKET_CATEGORY_CODE;
        } else {
            return PromoCodeType.NOT_FOUND;
        }
    }

    private boolean isDiscountCodeUsageExceeded(PromoCodeDiscount discount) {
        return discount.getMaxUsage() != null && discount.getMaxUsage() <= promoCodeRepository.countConfirmedPromoCode(discount.getId(), categoriesOrNull(discount), null, categoriesOrNull(discount) != null ? "X" : null);
    }

    private Pair<Optional<String>, BindingResult> makeSimpleReservation(Event event,
                                                                        int ticketCategoryId,
                                                                        String promoCode,
                                                                        ServletWebRequest request,
                                                                        Optional<PromoCodeDiscount> promoCodeDiscount,
                                                                        Principal principal) {

        Locale locale = RequestUtils.getMatchingLocale(request, event);
        ReservationForm form = new ReservationForm();
        form.setPromoCode(promoCode);
        TicketReservationModification reservation = new TicketReservationModification();
        reservation.setQuantity(1);
        reservation.setTicketCategoryId(ticketCategoryId);
        form.setReservation(Collections.singletonList(reservation));
        var bindingRes = new BeanPropertyBindingResult(form, "reservationForm");
        return Pair.of(createTicketReservation(form, bindingRes, event, locale, promoCodeDiscount.map(PromoCodeDiscount::getPromoCode), principal), bindingRes);
    }

    private Optional<String> createTicketReservation(ReservationForm reservation,
                                                     BindingResult bindingResult,
                                                     Event event,
                                                     Locale locale,
                                                     Optional<String> promoCodeDiscount,
                                                     Principal principal) {
        return ReservationUtil.validateCreateRequest(reservation, bindingResult, ticketReservationManager, eventManager, additionalServiceManager, promoCodeDiscount.orElse(null), event)
            .flatMap(selected -> ticketReservationManager.createTicketReservation(event, selected.getLeft(), selected.getRight(), promoCodeDiscount, locale, bindingResult, principal));
    }

    public Optional<PromoCodeDiscount> findById(int id) {
        return promoCodeRepository.findOptionalById(id);
    }

    public void disablePromoCode(int promoCodeId) {
        promoCodeRepository.updateEventPromoCodeEnd(promoCodeId, ZonedDateTime.now(clockProvider.getClock()));
    }

    public int countUsage(int promoCodeId) {
        Optional<PromoCodeDiscount> code = findById(promoCodeId);
        if(code.isEmpty()) {
            return 0;
        }
        return promoCodeRepository.countConfirmedPromoCode(promoCodeId, categoriesOrNull(code.get()), null, categoriesOrNull(code.get()) != null ? "X" : null);
    }

    public List<PromoCodeUsageResult> retrieveDetailedUsage(int promoCodeId, Integer eventId) {
        return findById(promoCodeId)
            .map(pc -> promoCodeRepository.findDetailedUsage(pc.getPromoCode(), eventId))
            .orElse(List.of());
    }

}
