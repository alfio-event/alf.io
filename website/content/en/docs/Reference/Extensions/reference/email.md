---
title: "Email"
linkTitle: "Email"
weight: 10
date: 2020-11-26
description: >
  Compatible Application Events for the "Email" entity
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
                <td rowspan="3">CONFIRMATION_MAIL_CUSTOM_TEXT</td>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
                <td>`event`</td>
                <td rowspan="3">[`CustomEmailText`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/extension/CustomEmailText.java) or `null`</td>
                <td rowspan="3">Extensions will be invoked **synchronously** to get custom text for the Reservation Confirmation email.</td>
            </tr>
            <tr>
                <td>[`TicketReservation`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/TicketReservation.java)</td>
                <td>`reservation`</td>
            </tr>
            <tr>
                <td>[`TicketReservationAdditionalInfo`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/TicketReservationAdditionalInfo.java)</td>
                <td>`additionalInfo`</td>
            </tr>
            <tr>
                <td rowspan="4">TICKET_MAIL_CUSTOM_TEXT</td>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
                <td>`event`</td>
                <td rowspan="4">[`CustomEmailText`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/extension/CustomEmailText.java) or `null`</td>
                <td rowspan="4">Extensions will be invoked **synchronously** to get custom text for the ticket email.</td>
            </tr>
            <tr>
                <td>[`TicketReservation`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/TicketReservation.java)</td>
                <td>`reservation`</td>
            </tr>
            <tr>
                <td>[`TicketReservationAdditionalInfo`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/TicketReservationAdditionalInfo.java)</td>
                <td>`additionalInfo`</td>
            </tr>
            <tr>
                <td>`List<`[`TicketFieldValue`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/TicketFieldValue.java)`>`</td>
                <td>`fields`</td>
            </tr>
        </tbody>
    </table>
</div>