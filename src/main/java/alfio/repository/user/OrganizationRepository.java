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
package alfio.repository.user;

import alfio.model.user.Organization;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.util.List;
import java.util.Optional;

@QueryRepository
public interface OrganizationRepository {

    @Query("SELECT * FROM organization where id = :id")
    Organization getById(@Bind("id") int id);

    @Query("select name, email from organization where id = :id")
    Organization.OrganizationContact getContactById(@Bind("id") int id);

    @Query("SELECT * FROM organization where name = :name")
    Optional<Organization> findByName(@Bind("name") String name);

    @Query("SELECT id FROM organization where name = :name")
    int getIdByName(@Bind("name") String name);

    @Query("INSERT INTO organization(name, description, email) VALUES (:name, :description, :email)")
    int create(@Bind("name") String name, @Bind("description") String description, @Bind("email") String email);

    @Query("update organization set name = :name, description = :description, email = :email where id = :id")
    int update(@Bind("id") int id, @Bind("name") String name, @Bind("description") String description, @Bind("email") String email);

    @Query("(select organization.* from organization inner join j_user_organization on org_id = organization.id where j_user_organization.user_id = (select ba_user.id from ba_user where ba_user.username = :username)) " +
        " union " +
        "(select * from organization where 'ROLE_ADMIN' in (select role from ba_user inner join authority on ba_user.username = authority.username where ba_user.username = :username))")
    List<Organization> findAllForUser(@Bind("username") String username);


    @Query("(select organization.id from organization inner join j_user_organization on org_id = organization.id where j_user_organization.user_id = (select ba_user.id from ba_user where ba_user.username = :username)) " +
        " union " +
        "(select organization.id from organization where 'ROLE_ADMIN' in (select role from ba_user inner join authority on ba_user.username = authority.username where ba_user.username = :username))")
    List<Integer> findAllOrganizationIdForUser(@Bind("username") String username);

    @Query("(select organization.* from organization inner join j_user_organization on org_id = organization.id where j_user_organization.user_id = (select ba_user.id from ba_user where ba_user.username = :username) and organization.id = :orgId) " +
        " union " +
        "(select * from organization where 'ROLE_ADMIN' in (select role from ba_user inner join authority on ba_user.username = authority.username where ba_user.username = :username) and id = :orgId)")
    Optional<Organization> findOrganizationForUser(@Bind("username") String username, @Bind("orgId") int orgId);
}
