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
                <td>`billingDetails`</td>
                <td>[`BillingDetails`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/BillingDetails.java)</td>
                <td>Billing info for the reservation</td>
            </tr>
        </tbody>
    </table>
</div>

### Reservation cancelled
`RESERVATION_CANCELLED`

Fired **synchronously** once one or more reservations have been cancelled.
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
                <td>`reservationIds`</td>
                <td>`Collection<String>`</td>
                <td>Cancelled reservation IDs</td>
            </tr>
            <tr>
                <td>`reservations`</td>
                <td>`List<`[`TicketReservation`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservation.java)`>`</td>
                <td>Cancelled reservations</td>
            </tr>
        </tbody>
    </table>
</div>

### Reservation expired
`RESERVATION_EXPIRED`

Fired **synchronously** once one or more reservations have expired.
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
                <td>`reservationIds`</td>
                <td>`Collection<String>`</td>
                <td>Cancelled reservation IDs</td>
            </tr>
            <tr>
                <td>`reservations`</td>
                <td>`List<`[`TicketReservation`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservation.java)`>`</td>
                <td>Cancelled reservations</td>
            </tr>
        </tbody>
    </table>
</div>

### Reservation validation
`RESERVATION_VALIDATION`

Fired **synchronously** when a reservation needs to be validated. No result expected, use the input [`BindingResult`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/validation/BindingResult.html) to add any validation errors
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
                <td>`reservationId`</td>
                <td>`String`</td>
                <td>Reservation ID</td>
            </tr>
            <tr>
                <td>`reservation`</td>
                <td>[`TicketReservation`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservation.java)</td>
                <td>Reservation to validate</td>
            </tr>
            <tr>
                <td>`form`</td>
                <td>[`ContactAndTicketsForm`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/controller/form/ContactAndTicketsForm.java) or [`PaymentForm`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/controller/form/PaymentForm.java)</td>
                <td>Customer provided input values</td>
            </tr>
            <tr>
                <td>`reservation`</td>
                <td>[`TicketReservation`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservation.java)</td>
                <td>Reservation to validate</td>
            </tr>
            <tr>
                <td>`bindingResult`</td>
                <td>[`BindingResult`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/validation/BindingResult.html)</td>
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
                <td>`taxIdNumber`</td>
                <td>`String`</td>
                <td>TAX ID number to validate</td>
            </tr>
            <tr>
                <td>`countryCode`</td>
                <td>`String`</td>
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
                <td>`reservationId`</td>
                <td>`String`</td>
                <td>Reservation ID</td>
            </tr>
            <tr>
                <td>`email`</td>
                <td>`String`</td>
                <td>Customer email</td>
            </tr>
            <tr>
                <td>`customerName`</td>
                <td>[`CustomerName`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/CustomerName.java)</td>
                <td>Customer first/last name</td>
            </tr>
            <tr>
                <td>`userLanguage`</td>
                <td>`String`</td>
                <td>ISO 639-1 2-letters language code</td>
            </tr>
            <tr>
                <td>`billingAddress`</td>
                <td>`String`</td>
                <td>multi-line billing address</td>
            </tr>
            <tr>
                <td>`billingDetails`</td>
                <td>[`BillingDetails`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/BillingDetails.java)</td>
                <td>Billing data</td>
            </tr>
            <tr>
                <td>`customerReference`</td>
                <td>`String`</td>
                <td>Customer reference (PO number)</td>
            </tr>
            <tr>
                <td>`reservationCost`</td>
                <td>[`TotalPrice`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TotalPrice.java)</td>
                <td>Reservation price details</td>
            </tr>
            <tr>
                <td>`invoiceRequested`</td>
                <td>`boolean`</td>
                <td>Wether customer has requested an invoice</td>
            </tr>
            <tr>
                <td>`vatCountryCode`</td>
                <td>`String`</td>
                <td>2-digit TAX ID Country code</td>
            </tr>
            <tr>
                <td>`vatNr`</td>
                <td>`String`</td>
                <td>TAX ID</td>
            </tr>
            <tr>
                <td>`vatStatus`</td>
                <td>[`PriceContainer.VatStatus`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/PriceContainer.java#L36)</td>
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
                <td>`reservationId`</td>
                <td>`String`</td>
                <td>Reservation ID</td>
            </tr>
            <tr>
                <td>`invoiceNumber`</td>
                <td>`String`</td>
                <td>Invoice Number</td>
            </tr>
            <tr>
                <td>`organization`</td>
                <td>[`Organization`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Organization.java)</td>
                <td>Organizer details</td>
            </tr>
        </tbody>
    </table>
</div>

### Credit Note generated
`CREDIT_NOTE_GENERATED`

Fired **asynchronously** after generating a credit note. This allows for an integration with external billing systems.
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
                <td>`reservationId`</td>
                <td>`String`</td>
                <td>Reservation ID</td>
            </tr>
            <tr>
                <td>`reservation`</td>
                <td>[`TicketReservation`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservation.java)</td>
                <td>Reservation details</td>
            </tr>
            <tr>
                <td>`billingDetails`</td>
                <td>[`BillingDetails`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/BillingDetails.java)</td>
                <td>Billing data</td>
            </tr>
            <tr>
                <td>`reservationCost`</td>
                <td>[`TotalPrice`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TotalPrice.java)</td>
                <td>Reservation price details</td>
            </tr>
            <tr>
                <td>`billingDocumentId`</td>
                <td>`long`</td>
                <td>Internal Billing Document ID</td>
            </tr>
        </tbody>
    </table>
</div>

### Stuck reservations
`STUCK_RESERVATIONS`

Fired **asynchronously** when one or more "stuck" reservations are detected. A reservation is considered to be "stuck" if it's expired while waiting for a confirmation from the payment provider
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
                <td>`stuckReservationsId`</td>
                <td>`List<String>`</td>
                <td>Stuck reservations IDs</td>
            </tr>
        </tbody>
    </table>
</div>

### Offline payments about to expire
`OFFLINE_RESERVATIONS_WILL_EXPIRE`

Fired **asynchronously** when one or more offline payments are going to expire.
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
                <td>`reservations`</td>
                <td>`List<`[`TicketReservationInfo`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/TicketReservationInfo.java)`>`</td>
                <td>Reservation details</td>
            </tr>
        </tbody>
    </table>
</div>

### Refund issued
`REFUND_ISSUED`

Fired **asynchronously** after a refund has been issued.
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
                <td>Reservation details</td>
            </tr>
            <tr>
                <td>`transaction`</td>
                <td>[`Transaction`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/Transaction.java)</td>
                <td>Transaction details</td>
            </tr>
            <tr>
                <td>`paymentInfo`</td>
                <td>[`PaymentInformation`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/model/PaymentInformation.java)</td>
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
                <td>`quantityByCategory`</td>
                <td>`Map<Integer, Long>`</td>
                <td>How many tickets are being purchased for each category</td>
            </tr>
            <tr>
                <td>`reservationId`</td>
                <td>`String`</td>
                <td>Reservation ID - **may be undefined**</td>
            </tr>
        </tbody>
    </table>
</div>
