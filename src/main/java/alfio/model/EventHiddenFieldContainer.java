package alfio.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.tuple.Pair;

import java.time.ZoneId;
import java.util.Optional;

/**
 * Created by celestino on 06.07.16.
 */
public interface EventHiddenFieldContainer {
    @JsonIgnore
    String getPrivateKey();

    @JsonIgnore
    Pair<String, String> getLatLong();

    @JsonIgnore
    ZoneId getZoneId();

    @JsonIgnore
    String getGoogleCalendarUrl();

    @JsonIgnore
    String getGoogleCalendarUrl(String description);

    @JsonIgnore
    Optional<byte[]> getIcal(String description, String organizerName, String organizerEmail);
}
