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

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class MailerUtilTest {

    private static final String ORG_EMAIL = "org@example.org";
    private OrganizationRepository organizationRepository;
    private Organization organization;
    private Configurable configurable;

    @BeforeEach
    void setUp() {
        configurable = mock(Configurable.class);
        organizationRepository = mock(OrganizationRepository.class);
        organization = mock(Organization.class);
        when(organization.getEmail()).thenReturn(ORG_EMAIL);
    }

    @Test
    void setReplyToOrgEmailWhenEnabled() {
        int orgId = 1;
        when(organizationRepository.getById(orgId)).thenReturn(organization);
        when(configurable.getOrganizationId()).thenReturn(orgId);
        var consumer = new AtomicReference<String>();
        MailerUtil.setReplyToIfPresent(null, configurable, organizationRepository, true, consumer::set);
        assertEquals(ORG_EMAIL, consumer.get());
        consumer.set(null);
        // try again to test a cache hit
        MailerUtil.setReplyToIfPresent(null, configurable, organizationRepository, true, consumer::set);
        assertEquals(ORG_EMAIL, consumer.get());

        verify(organizationRepository, times(1)).getById(orgId);
    }

    @Test
    void doNotSetOrgEmailWhenReplyToIsAlreadySet() {
        int orgId = 2;
        String customEmail = "org2@example.org";
        when(organizationRepository.getById(orgId)).thenReturn(organization);
        when(configurable.getOrganizationId()).thenReturn(orgId);
        var consumer = new AtomicReference<String>();
        MailerUtil.setReplyToIfPresent(customEmail, configurable, organizationRepository, true, consumer::set);
        assertEquals(customEmail, consumer.get());
        verify(organizationRepository, never()).getById(anyInt());
    }

    @Test
    void doNotSetOrgEmailWhenNotEnabled() {
        int orgId = 3;
        when(organizationRepository.getById(orgId)).thenReturn(organization);
        when(configurable.getOrganizationId()).thenReturn(orgId);
        var consumer = new AtomicReference<String>();
        MailerUtil.setReplyToIfPresent(null, configurable, organizationRepository, false, consumer::set);
        assertNull(consumer.get());
        verify(organizationRepository, never()).getById(anyInt());
    }
}