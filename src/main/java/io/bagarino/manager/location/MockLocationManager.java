package io.bagarino.manager.location;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.TimeZone;

@Component
@Profile("dev")
public class MockLocationManager implements LocationManager {
    @Override
    public Pair<String, String> geocode(String address) {
        return Pair.of("0", "0");
    }

    @Override
    public TimeZone getTimezone(Pair<String, String> location) {
        return getTimezone("", "");
    }

    @Override
    public TimeZone getTimezone(String latitude, String longitude) {
        return TimeZone.getDefault();
    }
}
