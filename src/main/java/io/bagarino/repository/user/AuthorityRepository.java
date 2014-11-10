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
package io.bagarino.repository.user;

import io.bagarino.datamapper.Bind;
import io.bagarino.datamapper.Query;
import io.bagarino.datamapper.QueryRepository;
import io.bagarino.model.user.Authority;

import java.util.List;

@QueryRepository
public interface AuthorityRepository {

    String ROLE_ADMIN  = "ROLE_ADMIN";
    String ROLE_OWNER  = "ROLE_OWNER";

    @Query("SELECT * from authority where username = :username")
    List<Authority> findGrantedAuthorities(@Bind("username") String username);

    @Query("INSERT INTO authority(username, role) VALUES (:username, :role)")
    int create(@Bind("username") String username, @Bind("role") String role);
}
