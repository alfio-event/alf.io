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

import alfio.manager.BillingDocumentManager;
import alfio.manager.NotificationManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.system.AdminJobExecutor;
import alfio.model.system.AdminJobSchedule;
import alfio.repository.EventRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.RenderedTemplate;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Component
@AllArgsConstructor
public class BillingDocumentJobExecutor implements AdminJobExecutor {

    private final BillingDocumentManager billingDocumentManager;
    private final TicketReservationManager ticketReservationManager;
    private final EventRepository eventRepository;
    private final NotificationManager notificationManager;
    private final OrganizationRepository organizationRepository;

    @Override
    public Set<JobName> getJobNames() {
        return EnumSet.of(JobName.REGENERATE_INVOICES);
    }

    @Override
    public String process(AdminJobSchedule schedule) {
        var metadata = schedule.getMetadata();
        int eventId = Objects.requireNonNull((Integer) metadata.get("eventId"));
        var username = (String) metadata.get("username");
        var event = eventRepository.findById(eventId);
        var counter = new AtomicInteger();
        Pattern.compile(",")
            .splitAsStream(Objects.requireNonNull((String) metadata.get("ids")))
            .map(String::trim)
            .map(Long::valueOf)
            .forEach(id -> {
                var billingDocument = billingDocumentManager.getDocumentById(id).orElseThrow();
                var reservation = ticketReservationManager.findById(billingDocument.getReservationId()).orElseThrow();
                billingDocumentManager.createBillingDocument(event, reservation, username, ticketReservationManager.orderSummaryForReservation(reservation, event));
                counter.incrementAndGet();
            });
        if(counter.get() > 0) {
            var organization = organizationRepository.getById(event.getOrganizationId());
            notificationManager.sendSimpleEmail(event, null, organization.getEmail(), "Invoice Regeneration complete",
                () -> RenderedTemplate.plaintext("Invoice regeneration for event "+event.getDisplayName()+ " is now complete. "+counter.get()+" invoices generated.", Map.of()));
        }
        return "generated";
    }
}
