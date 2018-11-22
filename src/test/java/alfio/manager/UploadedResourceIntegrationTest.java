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

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.UploadBase64FileModification;
import alfio.repository.EventRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static alfio.test.util.IntegrationTestUtil.AVAILABLE_SEATS;
import static alfio.test.util.IntegrationTestUtil.initEvent;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
public class UploadedResourceIntegrationTest extends BaseIntegrationTest {

    private static final byte[] FILE = {1,2,3,4};
    private static final byte[] ONE_PIXEL_BLACK_GIF = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAUEBAAAACwAAAAAAQABAAACAkQBADs=");

    private static final Map<String, String> DESCRIPTION = Collections.singletonMap("en", "desc");


    @Autowired
    UploadedResourceManager uploadedResourceManager;

    @Autowired
    ConfigurationRepository configurationRepository;

    @Autowired
    UserManager userManager;

    @Autowired
    EventManager eventManager;

    @Autowired
    OrganizationRepository organizationRepository;
    @Autowired
    private EventRepository eventRepository;

    Event event;
    String user;

    @Before
    public void ensureConfiguration() {

        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now().minusDays(1), LocalTime.now()),
                new DateTimeModification(LocalDate.now().plusDays(1), LocalTime.now()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null));
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);

        event = eventAndUser.getKey();
        user = eventAndUser.getValue() + "_owner";
    }

    @Test
    public void testGlobal() {
        Assert.assertFalse(uploadedResourceManager.hasResource("file_name.txt"));
        Assert.assertTrue(uploadedResourceManager.findAll().isEmpty());

        UploadBase64FileModification toSave = new UploadBase64FileModification();
        toSave.setFile(FILE);
        toSave.setName("file_name.txt");
        toSave.setType("text/plain");
        Assert.assertEquals(1, uploadedResourceManager.saveResource(toSave));

        Assert.assertTrue(uploadedResourceManager.hasResource("file_name.txt"));
        Assert.assertEquals(1, uploadedResourceManager.findAll().size());
        Assert.assertEquals(toSave.getName(), uploadedResourceManager.get("file_name.txt").getName());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        uploadedResourceManager.outputResource("file_name.txt", baos);
        Assert.assertArrayEquals(FILE, baos.toByteArray());

        toSave.setFile(ONE_PIXEL_BLACK_GIF);
        Assert.assertEquals(1, uploadedResourceManager.saveResource(toSave));

        ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
        uploadedResourceManager.outputResource("file_name.txt", baos1);
        Assert.assertArrayEquals(ONE_PIXEL_BLACK_GIF, baos1.toByteArray());
    }

    @Test
    public void testOrganization() {
        int orgId = event.getOrganizationId();
        Assert.assertFalse(uploadedResourceManager.hasResource(orgId, "file_name.txt"));
        Assert.assertTrue(uploadedResourceManager.findAll(orgId).isEmpty());

        UploadBase64FileModification toSave = new UploadBase64FileModification();
        toSave.setFile(FILE);
        toSave.setName("file_name.txt");
        toSave.setType("text/plain");
        Assert.assertEquals(1, uploadedResourceManager.saveResource(orgId, toSave));

        Assert.assertTrue(uploadedResourceManager.hasResource(orgId, "file_name.txt"));
        Assert.assertEquals(1, uploadedResourceManager.findAll(orgId).size());
        Assert.assertEquals(toSave.getName(), uploadedResourceManager.get(orgId, "file_name.txt").getName());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        uploadedResourceManager.outputResource(orgId, "file_name.txt", baos);
        Assert.assertArrayEquals(FILE, baos.toByteArray());

        toSave.setFile(ONE_PIXEL_BLACK_GIF);
        Assert.assertEquals(1, uploadedResourceManager.saveResource(orgId, toSave));

        ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
        uploadedResourceManager.outputResource(orgId, "file_name.txt", baos1);
        Assert.assertArrayEquals(ONE_PIXEL_BLACK_GIF, baos1.toByteArray());
    }

    @Test
    public void testEvent() {
        int orgId = event.getOrganizationId();
        int eventId = event.getId();
        Assert.assertFalse(uploadedResourceManager.hasResource(orgId, eventId, "file_name.txt"));
        Assert.assertTrue(uploadedResourceManager.findAll(orgId, eventId).isEmpty());

        UploadBase64FileModification toSave = new UploadBase64FileModification();
        toSave.setFile(FILE);
        toSave.setName("file_name.txt");
        toSave.setType("text/plain");
        Assert.assertEquals(1, uploadedResourceManager.saveResource(orgId, eventId, toSave));

        Assert.assertTrue(uploadedResourceManager.hasResource(orgId, eventId, "file_name.txt"));
        Assert.assertEquals(1, uploadedResourceManager.findAll(orgId, eventId).size());
        Assert.assertEquals(toSave.getName(), uploadedResourceManager.get(orgId, eventId, "file_name.txt").getName());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        uploadedResourceManager.outputResource(orgId, eventId, "file_name.txt", baos);
        Assert.assertArrayEquals(FILE, baos.toByteArray());

        toSave.setFile(ONE_PIXEL_BLACK_GIF);
        Assert.assertEquals(1, uploadedResourceManager.saveResource(orgId, eventId, toSave));

        ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
        uploadedResourceManager.outputResource(orgId, eventId, "file_name.txt", baos1);
        Assert.assertArrayEquals(ONE_PIXEL_BLACK_GIF, baos1.toByteArray());
    }


}
