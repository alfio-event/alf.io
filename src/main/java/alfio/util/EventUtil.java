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
package alfio.util;

import alfio.controller.decorator.SaleableTicketCategory;
import alfio.manager.PurchaseContextFieldManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.metadata.JoinLink;
import alfio.model.metadata.OnlineConfiguration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.AdditionalServiceItemRepository;
import alfio.repository.TicketRepository;
import biweekly.ICalVersion;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.io.text.ICalWriter;
import biweekly.property.Method;
import biweekly.property.Organizer;
import biweekly.property.Status;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static alfio.model.EventCheckInInfo.VERSION_FOR_CODE_CASE_INSENSITIVE;
import static alfio.model.EventCheckInInfo.VERSION_FOR_LINKED_ADDITIONAL_SERVICE;
import static alfio.model.system.ConfigurationKeys.*;
import static java.time.temporal.ChronoField.*;

@Log4j2
public final class EventUtil {

    private EventUtil() {}

    private static final DateTimeFormatter JSON_TIME_FORMATTER = new DateTimeFormatterBuilder()
        .appendValue(HOUR_OF_DAY, 2)
        .appendLiteral(':')
        .appendValue(MINUTE_OF_HOUR, 2)
        .optionalStart()
        .appendLiteral(':')
        .appendValue(SECOND_OF_MINUTE, 2)
        .toFormatter(Locale.ROOT);

    public static final DateTimeFormatter JSON_DATETIME_FORMATTER = new DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .append(DateTimeFormatter.ISO_LOCAL_DATE)
        .appendLiteral('T')
        .append(JSON_TIME_FORMATTER)
        .appendLiteral('Z')
        .toFormatter(Locale.ROOT);

    public static boolean displayWaitingQueueForm(Event event, List<SaleableTicketCategory> categories, ConfigurationManager configurationManager, Predicate<EventAndOrganizationId> noTicketsAvailable) {
        var confVal = configurationManager.getFor(List.of(STOP_WAITING_QUEUE_SUBSCRIPTIONS, ENABLE_PRE_REGISTRATION, ENABLE_WAITING_QUEUE), event.getConfigurationLevel());
        return !confVal.get(STOP_WAITING_QUEUE_SUBSCRIPTIONS).getValueAsBooleanOrDefault()
            && checkWaitingQueuePreconditions(event, categories, noTicketsAvailable, confVal);
    }

    private static boolean checkWaitingQueuePreconditions(Event event, List<SaleableTicketCategory> categories, Predicate<EventAndOrganizationId> noTicketsAvailable, Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> confVal) {
        return findLastCategory(categories).map(lastCategory -> {
            ZonedDateTime now = event.now(ClockProvider.clock());
            if(isPreSales(event, categories)) {
                return confVal.get(ENABLE_PRE_REGISTRATION).getValueAsBooleanOrDefault();
            } else if(confVal.get(ENABLE_WAITING_QUEUE).getValueAsBooleanOrDefault()) {
                return now.isBefore(lastCategory.getZonedExpiration()) && noTicketsAvailable.test(event);
            }
            return false;
        }).orElse(false);
    }

    public static boolean checkWaitingQueuePreconditions(Event event, List<SaleableTicketCategory> categories, ConfigurationManager configurationManager, Predicate<EventAndOrganizationId> noTicketsAvailable) {
        var confVal = configurationManager.getFor(List.of(ENABLE_PRE_REGISTRATION, ENABLE_WAITING_QUEUE), event.getConfigurationLevel());
        return checkWaitingQueuePreconditions(event, categories, noTicketsAvailable, confVal);
    }

    private static Optional<SaleableTicketCategory> findLastCategory(List<SaleableTicketCategory> categories) {
        return sortCategories(categories, (c1, c2) -> c2.getUtcExpiration().compareTo(c1.getUtcExpiration())).findFirst();
    }

    private static Optional<SaleableTicketCategory> findFirstCategory(List<SaleableTicketCategory> categories) {
        return sortCategories(categories, Comparator.comparing(SaleableTicketCategory::getUtcExpiration)).findFirst();
    }

    private static Stream<SaleableTicketCategory> sortCategories(List<SaleableTicketCategory> categories, Comparator<SaleableTicketCategory> comparator) {
        return Optional.ofNullable(categories).orElse(Collections.emptyList()).stream().sorted(comparator);
    }

    public static boolean isPreSales(Event event, List<SaleableTicketCategory> categories) {
        ZonedDateTime now = event.now(ClockProvider.clock());
        return findFirstCategory(categories).map(c -> now.isBefore(c.getZonedInception())).orElse(false);
    }

    public static Stream<MapSqlParameterSource> generateEmptyTickets(EventAndOrganizationId event, Date creationDate, int limit, Ticket.TicketStatus ticketStatus) {
        return generateStreamForTicketCreation(limit)
            .map(ps -> buildTicketParams(event.getId(), creationDate, Optional.empty(), 0, ps, ticketStatus));
    }

    public static Stream<MapSqlParameterSource> generateStreamForTicketCreation(int limit) {
        return Stream.generate(MapSqlParameterSource::new)
                .limit(limit);
    }

    public static MapSqlParameterSource buildTicketParams(int eventId,
                                              Date creation,
                                              Optional<TicketCategory> tc,
                                              int srcPriceCts,
                                              MapSqlParameterSource ps) {
        return buildTicketParams(eventId, creation, tc, srcPriceCts, ps, Ticket.TicketStatus.FREE);
    }

    private static MapSqlParameterSource buildTicketParams(int eventId,
                                                           Date creation,
                                                           Optional<TicketCategory> tc,
                                                           int srcPriceCts,
                                                           MapSqlParameterSource ps,
                                                           Ticket.TicketStatus ticketStatus) {
        return ps.addValue("uuid", UUID.randomUUID().toString())
            .addValue("creation", creation)
            .addValue("categoryId", tc.map(TicketCategory::getId).orElse(null))
            .addValue("eventId", eventId)
            .addValue("status", ticketStatus.name())
            .addValue("srcPriceCts", srcPriceCts);
    }

    public static int evaluatePrice(BigDecimal price, boolean freeOfCharge, String currencyCode) {
        return freeOfCharge ? 0 : MonetaryUtil.unitToCents(Objects.requireNonNull(price), Objects.requireNonNull(currencyCode));
    }

    public static int determineAvailableSeats(TicketCategoryStatisticView tc, EventStatisticView e) {
        return tc.isBounded() ? tc.getNotSoldTicketsCount() : e.getDynamicAllocation();
    }

    public static Optional<byte[]> getIcalForEvent(Event event, TicketCategory ticketCategory, String description, Organization organization) {
    	 ICalendar ical = new ICalendar();
    	 ical.setProductId("-//Alf.io//Alf.io v2.0//EN");
    	 ical.setMethod(Method.PUBLISH);
    	 
         VEvent vEvent = new VEvent();
         vEvent.setSummary(event.getDisplayName());
         vEvent.setDescription(MustacheCustomTag.renderToTextCommonmark(description));
         if (!isAccessOnline(ticketCategory, event)) {
             // add location only if the attendee can access the location
            vEvent.setLocation(RegExUtils.replacePattern(event.getLocation(), "[\n\r\t]+", " "));
         }
         ZonedDateTime begin = Optional.ofNullable(ticketCategory).map(tc -> tc.getTicketValidityStart(event.getZoneId())).orElse(event.getBegin());
         ZonedDateTime end = Optional.ofNullable(ticketCategory).map(tc -> tc.getTicketValidityEnd(event.getZoneId())).orElse(event.getEnd());
         vEvent.setDateStart(Date.from(begin.toInstant()));
         vEvent.setDateEnd(Date.from(end.toInstant()));
         vEvent.setUrl(event.getWebsiteUrl());
         vEvent.setStatus(Status.confirmed());
         
         if(organization != null) {
        	 vEvent.setOrganizer(new Organizer(organization.getName(), organization.getEmail()));
         }
         
         ical.addEvent(vEvent);
         StringWriter strWriter = new StringWriter();
         try (ICalWriter writer = new ICalWriter(strWriter, ICalVersion.V2_0)) {
             writer.write(ical);
             return Optional.of(strWriter.toString().getBytes(StandardCharsets.UTF_8));
         } catch (IOException e) {
             log.warn("was not able to generate iCal for event " + event.getShortName(), e);
             return Optional.empty();
         }
    }
    
    public static Optional<byte[]> getIcalForEvent(Event event, TicketCategory ticketCategory, String description) {
    	return getIcalForEvent(event, ticketCategory, description, null);
    }

    public static String getGoogleCalendarURL(Event event, TicketCategory category, String description) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyMMdd'T'HHmmss");
        ZonedDateTime validityStart = Optional.ofNullable(category).map(TicketCategory::getTicketValidityStart).map(d -> d.withZoneSameInstant(event.getZoneId())).orElse(event.getBegin());
        ZonedDateTime validityEnd = Optional.ofNullable(category).map(TicketCategory::getTicketValidityEnd).map(d -> d.withZoneSameInstant(event.getZoneId())).orElse(event.getEnd());
        return UriComponentsBuilder.fromUriString("https://www.google.com/calendar/event")
            .queryParam("action", "TEMPLATE")
            .queryParam("dates", validityStart.format(formatter) + "/" + validityEnd.format(formatter))
            .queryParam("ctz", event.getTimeZone())
            .queryParam("text", event.getDisplayName())
            .queryParam("location", event.getLocation())
            .queryParam("details", StringUtils.abbreviate(description, 1024))
            .toUriString();
    }

    public static BiFunction<Ticket, Event, List<FieldConfigurationDescriptionAndValue>> retrieveFieldValues(TicketRepository ticketRepository,
                                                                                                             PurchaseContextFieldManager purchaseContextFieldManager,
                                                                                                             AdditionalServiceItemRepository additionalServiceItemRepository,
                                                                                                             boolean formatValues) {
        return (ticket, event) -> {
            String reservationId = ticket.getTicketsReservationId();
            var additionalServiceItems = getBookedAdditionalServices(ticketRepository, additionalServiceItemRepository, ticket, event, reservationId);
            return purchaseContextFieldManager.getFieldDescriptionAndValues(event, ticket, null, additionalServiceItems, ticket.getUserLanguage(), formatValues);
        };
    }

    private static List<BookedAdditionalService> getBookedAdditionalServices(TicketRepository ticketRepository, AdditionalServiceItemRepository additionalServiceItemRepository, Ticket ticket, Event event, String reservationId) {
        if (event.supportsLinkedAdditionalServices()) {
            return additionalServiceItemRepository.getAdditionalServicesBookedForTicket(reservationId, ticket.getId(), ticket.getUserLanguage(), event.getId());
        } else {
            var ticketsInReservation = ticketRepository.findFirstTicketIdInReservation(reservationId);
            if (ticketsInReservation.filter(id -> id == ticket.getId()).isPresent()) {
                return additionalServiceItemRepository.getAdditionalServicesBookedForReservation(reservationId, ticket.getUserLanguage(), event.getId());
            }
        }
        return List.of();
    }

    public static Optional<String> findMatchingLink(ZoneId eventZoneId, OnlineConfiguration categoryConfiguration, OnlineConfiguration eventConfiguration) {
        return firstMatchingCallLink(eventZoneId, categoryConfiguration, eventConfiguration)
            .map(JoinLink::getLink);
    }

    public static Optional<JoinLink> firstMatchingCallLink(ZoneId eventZoneId, OnlineConfiguration categoryConfiguration, OnlineConfiguration eventConfiguration) {
        var now = ZonedDateTime.now(ClockProvider.clock().withZone(eventZoneId));
        return firstMatchingCallLink(categoryConfiguration, eventZoneId, now)
            .or(() -> firstMatchingCallLink(eventConfiguration, eventZoneId, now));
    }

    private static Optional<JoinLink> firstMatchingCallLink(OnlineConfiguration onlineConfiguration, ZoneId zoneId, ZonedDateTime now) {
        return Optional.ofNullable(onlineConfiguration).stream()
            .flatMap(configuration -> configuration.getCallLinks().stream())
            .sorted(Comparator.comparing(JoinLink::getValidFrom).reversed())
            .filter(joinLink -> now.isBefore(joinLink.getValidTo().atZone(zoneId)) && now.plusSeconds(1).isAfter(joinLink.getValidFrom().atZone(zoneId)))
            .findFirst();
    }

    public static boolean isAccessOnline(TicketCategory category, EventCheckInInfo event) {
        return event.getFormat() == Event.EventFormat.ONLINE
            || event.getFormat() == Event.EventFormat.HYBRID
            && category != null
            && category.getTicketAccessType() == TicketCategory.TicketAccessType.ONLINE;
    }

    /**
     * Returns the message in the desired languages, if present.
     * If none of the languages are found, it returns the first available, if any.
     * @param lang the desired language
     * @param fallback fallback entity for detecting language
     * @return link description in the desired language, or the first one if not found. Or {@code null} if the map is empty
     */
    public static String getLocalizedMessage(Map<String, String> messagesByLang, String lang, LocalizedContent fallback) {
        if(messagesByLang.isEmpty()) {
            return null;
        }

        if(messagesByLang.containsKey(lang)) {
            return messagesByLang.get(lang);
        }

        var defaultLanguage = fallback.getFirstContentLanguage().getLanguage();

        if(messagesByLang.containsKey(defaultLanguage)) {
            return messagesByLang.get(defaultLanguage);
        }

        return messagesByLang.values().stream().findFirst().orElseThrow();
    }

    public static boolean supportsCaseInsensitiveQRCode(String version) {
        return version != null
            && MigrationVersion.fromVersion(version).compareTo(MigrationVersion.fromVersion(VERSION_FOR_CODE_CASE_INSENSITIVE)) >= 0;
    }

    public static boolean supportsLinkedAdditionalServices(String version) {
        return version != null
            && MigrationVersion.fromVersion(version).compareTo(MigrationVersion.fromVersion(VERSION_FOR_LINKED_ADDITIONAL_SERVICE)) >= 0;
    }
}
