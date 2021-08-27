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
package alfio.controller.api.v2.user.support;

import alfio.controller.api.support.TicketHelper;
import alfio.controller.api.v2.model.ReservationInfo;
import alfio.controller.support.Formatters;
import alfio.manager.EventManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.repository.AdditionalServiceItemRepository;
import alfio.repository.TicketFieldRepository;
import alfio.util.ClockProvider;
import alfio.util.EventUtil;
import alfio.util.Validator;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.*;

@Component
@AllArgsConstructor
public class BookingInfoTicketLoader {

    private final EventManager eventManager;
    private final ConfigurationManager configurationManager;
    private final TicketFieldRepository ticketFieldRepository;
    private final TicketHelper ticketHelper;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;
    private final TicketReservationManager ticketReservationManager;
    private final MessageSourceManager messageSourceManager;
    private final ClockProvider clockProvider;


    public ReservationInfo.BookingInfoTicket toBookingInfoTicket(Ticket ticket, Event event) {
        var descriptionsByTicketFieldId = ticketFieldRepository.findDescriptions(event.getShortName())
            .stream()
            .collect(Collectors.groupingBy(TicketFieldDescription::getTicketFieldConfigurationId));

        var valuesByTicketIds = ticketFieldRepository.findAllValuesByTicketIds(List.of(ticket.getId()))
            .stream()
            .collect(Collectors.groupingBy(TicketFieldValue::getTicketId));

        boolean hasPaidSupplement = ticketReservationManager.hasPaidSupplements(ticket.getTicketsReservationId());
        Map<String, String> formattedDates = Map.of();
        boolean onlineEventStarted = false;
        if(event.isOnline()) {
            var eventConfiguration = eventManager.getMetadataForEvent(event).getOnlineConfiguration();
            var ticketCategoryConfiguration = eventManager.getMetadataForCategory(event, ticket.getCategoryId()).getOnlineConfiguration();
            var checkInDate = EventUtil.firstMatchingCallLink(event.getZoneId(), ticketCategoryConfiguration, eventConfiguration)
                .map(link -> link.getValidFrom().atZone(event.getZoneId()))
                .orElse(event.getBegin());
            formattedDates = Formatters.getFormattedDate(event, checkInDate, "common.ticket-category.date-format",
                messageSourceManager.getMessageSourceFor(event));
            onlineEventStarted = event.now(clockProvider).isAfter(checkInDate);
        }

        return toBookingInfoTicket(ticket,
            hasPaidSupplement,
            event,
            getTicketFieldsFilterer(ticket.getTicketsReservationId(), event),
            descriptionsByTicketFieldId,
            valuesByTicketIds,
            formattedDates,
            onlineEventStarted);
    }

    public ReservationInfo.BookingInfoTicket toBookingInfoTicket(Ticket t,
                                                                 boolean hasPaidSupplement,
                                                                 Event event,
                                                                 Validator.TicketFieldsFilterer ticketFieldsFilterer,
                                                                 Map<Integer, List<TicketFieldDescription>> descriptionsByTicketFieldId,
                                                                 Map<Integer, List<TicketFieldValue>> valuesByTicketIds,
                                                                 Map<String, String> formattedOnlineCheckInDate,
                                                                 boolean onlineEventStarted) {
        // TODO: n+1, should be cleaned up! see TicketDecorator.getCancellationEnabled
        var configuration = configurationManager.getFor(EnumSet.of(ALLOW_FREE_TICKETS_CANCELLATION, SEND_TICKETS_AUTOMATICALLY, ALLOW_TICKET_DOWNLOAD), ConfigurationLevel.ticketCategory(event, t.getCategoryId()));
        boolean cancellationEnabled = t.getFinalPriceCts() == 0 &&
            (!hasPaidSupplement && configuration.get(ALLOW_FREE_TICKETS_CANCELLATION).getValueAsBooleanOrDefault()) && // freeCancellationEnabled
            eventManager.checkTicketCancellationPrerequisites().apply(t); // cancellationPrerequisitesMet
        //
        return toBookingInfoTicket(t,
            cancellationEnabled,
            configuration.get(SEND_TICKETS_AUTOMATICALLY).getValueAsBooleanOrDefault(),
            configuration.get(ALLOW_TICKET_DOWNLOAD).getValueAsBooleanOrDefault(),
            ticketFieldsFilterer.getFieldsForTicket(t.getUuid()),
            descriptionsByTicketFieldId,
            valuesByTicketIds.getOrDefault(t.getId(), Collections.emptyList()),
            formattedOnlineCheckInDate,
            onlineEventStarted);
    }

    public Validator.TicketFieldsFilterer getTicketFieldsFilterer(String reservationId, EventAndOrganizationId event) {
        var fields = ticketFieldRepository.findAdditionalFieldsForEvent(event.getId());
        return new Validator.TicketFieldsFilterer(fields, ticketHelper.getTicketUUIDToCategoryId(),
            new HashSet<>(additionalServiceItemRepository.findAdditionalServiceIdsByReservationUuid(reservationId)),
            ticketReservationManager.findFirstInReservation(reservationId));
    }

    private static ReservationInfo.BookingInfoTicket toBookingInfoTicket(Ticket ticket,
                                                                         boolean cancellationEnabled,
                                                                         boolean sendMailEnabled,
                                                                         boolean downloadEnabled,
                                                                         List<TicketFieldConfiguration> ticketFields,
                                                                         Map<Integer, List<TicketFieldDescription>> descriptionsByTicketFieldId,
                                                                         List<TicketFieldValue> ticketFieldValues,
                                                                         Map<String, String> formattedOnlineCheckInDate,
                                                                         boolean onlineEventStarted) {


        var valueById = ticketFieldValues.stream().collect(Collectors.toMap(TicketFieldValue::getTicketFieldConfigurationId, Function.identity()));


        var ticketFieldsAdditional = ticketFields.stream()
            .sorted(Comparator.comparing(TicketFieldConfiguration::getOrder))
            .map(tfc -> {
                var tfd = descriptionsByTicketFieldId.get(tfc.getId()).get(0);//take first, temporary!
                var fieldValue = valueById.get(tfc.getId());
                var t = new TicketFieldConfigurationDescriptionAndValue(tfc, tfd, tfc.getCount(), fieldValue == null ? null : fieldValue.getValue());
                var descs = fromFieldDescriptions(descriptionsByTicketFieldId.get(t.getTicketFieldConfigurationId()));
                return toAdditionalField(t, descs);
            }).collect(Collectors.toList());

        return new ReservationInfo.BookingInfoTicket(ticket.getUuid(),
            ticket.getFirstName(),
            ticket.getLastName(),
            ticket.getEmail(),
            ticket.getFullName(),
            ticket.getUserLanguage(),
            ticket.getAssigned(),
            ticket.getLockedAssignment(),
            ticket.getStatus() == Ticket.TicketStatus.ACQUIRED,
            cancellationEnabled,
            sendMailEnabled,
            downloadEnabled,
            ticketFieldsAdditional,
            formattedOnlineCheckInDate,
            onlineEventStarted);
    }

    private static ReservationInfo.AdditionalField toAdditionalField(TicketFieldConfigurationDescriptionAndValue t, Map<String, ReservationInfo.Description> description) {
        var fields = t.getFields().stream().map(f -> new ReservationInfo.Field(f.getFieldIndex(), f.getFieldValue())).collect(Collectors.toList());
        return new ReservationInfo.AdditionalField(t.getName(), t.getValue(), t.getType(), t.isRequired(), t.isEditable(),
            t.getMinLength(), t.getMaxLength(), t.getRestrictedValues(),
            fields, t.isBeforeStandardFields(), description);
    }

    private static Map<String, ReservationInfo.Description> fromFieldDescriptions(List<TicketFieldDescription> descs) {
        return descs.stream().collect(Collectors.toMap(TicketFieldDescription::getLocale,
            d -> new ReservationInfo.Description(d.getLabelDescription(), d.getPlaceholderDescription(), d.getRestrictedValuesDescription())));
    }
}
