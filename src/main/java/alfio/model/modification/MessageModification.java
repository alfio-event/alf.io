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
package alfio.model.modification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Locale;
import java.util.Optional;

@Data
public class MessageModification {

    private final Locale locale;
    private final String subject;
    private final String text;
    private final String subjectExample;
    private final String textExample;
    private final boolean attachTicket;

    @JsonCreator
    public MessageModification(@JsonProperty("locale") Locale locale,
                               @JsonProperty("subject") String subject,
                               @JsonProperty("text") String text,
                               @JsonProperty("subjectExample") String subjectExample,
                               @JsonProperty("textExample") String textExample,
                               @JsonProperty("attachTicket") Boolean attachTicket) {
        this.locale = locale;
        this.subject = subject;
        this.text = text;
        this.subjectExample = subjectExample;
        this.textExample = textExample;
        this.attachTicket = Optional.ofNullable(attachTicket).orElse(false);
    }

    public static MessageModification preview(MessageModification original, String subject, String text, boolean attachTicket) {
        return new MessageModification(original.locale, original.subject, original.text, subject, text, attachTicket);
    }
}
