package alfio.controller.form;

import static java.util.stream.Collectors.toList;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.validation.Errors;

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
import alfio.util.OptionalWrapper;

public class ValidateReservationForm {
	private ReservationForm form;
	public ValidateReservationForm(ReservationForm form) {
		this.form = form;
	}

	public Optional<Pair<List<TicketReservationWithOptionalCodeModification>, List<ASReservationWithOptionalCodeModification>>> validate(Errors bindingResult,
			TicketReservationManager tickReservationManager,
			AdditionalServiceRepository additionalServiceRepository,
			EventManager eventManager,
			Event event) {
		int selectionCount = form.ticketSelectionCount();

		if (selectionCount <= 0) {
			bindingResult.reject(ErrorsCode.STEP_1_SELECT_AT_LEAST_ONE);
			return Optional.empty();
		}

		List<Pair<TicketReservationModification, Integer>> maxTicketsByTicketReservation = form.selected().stream()
				.map(r -> Pair.of(r, tickReservationManager.maxAmountOfTicketsForCategory(event.getOrganizationId(), event.getId(), r.getTicketCategoryId())))
				.collect(toList());

		Optional<Pair<TicketReservationModification, Integer>> error = maxTicketsByTicketReservation.stream()
				.filter(p -> p.getKey().getAmount() > p.getValue())
				.findAny();

		if(error.isPresent()) {
			bindingResult.reject(ErrorsCode.STEP_1_OVER_MAXIMUM, new Object[] { error.get().getValue() }, null);
			return Optional.empty();
		}

		final List<TicketReservationModification> categories = form.selected();
		final List<AdditionalServiceReservationModification> additionalServices = form.selectedAdditionalServices();

		final boolean validCategorySelection = categories.stream().allMatch(c -> {
			TicketCategory tc = eventManager.getTicketCategoryById(c.getTicketCategoryId(), event.getId());
			return OptionalWrapper.optionally(() -> eventManager.findEventByTicketCategory(tc)).isPresent();
		});


		// fixed 05.07 by hhkim
		final boolean validAdditionalServiceSelected = additionalServices.stream().allMatch(asm -> {
			AdditionalService as = eventManager.getAdditionalServiceById(asm.getAdditionalServiceId(), event.getId());
			ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
			return as.isValidTime(now, event) && asm.isValidPrice(as, selectionCount) &&
					OptionalWrapper.optionally(() -> eventManager.findEventByAdditionalService(as)).isPresent();
		});

		if(!validCategorySelection || !validAdditionalServiceSelected) {
			bindingResult.reject(ErrorsCode.STEP_1_TICKET_CATEGORY_MUST_BE_SALEABLE);
			return Optional.empty();
		}

		List<TicketReservationWithOptionalCodeModification> res = new ArrayList<>();
		//
		Optional<SpecialPrice> specialCode = Optional.ofNullable(StringUtils.trimToNull(form.getPromoCode())).flatMap(
				(trimmedCode) -> tickReservationManager.getSpecialPriceByCode(trimmedCode));
		//
		final ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
		maxTicketsByTicketReservation.forEach((pair) -> validateCategory(bindingResult, tickReservationManager, eventManager, event, pair.getRight(), res, specialCode, now, pair.getLeft()));
		return bindingResult.hasErrors() ? Optional.empty() : Optional.of(Pair.of(res, additionalServices.stream().map(as -> new ASReservationWithOptionalCodeModification(as, specialCode)).collect(Collectors.toList())));
	}

	public static void validateCategory(Errors bindingResult, TicketReservationManager tickReservationManager, EventManager eventManager,
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
