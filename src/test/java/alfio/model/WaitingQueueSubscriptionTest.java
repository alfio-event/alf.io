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
