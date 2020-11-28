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
import alfio.model.LightweightMailMessage;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@QueryRepository
public interface EmailMessageRepository {

    /**
     * This method returns a lightweight instance of EmailMessage. The property "Attachments" is always null.
     * @param eventId
     * @param checksum
     * @return
     */
    @Query("select id from email_message where event_id = :eventId and checksum = :checksum limit 1")
    Optional<Integer> findIdByEventIdAndChecksum(@Bind("eventId") int eventId, @Bind("checksum") String checksum);

    @Query("insert into email_message (event_id, reservation_id, status, recipient, subject, message, html_message, attachments, checksum, request_ts, email_cc) values(:eventId, :reservationId, 'WAITING', :recipient, :subject, :message, :htmlMessage, :attachments, :checksum, :timestamp, :emailCC)")
    int insert(@Bind("eventId") int eventId,
               @Bind("reservationId") String reservationId,
               @Bind("recipient") String recipient,
               @Bind("emailCC") String cc,
               @Bind("subject") String subject,
               @Bind("message") String message,
               @Bind("htmlMessage") String htmlMessage,
               @Bind("attachments") String attachments,
               @Bind("checksum") String checksum,
               @Bind("timestamp") ZonedDateTime requestTimestamp);

    @Query("insert into email_message (event_id, reservation_id, status, recipient, subject, message, html_message, attachments, checksum, request_ts, email_cc, organization_id_fk) values(:eventId, :reservationId, 'WAITING', :recipient, :subject, :message, :htmlMessage, :attachments, :checksum, :timestamp, :emailCC, :organization_id)")
    int insertWithOrganization(@Bind("eventId") int eventId,
               @Bind("reservationId") String reservationId,
               @Bind("recipient") String recipient,
               @Bind("emailCC") String cc,
               @Bind("subject") String subject,
               @Bind("message") String message,
               @Bind("htmlMessage") String htmlMessage,
               @Bind("attachments") String attachments,
               @Bind("checksum") String checksum,
               @Bind("timestamp") ZonedDateTime requestTimestamp,
               @Bind("organization_id") int organization_id);

    @Query("insert into email_message (event_id, reservation_id, status, recipient, subject, message, html_message, attachments, checksum, request_ts, email_cc, organization_id_fk) values(:eventId, :reservationId, 'PROMO_CODE', :recipient, :subject, :message, :htmlMessage, :attachments, :checksum, :timestamp, :emailCC, :organization_id)")
    int insertWithPromoCode(@Bind("eventId") int eventId,
               @Bind("reservationId") String reservationId,
               @Bind("recipient") String recipient,
               @Bind("emailCC") String cc,
               @Bind("subject") String subject,
               @Bind("message") String message,
               @Bind("htmlMessage") String htmlMessage,
               @Bind("attachments") String attachments,
               @Bind("checksum") String checksum,
               @Bind("timestamp") ZonedDateTime requestTimestamp,
               @Bind("organization_id") int organization_id);

    @Query("update email_message set status = :status where id = :messageId and event_id = :eventId and checksum = :checksum and status in (:expectedStatuses)")
    int updateStatus(@Bind("messageId") int messageId, @Bind("eventId") int eventId, @Bind("checksum") String checksum, @Bind("status") String status, @Bind("expectedStatuses") List<String> expectedStatuses);

    @Query("update email_message set status = 'WAITING', html_message = :htmlMessage where id = :messageId and event_id = :eventId")
    int updateStatusToWaitingWithHtml(@Bind("eventId") int eventId, @Bind("messageId") int messageId, @Bind("htmlMessage") String htmlMessage);

    @Query("update email_message set status = :status, attempts = :attempts where id = :messageId and status in (:expectedStatuses) ")
    int updateStatusAndAttempts(@Bind("messageId") int messageId, @Bind("status") String status, @Bind("attempts") int attempts, @Bind("expectedStatuses") List<String> expectedStatuses);

    @Query("update email_message set status = :status, attempts = :attempts, request_ts = :nextDate where id = :messageId and status in (:expectedStatuses) ")
    int updateStatusAndAttempts(@Bind("messageId") int messageId, @Bind("status") String status, @Bind("nextDate") Date date, @Bind("attempts") int attempts, @Bind("expectedStatuses") List<String> expectedStatuses);


    @Query("select id from email_message where event_id = :eventId and (status = 'WAITING' or status = 'RETRY') and request_ts <= :date limit 100 for update skip locked")
    List<Integer> loadIdsWaitingForProcessing(@Bind("eventId") int eventId, @Bind("date") Date date);

    @Query("select id from email_message where status = 'PROMO_CODE' and request_ts <= :date limit 100 for update skip locked")
    List<Integer> loadIdsPromoCodesForProcessing(@Bind("date") Date date);

//    @Query("update email_message set status = 'SENT', sent_ts = :sentTimestamp where event_id = :eventId and checksum = :checksum and status in (:expectedStatuses)")
    @Query("update email_message set status = 'SENT', sent_ts = :sentTimestamp, html_message = null where event_id = :eventId and checksum = :checksum and status in (:expectedStatuses)")
    int updateStatusToSent(@Bind("eventId") int eventId, @Bind("checksum") String checksum, @Bind("sentTimestamp") ZonedDateTime sentTimestamp, @Bind("expectedStatuses") List<String> expectedStatuses);

    String FIND_MAILS = "select id, event_id, status, recipient, subject, message, checksum, request_ts, sent_ts, attempts, email_cc from email_message where event_id = :eventId and " +
        " (:search is null or (recipient like :search or subject like :search or message like :search)) order by sent_ts desc, id ";

    @Query("select * from (" + FIND_MAILS +"limit :pageSize offset :page) as d_tbl")
    List<LightweightMailMessage> findByEventId(@Bind("eventId") int eventId, @Bind("page") int page, @Bind("pageSize") int pageSize, @Bind("search") String search);

    @Query("select id, event_id, status, recipient, subject, message, checksum, request_ts, sent_ts, attempts, email_cc from email_message where event_id = :eventId and reservation_id = :reservationId order by sent_ts desc, id")
    List<LightweightMailMessage> findByEventIdAndReservationId(@Bind("eventId") int eventId, @Bind("reservationId") String reservationId);

    @Query("select count(*) from (" + FIND_MAILS + ") as d_tbl")
    Integer countFindByEventId(@Bind("eventId") int eventId, @Bind("search") String search);


    @Query("select * from email_message where id = :id")
    EmailMessage findById(@Bind("id") int id);

    @Query("select * from email_message where id = :messageId and event_id = :eventId")
    Optional<EmailMessage> findByEventIdAndMessageId(@Bind("eventId") int eventId, @Bind("messageId") int messageId);

    @Query("update email_message set status = 'RETRY', attempts = coalesce(attempts, 0) +1 where status = 'IN_PROCESS' and request_ts < :date")
    int setToRetryOldInProcess(@Bind("date") Date date);
}
