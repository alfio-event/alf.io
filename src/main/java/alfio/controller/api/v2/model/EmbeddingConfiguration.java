package alfio.controller.api.v2.model;

import java.util.Objects;

public class EmbeddingConfiguration {
    private final String notificationOrigin;

    public EmbeddingConfiguration(String notificationOrigin) {
        this.notificationOrigin = Objects.requireNonNullElse(notificationOrigin, "");
    }

    public boolean isEnabled() {
        return notificationOrigin != null && !notificationOrigin.isBlank();
    }

    public String getNotificationOrigin() {
        return notificationOrigin;
    }
}
