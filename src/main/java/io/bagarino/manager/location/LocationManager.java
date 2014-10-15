package io.bagarino.manager.location;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.cache.annotation.Cacheable;

import java.util.TimeZone;

public interface LocationManager {
    @Cacheable
    Pair<String, String> geocode(String address);

    @Cacheable
    TimeZone getTimezone(Pair<String, String> location);

    @Cacheable
    TimeZone getTimezone(String latitude, String longitude);
}
