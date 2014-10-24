/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.repository;

import io.bagarino.config.DataSourceConfiguration;
import io.bagarino.model.Event;
import org.apache.commons.lang3.tuple.Pair;
import org.hsqldb.util.DatabaseManagerSwing;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.math.BigDecimal;
import java.time.*;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class })
@ActiveProfiles("dev")
public class EventRepositoryTest {


    @Autowired
    private EventRepository eventRepository;

    @Before
    public void setUp() throws Exception {
        //setup hsqldb and make it usable from eventRepository
    }

    @After
    public void tearDown() throws Exception {

    }


    @Test
    public void testJavaInsertedDatesRespectTheirTimeZone() throws Exception {
        //these are the values of what we have inserted in the SQL insert script
        LocalDateTime beginEventDate = LocalDateTime.of(2015,4,18,0,0,0);
        LocalDateTime endEventDate = LocalDateTime.of(2015,4,19,23,59,59);
        TimeZone eventTimeZone = TimeZone.getTimeZone("America/New_York");

        Date dateBegin = Date.from(beginEventDate.toInstant(ZoneOffset.UTC));
        Date dateEnd = Date.from(endEventDate.toInstant(ZoneOffset.UTC));

        Pair<Integer, Integer> pair = eventRepository.insert("test from unit test", "unittest", "Lugano", "9", "8", dateBegin, dateEnd, eventTimeZone.getDisplayName(), 0, "CHF", 4, true, new BigDecimal(1), "", "", 0);
        Event e = eventRepository.findById(pair.getLeft());
        assertNotNull("Event not found in DB", e);

        assertEquals("Begin date is not correct", e.getBegin(),ZonedDateTime.of(beginEventDate, eventTimeZone.toZoneId()));
        assertEquals("End date is not correct", e.getEnd(),ZonedDateTime.of(endEventDate, eventTimeZone.toZoneId()));

        //since when debugging the toString method is used .... and it rely on the laptop TimZone, we test it too
        System.out.println(e.getBegin().toString());
        System.out.println(e.getEnd().toString());
    }

    @Test
         public void testSQLInsertedDatesRespectTheirTimeZone() throws Exception {
        //these are the values of what we have inserted in the SQL insert script
        LocalDateTime beginEventDate = LocalDateTime.of(2015,4,18,0,0,0);
        LocalDateTime endEventDate = LocalDateTime.of(2015,4,19,23,59,59);
        TimeZone eventTimeZone = TimeZone.getTimeZone("America/New_York");

        Event e = eventRepository.findById(0);
        assertNotNull("Event not found in DB", e);

        assertEquals("Begin date is not correct", e.getBegin(), ZonedDateTime.of(beginEventDate, eventTimeZone.toZoneId()));
        assertEquals("End date is not correct", e.getEnd(),ZonedDateTime.of(endEventDate, eventTimeZone.toZoneId()));

        //since when debugging the toString method is used .... and it rely on the laptop TimZone, we test it too
        System.out.println(e.getBegin().toString());
        System.out.println(e.getEnd().toString());
    }
}