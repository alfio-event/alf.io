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
import alfio.util.ErrorsCode;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.validation.Errors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

//step 1 : choose tickets
@Data
public class ReservationForm implements Serializable {

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

    public Optional<Pair<List<TicketReservationWithOptionalCodeModification>, List<ASReservationWithOptionalCodeModification>>> validate(Errors bindingResult,
                                                                                                                                         TicketReservationManager tickReservationManager,
                                                                                                                                         AdditionalServiceRepository additionalServiceRepository,
                                                                                                                                         EventManager eventManager,
                                                                                                                                         String sessionPromoCodeDiscount,
                                                                                                                                         Event event) {
        int selectionCount = ticketSelectionCount();

        if (selectionCount <= 0) {
            bindingResult.reject(ErrorsCode.STEP_1_SELECT_AT_LEAST_ONE);
            return Optional.empty();
        }

        List<Pair<TicketReservationModification, Integer>> maxTicketsByTicketReservation = selected().stream()
            .map(r -> Pair.of(r, tickReservationManager.maxAmountOfTicketsForCategory(event.getOrganizationId(), event.getId(), r.getTicketCategoryId(), sessionPromoCodeDiscount)))
            .collect(toList());
        Optional<Pair<TicketReservationModification, Integer>> error = maxTicketsByTicketReservation.stream()
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
            return eventManager.eventExistsById(tc.getEventId());
        });


        final boolean validAdditionalServiceSelected = additionalServices.stream().allMatch(asm -> {
            AdditionalService as = eventManager.getAdditionalServiceById(asm.getAdditionalServiceId(), event.getId());
            ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
            return as.getInception(event.getZoneId()).isBefore(now) &&
                as.getExpiration(event.getZoneId()).isAfter(now) &&
                asm.getQuantity() >= 0 &&
                ((as.isFixPrice() && asm.isQuantityValid(as, selectionCount)) || (!as.isFixPrice() && asm.getAmount() != null && asm.getAmount().compareTo(BigDecimal.ZERO) >= 0)) &&
                eventManager.eventExistsById(as.getEventId());
        });

        if(!validCategorySelection || !validAdditionalServiceSelected) {
            bindingResult.reject(ErrorsCode.STEP_1_TICKET_CATEGORY_MUST_BE_SALEABLE);
            return Optional.empty();
        }

        List<TicketReservationWithOptionalCodeModification> res = new ArrayList<>();
        //
        Optional<SpecialPrice> specialCode = Optional.ofNullable(StringUtils.trimToNull(promoCode))
            .flatMap(tickReservationManager::getSpecialPriceByCode);
        //
        final ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
        maxTicketsByTicketReservation.forEach((pair) -> validateCategory(bindingResult, tickReservationManager, eventManager, event, pair.getRight(), res, specialCode, now, pair.getLeft()));
        return bindingResult.hasErrors() ? Optional.empty() : Optional.of(Pair.of(res, additionalServices.stream().map(as -> new ASReservationWithOptionalCodeModification(as, specialCode)).collect(Collectors.toList())));
    }

    private static void validateCategory(Errors bindingResult, TicketReservationManager tickReservationManager, EventManager eventManager,
                                         Event event, int maxAmountOfTicket, List<TicketReservationWithOptionalCodeModification> res,
                                         Optional<SpecialPrice> specialCode, ZonedDateTime now, TicketReservationModification r) {
        TicketCategory tc = eventManager.getTicketCategoryById(r.getTicketCategoryId(), event.getId());
        SaleableTicketCategory ticketCategory = new SaleableTicketCategory(tc, "", now, event, tickReservationManager.countAvailableTickets(event, tc), maxAmountOfTicket, null);

        if (!ticketCategory.getSaleable()) {
            bindingResult.reject(ErrorsCode.STEP_1_TICKET_CATEGORY_MUST_BE_SALEABLE);
            return;
        }

        res.add(new TicketReservationWithOptionalCodeModification(r, ticketCategory.isAccessRestricted() ? specialCode : Optional.empty()));
    }
}
