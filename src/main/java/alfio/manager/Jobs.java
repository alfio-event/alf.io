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
package alfio.manager;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.time.DateUtils;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class Jobs {

    private static final int ONE_MINUTE = 1000 * 60;
    private static final int THIRTY_MINUTES = 30 * ONE_MINUTE;
    private static final int THIRTY_SECONDS = 1000 * 30;
    private static final int FIVE_SECONDS = 1000 * 5;
    private final TicketReservationManager ticketReservationManager;
    private final NotificationManager notificationManager;
    private final SpecialPriceTokenGenerator specialPriceTokenGenerator;
    private final FileUploadManager fileUploadManager;
    private final WaitingQueueSubscriptionProcessor waitingQueueSubscriptionProcessor;

    @Autowired
    public Jobs(TicketReservationManager ticketReservationManager,
                NotificationManager notificationManager,
                SpecialPriceTokenGenerator specialPriceTokenGenerator,
                FileUploadManager fileUploadManager,
                WaitingQueueSubscriptionProcessor waitingQueueSubscriptionProcessor) {
        this.ticketReservationManager = ticketReservationManager;
        this.notificationManager = notificationManager;
        this.specialPriceTokenGenerator = specialPriceTokenGenerator;
        this.fileUploadManager = fileUploadManager;
        this.waitingQueueSubscriptionProcessor = waitingQueueSubscriptionProcessor;
    }


    public void cleanupExpiredPendingReservation() {
        //cleanup reservation that have a expiration older than "now minus 10 minutes": this give some additional slack.
        final Date expirationDate = DateUtils.addMinutes(new Date(), -10);
        ticketReservationManager.cleanupExpiredReservations(expirationDate);
        ticketReservationManager.cleanupExpiredOfflineReservations(expirationDate);
        ticketReservationManager.markExpiredInPaymentReservationAsStuck(expirationDate);
    }

    public void sendOfflinePaymentReminder() {
        ticketReservationManager.sendReminderForOfflinePayments();
    }

    public void sendTicketAssignmentReminder() {
        ticketReservationManager.sendReminderForTicketAssignment();
        ticketReservationManager.sendReminderForOptionalData();
    }

    public void generateSpecialPriceCodes() {
        specialPriceTokenGenerator.generatePendingCodes();
    }

    public void sendEmails() {
        notificationManager.sendWaitingMessages();
    }

    public void processReleasedTickets() {
        waitingQueueSubscriptionProcessor.handleWaitingTickets();
    }

    public void cleanupUnreferencedBlobFiles() {
        fileUploadManager.cleanupUnreferencedBlobFiles();
    }

    @DisallowConcurrentExecution
    @Log4j2
    public static class CleanupExpiredPendingReservation implements Job {

        public static long INTERVAL = THIRTY_SECONDS;

        @Autowired
        private Jobs jobs;

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            log.trace("running job " + getClass().getSimpleName());
            jobs.cleanupExpiredPendingReservation();
        }
    }

    @DisallowConcurrentExecution
    @Log4j2
    public static class SendOfflinePaymentReminder implements  Job {

        public static long INTERVAL = THIRTY_MINUTES;

        @Autowired
        private Jobs jobs;

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            log.trace("running job " + getClass().getSimpleName());
            jobs.sendOfflinePaymentReminder();
        }
    }

    @DisallowConcurrentExecution
    @Log4j2
    public static class SendTicketAssignmentReminder implements  Job {

        public static long INTERVAL = THIRTY_MINUTES;

        @Autowired
        private Jobs jobs;

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            log.trace("running job " + getClass().getSimpleName());
            jobs.sendTicketAssignmentReminder();
        }
    }


    @DisallowConcurrentExecution
    @Log4j2
    public static class GenerateSpecialPriceCodes implements  Job {

        public static long INTERVAL = THIRTY_SECONDS;

        @Autowired
        private Jobs jobs;

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            log.trace("running job " + getClass().getSimpleName());
            jobs.generateSpecialPriceCodes();
        }
    }

    @DisallowConcurrentExecution
    @Log4j2
    public static class SendEmails implements Job {

        public static long INTERVAL = FIVE_SECONDS;

        @Autowired
        private Jobs jobs;

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            log.trace("running job " + getClass().getSimpleName());
            jobs.sendEmails();
        }
    }

    @DisallowConcurrentExecution
    @Log4j2
    public static class ProcessReleasedTickets implements Job {

        public static long INTERVAL = THIRTY_SECONDS;

        @Autowired
        private Jobs jobs;

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            log.trace("running job " + getClass().getSimpleName());
            jobs.processReleasedTickets();
        }
    }

    @DisallowConcurrentExecution
    @Log4j2
    public static class CleanupUnreferencedBlobFiles implements Job {

        public static long INTERVAL = ONE_MINUTE * 60;

        @Autowired
        private Jobs jobs;

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            log.trace("running job " + getClass().getSimpleName());
            jobs.cleanupUnreferencedBlobFiles();
        }
    }

}
