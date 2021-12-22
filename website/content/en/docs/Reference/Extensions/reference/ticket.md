---
title: "Ticket"
linkTitle: "Ticket"
weight: 3
date: 2020-11-26
description: >
  Compatible Application Events for the "Ticket" entity
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
                <td rowspan="2">TICKET_ASSIGNED</td>
                <td>[`Ticket`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Ticket.java)</td>
                <td>`ticket`</td>
                <td rowspan="2">`void`</td>
                <td rowspan="2">Extensions will be invoked asynchronously once a ticket has been assigned.</td>
            </tr>
            <tr>
                <td>`Map<String, List&lt;String>>`</td>
                <td>`additionalInfo`</td>
            </tr>
            <tr>
                <td rowspan="2">TICKET_CANCELLED</td>
                <td>`Collection<String>`</td>
                <td>`ticketUUIDs`</td>
                <td rowspan="2">`void`</td>
                <td rowspan="2">Extension will be invoked synchronously once one or more tickets (but not the entire reservation at once) have been cancelled. Once a ticket has been cancelled, its UUID is reset.</td>
            </tr>
            <tr>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
                <td>`event`</td>
            </tr>
            <tr>
                <td>TICKET_CHECKED_IN</td>
                <td>[`Ticket`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Ticket.java)</td>
                <td>`ticket`</td>
                <td>`void`</td>
                <td>Extensions will be invoked asynchronously once a ticket has been checked in.</td>
            </tr>
            <tr>
                <td>TICKET_REVERT_CHECKED_IN</td>
                <td>[`Ticket`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Ticket.java)</td>
                <td>`ticket`</td>
                <td>`void`</td>
                <td>Extensions will be invoked asynchronously once a ticket has been reverted from the checked in status.</td>
            </tr>
            <tr>
                <td rowspan="5">ONLINE_CHECK_IN_REDIRECT</td>
                <td>[`Ticket`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Ticket.java)</td>
                <td>`ticket`</td>
                <td rowspan="5">`String` (a valid URL) or `null`</td>
                <td rowspan="5">Extensions will be invoked when an online check in happens. The URL returned by the extension will be used to redirect the ticket holder to the target platform/page</td>
            </tr>
            <tr>
                <td>[`EventWithCheckInInfo`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/checkin/EventWithCheckInInfo.java)</td>
                <td>`event`</td>
            </tr>
            <tr>
                <td>`String`</td>
                <td>`originalUrl`</td>
            </tr>
            <tr>
                <td>`int`</td>
                <td>`eventId`</td>
            </tr>
            <tr>
                <td>`int`</td>
                <td>`organizationId`</td>
            </tr>
        </tbody>
    </table>
</div>