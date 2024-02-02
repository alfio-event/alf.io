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
                <td>`ticket`</td>
                <td>[`Ticket`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Ticket.java)</td>
                <td>Details about the ticket</td>
            </tr>
            <tr>
                <td>`additionalInfo`</td>
                <td>`Map<String, List<String>>`</td>
                <td>Additional information provided by the ticket holder</td>
            </tr>
        </tbody>
    </table>
</div>

### Ticket cancelled
`TICKET_CANCELLED`

Fired **synchronously** once one or more tickets (but not the entire reservation at once) have been cancelled.
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
                <td>`ticket`</td>
                <td>[`Ticket`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Ticket.java)</td>
                <td>Details about the ticket</td>
            </tr>
            <tr>
                <td>`additionalInfo`</td>
                <td>`Map<String, List<String>>`</td>
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
                <td>`ticket`</td>
                <td>[`Ticket`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Ticket.java)</td>
                <td>Details about the ticket</td>
            </tr>
        </tbody>
    </table>
</div>

### Ticket check-in reverted
`TICKET_REVERT_CHECKED_IN`

Fired **asynchronously** once the ticket checked-in status has been reverted.
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
                <td>`ticket`</td>
                <td>[`Ticket`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Ticket.java)</td>
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
                <td>[`EventWithCheckInInfo`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/checkin/EventWithCheckInInfo.java)</td>
                <td>Details about the event</td>
            </tr>
            <tr>
                <td>`originalUrl`</td>
                <td>`String`</td>
                <td>the default redirect URL</td>
            </tr>
            <tr>
                <td>`eventId`</td>
                <td>`int`</td>
                <td>ID of the Event</td>
            </tr>
            <tr>
                <td>`organizationId`</td>
                <td>`int`</td>
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
                <td>`ticket`</td>
                <td>[`Ticket`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Ticket.java)</td>
                <td>Details about the ticket</td>
            </tr>
            <tr>
                <td>`additionalInfo`</td>
                <td>`Map<String, List<String>>`</td>
                <td>Additional information provided by the ticket holder</td>
            </tr>
            <tr>
                <td>`ticketMetadata`</td>
                <td>[`TicketMetadata`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/metadata/TicketMetadata.java)</td>
                <td>Existing metadata for ticket. **Might be undefined**</td>
            </tr>
        </tbody>
    </table>
</div>

### Customize ticket metadata
`TICKET_ASSIGNED_GENERATE_METADATA`

Fired **synchronously** before marking a ticket as "acquired". The purpose of this extension is to allow metadata customization.

A result of type [`TicketMetadata`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/metadata/TicketMetadata.java) is expected. Return `null` if you don't need to modify the current metadata.
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
                <td>`ticket`</td>
                <td>[`Ticket`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Ticket.java)</td>
                <td>Details about the ticket</td>
            </tr>
            <tr>
                <td>`ticketMetadata`</td>
                <td>[`TicketMetadata`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/metadata/TicketMetadata.java)</td>
                <td>Existing metadata for ticket.</td>
            </tr>
            <tr>
                <td>`additionalInfo`</td>
                <td>`Map<String, List<String>>`</td>
                <td>Additional information provided by the ticket holder</td>
            </tr>
        </tbody>
    </table>
</div>