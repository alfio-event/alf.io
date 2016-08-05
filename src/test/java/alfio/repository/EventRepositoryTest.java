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

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.config.WebSecurityConfig;
import alfio.model.Event;
import alfio.model.PriceContainer;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.IntegrationTestUtil;
import ch.digitalfondue.npjt.AffectedRowCountAndKey;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, WebSecurityConfig.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS})
@WebIntegrationTest("server.port:9000")
@Transactional
public class EventRepositoryTest {

    private static final String NEW_YORK_TZ = "America/New_York";
    private static final String ORG_NAME = "name";

    @BeforeClass
    public static void initEnv() {
        IntegrationTestUtil.initSystemProperties();
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


    @Test
    public void testJavaInsertedDatesRespectTheirTimeZone() throws Exception {
        //these are the values of what we have inserted in the SQL insert script
        ZonedDateTime beginEventDate = ZonedDateTime.of(2015, 4, 18, 0, 0, 0, 0, ZoneId.of("America/New_York"));
        ZonedDateTime endEventDate = ZonedDateTime.of(2015, 4, 19, 23, 59, 59, 0, ZoneId.of("America/New_York"));

        int orgId = organizationRepository.findByName(ORG_NAME).stream().findFirst().orElseThrow(IllegalStateException::new).getId();

        AffectedRowCountAndKey<Integer> pair = eventRepository.insert("unittest", Event.EventType.INTERNAL, "display Name", "http://localhost:8080/", "http://localhost:8080",
            "http://localhost:8080", null, null, "Lugano", "9", "8", beginEventDate, endEventDate, NEW_YORK_TZ, "CHF", 4, true,
            new BigDecimal(1), "", "", orgId, 7, PriceContainer.VatStatus.INCLUDED, 0);
        Event e = eventRepository.findById(pair.getKey());
        assertNotNull("Event not found in DB", e);

        assertEquals("Begin date is not correct", beginEventDate, e.getBegin());
        assertEquals("End date is not correct", endEventDate, e.getEnd());

        //since when debugging the toString method is used .... and it rely on the system TimeZone, we test it too
        System.out.println(e.getBegin().toString());
        System.out.println(e.getEnd().toString());
    }
}