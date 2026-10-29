---
title: "Other"
linkTitle: "Other"
weight: 11
date: 2020-11-26
description: >
  Other Application Events
---

### Waiting List subscription
`WAITING_QUEUE_SUBSCRIBED`

Fired when someone subscribes to a waiting list
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
                <td><code>waitingQueueSubscription</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/WaitingQueueSubscription.java" target="_blank" rel="noopener"><code>WaitingQueueSubscription</code></a></td>
                <td>Details about the subscription</td>
            </tr>
        </tbody>
    </table>
</div>

### PDF Generation
`PDF_GENERATION`

Fired when a PDF needs to be generated. This is useful if you want to delegate the actual PDF generation to a dedicated service.

Script is expected to save the result in a temporary file (e.g. by using the `postBodyAndSaveResponse` method of [SimpleHttpClient](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/extension/SimpleHttpClient.java#L84))

This is a **synchronous** call. 
A result of type [`PdfGenerationResult`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/extension/PdfGenerationResult.java) is expected. Return `null` if the generation was not successful. 
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
                <td><code>event</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Event.java" target="_blank" rel="noopener"><code>Event</code></a></td>
                <td>The event</td>
            </tr>
            <tr>
                <td><code>html</code></td>
                <td><code>String</code></td>
                <td>HTML to be converted to PDF</td>
            </tr>
            <tr>
                <td><code>outputStream</code></td>
                <td><a href="https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStream.html" target="_blank" rel="noopener"><code>OutputStream</code></a></td>
                <td>OutputStream used to save the produced PDF</td>
            </tr>
        </tbody>
    </table>
</div>

### OAuth2 State param
`OAUTH2_STATE_GENERATION`

Fired when an an OAuth2 [state parameter](https://www.oauth.com/oauth2-servers/accessing-data/authorization-request/) is needed, I.e. for setting up Stripe/Mollie connect.

This is a **synchronous** call. A `String` containing a valid URL must be returned, or `null` to proceed with the default settings. 
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
                <td><code>baseUrl</code></td>
                <td>String</td>
                <td>The configured "Base URL" for the current organizer</td>
            </tr>
            <tr>
                <td><code>organizationId</code></td>
                <td><code>int</code></td>
                <td>Organizer ID</td>
            </tr>
        </tbody>
    </table>
</div>

