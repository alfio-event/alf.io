---
title: "Email"
linkTitle: "Email"
weight: 10
date: 2020-11-26
description: >
  Compatible Application Events for the "Email" entity
---

### Add custom text to the Reservation Confirmation email
`CONFIRMATION_MAIL_CUSTOM_TEXT`

Fired before sending a reservation confirmation email.

This is a **synchronous** call. 
A result of type [`CustomEmailText`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/extension/CustomEmailText.java) is expected. Return `null` if the generation was not successful. 
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
                <td>`reservation`</td>
                <td>[`TicketReservation`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservation.java)</td>
                <td>Details about the reservation</td>
            </tr>
            <tr>
                <td>`purchaseContext`</td>
                <td>[`PurchaseContext`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/PurchaseContext.java)</td>
                <td>The PurchaseContext (Event or Subscription) for which the reservation has been made</td>
            </tr>
            <tr>
                <td>`billingData`</td>
                <td>[`TicketReservationAdditionalInfo`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservationAdditionalInfo.java)</td>
                <td>Billing info for the reservation</td>
            </tr>
        </tbody>
    </table>
</div>


### Add custom text to the Ticket email
`TICKET_MAIL_CUSTOM_TEXT`

Fired before sending a ticket email.

This is a **synchronous** call. 
A result of type [`CustomEmailText`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/extension/CustomEmailText.java) is expected. Return `null` if the generation was not successful. 
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
                <td>`reservation`</td>
                <td>[`TicketReservation`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservation.java)</td>
                <td>Details about the reservation</td>
            </tr>
            <tr>
                <td>`event`</td>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Event.java)</td>
                <td>The Event for which the ticket has been confirmeds</td>
            </tr>
            <tr>
                <td>`billingData`</td>
                <td>[`TicketReservationAdditionalInfo`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservationAdditionalInfo.java)</td>
                <td>Billing info for the reservation</td>
            </tr>
            <tr>
                <td>`additionalFields`</td>
                <td>`List<`[`TicketFieldValue`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketFieldValue.java)`>`</td>
                <td>Additional info provided for the ticket holder</td>
            </tr>
        </tbody>
    </table>
</div>
