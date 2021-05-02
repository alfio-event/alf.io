package alfio.controller.api.v2.model;

public class AuthenticationStatus {
    private final boolean enabled;
    private final User user;

    public AuthenticationStatus(boolean enabled, User user) {
        this.enabled = enabled;
        this.user = user;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public User getUser() {
        return user;
    }
}
