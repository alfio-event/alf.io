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

import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.modification.TicketReservationModification;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.user.Organization;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.WaitingQueueRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.*;
import ch.digitalfondue.npjt.AffectedRowCountAndKey;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.EventUtil.determineAvailableSeats;

@Component
public class WaitingQueueManager {

    private static final Logger log = LoggerFactory.getLogger(WaitingQueueManager.class);

    private final WaitingQueueRepository waitingQueueRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final ConfigurationManager configurationManager;
    private final EventStatisticsManager eventStatisticsManager;
    private final NotificationManager notificationManager;
    private final TemplateManager templateManager;
    private final MessageSourceManager messageSourceManager;
    private final OrganizationRepository organizationRepository;
    private final ExtensionManager extensionManager;
    private final ClockProvider clockProvider;

    public WaitingQueueManager(WaitingQueueRepository waitingQueueRepository,
                               TicketCategoryRepository ticketCategoryRepository,
                               ConfigurationManager configurationManager,
                               EventStatisticsManager eventStatisticsManager,
                               NotificationManager notificationManager,
                               TemplateManager templateManager,
                               MessageSourceManager messageSourceManager,
                               OrganizationRepository organizationRepository,
                               ExtensionManager extensionManager,
                               ClockProvider clockProvider) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.configurationManager = configurationManager;
        this.eventStatisticsManager = eventStatisticsManager;
        this.notificationManager = notificationManager;
        this.templateManager = templateManager;
        this.messageSourceManager = messageSourceManager;
        this.organizationRepository = organizationRepository;
        this.extensionManager = extensionManager;
        this.clockProvider = clockProvider;
    }

    public boolean subscribe(Event event, CustomerName customerName, String email, Integer selectedCategoryId, Locale userLanguage) {
        try {
            if(configurationManager.getFor(STOP_WAITING_QUEUE_SUBSCRIPTIONS, ConfigurationLevel.event(event)).getValueAsBooleanOrDefault()) {
                log.info("waiting list subscription denied for event {} ({})", event.getShortName(), event.getId());
                return false;
            }
            WaitingQueueSubscription.Type subscriptionType = getSubscriptionType(event);
            validateSubscriptionType(event, subscriptionType);
            validateSelectedCategoryId(event.getId(), selectedCategoryId);
            AffectedRowCountAndKey<Integer> key = waitingQueueRepository.insert(event.getId(), customerName.getFullName(), customerName.getFirstName(), customerName.getLastName(), email, event.now(clockProvider), userLanguage.getLanguage(), subscriptionType, selectedCategoryId);
            notifySubscription(event, customerName, email, userLanguage, subscriptionType);
            extensionManager.handleWaitingQueueSubscription(waitingQueueRepository.loadById(key.getKey()));
            return true;
        } catch(DuplicateKeyException e) {
            return true;//why are you subscribing twice?
        } catch (Exception e) {
            log.error("error during subscription", e);
            return false;
        }
    }

    private void validateSelectedCategoryId(int eventId, Integer selectedCategoryId) {
        Optional.ofNullable(selectedCategoryId).ifPresent(id -> Validate.isTrue(ticketCategoryRepository.findUnboundedOrderByExpirationDesc(eventId).stream().anyMatch(c -> id.equals(c.getId()))));
    }

    private void notifySubscription(Event event, CustomerName name, String email, Locale userLanguage, WaitingQueueSubscription.Type subscriptionType) {
        var messageSource = messageSourceManager.getMessageSourceFor(event);
        Organization organization = organizationRepository.getById(event.getOrganizationId());
        Map<String, Object> model = TemplateResource.buildModelForWaitingQueueJoined(organization, event, name);
        notificationManager.sendSimpleEmail(event, null, email, messageSource.getMessage("email-waiting-queue.subscribed.subject", new Object[]{event.getDisplayName()}, userLanguage),
                () -> templateManager.renderTemplate(event, TemplateResource.WAITING_QUEUE_JOINED, model, userLanguage));
        if(configurationManager.getFor(ENABLE_WAITING_QUEUE_NOTIFICATION, ConfigurationLevel.event(event)).getValueAsBooleanOrDefault()) {
            String adminTemplate = messageSource.getMessage("email-waiting-queue.subscribed.admin.text",
                    new Object[] {subscriptionType, event.getDisplayName()}, Locale.ENGLISH);
            notificationManager.sendSimpleEmail(event, null, organization.getEmail(), messageSource.getMessage("email-waiting-queue.subscribed.admin.subject",
                            new Object[]{event.getDisplayName()}, Locale.ENGLISH),
                    () -> RenderedTemplate.plaintext(templateManager.renderString(event, adminTemplate, model, Locale.ENGLISH, TemplateManager.TemplateOutput.TEXT), model));
        }

    }

    private WaitingQueueSubscription.Type getSubscriptionType(Event event) {
        ZonedDateTime now = event.now(clockProvider);
        return ticketCategoryRepository.findAllTicketCategories(event.getId()).stream()
                .filter(tc -> !tc.isAccessRestricted())
                .filter(tc -> now.isAfter(tc.getInception(event.getZoneId())))
                .findFirst()
                .map(tc -> WaitingQueueSubscription.Type.SOLD_OUT)
                .orElse(WaitingQueueSubscription.Type.PRE_SALES);
    }

    private void validateSubscriptionType(EventAndOrganizationId event, WaitingQueueSubscription.Type type) {
        if(type == WaitingQueueSubscription.Type.PRE_SALES) {
            Validate.isTrue(configurationManager.getFor(ENABLE_PRE_REGISTRATION, ConfigurationLevel.event(event)).getValueAsBooleanOrDefault(), "PRE_SALES Waiting list is not active");
        } else {
            Validate.isTrue(eventStatisticsManager.noSeatsAvailable().test(event), "SOLD_OUT Waiting list is not active");
        }
    }

    public List<WaitingQueueSubscription> loadAllSubscriptionsForEvent(int eventId) {
        return waitingQueueRepository.loadAllWaiting(eventId);
    }

    public Optional<WaitingQueueSubscription> updateSubscriptionStatus(int id, WaitingQueueSubscription.Status newStatus, WaitingQueueSubscription.Status currentStatus) {
        return Optional.of(waitingQueueRepository.updateStatus(id, newStatus, currentStatus))
            .filter(i -> i > 0)
            .map(i -> waitingQueueRepository.loadById(id));
    }

    public int countSubscribers(int eventId) {
        return waitingQueueRepository.countWaitingPeople(eventId);
    }

}
