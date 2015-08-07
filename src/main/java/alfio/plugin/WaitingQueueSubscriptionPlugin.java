package alfio.plugin;

import alfio.model.WaitingQueueSubscription;

/**
 * A plugin that will be triggered once a user registered to the waiting queue.
 */
public interface WaitingQueueSubscriptionPlugin extends Plugin {

    /**
     * This method is called immediately after a waiting queue subscription.
     * @param waitingQueueSubscription the subscription
     */
    void onWaitingQueueSubscription(WaitingQueueSubscription waitingQueueSubscription);
}
