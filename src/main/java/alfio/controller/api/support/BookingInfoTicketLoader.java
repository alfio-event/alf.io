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
package alfio.controller.api.support;

import alfio.controller.support.Formatters;
import alfio.manager.EventManager;
import alfio.manager.PurchaseContextFieldManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.repository.AdditionalServiceItemRepository;
import alfio.util.ClockProvider;
import alfio.util.EventUtil;
import alfio.util.Validator;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.model.system.ConfigurationKeys.*;

@Component
@AllArgsConstructor
public class BookingInfoTicketLoader {

    private final EventManager eventManager;
    private final ConfigurationManager configurationManager;
    private final PurchaseContextFieldManager purchaseContextFieldManager;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;
    private final TicketReservationManager ticketReservationManager;
    private final MessageSourceManager messageSourceManager;
    private final ClockProvider clockProvider;


    public BookingInfoTicket toBookingInfoTicket(Ticket ticket, Event event, Set<PurchaseContextFieldConfiguration.Context> contexts) {
        var descriptionsByTicketFieldId = purchaseContextFieldManager.findDescriptionsGroupedByFieldId(event);

        var valuesByTicketIds = purchaseContextFieldManager.findAllValuesByTicketId(ticket.getId());

        boolean hasPaidSupplement = ticketReservationManager.hasPaidSupplements(ticket.getEventId(), ticket.getTicketsReservationId());
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
            onlineEventStarted,
            contexts);
    }

    public BookingInfoTicket toBookingInfoTicket(Ticket t,
                                                 boolean hasPaidSupplement,
                                                 Event event,
                                                 Validator.AdditionalFieldsFilterer additionalFieldsFilterer,
                                                 Map<Long, List<PurchaseContextFieldDescription>> descriptionsByTicketFieldId,
                                                 Map<Integer, List<PurchaseContextFieldValue>> valuesByTicketIds,
                                                 Map<String, String> formattedOnlineCheckInDate,
                                                 boolean onlineEventStarted,
                                                 Set<PurchaseContextFieldConfiguration.Context> contexts) {
        // TODO: n+1, should be cleaned up! see TicketDecorator.getCancellationEnabled
        var configuration = configurationManager.getFor(EnumSet.of(ALLOW_FREE_TICKETS_CANCELLATION, SEND_TICKETS_AUTOMATICALLY, ALLOW_TICKET_DOWNLOAD), ConfigurationLevel.ticketCategory(event, t.getCategoryId()));
        boolean cancellationEnabled = t.getFinalPriceCts() == 0 &&
            !event.expired() &&
            (!hasPaidSupplement && configuration.get(ALLOW_FREE_TICKETS_CANCELLATION).getValueAsBooleanOrDefault()) && // freeCancellationEnabled
            eventManager.checkTicketCancellationPrerequisites().apply(t); // cancellationPrerequisitesMet
        //
        return toBookingInfoTicket(t,
            cancellationEnabled,
            configuration.get(SEND_TICKETS_AUTOMATICALLY).getValueAsBooleanOrDefault(),
            configuration.get(ALLOW_TICKET_DOWNLOAD).getValueAsBooleanOrDefault(),
            additionalFieldsFilterer.getFieldsForTicket(t.getUuid(), contexts),
            descriptionsByTicketFieldId,
            valuesByTicketIds.getOrDefault(t.getId(), Collections.emptyList()),
            formattedOnlineCheckInDate,
            onlineEventStarted);
    }

    public Validator.AdditionalFieldsFilterer getSubscriptionFieldsFilterer(String reservationId, SubscriptionDescriptor descriptor) {
        var fields = purchaseContextFieldManager.findAdditionalFields(descriptor);
        return new Validator.AdditionalFieldsFilterer(fields,
            List.of(),
            false,
            List.of());
    }
    public Validator.AdditionalFieldsFilterer getTicketFieldsFilterer(String reservationId, Event event) {
        var fields = purchaseContextFieldManager.findAdditionalFields(event);
        return new Validator.AdditionalFieldsFilterer(fields,
            ticketReservationManager.findTicketsInReservation(reservationId),
            event.supportsLinkedAdditionalServices(),
            additionalServiceItemRepository.findByReservationUuid(event.getId(), reservationId));
    }

    private static BookingInfoTicket toBookingInfoTicket(Ticket ticket,
                                                         boolean cancellationEnabled,
                                                         boolean sendMailEnabled,
                                                         boolean downloadEnabled,
                                                         List<PurchaseContextFieldConfiguration> ticketFields,
                                                         Map<Long, List<PurchaseContextFieldDescription>> descriptionsByTicketFieldId,
                                                         List<PurchaseContextFieldValue> purchaseContextFieldValues,
                                                         Map<String, String> formattedOnlineCheckInDate,
                                                         boolean onlineEventStarted) {


        var valuesById = purchaseContextFieldValues.stream()
            .collect(Collectors.groupingBy(PurchaseContextFieldValue::getFieldConfigurationId));


        var ticketFieldsAdditional = ticketFields.stream()
            // hide additional service related fields
            .filter(ticketFieldConfiguration -> ticketFieldConfiguration.getAdditionalServiceId() != null)
            .sorted(Comparator.comparing(PurchaseContextFieldConfiguration::getOrder))
            .flatMap(tfc -> toAdditionalFieldsStream(descriptionsByTicketFieldId, tfc, valuesById))
            .collect(Collectors.toList());

        return new BookingInfoTicket(ticket.getUuid(),
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

    public static Stream<AdditionalField> toAdditionalFieldsStream(Map<Long, List<PurchaseContextFieldDescription>> descriptionsByTicketFieldId, PurchaseContextFieldConfiguration tfc, Map<Long, List<PurchaseContextFieldValue>> valuesById) {
        var tfd = descriptionsByTicketFieldId.get(tfc.getId()).get(0);//take first, temporary!
        var fieldValues = valuesById.get(tfc.getId());
        var descriptions = fromFieldDescriptions(descriptionsByTicketFieldId.get(tfc.getId()));
        if (fieldValues == null) {
            var t = new FieldConfigurationDescriptionAndValue(tfc, tfd, tfc.getCount(), null);
            return Stream.of(toAdditionalField(t, descriptions));
        }
        return fieldValues.stream()
            .map(fieldValue -> {
                var t = new FieldConfigurationDescriptionAndValue(tfc, tfd, tfc.getCount(), fieldValue.getValue());
                return toAdditionalField(t, descriptions);
            });
    }

    public static AdditionalField toAdditionalField(FieldConfigurationDescriptionAndValue t, Map<String, Description> description) {
        var fields = t.getFields().stream().map(f -> new Field(f.getFieldIndex(), f.getFieldValue())).collect(Collectors.toList());
        var restrictedValues = t.getRestrictedValues().stream()
            .filter(rv -> Objects.equals(t.getValue(), rv) || !t.getDisabledValues().contains(rv))
            .collect(Collectors.toList());
        return new AdditionalField(t.getName(), t.getValue(), t.getType(), t.isRequired(), t.isEditable(),
            t.getMinLength(), t.getMaxLength(), restrictedValues,
            fields, t.isBeforeStandardFields(), description);
    }

    public static Map<String, Description> fromFieldDescriptions(List<PurchaseContextFieldDescription> descs) {
        return descs.stream().collect(Collectors.toMap(PurchaseContextFieldDescription::getLocale,
            d -> new Description(d.getLabelDescription(), d.getPlaceholderDescription(), d.getRestrictedValuesDescription())));
    }
}
