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
package alfio.repository.user.join;

import alfio.model.user.join.UserOrganization;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;
import ch.digitalfondue.npjt.QueryType;

import java.util.Collection;
import java.util.List;

@QueryRepository
public interface UserOrganizationRepository {

    @Query("select exists(select 1 from j_user_organization where user_id = :userId and org_id = :organizationId)")
    boolean userIsInOrganization(@Bind("userId") int userId, @Bind("organizationId") int organizationId);

    @Query("select * from j_user_organization where user_id = :userId")
    List<UserOrganization> findByUserId(@Bind("userId") int userId);

    @Query("select * from j_user_organization where org_id = :organizationId")
    List<UserOrganization> findByOrganizationId(@Bind("organizationId") int organizationId);

    @Query("select * from j_user_organization where org_id in (:organizationIds) order by user_id")
    List<UserOrganization> findByOrganizationIdsOrderByUserId(@Bind("organizationIds") List<Integer> organizationIds);

    @Query("insert into j_user_organization (user_id, org_id) values(:userId, :organizationId)")
    int create(@Bind("userId") int userId, @Bind("organizationId") int organizationId);

    @Query(type = QueryType.TEMPLATE, value = "insert into j_user_organization (user_id, org_id) values(:userId, :organizationId)")
    String bulkCreate();

    @Query("update j_user_organization set org_id = :organizationId where user_id = :userId")
    int updateUserOrganization(@Bind("userId") int userId, @Bind("organizationId") int organizationId);

    @Query("select distinct(org_id) from j_user_organization where user_id in(:users)")
    List<Integer> findOrganizationsForUsers(@Bind("users") List<Integer> users);

    @Query("delete from j_user_organization where user_id = :userId and org_id in (:organizationIds)")
    int removeOrganizationUserLinks(@Bind("userId") int userId, @Bind("organizationIds") Collection<Integer> organizationIds);

    @Query("delete from j_user_organization where org_id = :organizationId")
    int cleanupOrganization(@Bind("organizationId") int organizationId);
}
