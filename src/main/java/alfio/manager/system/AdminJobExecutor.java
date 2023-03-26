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
package alfio.manager.system;

import alfio.model.system.AdminJobSchedule;

import java.util.Arrays;
import java.util.Set;

public interface AdminJobExecutor {

    enum JobName {
        CHECK_OFFLINE_PAYMENTS(false),
        SEND_TICKET_ASSIGNMENT_REMINDER(false),
        SEND_OFFLINE_PAYMENT_REMINDER(false),
        UNKNOWN(false),
        SEND_OFFLINE_PAYMENT_TO_ORGANIZER(false),
        REGENERATE_INVOICES(false),
        ASSIGN_TICKETS_TO_SUBSCRIBERS(false),
        EXECUTE_EXTENSION(true),
        RETRY_RESERVATION_CONFIRMATION(true);

        private final boolean allowsMultiple;

        JobName(boolean allowsMultiple) {
            this.allowsMultiple = allowsMultiple;
        }

        public boolean allowsMultipleScheduling() {
            return allowsMultiple;
        }

        public static JobName safeValueOf(String value) {
            return Arrays.stream(values())
                .filter(v -> v.name().equalsIgnoreCase(value))
                .findFirst().orElse(UNKNOWN);
        }
    }

    /**
     * Returns the supported Job names.
     * @return the job name
     */
    Set<JobName> getJobNames();

    /**
     * Process the scheduled job
     *
     * @param schedule info about the job
     * @return an optional message, can be null
     */
    String process(AdminJobSchedule schedule);
}
