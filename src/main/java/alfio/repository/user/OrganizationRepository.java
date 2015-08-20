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

@QueryRepository
public interface OrganizationRepository {

    @Query("SELECT * FROM organization")
    List<Organization> findAll();

    @Query("SELECT * FROM organization where id = :id")
    Organization getById(@Bind("id") int id);

    @Query("SELECT * FROM organization where name = :name")
    List<Organization> findByName(@Bind("name") String name);

    @Query("INSERT INTO organization(name, description, email) VALUES (:name, :description, :email)")
    int create(@Bind("name") String name, @Bind("description") String description, @Bind("email") String email);

    @Query("update organization set name = :name, description = :description, email = :email where id = :id")
    int update(@Bind("id") int id, @Bind("name") String name, @Bind("description") String description, @Bind("email") String email);
}
