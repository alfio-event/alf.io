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
import alfio.model.Event;
import alfio.model.SpecialPrice;
import alfio.model.TicketCategory;
import alfio.model.modification.TicketReservationModification;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.util.ErrorsCode;
import alfio.util.OptionalWrapper;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.BindingResult;

import javax.servlet.http.HttpServletRequest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

//step 1 : choose tickets
@Data
public class ReservationForm {

	private String promoCode;
	private List<TicketReservationModification> reservation;

	private List<TicketReservationModification> selected() {
		return ofNullable(reservation)
				.orElse(emptyList())
				.stream()
				.filter((e) -> e != null && e.getAmount() != null && e.getTicketCategoryId() != null
						&& e.getAmount() > 0).collect(toList());
	}

	private int selectionCount() {
		return selected().stream().mapToInt(TicketReservationModification::getAmount).sum();
	}

	public Optional<List<TicketReservationWithOptionalCodeModification>> validate(BindingResult bindingResult,
																				  TicketReservationManager tickReservationManager,
																				  EventManager eventManager,
																				  Event event, HttpServletRequest request) {
		int selectionCount = selectionCount();

		if (selectionCount <= 0) {
			bindingResult.reject(ErrorsCode.STEP_1_SELECT_AT_LEAST_ONE);
			return Optional.empty();
		}

		final int maxAmountOfTicket = tickReservationManager.maxAmountOfTickets();

		if (selectionCount > maxAmountOfTicket) {
			bindingResult.reject(ErrorsCode.STEP_1_OVER_MAXIMUM, new Object[] { maxAmountOfTicket }, null);
			return Optional.empty();
		}

		final List<TicketReservationModification> selected = selected();
		final ZoneId eventZoneId = selected.stream().findFirst().map(r -> {
			TicketCategory tc = eventManager.getTicketCategoryById(r.getTicketCategoryId(), event.getId());
			return eventManager.findEventByTicketCategory(tc).getZoneId();
		}).orElseThrow(IllegalStateException::new);

		List<TicketReservationWithOptionalCodeModification> res = new ArrayList<>();
		//
		Optional<SpecialPrice> specialCode = Optional.ofNullable(StringUtils.trimToNull(promoCode)).flatMap(
				(trimmedCode) -> OptionalWrapper.optionally(() -> tickReservationManager.getSpecialPriceByCode(trimmedCode)));
		//
		final ZonedDateTime now = ZonedDateTime.now(eventZoneId);
		selected.forEach((r) -> validateCategory(bindingResult, tickReservationManager, eventManager, event, maxAmountOfTicket, res, specialCode, now, r, request));
		return bindingResult.hasErrors() ? Optional.empty() : Optional.of(res);
	}

	private static void validateCategory(BindingResult bindingResult, TicketReservationManager tickReservationManager, EventManager eventManager, Event event, int maxAmountOfTicket, List<TicketReservationWithOptionalCodeModification> res, Optional<SpecialPrice> specialCode, ZonedDateTime now, TicketReservationModification r, HttpServletRequest request) {
		TicketCategory tc = eventManager.getTicketCategoryById(r.getTicketCategoryId(), event.getId());
		SaleableTicketCategory ticketCategory = new SaleableTicketCategory(tc, now, event, tickReservationManager
                .countUnsoldTicket(event.getId(), tc.getId()), maxAmountOfTicket);

		if (!ticketCategory.getSaleable()) {
            bindingResult.reject(ErrorsCode.STEP_1_TICKET_CATEGORY_MUST_BE_SALEABLE);
			return;
        }

		res.add(new TicketReservationWithOptionalCodeModification(r, ticketCategory.isAccessRestricted() ? specialCode : Optional.empty()));
	}
}
