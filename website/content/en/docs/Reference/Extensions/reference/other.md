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
                <td>`waitingQueueSubscription`</td>
                <td>[`WaitingQueueSubscription`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/WaitingQueueSubscription.java)</td>
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
                <td>`event`</td>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Event.java)</td>
                <td>The event</td>
            </tr>
            <tr>
                <td>`html`</td>
                <td>`String`</td>
                <td>HTML to be converted to PDF</td>
            </tr>
            <tr>
                <td>`outputStream`</td>
                <td>[`OutputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/OutputStream.html)</td>
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
                <td>`baseUrl`</td>
                <td>String</td>
                <td>The configured "Base URL" for the current organizer</td>
            </tr>
            <tr>
                <td>`organizationId`</td>
                <td>`int`</td>
                <td>Organizer ID</td>
            </tr>
        </tbody>
    </table>
</div>

