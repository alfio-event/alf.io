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
package alfio.job.executor;

import alfio.manager.AdminReservationRequestManager;
import alfio.manager.system.AdminJobExecutor;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.FieldNameAndValue;
import alfio.model.TicketCategory;
import alfio.model.modification.AdminReservationModification;
import alfio.model.modification.AdminReservationModification.*;
import alfio.model.modification.DateTimeModification;
import alfio.model.subscription.AvailableSubscriptionsByEvent;
import alfio.model.system.AdminJobSchedule;
import alfio.repository.EventRepository;
import alfio.repository.PurchaseContextFieldRepository;
import alfio.repository.SubscriptionRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.util.ClockProvider;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static alfio.manager.NotificationManager.SEND_TICKET_CC;
import static alfio.model.system.ConfigurationKeys.GENERATE_TICKETS_FOR_SUBSCRIPTIONS;

@Component
public class AssignTicketToSubscriberJobExecutor implements AdminJobExecutor {

    public static final String EVENT_ID = "eventId";
    public static final String ORGANIZATION_ID = "organizationId";
    public static final String FORCE_GENERATION = "forceGeneration";
    private static final Logger log = LoggerFactory.getLogger(AssignTicketToSubscriberJobExecutor.class);
    private final AdminReservationRequestManager requestManager;
    private final ConfigurationManager configurationManager;
    private final SubscriptionRepository subscriptionRepository;
    private final EventRepository eventRepository;
    private final ClockProvider clockProvider;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final PurchaseContextFieldRepository purchaseContextFieldRepository;

    public AssignTicketToSubscriberJobExecutor(AdminReservationRequestManager requestManager,
                                               ConfigurationManager configurationManager,
                                               SubscriptionRepository subscriptionRepository,
                                               EventRepository eventRepository,
                                               ClockProvider clockProvider,
                                               TicketCategoryRepository ticketCategoryRepository,
                                               PurchaseContextFieldRepository purchaseContextFieldRepository) {
        this.requestManager = requestManager;
        this.configurationManager = configurationManager;
        this.subscriptionRepository = subscriptionRepository;
        this.eventRepository = eventRepository;
        this.clockProvider = clockProvider;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.purchaseContextFieldRepository = purchaseContextFieldRepository;
    }

    @Override
    public Set<JobName> getJobNames() {
        return EnumSet.of(JobName.ASSIGN_TICKETS_TO_SUBSCRIBERS);
    }

    @Override
    public String process(AdminJobSchedule schedule) {
        var metadata = schedule.getMetadata();
        log.debug("Executing AssignTicketToSubscribers with metadata {}", metadata);
        // 1. Find all subscriptions bought until now. Filters:
        //     - subscription_descriptor_fk is linked to the current event
        //     - id does not have any reservations attached to the current event
        //     - validity_from is <= now()
        //     - validity_to is null or > now()
        //     - status = 'ACQUIRED'
        var subscriptionsByEvent = subscriptionRepository.loadAvailableSubscriptionsByEvent((Integer) metadata.get(EVENT_ID), (Integer) metadata.get(ORGANIZATION_ID));
        if (!subscriptionsByEvent.isEmpty()) {
            boolean forceGeneration = Boolean.TRUE.equals(metadata.get(FORCE_GENERATION));
            Set<Integer> ids = subscriptionsByEvent.keySet();
            var fieldsByEventId = purchaseContextFieldRepository.findAdditionalFieldNamesForEvents(ids);
            eventRepository.findByIds(ids).forEach(event -> {
                // 2. for each event check if the flag is active, unless forceGeneration has been specified
                boolean generationEnabled = forceGeneration || configurationManager.getFor(GENERATE_TICKETS_FOR_SUBSCRIPTIONS, event.getConfigurationLevel())
                    .getValueAsBooleanOrDefault();
                if (generationEnabled) {
                    var subscriptions = subscriptionsByEvent.get(event.getId());
                    var availableCategories = ticketCategoryRepository.findAllWithAvailableTickets(event.getId());
                    if (CollectionUtils.isNotEmpty(availableCategories)) {
                        // 3. create reservation import request for the subscribers. ID is "AUTO_${eventShortName}_${now_ISO}"
                        var requestId = String.format("AUTO_%s_%s", event.getShortName(), LocalDateTime.now(clockProvider.getClock()).format(DateTimeFormatter.ISO_DATE_TIME));
                        requestManager.insertRequest(requestId,
                            buildBody(event, subscriptions, availableCategories, fieldsByEventId.getOrDefault(event.getId(), Set.of())),
                            event,
                            false,
                            "admin");

                    } else {
                        log.warn("Cannot find a suitable ticket category for event {}", event.getId());
                    }
                }
            });
        } else {
            log.debug("No subscriptions found for metadata {}", metadata);
        }
        return null;
    }

    private AdminReservationModification buildBody(Event event,
                                                   List<AvailableSubscriptionsByEvent> subscriptions,
                                                   List<TicketCategory> availableCategories,
                                                   Set<String> fieldsForEvent) {
        var clock = clockProvider.getClock();
        var subscriptionsByDescriptor = subscriptions.stream().collect(Collectors.groupingBy(AvailableSubscriptionsByEvent::getDescriptorId));
        var tickets = subscriptionsByDescriptor.values().stream().flatMap(availableSubscriptionsByEvents -> {
            var firstValue = availableSubscriptionsByEvents.get(0);
            var categoryOptional = availableCategories.stream()
                .filter(c -> CollectionUtils.isEmpty(firstValue.getCompatibleCategoryIds()) || firstValue.getCompatibleCategoryIds().contains(c.getId()))
                .findFirst();

            if (categoryOptional.isEmpty()) {
                var categoriesIds = availableCategories.stream().map(TicketCategory::getId).collect(Collectors.toSet());
                log.warn("Skipping descriptor {}. No compatible category found (wanted: one of {}, available: {})", firstValue.getDescriptorId(), firstValue.getCompatibleCategoryIds(), categoriesIds);
            }

            return categoryOptional.stream()
                .map(category -> new TicketsInfo(
                    new Category(category.getId(), category.getName(), category.getPrice(), category.getTicketAccessType()),
                    toAttendees(availableSubscriptionsByEvents, fieldsForEvent),
                    false,
                    false
                ));
        }).collect(Collectors.toList());

        return new AdminReservationModification(
            new DateTimeModification(LocalDate.now(clock), LocalTime.now(clock).plusMinutes(5L)),
            new CustomerData("", "", "", null, "", null, null, null, null),
            tickets,
            event.getContentLanguages().get(0).getLanguage(),
            false,
            false,
            null,
            new Notification(false, true),
            null,
            null
        );
    }

    private List<Attendee> toAttendees(List<AvailableSubscriptionsByEvent> subscriptions,
                                       Set<String> fieldsForEvent) {
        return subscriptions.stream()
            .map(s -> {
                Map<String, String> metadata = Map.of();
                if (!s.getEmailAddress().strip().equalsIgnoreCase(s.getReservationEmail().strip())) {
                    metadata = Map.of(SEND_TICKET_CC, "[\""+s.getReservationEmail()+"\"]");
                }

                return new Attendee(null,
                        s.getFirstName(),
                        s.getLastName(),
                        s.getEmailAddress(),
                        s.getUserLanguage(),
                        false,
                        s.getSubscriptionId() + "_auto",
                        s.getSubscriptionId(),
                        parseExistingFields(s, fieldsForEvent),
                        metadata);
                }
            ).collect(Collectors.toList());
    }

    private static Map<String, List<String>> parseExistingFields(AvailableSubscriptionsByEvent s, Set<String> eventFieldNames) {
        return s.getAdditionalFields().stream()
            .filter(f -> eventFieldNames.contains(f.getName()))
            .collect(Collectors.groupingBy(FieldNameAndValue::getName, Collectors.mapping(FieldNameAndValue::getValue, Collectors.toList())));
    }
}
