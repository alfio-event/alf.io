---
title: "Subscription"
linkTitle: "Subscription"
weight: 4
date: 2023-02-05
description: >
  Compatible Application Events for the "Subscription" entity
---

### Customize subscription metadata
`SUBSCRIPTION_ASSIGNED_GENERATE_METADATA`

Fired **synchronously** before marking a subscription as "acquired". The purpose of this extension is to allow metadata customization.

A result of type [`SubscriptionMetadata`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/metadata/SubscriptionMetadata.java) is expected. Return `null` if you don't need to modify the current metadata.
<div class="table-responsive table-hover">
    <table class="table table-sm td-initial">
        <thead>
            <tr>
                <th>Variable</th>
                <th>Type</th>
                <th>About</th>
            </tr>
        </thead>
        <tbody>
            <tr>
                <td><code>subscription</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/subscription/Subscription.java" target="_blank" rel="noopener"><code>Subscription</code></a></td>
                <td>Details about the subscription to be acquired</td>
            </tr>
            <tr>
                <td><code>subscriptionDescriptor</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/subscription/SubscriptionDescriptor.java" target="_blank" rel="noopener"><code>SubscriptionDescriptor</code></a></td>
                <td>Subscription configuration (template)</td>
            </tr>
            <tr>
                <td><code>metadata</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/metadata/SubscriptionMetadata.java" target="_blank" rel="noopener"><code>SubscriptionMetadata</code></a></td>
                <td>Existing metadata for subscription.</td>
            </tr>
            <tr>
                <td><code>additionalInfo</code></td>
                <td><code>Map&lt;String, List&lt;String&gt;&gt;</code></td>
                <td>Additional information provided by the subscription holder</td>
            </tr>
        </tbody>
    </table>
</div>