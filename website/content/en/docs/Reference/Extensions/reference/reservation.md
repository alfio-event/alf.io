---
title: "Reservation"
linkTitle: "Reservation"
weight: 2
date: 2020-11-26
description: >
  Compatible Application Events for the "Reservation" entity
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
                <td rowspan="2">RESERVATION_CONFIRMED</td>
                <td>[`TicketReservation`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/TicketReservation.java)</td>
                <td>`ticketReservation`</td>
                <td rowspan="2">`void`</td>
                <td rowspan="2">Extensions will be invoked **asynchronously** once a reservation has been confirmed.</td>
            </tr>
            <tr>
                <td>[`BillingDetails`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/BillingDetails.java)</td>
                <td>`billingDetails`</td>
            </tr>
            <tr>
                <td rowspan="2">RESERVATION_CANCELLED</td>
                <td>`Collection<String>`</td>
                <td>`reservationIdsToRemove`</td>
                <td rowspan="2">`void`</td>
                <td rowspan="2">Extensions will be invoked synchronously once one or more reservations have expired.</td>
            </tr>
            <tr>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
                <td>`event`</td>
            </tr>
            <tr>
                <td rowspan="2">RESERVATION_CREDIT_NOTE_ISSUED</td>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
                <td>`event`</td>
                <td rowspan="2">`void`</td>
                <td rowspan="2">Extensions will be invoked synchronously when the reservations credit note is issued for the event.</td>
            </tr>
            <tr>
                <td>`List<String>`</td>
                <td>`reservationIds`</td>
            </tr>
            <tr>
                <td rowspan="2">RESERVATION_EXPIRED</td>
                <td>`Collection<String>`</td>
                <td>`reservationIdsToRemove`</td>
                <td rowspan="2">`void`</td>
                <td rowspan="2">Extensions will be invoked synchronously once one or more reservations have expired.</td>
            </tr>
            <tr>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
                <td>`event`</td>
            </tr>
            <tr>
                <td rowspan="12">INVOICE_GENERATION</td>
                <td>`String`</td>
                <td>`reservationId`</td>
                <td rowspan="12">`InvoiceGeneration` or `null`</td>
                <td rowspan="12">Extensions will be invoked synchronously while generating an invoice.</td>
            </tr>
            <tr>
                <td>`String`</td>
                <td>`email`</td>
            </tr>
            <tr>
                <td>`String`</td>
                <td>`email`</td>
            </tr>
            <tr>
                <td>`String`</td>
                <td>`customerName`</td>
            </tr>
            <tr>
                <td>`String`</td>
                <td>`userLanguage`: ISO 639-1 2-letters language code</td>
            </tr>
            <tr>
                <td>`String`</td>
                <td>`billingAddress`</td>
            </tr>
            <tr>
                <td>`String`</td>
                <td>`customerReference`</td>
            </tr>
            <tr>
                <td>[`TotalPrice`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/TotalPrice.java)</td>
                <td>`reservationCost`</td>
            </tr>
            <tr>
                <td>`boolean`</td>
                <td>`invoiceRequested`: whether or not the user has requested an invoice or just a receipt</td>
            </tr>
            <tr>
                <td>`String`</td>
                <td>`vatCountryCode`: the EU country of business of the customer, if any</td>
            </tr>
            <tr>
                <td>`String`</td>
                <td>`vatNr`</td>
            </tr>
            <tr>
                <td>[`VatStatus`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/PriceContainer.java#L37) </td>
                <td>`vatStatus`: see [#278](https://github.com/alfio-event/alf.io/issues/278)</td>
            </tr>
            <tr>
                <td rowspan="2">TAX_ID_NUMBER_VALIDATION</td>
                <td>`String`</td>
                <td>`countryCode`</td>
                <td rowspan="2">`boolean`</td>
                <td rowspan="2">Extensions will be invoked synchronously when a Tax ID (VAT/GST) number has to be validated. Please note that Alf.io already supports EU VAT validation (by calling the EU VIES web service). In these cases, the TAX_ID validation will be called only as fallback. Your extension should return a failed validation result if the country is not supported.</td>
            </tr>
            <tr>
                <td>`String`</td>
                <td>`taxIdNumber`</td>
            </tr>
            <tr>
                <td rowspan="6">RESERVATION_VALIDATION</td>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
                <td>`event`</td>
                <td rowspan="6">`void`</td>
                <td rowspan="6">Extensions will be invoked synchronously when a reservation needs to be validated.</td>
            </tr>
            <tr>
                <td>`String`</td>
                <td>`reservationId`</td>
            </tr>
            <tr>
                <td>[`TicketReservation`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/TicketReservation.java)</td>
                <td>`reservation`</td>
            </tr>
            <tr>
                <td>`Object`</td>
                <td>`clientForm`</td>
            </tr>
            <tr>
                <td>`NamedParameterJdbcTemplate`</td>
                <td>`jdbcTemplate`</td>
            </tr>
            <tr>
                <td>`BindingResult`</td>
                <td>`bindingResult`</td>
            </tr>
            <tr>
                <td rowspan="2">STUCK_RESERVATIONS</td>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
                <td>`event`</td>
                <td rowspan="2">`void`</td>
                <td rowspan="2">Extensions will be invoked asynchronously when the system detects a stuck reservation.</td>
            </tr>
            <tr>
                <td>`List<String>`</td>
                <td>`stuckReservationsId`</td>
            </tr>
            <tr>
                <td rowspan="2">OFFLINE_RESERVATIONS_WILL_EXPIRE</td>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
                <td>`event`</td>
                <td rowspan="2">`void`</td>
                <td rowspan="2">Extensions will be invoked asynchronously when an offline reservation will expire.</td>
            </tr>
            <tr>
                <td>`List<`[`TicketReservationInfo`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/TicketReservationInfo.java)`>`</td>
                <td>`reservations`</td>
            </tr>
            <tr>
                <td rowspan="3">REFUND_ISSUED</td>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
                <td>`event`</td>
                <td rowspan="3">`void`</td>
                <td rowspan="3">Extensions will be invoked asynchronously once a refund needs to be made.</td>
            </tr>
            <tr>
                <td>[`TicketReservation`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/TicketReservation.java)</td>
                <td>`reservation`</td>
            </tr>
            <tr>
                <td>[`TransactionAndPaymentInfo`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/TransactionAndPaymentInfo.java)</td>
                <td>`info`</td>
            </tr>
            <tr>
                <td rowspan="3">DYNAMIC_DISCOUNT_APPLICATION</td>
                <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
                <td>`event`</td>
                <td rowspan="3">[`PromoCodeDiscount`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/PromoCodeDiscount.java) or `null` </td>
                <td rowspan="3">Extensions will be invoked synchronously when a discount needs to be applied.</td>
            </tr>
            <tr>
                <td>`Map<Integer, Long>`</td>
                <td>`quantityByCategory`</td>
            </tr>
            <tr>
                <td>`String`</td>
                <td>`reservationId`</td>
            </tr>
        </tbody>
    </table>
</div>