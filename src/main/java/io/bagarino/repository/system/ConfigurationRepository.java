/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.repository.system;

import io.bagarino.datamapper.Bind;
import io.bagarino.datamapper.Query;
import io.bagarino.datamapper.QueryRepository;
import io.bagarino.model.system.Configuration;

import java.util.List;

@QueryRepository
public interface ConfigurationRepository {

    @Query("SELECT * FROM configuration")
    List<Configuration> findAll();

    @Query("SELECT * FROM configuration where c_key = :key")
    Configuration findByKey(@Bind("key") String key);

    @Query("INSERT into configuration(c_key, c_value, description) values(:key, :value, :description)")
    int insert(@Bind("key") String key, @Bind("value") String value, @Bind("description") String description);

    @Query("UPDATE configuration set c_value = :value where c_key = :key")
    int update(@Bind("key") String existingKey, @Bind("value") String newValue);
}
