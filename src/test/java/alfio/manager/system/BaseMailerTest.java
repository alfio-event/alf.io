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
package alfio.manager.system;

import alfio.model.Configurable;
import alfio.model.user.Organization;
import alfio.repository.user.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static alfio.manager.testSupport.MaybeConfigurationBuilder.existing;
import static alfio.manager.testSupport.MaybeConfigurationBuilder.missing;
import static alfio.model.system.ConfigurationKeys.MAIL_REPLY_TO;
import static alfio.model.system.ConfigurationKeys.MAIL_SET_ORG_REPLY_TO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class BaseMailerTest {

    private static final String ORG_EMAIL = "org@example.org";
    private OrganizationRepository organizationRepository;
    private Organization organization;
    private BaseMailer baseMailer;

    @BeforeEach
    void setUp() {
        organizationRepository = mock(OrganizationRepository.class);
        organization = mock(Organization.class);
        when(organization.getEmail()).thenReturn(ORG_EMAIL);
        baseMailer = new BaseMailerMock(organizationRepository);
    }

    @Test
    void setReplyToOrgEmailWhenEnabled() {
        var config = Map.of(
            MAIL_REPLY_TO, missing(MAIL_REPLY_TO),
            MAIL_SET_ORG_REPLY_TO, existing(MAIL_SET_ORG_REPLY_TO, "true")
        );
        int orgId = 1;
        when(organizationRepository.getById(orgId)).thenReturn(organization);
        var consumer = new AtomicReference<String>();
        baseMailer.setReplyToIfPresent(config, orgId, consumer::set);
        assertEquals(ORG_EMAIL, consumer.get());
        consumer.set(null);
        // try again to test a cache hit
        baseMailer.setReplyToIfPresent(config, orgId, consumer::set);
        assertEquals(ORG_EMAIL, consumer.get());

        verify(organizationRepository, times(1)).getById(orgId);
    }

    @Test
    void doNotSetOrgEmailWhenReplyToIsAlreadySet() {
        String customEmail = "org2@example.org";
        var config = Map.of(
            MAIL_REPLY_TO, existing(MAIL_REPLY_TO, customEmail),
            MAIL_SET_ORG_REPLY_TO, existing(MAIL_SET_ORG_REPLY_TO, "true")
        );
        int orgId = 2;
        when(organizationRepository.getById(orgId)).thenReturn(organization);
        var consumer = new AtomicReference<String>();
        baseMailer.setReplyToIfPresent(config, orgId, consumer::set);
        assertEquals(customEmail, consumer.get());
        verify(organizationRepository, never()).getById(anyInt());
    }

    @Test
    void doNotSetOrgEmailWhenNotEnabled() {
        int orgId = 3;
        var config = Map.of(
            MAIL_REPLY_TO, missing(MAIL_REPLY_TO),
            MAIL_SET_ORG_REPLY_TO, missing(MAIL_SET_ORG_REPLY_TO)
        );
        when(organizationRepository.getById(orgId)).thenReturn(organization);
        var consumer = new AtomicReference<String>();
        baseMailer.setReplyToIfPresent(config, orgId, consumer::set);
        assertNull(consumer.get());
        verify(organizationRepository, never()).getById(anyInt());
    }

    @Test
    void configurationIsRequired() {
        // NPE when conf map is null
        var exception = assertThrows(NullPointerException.class, () -> baseMailer.setReplyToIfPresent(null, -1, b -> {}));
        assertEquals(BaseMailer.MISSING_CONFIG_MESSAGE, exception.getMessage());

        var missingOrgReplyTo = Map.of(
            MAIL_REPLY_TO, missing(MAIL_REPLY_TO)
        );
        // NPE when MAIL_SET_ORG_REPLY_TO is missing
        exception = assertThrows(NullPointerException.class, () -> baseMailer.setReplyToIfPresent(missingOrgReplyTo, -1, b -> {}));
        assertTrue(exception.getMessage().startsWith(MAIL_SET_ORG_REPLY_TO.name()));

        var missingReplyTo = Map.of(
            MAIL_SET_ORG_REPLY_TO, missing(MAIL_SET_ORG_REPLY_TO)
        );
        // NPE when MAIL_REPLY_TO is missing
        exception = assertThrows(NullPointerException.class, () -> baseMailer.setReplyToIfPresent(missingReplyTo, -1, b -> {}));
        assertTrue(exception.getMessage().startsWith(MAIL_REPLY_TO.name()));

    }

    private static class BaseMailerMock extends BaseMailer {
        BaseMailerMock(OrganizationRepository organizationRepository) {
            super(organizationRepository);
        }

        @Override
        public void send(Configurable configurable, String fromName, String to, List<String> cc, String subject, String text, Optional<String> html, Attachment... attachment) {
            throw new IllegalStateException("no can do");
        }
    }
}