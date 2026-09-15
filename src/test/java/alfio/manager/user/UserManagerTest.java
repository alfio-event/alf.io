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
package alfio.manager.user;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.model.modification.OrganizationModification;
import alfio.model.result.ValidationResult;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.UUID;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class UserManagerTest {

    @Autowired
    UserManager userManager;

    @Autowired
    OrganizationRepository organizationRepository;


    @Test
    void checkUpdatePasswordNotNull() {

        String organizationName = UUID.randomUUID().toString();
        String username = UUID.randomUUID() + "_owner";

        var newPassword = "AaaaaBBBbbb123!!!!###---";

        var organizationModification = new OrganizationModification(null, organizationName, "email@example.com", "org", null, null);
        userManager.createOrganization(organizationModification, null);
        Organization organization = organizationRepository.findByName(organizationName).orElseThrow();
        userManager.insertUser(organization.getId(), username, "test", "test", "test@example.com", Role.OWNER, User.Type.INTERNAL, null);

        var res = userManager.validateNewPassword(username, null, newPassword, newPassword);
        Assertions.assertFalse(res.isSuccess());
        Assertions.assertEquals(List.of("alfio.old-password-invalid"), res.getErrorDescriptors().stream().map(ValidationResult.ErrorDescriptor::getFieldName).toList());
    }
}
