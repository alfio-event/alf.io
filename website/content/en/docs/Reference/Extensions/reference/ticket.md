---
title: "Ticket"
linkTitle: "Ticket"
weight: 3
date: 2020-11-26
description: >
  Compatible Application Events for the "Ticket" entity
---
### Ticket assigned
`TICKET_ASSIGNED`

Fired **asynchronously** once a ticket has been assigned
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
                <td><code>ticket</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Ticket.java" target="_blank" rel="noopener"><code>Ticket</code></a></td>
                <td>Details about the ticket</td>
            </tr>
            <tr>
                <td><code>additionalInfo</code></td>
                <td><code>Map&lt;String, List&lt;String&gt;&gt;</code></td>
                <td>Additional information provided by the ticket holder</td>
            </tr>
            <tr>
                <td><code>eventMetadata</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/metadata/AlfioMetadata.java" target="_blank" rel="noopener"><code>AlfioMetadata</code></a></td>
                <td>Metadata of the event</td>
            </tr>
            <tr>
                <td><code>onlineAccessTicket</code></td>
                <td><code>boolean</code></td>
                <td>Whether the ticket grants access to the online event</td>
            </tr>
            <tr>
                <td><code>linkedAdditionalServices</code></td>
                <td>List of <a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/extension/LinkedAdditionalService.java" target="_blank" rel="noopener"><code>LinkedAdditionalService</code></a></td>
                <td>Confirmed additional services linked to the ticket. Each item contains the <code>additionalServiceId</code>, the <code>quantity</code> and the <code>additional</code> values (<code>Map&lt;String, String&gt;</code>, by field name) provided for them. Items having the same values are grouped together. Empty if the event does not support linked additional services</td>
            </tr>
            <tr>
                <td><code>ticketQrCode</code></td>
                <td><code>String</code></td>
                <td>Content of the ticket QR code, i.e. the value scanned at check-in</td>
            </tr>
        </tbody>
    </table>
</div>

### Ticket cancelled
`TICKET_CANCELLED`

Fired **synchronously** once one or more tickets (but not the entire reservation at once) have been cancelled.
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
                <td><code>ticket</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Ticket.java" target="_blank" rel="noopener"><code>Ticket</code></a></td>
                <td>Details about the ticket</td>
            </tr>
            <tr>
                <td><code>additionalInfo</code></td>
                <td><code>Map&lt;String, List&lt;String&gt;&gt;</code></td>
                <td>Additional information provided by the ticket holder</td>
            </tr>
        </tbody>
    </table>
</div>

{{% pageinfo %}}
**Ticket UUID reset**

As security measure, once a ticket has been cancelled its UUID is reset. This happens **after** this event has been fired
{{%/pageinfo%}}

### Ticket checked-in
`TICKET_CHECKED_IN`

Fired **asynchronously** once a ticket has been checked in.
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
                <td><code>ticket</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Ticket.java" target="_blank" rel="noopener"><code>Ticket</code></a></td>
                <td>Details about the ticket</td>
            </tr>
        </tbody>
    </table>
</div>

### Ticket check-in reverted
`TICKET_REVERT_CHECKED_IN`

Fired **asynchronously** once the ticket checked-in status has been reverted.
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
                <td><code>ticket</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Ticket.java" target="_blank" rel="noopener"><code>Ticket</code></a></td>
                <td>Details about the ticket</td>
            </tr>
        </tbody>
    </table>
</div>

### Online check-in redirect
`ONLINE_CHECK_IN_REDIRECT`

Fired **synchronously** when a check-in for an online event happens.

Script is expected to return an `URL` where the ticket holder will be redirected. Or `null` to proceed with the default settings.
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
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/checkin/EventWithCheckInInfo.java" target="_blank" rel="noopener"><code>EventWithCheckInInfo</code></a></td>
                <td>Details about the event</td>
            </tr>
            <tr>
                <td><code>originalUrl</code></td>
                <td><code>String</code></td>
                <td>the default redirect URL</td>
            </tr>
            <tr>
                <td><code>eventId</code></td>
                <td><code>int</code></td>
                <td>ID of the Event</td>
            </tr>
            <tr>
                <td><code>organizationId</code></td>
                <td><code>int</code></td>
                <td>Organizer ID</td>
            </tr>
        </tbody>
    </table>
</div>


### Customize join URL for online events
`CUSTOM_ONLINE_JOIN_URL`

Fired **synchronously** before sending the ticket email. The purpose of this extension is to allow seamless integration with external, invitation-based virtual conference systems.

A result of type [`TicketMetadata`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/metadata/TicketMetadata.java) is expected. Return `null` to use default settings or throw an error if the link was not successful.
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
                <td><code>ticket</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Ticket.java" target="_blank" rel="noopener"><code>Ticket</code></a></td>
                <td>Details about the ticket</td>
            </tr>
            <tr>
                <td><code>additionalInfo</code></td>
                <td><code>Map&lt;String, List&lt;String&gt;&gt;</code></td>
                <td>Additional information provided by the ticket holder</td>
            </tr>
            <tr>
                <td><code>ticketMetadata</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/metadata/TicketMetadata.java" target="_blank" rel="noopener"><code>TicketMetadata</code></a></td>
                <td>Existing metadata for ticket. <strong>Might be undefined</strong></td>
            </tr>
        </tbody>
    </table>
</div>

### Customize ticket metadata
`TICKET_ASSIGNED_GENERATE_METADATA`

Fired **synchronously** before marking a ticket as "acquired". The purpose of this extension is to allow metadata customization.

A result of type [`TicketMetadata`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/metadata/TicketMetadata.java) is expected. Return `null` if you don't need to modify the current metadata.
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
                <td><code>ticket</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Ticket.java" target="_blank" rel="noopener"><code>Ticket</code></a></td>
                <td>Details about the ticket</td>
            </tr>
            <tr>
                <td><code>ticketMetadata</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/metadata/TicketMetadata.java" target="_blank" rel="noopener"><code>TicketMetadata</code></a></td>
                <td>Existing metadata for ticket.</td>
            </tr>
            <tr>
                <td><code>additionalInfo</code></td>
                <td><code>Map&lt;String, List&lt;String&gt;&gt;</code></td>
                <td>Additional information provided by the ticket holder</td>
            </tr>
            <tr>
                <td><code>attendeeResources</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/modification/AttendeeResources.java" target="_blank" rel="noopener"><code>AttendeeResources</code></a></td>
                <td>Attendee resources, such as Ticket PDF</td>
            </tr>
        </tbody>
    </table>
</div>