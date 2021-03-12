package alfio.model.subscription;

import lombok.Getter;

/**
 * Thrown when a Subscription can be used only a number of times for an event, and the current application
 * would go over that limit.
 */
@Getter
public class SubscriptionUsageExceededForEvent extends RuntimeException {

    private final int allowed;
    private final int requested;

    public SubscriptionUsageExceededForEvent(int allowed, int requested) {
        this.allowed = allowed;
        this.requested = requested;
    }

}
