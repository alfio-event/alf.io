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
package alfio.model.system;

import alfio.manager.system.AdminJobExecutor.JobName;
import alfio.model.support.JSONData;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.Map;

@Getter
public class AdminJobSchedule {

    public enum Status {
        SCHEDULED, RUNNING, EXECUTED, FAILED
    }

    private final long id;
    private final JobName jobName;
    private final ZonedDateTime requestTimestamp;
    private final Status status;
    private final ZonedDateTime executionTimestamp;
    private final Map<String, Object> metadata;
    private final int attempts;

    public AdminJobSchedule(@Column("id") long id,
                            @Column("job_name") String jobName,
                            @Column("request_ts") ZonedDateTime requestTimestamp,
                            @Column("status") Status status,
                            @Column("execution_ts") ZonedDateTime executionTimestamp,
                            @Column("metadata") @JSONData Map<String, Object> metadata,
                            @Column("attempts") int attempts) {
        this.id = id;
        this.jobName = JobName.safeValueOf(jobName);
        this.requestTimestamp = requestTimestamp;
        this.status = status;
        this.executionTimestamp = executionTimestamp;
        this.metadata = metadata;
        this.attempts = attempts;
    }
}
