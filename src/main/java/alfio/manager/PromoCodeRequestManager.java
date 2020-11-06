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
import alfio.controller.support.TemplateProcessor;
import alfio.manager.support.response.ValidatedResponse;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.Mailer;
import alfio.model.*;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.TicketReservationModification;
import alfio.model.result.ValidationResult;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import alfio.util.*;
import alfio.repository.EventRepository;
import alfio.repository.PromoCodeDiscountRepository;
import alfio.repository.SpecialPriceRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.util.ClockProvider;
import alfio.util.ErrorsCode;
import alfio.util.RequestUtils;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.ServletWebRequest;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static alfio.model.PromoCodeDiscount.categoriesOrNull;

@Component
@AllArgsConstructor
public class PromoCodeRequestManager {

    public static final Clock UTC = Clock.systemUTC();
    private SpecialPriceRepository specialPriceRepository;
    private PromoCodeDiscountRepository promoCodeRepository;
    private TicketCategoryRepository ticketCategoryRepository;
    private EventManager eventManager;
    private EventRepository eventRepository;
    private TicketReservationManager ticketReservationManager;
    private TicketRepository ticketRepository;
    private NotificationManager notificationManager;
    private final TemplateManager templateManager;
    private final EmailMessageRepository emailMessageRepository;
    private final ConfigurationManager configurationManager;
    private final OrganizationRepository organizationRepository;
    private final ClockProvider clockProvider;

    enum PromoCodeType {
        SPECIAL_PRICE, PROMO_CODE_DISCOUNT, TICKET_CATEGORY_CODE, NOT_FOUND
    }

    public Optional<String> createReservationFromPromoCode(String eventName,
                                                           String code,
                                                           BiConsumer<String, String> queryStringHandler,
                                                           Function<Pair<Optional<String>, BindingResult>, Optional<String>> handleErrors,
                                                           ServletWebRequest request) {

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
                    var res = makeSimpleReservation(e, category.getId(), trimmedCode, request, maybePromoCodeDiscount);
                    return handleErrors.apply(res);
                } else {
                    var specialPrice = specialPriceRepository.findActiveNotAssignedByCategoryId(category.getId(), 1).stream().findFirst();
                    if(specialPrice.isEmpty()) {
                        queryStringHandler.accept("errors", ErrorsCode.STEP_1_CODE_NOT_FOUND);
                        return Optional.empty();
                    }
                    var res = makeSimpleReservation(e, category.getId(), specialPrice.get().getCode(), request, maybePromoCodeDiscount);
                    return handleErrors.apply(res);
                }
            } else if (checkedCode.isSuccess() && codeType == PromoCodeType.SPECIAL_PRICE) {
                int ticketCategoryId = specialPriceRepository.getByCode(trimmedCode).get().getTicketCategoryId();
                var res = makeSimpleReservation(e, ticketCategoryId, trimmedCode, request, maybePromoCodeDiscount);
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
        Optional<PromoCodeDiscount> promotionCodeDiscount = maybeSpecialCode.flatMap((trimmedCode) -> promoCodeRepository.findPublicPromoCodeInEventOrOrganization(event.getId(), trimmedCode));
        promotionCodeDiscount = ticketReservationManager.checkPromoCodeIsValid(promotionCodeDiscount, event);

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

        } else if (promotionCodeDiscount.isPresent() && !promotionCodeDiscount.get().isCurrentlyValid(eventZoneId, now)) {
            return errorResponse;
        } else if (promotionCodeDiscount.isPresent() && isDiscountCodeUsageExceeded(promotionCodeDiscount.get())){
            return errorResponse;
        } else if(promotionCodeDiscount.isEmpty()) {
            return errorResponse;
        }
        //


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
                                                                        Optional<PromoCodeDiscount> promoCodeDiscount) {

        Locale locale = RequestUtils.getMatchingLocale(request, event);
        ReservationForm form = new ReservationForm();
        form.setPromoCode(promoCode);
        TicketReservationModification reservation = new TicketReservationModification();
        reservation.setAmount(1);
        reservation.setTicketCategoryId(ticketCategoryId);
        form.setReservation(Collections.singletonList(reservation));
        var bindingRes = new BeanPropertyBindingResult(form, "reservationForm");
        return Pair.of(createTicketReservation(form, bindingRes, event, locale, promoCodeDiscount.map(PromoCodeDiscount::getPromoCode)), bindingRes);
    }

    private Optional<String> createTicketReservation(ReservationForm reservation,
                                                     BindingResult bindingResult,
                                                     Event event,
                                                     Locale locale,
                                                     Optional<String> promoCodeDiscount) {
        return reservation.validate(bindingResult, ticketReservationManager, eventManager, promoCodeDiscount.orElse(null), event)
            .flatMap(selected -> ticketReservationManager.createTicketReservation(event, selected.getLeft(), selected.getRight(), promoCodeDiscount, locale, bindingResult));
    }

    public boolean sendEMail(int promoCodeId) {
        var pCode = promoCodeRepository.findOptionalById(promoCodeId);
        var countUsage = promoCodeRepository.countConfirmedPromoCode(promoCodeId,categoriesOrNull(pCode.get()), null, categoriesOrNull(pCode.get()) != null ? "X" : null);
        var locale = Locale.ITALY;
        if (pCode.isEmpty() || pCode.get().getEmailReference().isEmpty()){
            return false;
        }

        var model = new HashMap<String, Object>();
        model.put("promoCode", pCode.get().getPromoCode());
        model.put("promoCodeAmount", pCode.get().getMaxUsage() - countUsage);
        model.put("refEmail",pCode.get().getEmailReference());
        model.put("promoCodeDetails",pCode.get().getDescription());
        String baseUrl = configurationManager.getForSystem(ConfigurationKeys.BASE_URL).getRequiredValue();
        model.put("baseUrl",baseUrl);
        model.put("organization", organizationRepository.getById(pCode.get().getOrganizationId()));

        //get associated event, if any
        var eventId = pCode.get().getEventId();
        if (eventId == null) {
            //let's find a suitable eventID
            if (pCode.get().getAlfioMetadata().getAttributes().containsKey("idEvent")) {
                eventId = Integer.parseInt(pCode.get().getAlfioMetadata().getAttributes().get("idEvent").toString());
            } else {
                //no event, no mail (event_id is a FK on email table) :( attaching fake event
                var eventList = eventRepository.findByOrganizationIds(Collections.singleton(pCode.get().getOrganizationId()));
                if (eventList.size() == 0) {
                    return false; //no event for this organization
                } else {
                    eventId = eventList.get(0).getId(); //I don't care what event is binded to promocode
                }
            }
        }

        EventAndOrganizationId eventAndOrganizationId = new EventAndOrganizationId(eventId,pCode.get().getOrganizationId());
        var temp = TemplateProcessor.buildGenericEmail(templateManager, TemplateResource.EMAIL_FOR_PROMO_CODE, locale, model, eventAndOrganizationId );
        var subject = "Promo code activation";
        var textRender = temp.getTextPart();
        var htmlRender = temp.getHtmlPart();
        emailMessageRepository.insertWithPromoCode(eventId, "", pCode.get().getEmailReference(), null, subject, textRender, htmlRender, "", "", ZonedDateTime.now(UTC),pCode.get().getOrganizationId());
        return true;
    }

    public boolean sendPromotionalEmail(String recipient, String subject, String message, int organizationId) {
        var locale = Locale.ITALY;

        var model = new HashMap<String, Object>();
        model.put("message", message);
        String baseUrl = configurationManager.getForSystem(ConfigurationKeys.BASE_URL).getRequiredValue();
        model.put("baseUrl",baseUrl);
        model.put("organization", organizationRepository.getById(organizationId));
        model.put("refEmail",recipient);
        int eventId = -1;
        //no event, no mail (event_id is a FK on email table) :( attaching fake event
        var eventList = eventRepository.findByOrganizationIds(Collections.singleton(organizationId));
        if (eventList.size() == 0) {
            return false; //no event for this organization
        } else {
            eventId = eventList.get(0).getId(); //I don't care what event is binded to promocode
        }
        EventAndOrganizationId eventAndOrganizationId = new EventAndOrganizationId(eventId,organizationId);
        var temp = TemplateProcessor.buildGenericEmail(templateManager, TemplateResource.PROMOTIONAL_EMAIL, locale, model, eventAndOrganizationId );
        var textRender = temp.getTextPart();
        var htmlRender = temp.getHtmlPart();
        emailMessageRepository.insertWithPromoCode(eventId, "", recipient, null, subject, textRender, htmlRender, "", "", ZonedDateTime.now(UTC), organizationId);
        return true;
    }

}
