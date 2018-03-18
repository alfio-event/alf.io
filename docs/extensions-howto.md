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
        id: 'myExtensionIdentifier', // optional: id and version will be used later as a mechanism for checking if the script has a newer version
        displayName: 'My Extension', //mandatory: the name displayed in the configuration page
        version: 0, // optional
        async: false,
        events: [
            //supported values:
            //'RESERVATION_CONFIRMED', //fired on reservation confirmation. No results expected.
            //'RESERVATION_EXPIRED', //fired when reservation(s) expired
            //'RESERVATION_CANCELLED', //fired when reservation(s) are cancelled
            //'TICKET_CANCELLED', //fired when ticket(s) (but not the entire reservation) are cancelled
            //'TICKET_ASSIGNED', //fired on ticket assignment. No results expected.
            //'TICKET_CHECKED_IN', //fired when a ticket has been checked in. No results expected.
            //'TICKET_REVERT_CHECKED_IN', //fired when a ticket has been reverted from the checked in status. No results expected.
            //'WAITING_QUEUE_SUBSCRIPTION', //fired on waiting queue subscription. No results expected.
            //'STUCK_RESERVATIONS', //fired when the system has detected stuck reservations. No results expected.
            //'OFFLINE_RESERVATIONS_WILL_EXPIRE', //fired when an offline reservation will expire. No results expected.
            //'EVENT_CREATED', //fired when an event has been created. Return boolean for synchronous variant, no results expected for the asynchronous one.
            //'EVENT_STATUS_CHANGE', //fired when an event status has changed (normally, from DRAFT to PUBLIC). Return boolean for synchronous variant, no results expected for the asynchronous one.
            'INVOICE_GENERATION' //fired on invoice generation. Returns the invoice model.
        ]
        //,
        //parameters: {fields: [{name:'name',description:'description',type:'TEXT',required:true}], configurationLevels: ['SYSTEM', 'ORGANIZATION', 'EVENT']} //parameters
    };
}

/**
 * Executes the extension.
 * @param scriptEvent
 * @returns Object
 */
function executeScript(scriptEvent) {
    log.warn('hello from script with event: ' + scriptEvent);
    log.warn('extension parameters are: ' + extensionParameters);
    //this sample calls the https://csrng.net/ website and generates a random invoice number
    var randomNumber = restTemplate.getForObject('https://csrng.net/csrng/csrng.php?min=0&max=100', Java.type('java.util.ArrayList').class)[0].random;
    log.warn('the invoice number will be: ' + randomNumber);
    return {
        invoiceNumber: randomNumber
    };
}
```

each extension is registered to one or more Application Events, and is fired as soon as the Application Event occurs.

## Scope Variables

alf.io provides some objects and properties to the script in the script scope:

* **log** Log4j logger
* **extensionLogger** a logger that write in the extension_log table. It implement the [ExtensionLogger](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/extension/ExtensionLogger.java) interface.
* **restTemplate** Spring Framework's [RestTemplate](https://docs.spring.io/spring/docs/4.3.13.RELEASE/javadoc-api/org/springframework/web/client/RestTemplate.html)
* **GSON** Google's [JSON parser/generator](http://static.javadoc.io/com.google.code.gson/gson/2.8.2/com/google/gson/Gson.html)
* **returnClass** `java.lang.Class<?>` the expected result type
* **extensionParameters** a map containing the parameters of an extension

other event-related variables are also injected in the scope

## Supported Application Events

#### RESERVATION_CONFIRMED

extensions will be invoked **asynchronously** once a reservation has been confirmed.

##### additional global variables

* **reservation**: [TicketReservation](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/TicketReservation.java)


#### RESERVATION_EXPIRED

extensions will be invoked **synchronously** once one or more reservations have expired.

##### additional global variables
* **event**: [Event](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)
* **reservationIds**: String[] - the reservation IDs

##### expected result type
boolean

#### RESERVATION_CANCELLED

extensions will be invoked **synchronously** once one or more reservations have been cancelled.

##### additional global variables
* **event**: [Event](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)
* **reservationIds**: String[] - the reservation IDs

##### expected result type
boolean

#### TICKET_CANCELLED

extension will be invoked **synchronously** once one or more tickets (but not the entire reservation at once) have been cancelled

##### additional global variables
* **event**: [Event](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)
* **ticketUUIDs**: String[] - the cencelled tickets UUIDs. **Please note** that once a ticket has been cancelled, its UUID is reset.

#### TICKET_ASSIGNED

extensions will be invoked **asynchronously** once a ticket has been assigned.

##### additional global variables

* **ticket**: [Ticket](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Ticket.java)

#### TICKET_CHECKED_IN

extensions will be invoked **asynchronously** once a ticket has been checked in.

##### additional global variables

* **event**: [Event](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)
* **ticket**: [Ticket](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Ticket.java)


#### TICKET_REVERT_CHECKED_IN

extensions will be invoked **asynchronously** once a ticket has been reverted from the checked in status.

##### additional global variables

* **event**: [Event](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)
* **ticket**: [Ticket](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Ticket.java)


#### WAITING_QUEUE_SUBSCRIBED

extensions will be invoked **asynchronously** once someone subscribes to the waiting queue.

##### additional global variables

* **waitingQueueSubscription**: [WaitingQueueSubscription](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/WaitingQueueSubscription.java)

#### INVOICE_GENERATION

extensions will be invoked **synchronously** while generating an invoice.

##### additional global variables
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


#### STUCK_RESERVATIONS

extensions will be invoked **asynchronously** when the system will detect a stuck reservation.

##### additional global variables
* **event**: [Event](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)
* **reservations**: [TicketReservationInfo](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/TicketReservationInfo.java)

#### OFFLINE_RESERVATIONS_WILL_EXPIRE

extensions will be invoked **asynchronously**

##### additional global variables
* **event**: [Event](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)
* **reservationIds** List of String - list of reservation ids

#### EVENT_CREATED

extensions will be invoked **asynchronously** and **synchronously** when an event has been created.

##### additional global variables
* **event**: [Event](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)

#### EVENT_STATUS_CHANGE

extensions will be invoked **asynchronously** and **synchronously** when an event status change.

##### additional global variables
* **event**: [Event](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/model/Event.java)
* **status**: String - possible values: 'DRAFT', 'PUBLIC', 'DISABLED'

## Methods

#### getScriptMetadata

This methods returns the actual configuration options and capabilities of the extension.
It **must** return a JSON object with the following properties:

* async *boolean*: whether or not the script should be invoked asynchronously.
* events *string[]*: list of supported events
* configuration *{(key: string): string}*: the extension configuration (WIP)

#### executeScript

The actual event handling. Return types are event-dependent. Will always receive a single parameter (scriptEvent) 
which is the event that triggered the script.