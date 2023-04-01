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

import alfio.manager.system.AdminJobExecutor.JobName;
import alfio.model.support.JSONData;
import alfio.model.system.AdminJobSchedule;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QueryRepository
public interface AdminJobQueueRepository {

    @Query("select * from admin_job_queue where status = 'SCHEDULED' and job_name in (:jobNames) and request_ts < :now::timestamp for update skip locked")
    List<AdminJobSchedule> loadPendingSchedules(@Bind("jobNames") Collection<String> jobNames, @Bind("now") ZonedDateTime now);

    @Query("select * from admin_job_queue")
    List<AdminJobSchedule> loadAll();

    @Query("update admin_job_queue set status = :status, execution_ts = :executionDate, metadata = to_json(:metadata::json) where id = :id")
    int updateSchedule(@Bind("id") long id,
                       @Bind("status") AdminJobSchedule.Status status,
                       @Bind("executionDate")ZonedDateTime executionDate,
                       @Bind("metadata") @JSONData Map<String, Object> metadata);

    @Query("update admin_job_queue set request_ts = :requestTs, attempts = attempts + 1 where id = :id")
    int scheduleRetry(@Bind("id") long id,
                      @Bind("requestTs") ZonedDateTime requestTs);

    @Query("insert into admin_job_queue(job_name, request_ts, metadata, status, attempts, allow_duplicates)" +
        " values(:jobName, :requestTs, to_json(:metadata::json), 'SCHEDULED', 1, :allowDuplicates)" +
        " on conflict do nothing")
    int schedule(@Bind("jobName") JobName jobName,
                 @Bind("requestTs") ZonedDateTime requestTimestamp,
                 @Bind("metadata") @JSONData Map<String, Object> metadata,
                 @Bind("allowDuplicates") String allowDuplicates);

    @Query("delete from admin_job_queue where status in (:status) and request_ts <= :requestTs")
    int removePastSchedules(@Bind("requestTs") ZonedDateTime requestTs, @Bind("status") Set<String> statuses);
}
