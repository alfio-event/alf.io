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
package alfio.repository.system;

import alfio.datamapper.Bind;
import alfio.datamapper.Query;
import alfio.datamapper.QueryRepository;
import alfio.model.system.Configuration;

import java.util.List;

@QueryRepository
public interface ConfigurationRepository {

    @Query("SELECT * FROM configuration")
    List<Configuration> findAll();

    @Query("SELECT * FROM configuration where c_key = :key")
    Configuration findByKey(@Bind("key") String key);
    
    @Query("DELETE FROM configuration where c_key = :key")
    void deleteByKey(@Bind("key") String key);

    @Query("INSERT into configuration(c_key, c_value, description) values(:key, :value, :description)")
    int insert(@Bind("key") String key, @Bind("value") String value, @Bind("description") String description);

    @Query("UPDATE configuration set c_value = :value where c_key = :key")
    int update(@Bind("key") String existingKey, @Bind("value") String newValue);
}
