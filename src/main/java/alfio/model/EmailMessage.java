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
package alfio.model;

import alfio.util.Json;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
public class EmailMessage implements Comparable<EmailMessage> {

    public enum Status {
        WAITING, RETRY, IN_PROCESS, SENT, ERROR, PROMO_CODE
    }
    
    private final int id;
    private final int eventId;
    private final Status status;
    private final String recipient;
    private final List<String> cc;
    private final String subject;
    private final String message;
    private final String htmlMessage;
    private final String attachments;
    private final String checksum;
    private final ZonedDateTime requestTimestamp;
    private final ZonedDateTime sentTimestamp;
    private final int attempts;

    public EmailMessage(@Column("id") int id,
                        @Column("event_id") int eventId,
                        @Column("status") String status,
                        @Column("recipient") String recipient,
                        @Column("subject") String subject,
                        @Column("message") String message,
                        @Column("html_message") String htmlMessage,
                        @Column("attachments") String attachments,
                        @Column("checksum") String checksum,
                        @Column("request_ts") ZonedDateTime requestTimestamp,
                        @Column("sent_ts") ZonedDateTime sentTimestamp,
                        @Column("attempts") int attempts,
                        @Column("email_cc") String emailCC) {
        this.id = id;
        this.eventId = eventId;
        this.requestTimestamp = requestTimestamp;
        this.sentTimestamp = sentTimestamp;
        this.status = Status.valueOf(status);
        this.recipient = recipient;
        this.subject = subject;
        this.message = message;
        this.htmlMessage = htmlMessage;
        this.attachments = attachments;
        this.checksum = checksum;
        this.attempts = attempts;

        if(StringUtils.isNotBlank(emailCC)) {
            this.cc = Json.GSON.fromJson(emailCC, new TypeToken<List<String>>(){}.getType());
        } else {
            this.cc = new ArrayList<>();
        }
    }

    @Override
    public int compareTo(EmailMessage o) {
        return new CompareToBuilder().append(eventId, o.eventId).append(checksum, o.checksum).build();
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof EmailMessage)) {
            return false;
        }
        if(obj == this) {
            return true;
        }
        EmailMessage other = (EmailMessage)obj;
        return new EqualsBuilder().append(eventId, other.eventId).append(checksum, other.checksum).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(eventId).append(checksum).toHashCode();
    }
}
