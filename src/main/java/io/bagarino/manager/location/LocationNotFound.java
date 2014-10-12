package io.bagarino.manager.location;

public class LocationNotFound extends RuntimeException {

    public LocationNotFound(String message) {
        super(message);
    }

    public LocationNotFound(String message, Throwable cause) {
        super(message, cause);
    }
}
