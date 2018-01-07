# How to write an extension

Extensions allows you to link alf.io with your existing tools, such as:

* Billing/Accounting systems
* CRMs
* Additional Email marketing services (Mailjet, ...)
* Custom notifications (Slack, Telegram, etc.)

## how it works

Extensions can be added and modified only by the Administrator. 
For security and stability reasons, it is not possible to do that with less privileged users.

Each extension consists of a JavaScript script, you can find a sample below:

```javascript
/**
 * The script metadata object describes whether or not your extension should be invoked asynchronously, and which events it supports
 * @returns {{ async: boolean, events: string[] }}
 */
function getScriptMetadata() {
    return {
        async: false,
        events: [
            //supported values:
            //'RESERVATION_CONFIRMED', //fired on reservation confirmation. No results expected.
            //'RESERVATION_EXPIRED', //fired when reservation(s) expired
            //'RESERVATION_CANCELLED', //fired when reservation(s) are cancelled
            //'TICKET_ASSIGNED', //fired on ticket assignment. No results expected.
            //'WAITING_QUEUE_SUBSCRIPTION', //fired on waiting queue subscription. No results expected.
            'INVOICE_GENERATION' //fired on invoice generation. Returns the invoice model.
        ]
    };
}

/**
 * Executes the extension.
 * @param scriptEvent
 * @returns Object
 */
function executeScript(scriptEvent) {
    log.warn('hello from script with event: ' + scriptEvent);
    //this sample calls the https://csrng.net/ website and generates a random invoice number
    var randomNumber = restTemplate.getForObject('https://csrng.net/csrng/csrng.php?min=0&max=100', Java.type('java.util.ArrayList').class)[0].random;
    log.warn('the invoice number will be ' + randomNumber)
    return {
        invoiceNumber: randomNumber
    };
}
```

each extension is registered to one or more Application Events, and is fired as soon as the Application Event occurs.

## Scope Variables

alf.io provides some objects and properties to the script in the script scope:

* **log** Log4j logger
* **restTemplate** Spring Framework's [RestTemplate](https://docs.spring.io/spring/docs/4.3.13.RELEASE/javadoc-api/org/springframework/web/client/RestTemplate.html)
* **GSON** Google's [JSON parser/generator](http://static.javadoc.io/com.google.code.gson/gson/2.8.2/com/google/gson/Gson.html)
* **returnClass** `java.lang.Class<?>` the expected result type

other event-related variables are also injected in the scope

## Supported Application Events

#### RESERVATION_CONFIRMED

extensions will be invoked **asynchronously** once a reservation has been confirmed.

##### params

* **reservation**: [TicketReservation](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/TicketReservation.java)


#### RESERVATION_EXPIRED

extensions will be invoked **synchronously** once one or more reservations have expired.

##### params
* **event**: [Event](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)
* **reservationIds**: String[] - the reservation IDs

##### expected result type
boolean

#### RESERVATION_CANCELLED

extensions will be invoked **synchronously** once one or more reservations have been cancelled.

##### params
* **event**: [Event](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)
* **reservationIds**: String[] - the reservation IDs

##### expected result type
boolean

#### TICKET_ASSIGNED

extensions will be invoked **asynchronously** once a ticket has been assigned.

##### params

* **ticket**: [Ticket](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Ticket.java)

#### WAITING_QUEUE_SUBSCRIBED

extensions will be invoked **asynchronously** once someone subscribes to the waiting queue.

##### params

* **waitingQueueSubscription**: [WaitingQueueSubscription](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/WaitingQueueSubscription.java)

#### INVOICE_GENERATION

extensions will be invoked **synchronously** while generating an invoice.

##### params
* **event**: [Event](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)
* **reservationId**: String - the reservation ID
* **email**: String - contact email
* **customerName**: String
* **userLanguage**: String - ISO 639-1 2-letters language code
* **billingAddress**: String - the billing Address
* **reservationCost**: [TotalPrice](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/TotalPrice.java) 
* **invoiceRequested**: boolean - whether or not the user has requested an invoice or just a receipt
* **vatCountryCode**: String - the EU country of business of the customer, if any
* **vatNr**: String - Customer's VAT Number
* **vatStatus**: [VatStatus](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/PriceContainer.java#L37), see [#278](https://github.com/alfio-event/alf.io/issues/278)

##### expected result type

[InvoiceGeneration](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/extension/InvoiceGeneration.java) - The invoice content, currently limited to the invoice number.

## Methods

#### getScriptMetadata

This methods returns the actual configuration options and capabilities of the extension.
It **must** return a JSON object with the following properties:

* async *boolean*: whether or not the script should be invoked asynchronously.
* events *string[]*: list of supported events
* configuration *{(key: string): string}*: the extension configuration (WIP)

#### executeScript

the actual event handling. Parameters and return types are event-dependent.