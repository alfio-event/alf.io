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

import alfio.config.Initializer;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@Profile("!"+ Initializer.PROFILE_DISABLE_JOBS)
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

	@Scheduled(initialDelay = ONE_MINUTE, fixedDelay = THIRTY_SECONDS)
	public void cleanupExpiredPendingReservation() {
		//cleanup reservation that have a expiration older than "now minus 10 minutes": this give some additional slack.
        final Date expirationDate = DateUtils.addMinutes(new Date(), -10);
        ticketReservationManager.cleanupExpiredReservations(expirationDate);
        ticketReservationManager.cleanupExpiredOfflineReservations(expirationDate);
        ticketReservationManager.markExpiredInPaymentReservationAsStuck(expirationDate);
	}

    @Scheduled(fixedRate = THIRTY_MINUTES)
    public void sendOfflinePaymentReminder() {
        ticketReservationManager.sendReminderForOfflinePayments();
    }

    @Scheduled(fixedRate = THIRTY_MINUTES)
    public void sendTicketAssignmentReminder() {
        ticketReservationManager.sendReminderForTicketAssignment();
        ticketReservationManager.sendReminderForOptionalData();
    }

    @Scheduled(fixedDelay = THIRTY_SECONDS)
    public void generateSpecialPriceCodes() {
        specialPriceTokenGenerator.generatePendingCodes();
    }

    @Scheduled(fixedRate = FIVE_SECONDS)
    public void sendEmails() {
        notificationManager.sendWaitingMessages();
    }

    @Scheduled(fixedRate = ONE_MINUTE)
    public void enqueueNotSentEmail() {
        notificationManager.processNotSentEmail();
    }

    @Scheduled(fixedRate = THIRTY_SECONDS)
    public void processReleasedTickets() {
        waitingQueueSubscriptionProcessor.handleWaitingTickets();
    }

    @Scheduled(fixedRate = ONE_MINUTE * 60)
    public void cleanupUnreferencedBlobFiles() {
        fileUploadManager.cleanupUnreferencedBlobFiles();
    }
}
