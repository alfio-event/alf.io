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

import alfio.model.user.Authority;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;
import ch.digitalfondue.npjt.QueryType;

import java.util.List;
import java.util.Set;

@QueryRepository
public interface AuthorityRepository {


    @Query("select exists(select * from authority where username = :username and role in (:roles)) as res")
    boolean checkRole(@Bind("username") String username, @Bind("roles") Set<String> roles);

    @Query("SELECT * from authority where username = :username")
    List<Authority> findGrantedAuthorities(@Bind("username") String username);

    @Query("SELECT role from authority where username = :username order by role")
    List<String> findRoles(@Bind("username") String username);

    @Query("INSERT INTO authority(username, role) VALUES (:username, :role)")
    int create(@Bind("username") String username, @Bind("role") String role);

    @Query(type = QueryType.TEMPLATE, value = "INSERT INTO authority(username, role) VALUES (:username, :role)")
    String grantAll();

    @Query("DELETE from authority where username = :username")
    int revokeAll(@Bind("username") String username);
}
