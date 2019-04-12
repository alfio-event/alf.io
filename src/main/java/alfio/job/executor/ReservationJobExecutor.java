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
package alfio.job.executor;

import alfio.manager.TicketReservationManager;
import alfio.manager.system.AdminJobExecutor;
import alfio.model.system.AdminJobSchedule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

import static alfio.manager.system.AdminJobExecutor.JobName.*;

@Component
@RequiredArgsConstructor
public class ReservationJobExecutor implements AdminJobExecutor {

    private final TicketReservationManager ticketReservationManager;

    @Override
    public Set<JobName> getJobNames() {
        return EnumSet.of(
            CHECK_OFFLINE_PAYMENTS,
            SEND_TICKET_ASSIGNMENT_REMINDER,
            SEND_OFFLINE_PAYMENT_REMINDER,
            SEND_OFFLINE_PAYMENT_TO_ORGANIZER
        );
    }

    @Override
    public String process(AdminJobSchedule schedule) {
        switch(schedule.getJobName()) {
            case CHECK_OFFLINE_PAYMENTS:
                ticketReservationManager.checkOfflinePaymentsStatus();
                break;
            case SEND_TICKET_ASSIGNMENT_REMINDER:
                ticketReservationManager.sendReminderForTicketAssignment();
                ticketReservationManager.sendReminderForOptionalData();
                break;
            case SEND_OFFLINE_PAYMENT_REMINDER:
                ticketReservationManager.sendReminderForOfflinePayments();
                break;
            case SEND_OFFLINE_PAYMENT_TO_ORGANIZER:
                ticketReservationManager.sendReminderForOfflinePaymentsToEventManagers();
                break;
            default:
                return null;
        }
        return "OK";
    }
}
