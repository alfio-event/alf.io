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
package alfio.controller.form;

import alfio.controller.decorator.SaleableTicketCategory;
import alfio.manager.EventManager;
import alfio.manager.TicketReservationManager;
import alfio.model.AdditionalService;
import alfio.model.Event;
import alfio.model.SpecialPrice;
import alfio.model.TicketCategory;
import alfio.model.modification.ASReservationWithOptionalCodeModification;
import alfio.model.modification.AdditionalServiceReservationModification;
import alfio.model.modification.TicketReservationModification;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.repository.AdditionalServiceRepository;
import alfio.repository.TicketCategoryDescriptionRepository;
import alfio.util.ErrorsCode;
import alfio.util.OptionalWrapper;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.validation.BindingResult;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

//step 1 : choose tickets
@Data
public class ReservationForm {

    private String promoCode;
    private List<TicketReservationModification> reservation;
    private List<AdditionalServiceReservationModification> additionalService;

    private List<TicketReservationModification> selected() {
        return ofNullable(reservation)
                .orElse(emptyList())
                .stream()
                .filter((e) -> e != null && e.getAmount() != null && e.getTicketCategoryId() != null
                        && e.getAmount() > 0).collect(toList());
    }

    private List<AdditionalServiceReservationModification> selectedAdditionalServices() {
        return ofNullable(additionalService)
            .orElse(emptyList())
            .stream()
            .filter(e -> e != null && e.getQuantity() != null && e.getAdditionalServiceId() != null && e.getQuantity() > 0)
            .collect(toList());
    }

    private int ticketSelectionCount() {
        return selected().stream().mapToInt(TicketReservationModification::getAmount).sum();
    }

    private int additionalServicesSelectionCount(AdditionalServiceRepository additionalServiceRepository, int eventId) {
        return (int) selectedAdditionalServices().stream()
            .filter(as -> as.getAdditionalServiceId() != null && (additionalServiceRepository.getById(as.getAdditionalServiceId(), eventId).isFixPrice() || Optional.ofNullable(as.getAmount()).filter(a -> a.compareTo(BigDecimal.ZERO) > 0).isPresent()))
            .count();
    }

    public Optional<Pair<List<TicketReservationWithOptionalCodeModification>, List<ASReservationWithOptionalCodeModification>>> validate(BindingResult bindingResult,
                                                                                                                                         TicketReservationManager tickReservationManager,
                                                                                                                                         TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository,
                                                                                                                                         AdditionalServiceRepository additionalServiceRepository,
                                                                                                                                         EventManager eventManager,
                                                                                                                                         Event event,
                                                                                                                                         Locale locale) {
        int selectionCount = ticketSelectionCount();
        int additionalServicesCount = additionalServicesSelectionCount(additionalServiceRepository, event.getId());

        if (selectionCount + additionalServicesCount <= 0) {
            bindingResult.reject(ErrorsCode.STEP_1_SELECT_AT_LEAST_ONE);
            return Optional.empty();
        }

        final int maxAmountOfTicket = tickReservationManager.maxAmountOfTickets(event);

        if (selectionCount > maxAmountOfTicket) {
            bindingResult.reject(ErrorsCode.STEP_1_OVER_MAXIMUM, new Object[] { maxAmountOfTicket }, null);
            return Optional.empty();
        }

        Optional<Pair<TicketReservationModification, Integer>> error = selected().stream()
            .map(r -> Pair.of(r, tickReservationManager.maxAmountOfTicketsForCategory(event.getOrganizationId(), event.getId(), r.getTicketCategoryId())))
            .filter(p -> p.getKey().getAmount() > p.getValue())
            .findAny();

        if(error.isPresent()) {
            bindingResult.reject(ErrorsCode.STEP_1_OVER_MAXIMUM, new Object[] { error.get().getValue() }, null);
            return Optional.empty();
        }

        final List<TicketReservationModification> categories = selected();
        final List<AdditionalServiceReservationModification> additionalServices = selectedAdditionalServices();

        final boolean validCategorySelection = categories.stream().allMatch(c -> {
            TicketCategory tc = eventManager.getTicketCategoryById(c.getTicketCategoryId(), event.getId());
            return OptionalWrapper.optionally(() -> eventManager.findEventByTicketCategory(tc)).isPresent();
        });

        final boolean validAdditionalServiceSelected = additionalServices.stream().allMatch(asm -> {
            AdditionalService as = eventManager.getAdditionalServiceById(asm.getAdditionalServiceId(), event.getId());
            ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
            return as.getInception(event.getZoneId()).isBefore(now) && as.getExpiration(event.getZoneId()).isAfter(now) &&
                OptionalWrapper.optionally(() -> eventManager.findEventByAdditionalService(as)).isPresent();
        });

        if(!validCategorySelection || !validAdditionalServiceSelected) {
            bindingResult.reject(ErrorsCode.STEP_1_TICKET_CATEGORY_MUST_BE_SALEABLE);
            return Optional.empty();
        }

        List<TicketReservationWithOptionalCodeModification> res = new ArrayList<>();
        //
        Optional<SpecialPrice> specialCode = Optional.ofNullable(StringUtils.trimToNull(promoCode)).flatMap(
                (trimmedCode) -> OptionalWrapper.optionally(() -> tickReservationManager.getSpecialPriceByCode(trimmedCode)));
        //
        final ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
        categories.forEach((r) -> validateCategory(bindingResult, tickReservationManager, ticketCategoryDescriptionRepository, eventManager, event, maxAmountOfTicket, res, specialCode, now, r, locale));
        return bindingResult.hasErrors() ? Optional.empty() : Optional.of(Pair.of(res, additionalServices.stream().map(as -> new ASReservationWithOptionalCodeModification(as, specialCode)).collect(Collectors.toList())));
    }

    private static void validateCategory(BindingResult bindingResult, TicketReservationManager tickReservationManager, TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository, EventManager eventManager,
                                         Event event, int maxAmountOfTicket, List<TicketReservationWithOptionalCodeModification> res,
                                         Optional<SpecialPrice> specialCode, ZonedDateTime now, TicketReservationModification r,
                                         Locale locale) {
        TicketCategory tc = eventManager.getTicketCategoryById(r.getTicketCategoryId(), event.getId());
        SaleableTicketCategory ticketCategory = new SaleableTicketCategory(tc, "", now, event, tickReservationManager.countAvailableTickets(event, tc), maxAmountOfTicket, null);

        if (!ticketCategory.getSaleable()) {
            bindingResult.reject(ErrorsCode.STEP_1_TICKET_CATEGORY_MUST_BE_SALEABLE);
            return;
        }

        res.add(new TicketReservationWithOptionalCodeModification(r, ticketCategory.isAccessRestricted() ? specialCode : Optional.empty()));
    }
}
