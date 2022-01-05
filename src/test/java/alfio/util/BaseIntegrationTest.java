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

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles(resolver = ActiveTravisProfileResolver.class)
public class BaseIntegrationTest {
    public static final byte[] ONE_PIXEL_BLACK_GIF = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAUEBAAAACwAAAAAAQABAAACAkQBADs=");

    public static void testTransferEventToAnotherOrg(int eventId,
                                                     int currentOrgId,
                                                     String username,
                                                     NamedParameterJdbcTemplate jdbcTemplate) {
        int newOrgId = createNewOrg(username, jdbcTemplate);
        // update event set org id
        assertEquals(1, jdbcTemplate.update("update event set org_id = :orgId where id = :id", Map.of("orgId", newOrgId, "id", eventId)));

        // check that all the resources have been migrated
        var params = Map.of("eventId", eventId, "orgId", currentOrgId);
        // the following view is created when the tests start
        var errors = jdbcTemplate.queryForList("select entity || ' (' || cnt || ')' from count_resources_assigned_to_event_org where ev = :eventId and org = :orgId and cnt > 0",
            params,
            String.class
        );
        assertTrue(errors.isEmpty(), () -> "[Event] Found stale resources in " + String.join(", ", errors));
    }

    public static void testTransferSubscriptionDescriptorToAnotherOrg(UUID subscriptionDescriptorId,
                                                                      int currentOrgId,
                                                                      String username,
                                                                      NamedParameterJdbcTemplate jdbcTemplate) {
        int newOrgId = createNewOrg(username, jdbcTemplate);
        // update event set org id
        assertEquals(1, jdbcTemplate.update("update subscription_descriptor set organization_id_fk = :orgId where id = :id::uuid", Map.of("orgId", newOrgId, "id", subscriptionDescriptorId)));

        // check that all the resources have been migrated
        var params = Map.of("descriptorId", subscriptionDescriptorId, "orgId", currentOrgId);
        // the following view is created when the tests start
        var errors = jdbcTemplate.queryForList("select entity || ' (' || cnt || ')' from count_resources_assigned_to_subscription_org where sid = :descriptorId::uuid and org = :orgId and cnt > 0",
            params,
            String.class
        );
        assertTrue(errors.isEmpty(), () -> "[SubscriptionDescriptor] Found stale resources in " + String.join(", ", errors));
    }

    public static int createNewOrg(String username,
                                   NamedParameterJdbcTemplate jdbcTemplate) {
        var keyHolder = new GeneratedKeyHolder();
        var parameterSource = new MapSqlParameterSource("name", UUID.randomUUID().toString());
        jdbcTemplate.update("INSERT INTO organization(name, description, email, name_openid, slug) VALUES (:name, '', '', null, null)", parameterSource, keyHolder);
        assertNotNull(keyHolder.getKeys());
        Integer newOrgId = (Integer) keyHolder.getKeys().get("id");
        assertNotNull(newOrgId);
        Integer userId = jdbcTemplate.queryForObject("select id from ba_user where username = :username and enabled = true", Map.of("username", username), Integer.class);
        assertNotNull(userId, "user not found");
        assertEquals(1, jdbcTemplate.update("insert into j_user_organization (user_id, org_id) values(:userId, :organizationId)", Map.of("userId", userId, "organizationId", newOrgId)));
        return newOrgId;
    }
}