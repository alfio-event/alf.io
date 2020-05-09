package alfio.model;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public interface EventCheckInInfo {

    int getId();
    String getPrivateKey();
    ZonedDateTime getBegin();
    ZonedDateTime getEnd();
    ZoneId getZoneId();
    Event.EventFormat getFormat();

}
