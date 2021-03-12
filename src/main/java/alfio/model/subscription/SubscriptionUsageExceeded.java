package alfio.model.subscription;

import lombok.Getter;

/**
 * Thrown when a Subscription can be used only a fixed number of times, and the current application
 * would go over that limit.
 */
@Getter
public class SubscriptionUsageExceeded extends RuntimeException {
    public static String ERROR = "MAX_ENTRIES_OVERAGE";

    private final int allowed;
    private final int requested;

    public SubscriptionUsageExceeded(int allowed, int requested) {
        this.allowed = allowed;
        this.requested = requested;
    }

}
