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
package alfio.repository;

import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.config.WebSecurityConfig;
import alfio.model.Event;
import alfio.repository.user.OrganizationRepository;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, WebSecurityConfig.class})
@ActiveProfiles(Initializer.PROFILE_DEV)
@WebIntegrationTest("server.port:9000")
public class EventRepositoryTest {

    private static final String NEW_YORK_TZ = "America/New_York";
    private static final String ORG_NAME = "name";

    @BeforeClass
    public static void initEnv() {
        System.setProperty("datasource.dialect", "HSQLDB");
        System.setProperty("datasource.driver", "org.hsqldb.jdbcDriver");
        System.setProperty("datasource.url", "jdbc:hsqldb:mem:alfio");
        System.setProperty("datasource.username", "sa");
        System.setProperty("datasource.password", "");
        System.setProperty("datasource.validationQuery", "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS");
        //System.setProperty("spring.profiles.active", Initializer.PROFILE_DEV);
    }

    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private OrganizationRepository organizationRepository;

    @Before
    public void setUp() throws Exception {
        //setup hsqldb and make it usable from eventRepository
        organizationRepository.create(ORG_NAME, "description", "email@pippobaudo.com");
    }

    @After
    public void tearDown() throws Exception {

    }


    @Test
    public void testJavaInsertedDatesRespectTheirTimeZone() throws Exception {
        //these are the values of what we have inserted in the SQL insert script
        ZonedDateTime beginEventDate = ZonedDateTime.of(2015, 4, 18, 0, 0, 0, 0, ZoneId.of("America/New_York"));
        ZonedDateTime endEventDate = ZonedDateTime.of(2015, 4, 19, 23, 59, 59, 0, ZoneId.of("America/New_York"));

        int orgId = organizationRepository.findByName(ORG_NAME).stream().findFirst().orElseThrow(IllegalStateException::new).getId();

        Pair<Integer, Integer> pair = eventRepository.insert("test from unit test", "unittest","http://localhost:8080/", "http://localhost:8080", "http://localhost:8080","Lugano", "9", "8", beginEventDate, endEventDate, NEW_YORK_TZ, 0, "CHF", 4, true, new BigDecimal(1), "", "", orgId);
        Event e = eventRepository.findById(pair.getValue());
        assertNotNull("Event not found in DB", e);

        assertEquals("Begin date is not correct", beginEventDate, e.getBegin());
        assertEquals("End date is not correct", endEventDate, e.getEnd());

        //since when debugging the toString method is used .... and it rely on the system TimeZone, we test it too
        System.out.println(e.getBegin().toString());
        System.out.println(e.getEnd().toString());
    }

    @Test
    public void testSQLInsertedDatesRespectTheirTimeZone() throws Exception {
        //these are the values of what we have inserted in the SQL insert script
        TimeZone eventTimeZone = TimeZone.getTimeZone("America/New_York");
        ZoneId eventZoneId = eventTimeZone.toZoneId();
        ZonedDateTime beginEventDate = ZonedDateTime.of(2015,10,10,0,0,0,0,eventZoneId);
        ZonedDateTime endEventDate = ZonedDateTime.of(2015, 10, 10, 23, 59, 0, 0, eventZoneId);

        Event e = eventRepository.findById(0);
        assertNotNull("Event not found in DB", e);

        assertEquals("Begin date is not correct", e.getBegin(), beginEventDate);
        assertEquals("End date is not correct", e.getEnd(), endEventDate);

        //since when debugging the toString method is used .... and it rely on the system TimeZone, we test it too
        System.out.println(e.getBegin().toString());
        System.out.println(e.getEnd().toString());
    }
}