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
package alfio.job;

import alfio.config.Initializer;
import alfio.manager.*;
import alfio.manager.system.AdminJobExecutor;
import alfio.manager.system.AdminJobManager;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

/**
 * <p>Scheduled jobs. Important: all the jobs must be able to run on multiple instance at the same time.</p>
 * <p>Take great care in placing a select id ... for update skip locked to avoid multiple job execution for the same object</p>
 * <p>Note: it's a separate package, as we need to ensure that the called method are public (and possibly @Transactional!)</p>
 *
 */
@Component
@DependsOn("migrator")
@Profile("!" + Initializer.PROFILE_DISABLE_JOBS)
@AllArgsConstructor
@Log4j2
public class Jobs {


    private static final int ONE_MINUTE = 1000 * 60;
    private static final int THIRTY_SECONDS = 1000 * 30;
    private static final int FIVE_SECONDS = 1000 * 5;
    private static final int THIRTY_MINUTES = 30 * ONE_MINUTE;
    private static final String EVERY_HOUR = "0 0 0/1 * * ?";

    private final AdminReservationRequestManager adminReservationRequestManager;
    private final FileUploadManager fileUploadManager;
    private final NotificationManager notificationManager;
    private final SpecialPriceTokenGenerator specialPriceTokenGenerator;
    private final TicketReservationManager ticketReservationManager;
    private final WaitingQueueSubscriptionProcessor waitingQueueSubscriptionProcessor;
    private final AdminJobManager adminJobManager;


    //cron each minute: "0 0/1 * * * ?"

    @Scheduled(fixedRate = ONE_MINUTE * 60)
    public void cleanupUnreferencedBlobFiles() {
        log.trace("running job cleanupUnreferencedBlobFiles");
        try {
            fileUploadManager.cleanupUnreferencedBlobFiles(DateUtils.addDays(new Date(), -1));
        } finally {
            log.trace("end job cleanupUnreferencedBlobFiles");
        }
    }


    @Scheduled(fixedRate = THIRTY_SECONDS)
    public void generateSpecialPriceCodes() {
        log.trace("running job generateSpecialPriceCodes");
        try {
            specialPriceTokenGenerator.generatePendingCodes();
        } finally {
            log.trace("end job generateSpecialPriceCodes");
        }
    }


    //run each hour
    @Scheduled(cron = EVERY_HOUR)
    public void sendOfflinePaymentReminderToEventOrganizers() {
        log.trace("running job sendOfflinePaymentReminderToEventOrganizers");
        try {
            adminJobManager.scheduleExecution(AdminJobExecutor.JobName.SEND_OFFLINE_PAYMENT_TO_ORGANIZER, Map.of());
        } finally {
            log.trace("end job sendOfflinePaymentReminderToEventOrganizers");
        }
    }

    @Scheduled(cron = EVERY_HOUR)
    public void assignTicketsToSubscribers() {
        log.trace("running job assignTicketsToSubscribers");
        try {
            adminJobManager.scheduleExecution(AdminJobExecutor.JobName.ASSIGN_TICKETS_TO_SUBSCRIBERS, Map.of());
        } finally {
            log.trace("end job sendOfflinePaymentReminderToEventOrganizers");
        }
    }


    @Scheduled(fixedRate = FIVE_SECONDS)
    public void sendEmails() {
        log.trace("running job sendEmails");
        try {
            notificationManager.sendWaitingMessages();
        } finally {
            log.trace("end job sendEmails");
        }
    }

    @Scheduled(fixedRate = FIVE_SECONDS)
    public void processReservationRequests() {
        log.trace("running job processReservationRequests");
        try {
            long start = System.currentTimeMillis();
            Pair<Integer, Integer> result = adminReservationRequestManager.processPendingReservations();
            if (result.getLeft() > 0 || result.getRight() > 0) {
                log.info("ProcessReservationRequests: got {} success and {} failures. Elapsed {} ms", result.getLeft(), result.getRight(), System.currentTimeMillis() - start);
            }
        } finally {
            log.trace("end job processReservationRequests");
        }
    }


    @Scheduled(fixedRate = THIRTY_MINUTES)
    public void sendOfflinePaymentReminder() {
        log.trace("running job sendOfflinePaymentReminder");
        try {
            adminJobManager.scheduleExecution(AdminJobExecutor.JobName.SEND_OFFLINE_PAYMENT_REMINDER, Map.of());
        } finally {
            log.trace("end job sendOfflinePaymentReminder");
        }
    }

    @Scheduled(fixedRate = THIRTY_MINUTES)
    public void sendTicketAssignmentReminder() {
        log.trace("running job sendTicketAssignmentReminder");
        try {
            adminJobManager.scheduleExecution(AdminJobExecutor.JobName.SEND_TICKET_ASSIGNMENT_REMINDER, Map.of());
        } finally {
            log.trace("end job sendTicketAssignmentReminder");
        }
    }


    @Scheduled(fixedRate = THIRTY_SECONDS)
    public void cleanupExpiredPendingReservation() {
        log.trace("running job cleanupExpiredPendingReservation");
        try {
            //cleanup reservation that have a expiration older than "now minus 10 minutes": this give some additional slack.
            final Date expirationDate = DateUtils.addMinutes(new Date(), -10);
            ticketReservationManager.cleanupExpiredReservations(expirationDate);
            ticketReservationManager.cleanupExpiredOfflineReservations(expirationDate);
            ticketReservationManager.markExpiredInPaymentReservationAsStuck(expirationDate);
        } finally {
            log.trace("end job cleanupExpiredPendingReservation");
        }
    }


    @Scheduled(fixedRate = THIRTY_SECONDS)
    public void processReleasedTickets() {
        log.trace("running job processReleasedTickets");
        try {
            waitingQueueSubscriptionProcessor.handleWaitingTickets();
        } finally {
            log.trace("end job processReleasedTickets");
        }
    }

    @Scheduled(fixedRateString = "#{environment.acceptsProfiles('dev') ? (1000 * 60) : (30 * 60 * 1000)}")
    public void checkOfflinePaymentsStatus() {
        log.trace("running job checkOfflinePaymentsStatus");
        try {
            adminJobManager.scheduleExecution(AdminJobExecutor.JobName.CHECK_OFFLINE_PAYMENTS, Map.of());
        } finally {
            log.trace("end job checkOfflinePaymentsStatus");
        }
    }
}
