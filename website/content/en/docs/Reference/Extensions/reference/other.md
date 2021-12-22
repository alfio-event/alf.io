---
title: "Other"
linkTitle: "Other"
weight: 11
date: 2020-11-26
description: >
  Other Application Events
---

<div class="table-responsive">
    <table class="table table-sm table-striped">
        <thead>
        <tr>
            <th rowspan="2">Application Event</th>
            <th colspan="2" class="text-center">Additional global variables</th>
            <th rowspan="2">Expected result type</th>
            <th rowspan="2">About</th>
        </tr>
        <tr>
            <th>Type</th>
            <th>Name</th>
        </tr>
        </thead>
        <tbody>
            <tr>
                <td>WAITING_QUEUE_SUBSCRIBED</td>
                <td>[`WaitingQueueSubscription`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/WaitingQueueSubscription.java)</td>
                <td>`waitingQueueSubscription`</td>
                <td>`void`</td>
                <td>Extensions will be invoked asynchronously once someone subscribes to the waiting list.</td>
            </tr>
            <tr>
                <td rowspan="3">PDF_GENERATION</td>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
                <td>`event`</td>
                <td rowspan="3">`boolean`</td>
                <td rowspan="3">Extensions will be invoked synchronously when there is a PDF transformation. A `boolean` is returned to indicate if it was successful or not.</td>
            </tr>
            <tr>
                <td>`String`</td>
                <td>`html`</td>
            </tr>
            <tr>
                <td>`OutputStream`</td>
                <td>`outputStream`</td>
            </tr>
            <tr>
                <td rowspan="2">OAUTH2_STATE_GENERATION</td>
                <td>`int`</td>
                <td>`organizationId`</td>
                <td rowspan="2">`String` or `null`</td>
                <td rowspan="2">Extensions will be invoked to generate an OAuth2 [state parameter](https://www.oauth.com/oauth2-servers/accessing-data/authorization-request/).</td>
            </tr>
            <tr>
                <td>[`MaybeConfiguration`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/manager/system/ConfigurationManager.java)</td>
                <td>`baseUrl`</td>
            </tr>
        </tbody>
    </table>
</div>
