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
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.system.Configuration;
import alfio.repository.AdditionalServiceItemRepository;
import alfio.repository.TicketFieldRepository;
import alfio.repository.TicketRepository;
import biweekly.ICalVersion;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.io.text.ICalWriter;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.RegExUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.model.TicketFieldConfiguration.Context.ATTENDEE;
import static alfio.model.system.ConfigurationKeys.*;
import static java.time.temporal.ChronoField.*;

@UtilityClass
@Log4j2
public class EventUtil {

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
        return !configurationManager.getBooleanConfigValue(Configuration.from(event, STOP_WAITING_QUEUE_SUBSCRIPTIONS), false)
            && checkWaitingQueuePreconditions(event, categories, configurationManager, noTicketsAvailable);
    }

    public static boolean checkWaitingQueuePreconditions(Event event, List<SaleableTicketCategory> categories, ConfigurationManager configurationManager, Predicate<EventAndOrganizationId> noTicketsAvailable) {
        return findLastCategory(categories).map(lastCategory -> {
            ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
            if(isPreSales(event, categories)) {
                return configurationManager.getBooleanConfigValue(Configuration.from(event, ENABLE_PRE_REGISTRATION), false);
            } else if(configurationManager.getBooleanConfigValue(Configuration.from(event, ENABLE_WAITING_QUEUE), false)) {
                return now.isBefore(lastCategory.getZonedExpiration()) && noTicketsAvailable.test(event);
            }
            return false;
        }).orElse(false);
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
        ZonedDateTime now = ZonedDateTime.now(event.getZoneId());
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

    public static int evaluatePrice(int price, boolean freeOfCharge) {
        return freeOfCharge ? 0 : price;
    }

    public static int determineAvailableSeats(TicketCategoryStatisticView tc, EventStatisticView e) {
        return tc.isBounded() ? tc.getNotSoldTicketsCount() : e.getDynamicAllocation();
    }

    public static Optional<byte[]> getIcalForEvent(Event event, TicketCategory ticketCategory, String description) {
        ICalendar ical = new ICalendar();
        VEvent vEvent = new VEvent();
        vEvent.setSummary(event.getDisplayName());
        vEvent.setDescription(description);
        vEvent.setLocation(RegExUtils.replacePattern(event.getLocation(), "[\n\r\t]+", " "));
        ZonedDateTime begin = Optional.ofNullable(ticketCategory).map(tc -> tc.getTicketValidityStart(event.getZoneId())).orElse(event.getBegin());
        ZonedDateTime end = Optional.ofNullable(ticketCategory).map(tc -> tc.getTicketValidityEnd(event.getZoneId())).orElse(event.getEnd());
        vEvent.setDateStart(Date.from(begin.toInstant()));
        vEvent.setDateEnd(Date.from(end.toInstant()));
        vEvent.setUrl(event.getWebsiteUrl());
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
            .queryParam("detail", description)
            .toUriString();
    }

    public static Function<Ticket, List<TicketFieldConfigurationDescriptionAndValue>> retrieveFieldValues(TicketRepository ticketRepository,
                                                                                                          TicketFieldRepository ticketFieldRepository,
                                                                                                          AdditionalServiceItemRepository additionalServiceItemRepository) {
        return ticket -> {
            List<Ticket> ticketsInReservation = ticketRepository.findTicketsInReservation(ticket.getTicketsReservationId());
            //WORKAROUND: we only add the additionalServiceItems related fields only if it's the _first_ ticket of the reservation
            boolean isFirstTicket = ticketsInReservation.get(0).getId() == ticket.getId();

            Map<Integer, TicketFieldDescription> descriptions = ticketFieldRepository.findTranslationsFor(Locale.forLanguageTag(ticket.getUserLanguage()), ticket.getEventId());
            Map<String, TicketFieldValue> values = ticketFieldRepository.findAllByTicketIdGroupedByName(ticket.getId());
            Function<TicketFieldConfiguration, String> extractor = (f) -> Optional.ofNullable(values.get(f.getName())).map(TicketFieldValue::getValue).orElse("");
            List<AdditionalServiceItem> additionalServiceItems = isFirstTicket ? additionalServiceItemRepository.findByReservationUuid(ticket.getTicketsReservationId()) : Collections.emptyList();
            Set<Integer> additionalServiceIds = additionalServiceItems.stream().map(AdditionalServiceItem::getAdditionalServiceId).collect(Collectors.toSet());
            return ticketFieldRepository.findAdditionalFieldsForEvent(ticket.getEventId())
                .stream()
                .filter(f -> f.getContext() == ATTENDEE || Optional.ofNullable(f.getAdditionalServiceId()).filter(additionalServiceIds::contains).isPresent())
                .filter(f -> CollectionUtils.isEmpty(f.getCategoryIds()) || f.getCategoryIds().contains(ticket.getCategoryId()))
                .map(f-> {
                    int count = Math.max(1, Optional.ofNullable(f.getAdditionalServiceId()).map(id -> (int) additionalServiceItems.stream().filter(i -> i.getAdditionalServiceId() == id).count()).orElse(1));
                    return new TicketFieldConfigurationDescriptionAndValue(f, descriptions.getOrDefault(f.getId(), TicketFieldDescription.MISSING_FIELD), count, extractor.apply(f));
                })
                .collect(Collectors.toList());
        };
    }



}
