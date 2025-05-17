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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

class WaitingQueueSubscriptionTest {

    @Test
    void checkToString() {
        Assertions.assertEquals("WaitingQueueSubscription(id=0, creation=2025-02-02T00:00+01:00[Europe/Zurich], eventId=42, status=ACQUIRED, fullName=Firstname Lastname, firstName=Firstname, lastName=Lastname, emailAddress=email@email.tld, reservationId=ID, userLanguage=en, selectedCategoryId=null, subscriptionType=PRE_SALES)",
            new WaitingQueueSubscription(0, ZonedDateTime.of(LocalDateTime.of(2025, 2, 2, 0, 0, 0), ZoneId.of("Europe/Zurich")), 42, "ACQUIRED", "Firstname Lastname", "Firstname", "Lastname", "email@email.tld", "ID", "en", null, WaitingQueueSubscription.Type.PRE_SALES).toString());
    }
}
