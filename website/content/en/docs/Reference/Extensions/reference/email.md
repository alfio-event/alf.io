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
                <td><code>reservation</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservation.java" target="_blank" rel="noopener"><code>TicketReservation</code></a></td>
                <td>Details about the reservation</td>
            </tr>
            <tr>
                <td><code>purchaseContext</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/PurchaseContext.java" target="_blank" rel="noopener"><code>PurchaseContext</code></a></td>
                <td>The PurchaseContext (Event or Subscription) for which the reservation has been made</td>
            </tr>
            <tr>
                <td><code>billingData</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservationAdditionalInfo.java" target="_blank" rel="noopener"><code>TicketReservationAdditionalInfo</code></a></td>
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
                <td><code>reservation</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservation.java" target="_blank" rel="noopener"><code>TicketReservation</code></a></td>
                <td>Details about the reservation</td>
            </tr>
            <tr>
                <td><code>event</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Event.java" target="_blank" rel="noopener"><code>Event</code></a></td>
                <td>The Event for which the ticket has been confirmeds</td>
            </tr>
            <tr>
                <td><code>billingData</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservationAdditionalInfo.java" target="_blank" rel="noopener"><code>TicketReservationAdditionalInfo</code></a></td>
                <td>Billing info for the reservation</td>
            </tr>
            <tr>
                <td><code>additionalFields</code></td>
                <td><code>List&lt;</code><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketFieldValue.java" target="_blank" rel="noopener"><code>TicketFieldValue</code></a><code>&gt;</code></td>
                <td>Additional info provided for the ticket holder</td>
            </tr>
        </tbody>
    </table>
</div>
