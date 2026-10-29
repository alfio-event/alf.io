---
title: "Reservation"
linkTitle: "Reservation"
weight: 2
date: 2020-11-26
description: >
  Compatible Application Events for the "Reservation" entity
---
### Reservation confirmed
`RESERVATION_CONFIRMED`

Fired **asynchronously** once a reservation has been confirmed.
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
                <td><code>billingDetails</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/BillingDetails.java" target="_blank" rel="noopener"><code>BillingDetails</code></a></td>
                <td>Billing info for the reservation</td>
            </tr>
        </tbody>
    </table>
</div>

### Reservation cancelled
`RESERVATION_CANCELLED`

Fired **synchronously** once one or more reservations have been cancelled.
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
                <td><code>reservationIds</code></td>
                <td><code>Collection&lt;String&gt;</code></td>
                <td>Cancelled reservation IDs</td>
            </tr>
            <tr>
                <td><code>reservations</code></td>
                <td><code>List&lt;</code><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservation.java" target="_blank" rel="noopener"><code>TicketReservation</code></a><code>&gt;</code></td>
                <td>Cancelled reservations</td>
            </tr>
        </tbody>
    </table>
</div>

### Reservation expired
`RESERVATION_EXPIRED`

Fired **synchronously** once one or more reservations have expired.
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
                <td><code>reservationIds</code></td>
                <td><code>Collection&lt;String&gt;</code></td>
                <td>Cancelled reservation IDs</td>
            </tr>
            <tr>
                <td><code>reservations</code></td>
                <td><code>List&lt;</code><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservation.java" target="_blank" rel="noopener"><code>TicketReservation</code></a><code>&gt;</code></td>
                <td>Cancelled reservations</td>
            </tr>
        </tbody>
    </table>
</div>

### Reservation validation
`RESERVATION_VALIDATION`

Fired **synchronously** when a reservation needs to be validated. No result expected, use the input [`BindingResult`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/validation/BindingResult.html) to add any validation errors
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
                <td><code>reservationId</code></td>
                <td><code>String</code></td>
                <td>Reservation ID</td>
            </tr>
            <tr>
                <td><code>reservation</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservation.java" target="_blank" rel="noopener"><code>TicketReservation</code></a></td>
                <td>Reservation to validate</td>
            </tr>
            <tr>
                <td><code>form</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/controller/form/ContactAndTicketsForm.java" target="_blank" rel="noopener"><code>ContactAndTicketsForm</code></a> or <a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/controller/form/PaymentForm.java" target="_blank" rel="noopener"><code>PaymentForm</code></a></td>
                <td>Customer provided input values</td>
            </tr>
            <tr>
                <td><code>reservation</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservation.java" target="_blank" rel="noopener"><code>TicketReservation</code></a></td>
                <td>Reservation to validate</td>
            </tr>
            <tr>
                <td><code>bindingResult</code></td>
                <td><a href="https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/validation/BindingResult.html" target="_blank" rel="noopener"><code>BindingResult</code></a></td>
                <td>Binding result to register any validation errors</td>
            </tr>
        </tbody>
    </table>
</div>

### Tax ID validation
`TAX_ID_NUMBER_VALIDATION`

Fired **synchronously** when a Tax ID (VAT/GST) number has to be validated. Please note that Alf.io already supports EU VAT validation (by calling the EU VIES web service). In these cases, the TAX_ID validation will be called only as fallback. Your extension should return a failed validation result if the country is not supported. You can find an example [here](https://github.com/alfio-event/alf.io-extensions/tree/master/tax-id/IT/v1)

Expected return type is `boolean`
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
                <td><code>taxIdNumber</code></td>
                <td><code>String</code></td>
                <td>TAX ID number to validate</td>
            </tr>
            <tr>
                <td><code>countryCode</code></td>
                <td><code>String</code></td>
                <td>ISO 3166 2-letters country code</td>
            </tr>
        </tbody>
    </table>
</div>

### Invoice generation
`INVOICE_GENERATION`

Fired **synchronously** when generating an invoice. This allows for an integration with external billing systems.

Expected result is [`InvoiceGeneration`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/extension/InvoiceGeneration.java) or `null`
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
                <td><code>reservationId</code></td>
                <td><code>String</code></td>
                <td>Reservation ID</td>
            </tr>
            <tr>
                <td><code>email</code></td>
                <td><code>String</code></td>
                <td>Customer email</td>
            </tr>
            <tr>
                <td><code>customerName</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/CustomerName.java" target="_blank" rel="noopener"><code>CustomerName</code></a></td>
                <td>Customer first/last name</td>
            </tr>
            <tr>
                <td><code>userLanguage</code></td>
                <td><code>String</code></td>
                <td>ISO 639-1 2-letters language code</td>
            </tr>
            <tr>
                <td><code>billingAddress</code></td>
                <td><code>String</code></td>
                <td>multi-line billing address</td>
            </tr>
            <tr>
                <td><code>billingDetails</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/BillingDetails.java" target="_blank" rel="noopener"><code>BillingDetails</code></a></td>
                <td>Billing data</td>
            </tr>
            <tr>
                <td><code>customerReference</code></td>
                <td><code>String</code></td>
                <td>Customer reference (PO number)</td>
            </tr>
            <tr>
                <td><code>reservationCost</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TotalPrice.java" target="_blank" rel="noopener"><code>TotalPrice</code></a></td>
                <td>Reservation price details</td>
            </tr>
            <tr>
                <td><code>invoiceRequested</code></td>
                <td><code>boolean</code></td>
                <td>Wether customer has requested an invoice</td>
            </tr>
            <tr>
                <td><code>vatCountryCode</code></td>
                <td><code>String</code></td>
                <td>2-digit TAX ID Country code</td>
            </tr>
            <tr>
                <td><code>vatNr</code></td>
                <td><code>String</code></td>
                <td>TAX ID</td>
            </tr>
            <tr>
                <td><code>vatStatus</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/PriceContainer.java#L36" target="_blank" rel="noopener"><code>PriceContainer.VatStatus</code></a></td>
                <td>Tax application details</td>
            </tr>
        </tbody>
    </table>
</div>

### Credit Note generation
`CREDIT_NOTE_GENERATION`

Fired **synchronously** when generating a credit note. This allows for an integration with external billing systems.

Expected result is [`CreditNoteGeneration`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/extension/CreditNoteGeneration.java) or `null`
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
                <td><code>reservationId</code></td>
                <td><code>String</code></td>
                <td>Reservation ID</td>
            </tr>
            <tr>
                <td><code>invoiceNumber</code></td>
                <td><code>String</code></td>
                <td>Invoice Number</td>
            </tr>
            <tr>
                <td><code>organization</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Organization.java" target="_blank" rel="noopener"><code>Organization</code></a></td>
                <td>Organizer details</td>
            </tr>
        </tbody>
    </table>
</div>

### Credit Note generated
`CREDIT_NOTE_GENERATED`

Fired **asynchronously** after generating a credit note. This allows for an integration with external billing systems.
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
                <td><code>reservationId</code></td>
                <td><code>String</code></td>
                <td>Reservation ID</td>
            </tr>
            <tr>
                <td><code>reservation</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservation.java" target="_blank" rel="noopener"><code>TicketReservation</code></a></td>
                <td>Reservation details</td>
            </tr>
            <tr>
                <td><code>billingDetails</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/BillingDetails.java" target="_blank" rel="noopener"><code>BillingDetails</code></a></td>
                <td>Billing data</td>
            </tr>
            <tr>
                <td><code>reservationCost</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TotalPrice.java" target="_blank" rel="noopener"><code>TotalPrice</code></a></td>
                <td>Reservation price details</td>
            </tr>
            <tr>
                <td><code>billingDocumentId</code></td>
                <td><code>long</code></td>
                <td>Internal Billing Document ID</td>
            </tr>
        </tbody>
    </table>
</div>

### Stuck reservations
`STUCK_RESERVATIONS`

Fired **asynchronously** when one or more "stuck" reservations are detected. A reservation is considered to be "stuck" if it's expired while waiting for a confirmation from the payment provider
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
                <td><code>stuckReservationsId</code></td>
                <td><code>List&lt;String&gt;</code></td>
                <td>Stuck reservations IDs</td>
            </tr>
        </tbody>
    </table>
</div>

### Offline payments about to expire
`OFFLINE_RESERVATIONS_WILL_EXPIRE`

Fired **asynchronously** when one or more offline payments are going to expire.
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
                <td><code>reservations</code></td>
                <td><code>List&lt;</code><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservationInfo.java" target="_blank" rel="noopener"><code>TicketReservationInfo</code></a><code>&gt;</code></td>
                <td>Reservation details</td>
            </tr>
        </tbody>
    </table>
</div>

### Refund issued
`REFUND_ISSUED`

Fired **asynchronously** after a refund has been issued.
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
                <td>Reservation details</td>
            </tr>
            <tr>
                <td><code>transaction</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Transaction.java" target="_blank" rel="noopener"><code>Transaction</code></a></td>
                <td>Transaction details</td>
            </tr>
            <tr>
                <td><code>paymentInfo</code></td>
                <td><a href="https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/PaymentInformation.java" target="_blank" rel="noopener"><code>PaymentInformation</code></a></td>
                <td>Payment information</td>
            </tr>
        </tbody>
    </table>
</div>

### Dynamic discount application
`DYNAMIC_DISCOUNT_APPLICATION`

Fired **synchronously** when customer selects ticket categories in the event page. This allows you to define different discount policies based on the actual selection. 

Expected result type: [`PromoCodeDiscount`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/PromoCodeDiscount.java) or `null`
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
                <td><code>quantityByCategory</code></td>
                <td><code>Map&lt;Integer, Long&gt;</code></td>
                <td>How many tickets are being purchased for each category</td>
            </tr>
            <tr>
                <td><code>reservationId</code></td>
                <td><code>String</code></td>
                <td>Reservation ID - <strong>may be undefined</strong></td>
            </tr>
        </tbody>
    </table>
</div>
