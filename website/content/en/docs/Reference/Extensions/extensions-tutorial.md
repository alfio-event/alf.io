---
title: "Extensions tutorial"
linkTitle: "Extensions tutorial"
weight: 2
date: 2020-11-26
description: >
  Tutorial on how to use the extensions
---

# Alf.io extensions

The official repository for the extensions can be found [here](https://github.com/alfio-event/alf.io-extensions).

# How to write an extension

Extensions allow you to link Alf.io with your existing tools, such as:

* Billing/Accounting systems
* CRMs
* Additional Email marketing services (Mailjet, ...)
* Custom notifications (Slack, Telegram, etc.)

## How it works

Extensions can be added and modified by each user. However, some limitations are applied that can be found
[here](https://alf.io/docs/reference/extensions/introduction/#alf-io-extensions-language).

Each extension consists of a JavaScript script and is registered to one or more Application Events, 
and is fired as soon as the Application Event occurs.

You can find some sample code in the [introduction](https://alf.io/docs/reference/extensions/introduction/#example-of-a-working-script) page.

## Scope Variables

Alf.io provides some objects and properties to the script in the script scope:
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
                <td>`log`</td>
                <td>`Log4j`</td>
                <td>Logging utility</td>
            </tr>
            <tr>
                <td>`extensionLogger`</td>
                <td>[`ExtensionLogger`] ( https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/extension/ExtensionLogger.java)</td>
                <td>A logger that writes in the extension_log table.</td>
            </tr>
            <tr>
                <td>`simpleHttpClient`</td>
                <td>[`SimpleHttpClient`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/extension/SimpleHttpClient.java)</td>
                <td>A simplified version created by Alf.io for calling external services</td>
            </tr>
            <tr>
                <td>`httpClient`</td>
                <td>[`OkHttpClient`] ( http://square.github.io/okhttp/) TO CHECK</td>
                <td>A more powerful version of the above implementation of [`SimpleHttpClient`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/extension/SimpleHttpClient.java) </td>
            </tr>
            <tr>
                <td>`GSON`</td>
                <td>[`GSON`](https://github.com/google/gson)</td>
                <td>JSON parser/generator</td>
            </tr>
            <tr>
                <td>`returnClass`</td>
                <td>`java.lang.Class<?>`</td>
                <td>The return class of the methods</td>
            </tr>
            <tr>
                <td>`extensionParameters`</td>
                <td>`Map<String, Object>`</td>
                <td>Defined parameters as in the script metadata</td>
            </tr>
            <tr>
                <td>`event`</td>
                <td>[`Event`]( https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
                <td>Alf.io's implementation of a general event</td>
            </tr>
            <tr>
                <td>`eventId`</td>
                <td>`int`</td>
                <td>The id of the event</td>
            </tr>
            <tr>
                <td>`organizationId`</td>
                <td>`int`</td>
                <td>The id of the organization that organized the event </td>
            </tr>
            <tr>
                <td>`Utils`</td>
                <td>[`ExtensionUtils`]( https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/extension/ExtensionUtils.java)</td>
                <td>A collection of utilities</td>
            </tr>
        </tbody>
    </table>
</div>
       
Other event-related variables are also injected in the scope.
## Methods

#### `getScriptMetadata`

This method returns the actual configuration options and capabilities of the extension.
It **must** return a `JSON` object with the following properties:

* async `boolean`: whether or not the script should be invoked asynchronously.
* events `string[]`: list of supported events
* configuration {(key: `string`): `string`}: the extension configuration (WIP)

#### `executeScript`

The actual event handling. Return types are event-dependent. Will always receive a single parameter (`scriptEvent`) 
which is the event that triggered the script.

## Supported Application Events

Below is a table of all the possible Application events, with some additional global variables, expected result type and a short explanation.
<div class="table-responsive">
    <table class="table table-sm table-striped">
        <thead>
        <tr>
            <th rowspan="2">Event</th>
            <th colspan="2" >Additional global variables</th>
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
            <td rowspan="2">TICKET_ASSIGNED</td>
            <td>[`Ticket`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Ticket.java)</td>
            <td>`ticket`</td>
            <td rowspan="2">`void`</td>
            <td rowspan="2">Extensions will be invoked asynchronously once a ticket has been assigned.</td>
        </tr>
        <tr>
            <td>`Map<String, List<String>>`</td>
            <td>`additionalInfo`</td>
        </tr>
        <tr>
            <td>WAITING_QUEUE_SUBSCRIBED</td>
            <td>[`WaitingQueueSubscription`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/WaitingQueueSubscription.java)</td>
            <td>`waitingQueueSubscription`</td>
            <td>`void`</td>
            <td>Extensions will be invoked asynchronously once someone subscribes to the waiting queue.</td>
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
            <td rowspan="4">EVENT_METADATA_UPDATE</td>
            <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
            <td>`event`</td>
            <td rowspan="4">[`AlfioMetadata`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/metadata/AlfioMetadata.java) </td>
            <td rowspan="4">Extensions will be invoked synchronously when metadata needs to be updated.</td>
        </tr>
        <tr>
            <td>[`AlfioMetadata`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/metadata/AlfioMetadata.java)</td>
            <td>`metadata`</td>
        </tr>
        <tr>
            <td>[`Organization`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/user/Organization.java)</td>
            <td>`organization`</td>
        </tr>
        <tr>
            <td>[`MaybeConfiguration`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/manager/system/ConfigurationManager.java)</td>
            <td>`baseUrl`</td>
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
            <td>EVENT_CREATED</td>
            <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
            <td>`event`</td>
            <td>`void`</td>
            <td>Extensions will be invoked asynchronously and synchronously when an event has been created.</td>
        </tr>
        <tr>
            <td rowspan="2">EVENT_STATUS_CHANGE</td>
            <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
            <td>`event`</td>
            <td rowspan="2">`void`</td>
            <td rowspan="2">Extensions will be invoked asynchronously and synchronously when an event status changes.</td>
        </tr>
        <tr>
            <td>`Event.Status`</td>
            <td>`status`: possible values are ‘DRAFT’, ‘PUBLIC’ and ‘DISABLED’</td>
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
            <td rowspan="3">PDF_GENERATION</td>
            <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
            <td>`event`</td>
            <td rowspan="3">`boolean`</td>
            <td rowspan="3">Extensions will be invoked synchronously when there is a PDF transformation. A `boolean` is returned to indicate if it was successful or not.</td>
        </tr>
        <tr>
            <td>`String`</td>
            <td>`html`</td>
        </tr>
        <tr>
            <td>`OutputStream`</td>
            <td>`outputStream`</td>
        </tr>
        <tr>
            <td rowspan="2">OAUTH2_STATE_GENERATION</td>
            <td>`int`</td>
            <td>`organizationId`</td>
            <td rowspan="2">`String` or `null`</td>
            <td rowspan="2">Extensions will be invoked when an OAuth needs to be generated to a state parameter.</td>
        </tr>
        <tr>
            <td>[`MaybeConfiguration`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/manager/system/ConfigurationManager.java)</td>
            <td>`baseUrl`</td>
        </tr>
        <tr>
            <td rowspan="3">CONFIRMATION_MAIL_CUSTOM_TEXT</td>
            <td>[`Event`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)</td>
            <td>`event`</td>
            <td rowspan="3">[`CustomEmailText`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/extension/CustomEmailText.java) or `null`</td>
            <td rowspan="3">Extensions will be invoked synchronously when a reservation email custom text is made.</td>
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
            <td rowspan="4">Extensions will be invoked synchronously when a ticket email custom text is made.</td>
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
        <tr>
            <td rowspan="5">ONLINE_CHECK_IN_REDIRECT</td>
            <td>[`Ticket`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Ticket.java)</td>
            <td>`ticket`</td>
            <td rowspan="5">`String` or `null`</td>
            <td rowspan="5">Extensions will be invoked when an online check in happens.</td>
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