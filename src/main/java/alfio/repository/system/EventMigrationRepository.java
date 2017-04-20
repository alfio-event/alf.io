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

import alfio.model.system.EventMigration;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.time.ZonedDateTime;

@QueryRepository
public interface EventMigrationRepository {

    @Query("select * from event_migration where event_id = :eventId")
    EventMigration loadEventMigration(@Bind("eventId") int eventId);

    @Query("update event_migration set current_version = :currentVersion, build_ts = :currentTimestamp, status = :status where id = :id")
    int updateMigrationData(@Bind("id") int id, @Bind("currentVersion") String currentVersion, @Bind("currentTimestamp") ZonedDateTime currentTimestamp, @Bind("status") String status);

    @Query("insert into event_migration (event_id, current_version, build_ts, status) values(:eventId, :currentVersion, :currentTimestamp, :status)")
    int insertMigrationData(@Bind("eventId") int eventId, @Bind("currentVersion") String currentVersion, @Bind("currentTimestamp") ZonedDateTime currentTimestamp, @Bind("status") String status);



}
