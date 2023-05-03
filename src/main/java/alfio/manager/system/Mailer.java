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

import alfio.config.Initializer;
import alfio.model.Configurable;
import lombok.Data;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.*;

public interface Mailer {

    String SKIP_PASSBOOK = "skipPassbook";

    void send(Configurable configurable, String fromName, String to, List<String> cc, String subject, String text, Optional<String> html, Attachment... attachment);

    @Data
    class Attachment {
        private final String filename;
        //can be null if model and identifier are specified
        private final byte[] source;
        private final String contentType;

        //for dynamically generated attachment
        private final Map<String, String> model;
        private final AttachmentIdentifier identifier;
    }

    default String decorateSubjectIfDemo(String subject, Environment environment) {
        if(environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEMO))) {
            return "THIS IS A TEST: " + subject;
        } else {
            return subject;
        }
    }


    enum AttachmentIdentifier {
        TICKET_PDF {
            @Override
            public List<AttachmentIdentifier> reinterpretAs() {
                return Arrays.asList(PASSBOOK, CALENDAR_ICS);
            }
        }, CALENDAR_ICS {
            @Override
            public String fileName(String fileName) {
                return "calendar.ics";
            }

            @Override
            public String contentType(String contentType) {
                return "text/calendar";
            }
        }, INVOICE_PDF, RECEIPT_PDF, CREDIT_NOTE_PDF, PASSBOOK {
            @Override
            public String fileName(String fileName) {
                return "Passbook.pkpass";
            }

            @Override
            public String contentType(String contentType) {
                return "application/vnd.apple.pkpass";
            }
        }, SUBSCRIPTION_PDF;

        public List<AttachmentIdentifier> reinterpretAs() {
            return Collections.emptyList();
        }

        public String contentType(String contentType) {
            return contentType;
        }

        public String fileName(String fileName) {
            return fileName;
        }
    }
}