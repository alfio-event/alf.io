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
package alfio.repository;

import ch.digitalfondue.npjt.QueryRepository;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;

/**
 * This repository is specific to backoffice/admin operations.
 * It **must** be used from backoffice/admin components.
 */
@QueryRepository
public interface EventAdminRepository {
    /**
     * Checks if an event with the given "slug" already exists.
     * This method will temporarily deactivate Row Level Security in order to perform the check.
     *
     * @param slug the generated (or specified) slug
     * @return true if the event exists
     */
    default boolean existsBySlug(String slug) {
        var jdbcTemplate = getJdbcTemplate();
        boolean rlsEnabled = Boolean.TRUE.equals(jdbcTemplate.queryForObject("select coalesce(current_setting('alfio.checkRowAccess', true), 'false') = 'true'", EmptySqlParameterSource.INSTANCE, Boolean.class));
        if (rlsEnabled) {
            jdbcTemplate.queryForObject("select set_config('alfio.checkRowAccess', 'false', true)", EmptySqlParameterSource.INSTANCE, Boolean.class);
        }
        boolean exists = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "select exists(select 1 from event where short_name = :slug)",
            Map.of("slug", slug),
            Boolean.class));

        if (rlsEnabled) {
            jdbcTemplate.queryForObject("select set_config('alfio.checkRowAccess', 'true', true)", EmptySqlParameterSource.INSTANCE, Boolean.class);
        }
        return exists;
    }

    NamedParameterJdbcTemplate getJdbcTemplate();
}
