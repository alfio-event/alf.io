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

import ch.digitalfondue.npjt.QueryRepository;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Arrays;
import java.util.List;

@QueryRepository
public interface VacuumQuartzTables {

    List<String> quartzTables = Arrays.asList("qrtz_job_details", "qrtz_triggers", "qrtz_simple_triggers",
        "qrtz_cron_triggers", "qrtz_simprop_triggers", "qrtz_blob_triggers", "qrtz_calendars",
        "qrtz_paused_trigger_grps", "qrtz_fired_triggers", "qrtz_scheduler_state", "qrtz_locks");

    NamedParameterJdbcTemplate getTemplate();


    default void vacuumQuartzTables() {
        NamedParameterJdbcTemplate template = getTemplate();
        quartzTables.forEach(name -> template.update("vacuum full " + name, new EmptySqlParameterSource()));
    }
}
