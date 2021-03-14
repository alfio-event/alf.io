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
package alfio.repository;

import alfio.model.EmailMessage;
import alfio.model.Event;
import alfio.model.LightweightMailMessage;
import alfio.model.PurchaseContext;
import alfio.model.subscription.SubscriptionDescriptor;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;
import ch.digitalfondue.npjt.QueryType;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@QueryRepository
public interface EmailMessageRepository {

    @Query("select id from email_message where event_id = :eventId and checksum = :checksum limit 1")
    Optional<Integer> findIdByEventIdAndChecksum(@Bind("eventId") int eventId, @Bind("checksum") String checksum);

    @Query("select id from email_message where subscription_descriptor_id_fk = :subscriptionDescriptorId and checksum = :checksum limit 1")
    Optional<Integer> findIdBySubscriptionDescriptorAndChecksum(@Bind("subscriptionDescriptorId") UUID subscriptionDescriptorId, @Bind("checksum") String checksum);

    default Optional<Integer> findIdByPurchaseContextAndChecksum(PurchaseContext purchaseContext, String checksum) {
        if(purchaseContext.ofType(PurchaseContext.PurchaseContextType.event)) {
            return findIdByEventIdAndChecksum(((Event)purchaseContext).getId(), checksum);
        } else {
            return findIdBySubscriptionDescriptorAndChecksum(((SubscriptionDescriptor) purchaseContext).getId(), checksum);
        }
    }

    @Query("insert into email_message (event_id, organization_id_fk, subscription_descriptor_id_fk, reservation_id, status, recipient, subject, message, html_message, attachments, checksum, request_ts, email_cc)" +
        " values(:eventId, :organizationId, :subscriptionDescriptorId, :reservationId, 'WAITING', :recipient, :subject, :message, :htmlMessage, :attachments, :checksum, :timestamp, :emailCC)")
    int insert(@Bind("eventId") Integer eventId,
               @Bind("subscriptionDescriptorId") UUID subscriptionDescriptorId,
               @Bind("reservationId") String reservationId,
               @Bind("recipient") String recipient,
               @Bind("emailCC") String cc,
               @Bind("subject") String subject,
               @Bind("message") String message,
               @Bind("htmlMessage") String htmlMessage,
               @Bind("attachments") String attachments,
               @Bind("checksum") String checksum,
               @Bind("timestamp") ZonedDateTime requestTimestamp,
               @Bind("organizationId") int organizationId);


    @Query("update email_message set status = :status where id = :id and checksum = :checksum and status in (:expectedStatuses)")
    int updateStatus(@Bind("id") int messageId, @Bind("checksum") String checksum, @Bind("status") String status, @Bind("expectedStatuses") List<String> expectedStatuses);

    @Query("update email_message set status = 'WAITING', html_message = :htmlMessage where id = :messageId")
    int updateStatusToWaitingWithHtml(@Bind("messageId") int messageId, @Bind("htmlMessage") String htmlMessage);

    @Query("update email_message set status = :status, attempts = :attempts where id = :messageId and status in (:expectedStatuses) ")
    int updateStatusAndAttempts(@Bind("messageId") int messageId, @Bind("status") String status, @Bind("attempts") int attempts, @Bind("expectedStatuses") List<String> expectedStatuses);

    @Query("update email_message set status = :status, attempts = :attempts, request_ts = :nextDate where id = :messageId and status in (:expectedStatuses) ")
    int updateStatusAndAttempts(@Bind("messageId") int messageId, @Bind("status") String status, @Bind("nextDate") ZonedDateTime date, @Bind("attempts") int attempts, @Bind("expectedStatuses") List<String> expectedStatuses);


    @Query(type = QueryType.SELECT,
        value = "select * from email_message" +
                " where (" +
                " (event_id is not null and event_id in (select id from event where end_ts > now())) or " +
                " (subscription_descriptor_id_fk is not null and subscription_descriptor_id_fk in (select id from subscription_descriptor where validity_to is null or validity_to > now())) " +
                ") and (status = 'WAITING' or status = 'RETRY') limit 100 for update skip locked")
    List<EmailMessage> loadAllWaitingForProcessing();

    @Query("update email_message set status = 'SENT', sent_ts = :sentTimestamp, html_message = null where id = :id and checksum = :checksum and status in (:expectedStatuses)")
    int updateStatusToSent(@Bind("id") int id, @Bind("checksum") String checksum, @Bind("sentTimestamp") ZonedDateTime sentTimestamp, @Bind("expectedStatuses") List<String> expectedStatuses);

    String LIGHTWEIGHT_FIELDS = "id, event_id, subscription_descriptor_id_fk, status, recipient, subject, message, checksum, request_ts, sent_ts, attempts, email_cc, organization_id_fk ";
    String FIND_MAILS_BY_EVENT = "select " + LIGHTWEIGHT_FIELDS + " from email_message where event_id = :eventId and " +
        " (:search is null or (recipient like :search or subject like :search or message like :search)) order by sent_ts desc, id ";

    String FIND_MAILS_BY_SUBSCRIPTION = "select " + LIGHTWEIGHT_FIELDS + " from email_message where subscription_descriptor_id_fk = :subscriptionId and " +
        " (:search is null or (recipient like :search or subject like :search or message like :search)) order by sent_ts desc, id ";

    @Query("select * from (" + FIND_MAILS_BY_EVENT +" limit :pageSize offset :page) as d_tbl")
    List<LightweightMailMessage> findByEventId(@Bind("eventId") int eventId, @Bind("page") int page, @Bind("pageSize") int pageSize, @Bind("search") String search);

    @Query("select id, event_id, status, recipient, subject, message, checksum, request_ts, sent_ts, attempts, email_cc, subscription_descriptor_id_fk, organization_id_fk from email_message where event_id = :eventId and reservation_id = :reservationId order by sent_ts desc, id")
    List<LightweightMailMessage> findByEventIdAndReservationId(@Bind("eventId") int eventId, @Bind("reservationId") String reservationId);

    @Query("select * from (" + FIND_MAILS_BY_SUBSCRIPTION + " limit :pageSize offset :page) as d_tbl")
    List<LightweightMailMessage> findBySubscriptionDescriptorId(@Bind("subscriptionId") UUID subscriptionDescriptorId, @Bind("page") int page, @Bind("pageSize") int pageSize, @Bind("search") String search);

    @Query("select id, event_id, status, recipient, subject, message, checksum, request_ts, sent_ts, attempts, email_cc, subscription_descriptor_id_fk, organization_id_fk from email_message where subscription_descriptor_id_fk = :subscriptionDescriptorId and reservation_id = :reservationId order by sent_ts desc, id")
    List<LightweightMailMessage> findBySubscriptionDescriptorAndReservationId(@Bind("subscriptionDescriptorId") UUID subscriptionDescriptorId, @Bind("reservationId") String reservationId);

    default List<LightweightMailMessage> findByPurchaseContextAndReservationId(PurchaseContext purchaseContext, String reservationId) {
        if(purchaseContext.ofType(PurchaseContext.PurchaseContextType.event)) {
            return findByEventIdAndReservationId(((Event)purchaseContext).getId(), reservationId);
        } else {
            return findBySubscriptionDescriptorAndReservationId(((SubscriptionDescriptor)purchaseContext).getId(), reservationId);
        }
    }

    @Query("select count(*) from (" + FIND_MAILS_BY_EVENT + ") as d_tbl")
    Integer countFindByEventId(@Bind("eventId") int eventId, @Bind("search") String search);

    @Query("select count(*) from ("+FIND_MAILS_BY_SUBSCRIPTION+") as d_tbl")
    Integer countFindBySubscriptionDescriptorId(@Bind("subscriptionId") UUID subscriptionId, @Bind("search") String search);

    @Query("select * from email_message where id = :id")
    EmailMessage findById(@Bind("id") int id);

    @Query("select "+LIGHTWEIGHT_FIELDS+" from email_message where id = :messageId and event_id = :eventId")
    Optional<LightweightMailMessage> findByEventIdAndMessageId(@Bind("eventId") int eventId, @Bind("messageId") int messageId);

    @Query("select "+LIGHTWEIGHT_FIELDS+" from email_message where id = :messageId and subscription_descriptor_id_fk = :subscriptionId")
    Optional<LightweightMailMessage> findBySubscriptionDescriptorIdAndMessageId(@Bind("subscriptionId") UUID subscriptionId, @Bind("messageId") int messageId);

    @Query("update email_message set status = 'RETRY', attempts = coalesce(attempts, 0) +1 where status = 'IN_PROCESS' and request_ts < :date")
    int setToRetryOldInProcess(@Bind("date") ZonedDateTime date);
}
