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
    <table class="table table-sm">
        <thead>
            <tr>
                <th>Variable</th>
                <th>Type</th>
                <th>About</th>
            </tr>
        </thead>
        <tbody>
            <tr>
                <td>`subscription`</td>
                <td>[`Subscription`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/subscription/Subscription.java)</td>
                <td>Details about the subscription to be acquired</td>
            </tr>
            <tr>
                <td>`subscriptionDescriptor`</td>
                <td>[`SubscriptionDescriptor`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/subscription/SubscriptionDescriptor.java)</td>
                <td>Subscription configuration (template)</td>
            </tr>
            <tr>
                <td>`metadata`</td>
                <td>[`SubscriptionMetadata`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/metadata/SubscriptionMetadata.java)</td>
                <td>Existing metadata for subscription.</td>
            </tr>
            <tr>
                <td>`additionalInfo`</td>
                <td>`Map<String, List<String>>`</td>
                <td>Additional information provided by the subscription holder</td>
            </tr>
        </tbody>
    </table>
</div>