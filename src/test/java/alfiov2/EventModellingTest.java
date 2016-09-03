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
package alfiov2;

import alfio.model.ContentLanguage;
import alfio.model.transaction.PaymentProxy;
import alfiov2.builder.EventBuilder;
import alfiov2.builder.PaymentOptionsBuilder;
import alfiov2.command.admin.EventDescriptor;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.stream.Stream;

import static alfiov2.builder.I18nTextBuilder.text;
import static java.util.Collections.singleton;

public class EventModellingTest {

    private static final ZoneId ETC = ZoneId.of("ECT");

    @Test
    public void testSingleDayConference() throws Exception {
        EventBuilder.StrictAllocation vdt17 = EventBuilder.StrictAllocation.newEvent("Voxxed Days Ticino", 440, ETC);
        vdt17.addTimeSlot(LocalDateTime.of(2017, 5, 6, 9, 0, 0, 0), LocalDateTime.of(2017, 5, 6, 18, 0, 0, 0));
        //Category with limited seats
        vdt17.addCategory(singleton(text(ContentLanguage.ENGLISH, "ILOVEVDT", "Super price!")), 20, PaymentOptionsBuilder.of(new BigDecimal("95.0"), "CHF").vatIncluded(new BigDecimal("8.0")).enableMethods(PaymentProxy.availableProxies()));
        vdt17.publishFrom(LocalDateTime.of(2016, 9, 1, 10, 0));
        EventDescriptor eventDescriptor = vdt17.createEventDescriptor();

    }

    @Test
    public void testSingleDayConferenceWithWorkshop() throws Exception {

        EventBuilder.StrictAllocation vdt17 = EventBuilder.StrictAllocation.newEvent("Voxxed Days Ticino", 440, ETC);
        vdt17.addTimeSlot(LocalDateTime.of(2017, 5, 6, 9, 0, 0, 0), LocalDateTime.of(2017, 5, 6, 18, 0, 0, 0));
        //tickets are always sold at the combi level, then if they are for the non combi category, they are moved to the category
        vdt17.addCategory(singleton(text(ContentLanguage.ENGLISH, "WORKSHOP_JAVA", "Workshop description")), 20, PaymentOptionsBuilder.of(new BigDecimal("120.0"), "CHF").vatIncluded(new BigDecimal("8.0")).enableMethods(PaymentProxy.availableProxies()))
            .enableCombiWithDefault(PaymentOptionsBuilder.of(new BigDecimal("255"), "CHF").vatIncluded(new BigDecimal("8.0")).enableMethods(PaymentProxy.availableProxies()));

        vdt17.publishFrom(LocalDateTime.of(2016, 9, 1, 0, 0, 0, 0));
        vdt17.createEventDescriptor();
    }

    @Test
    public void testMultipleDaysConference() throws Exception {
        EventBuilder.StrictAllocation  multiDayEvent = EventBuilder.StrictAllocation.newEvent("Multiple Days", 4000, ETC);
        Stream.of(Pair.of(LocalDateTime.of(2017, 11, 7, 10, 0, 0, 0), LocalDateTime.of(2017, 11, 7, 18, 0, 0, 0)), Pair.of(LocalDateTime.of(2017, 11, 8, 9, 0, 0, 0), LocalDateTime.of(2017, 11, 8, 18, 0, 0, 0)))
            .forEach(p -> multiDayEvent.addTimeSlot(p.getLeft(), p.getRight()));

        //start selling immediately with any payment method at 595 EUR
        //start selling tickets with a price created the DEFAULT category
        multiDayEvent.startSellingTickets(PaymentOptionsBuilder.of(new BigDecimal("255"), "CHF").vatIncluded(new BigDecimal("8.0")).enableMethods(PaymentProxy.availableProxies())); //start selling tickets immediately
    }

    @Test
    public void testMultipleDaysConferenceWithMultipleWorkshops() throws Exception {
        EventBuilder.StrictAllocation combiEvent = EventBuilder.StrictAllocation.newEvent("Combi event", 4000, ETC);

        Stream.of(Pair.of(LocalDateTime.of(2017, 11, 7, 10, 0, 0, 0), LocalDateTime.of(2017, 11, 7, 18, 0, 0, 0)), Pair.of(LocalDateTime.of(2017, 11, 8, 9, 0, 0, 0), LocalDateTime.of(2017, 11, 8, 18, 0, 0, 0)))
            .forEach(p -> combiEvent.addTimeSlot(p.getLeft(), p.getRight()));

        //tickets are always sold at the combi level, then if they are for the non combi category, they are moved to the category
        combiEvent.addCategory(singleton(text(ContentLanguage.ENGLISH, "WORKSHOP_JAVA", "Workshop")), 20, PaymentOptionsBuilder.of(new BigDecimal("120.0"), "CHF").vatIncluded(new BigDecimal("8.0")).enableMethods(PaymentProxy.availableProxies()))
            .enableCombiWithDefault(PaymentOptionsBuilder.of(new BigDecimal("255"), "EUR").vatIncluded(new BigDecimal("8.0")).enableMethods(PaymentProxy.availableProxies()));

        combiEvent.addCategory(singleton(text(ContentLanguage.ENGLISH, "WORKSHOP_ANGULAR", "Workshop Angular")), 35, PaymentOptionsBuilder.of(new BigDecimal("120.0"), "CHF").vatIncluded(new BigDecimal("8.0")).enableMethods(PaymentProxy.availableProxies()))
            .enableCombiWithDefault(PaymentOptionsBuilder.of(new BigDecimal("255"), "EUR").vatIncluded(new BigDecimal("8.0")).enableMethods(PaymentProxy.availableProxies()));


        //start selling immediately with any payment method at 500 EUR
        //start selling ticklets with a price created the DEFAULT category
        combiEvent.startSellingTickets(PaymentOptionsBuilder.of(new BigDecimal("255"), "EUR").vatIncluded(new BigDecimal("8.0")).enableMethods(PaymentProxy.availableProxies())); //start selling tickets immediately
    }



    @Test
    public void testFreeEvent() throws Exception {
        EventBuilder.StrictAllocation jugJune17 = EventBuilder.StrictAllocation.newEvent("JUG Lugano", 3, ETC);
        jugJune17.addTimeSlot(LocalDateTime.of(2017, 5, 6, 18, 30, 0, 0), LocalDateTime.of(2017, 5, 6, 20, 0, 0, 0));
        jugJune17.startSellingTickets(PaymentOptionsBuilder.none());
    }

    @Test
    public void testFreeEventWithGadgets() throws Exception {
        EventBuilder.StrictAllocation giada = EventBuilder.StrictAllocation.newEvent("Giada Art", 3, ETC);
        giada.addTimeSlot(LocalDateTime.of(2017, 5, 6, 9, 0, 0, 0), LocalDateTime.of(2017, 5, 6, 18, 0, 0, 0));
        giada.addAdditionalItem(singleton(text(ContentLanguage.ENGLISH, "T-Shirt", "Buy the event T-Shirt!")), PaymentOptionsBuilder.of(new BigDecimal("20"), "CHF").vatIncluded(new BigDecimal("8.0")).enableMethods(PaymentProxy.availableProxies()));
        giada.startSellingTickets(PaymentOptionsBuilder.none());
    }

    @Test
    public void testMuseumExposition() throws Exception {
        EventBuilder.RelaxedAllocation jugJune17 = EventBuilder.RelaxedAllocation.createEvent("LAC - Herman Hesse", ZonedDateTime.of(2017, 4, 1, 9, 0, 0, 0, ETC));
    }

}