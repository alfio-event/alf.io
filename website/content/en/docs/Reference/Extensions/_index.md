
---
title: "Extensions"
linkTitle: "Extensions"
weight: 1
date: 2020-11-26
description: >
  How to customize Alf.io processes and flows
---

Extensions are a powerful and flexible way to customize Alf.io's processes and flows. You can define [additional validations](./reference/reservation/#reservation-validation), [generate discounts on the fly](./reference/reservation/#dynamic-discount-application), interact with third party systems and more.

## Extension types

We support different kind of execution: asynchronous and synchronous

#### Asynchronous
The extension will be called in the background. They're meant to notify external systems and/or to process data without impacting on the end user.

No result is expected from the call.

If the execution fails, it will be retried with an exponential back-off time for a maximum of 36h.
If the execution still fails after 36h, it will be discarded

#### Synchronous

Synchronous extensions affects the way users interacts with your alf.io instance. A result is usually expected from the execution and it might forbid the user to continue the process (i.e. additional parameters validation).

{{% pageinfo %}}
You should design your (sync) extensions to be small, simple, and quick.
{{%/pageinfo%}}

## Alf.io extensions language

The extensions must be written in Javascript. However, we put some limitations on the functionalities of the language 
in order to prevent misuse or just to help you avoid making some mistakes. What we do is, before compiling your script, 
we verify that the code is legal. Then, also during compilation time, other checks are used to double verify the code.
Towards the end of this page you can find some sample code of a valid script.

### Limitations on loops

Loops such as `while` and `do` are not permitted. In case they are used, the script fails. Instead, you can use 
the `for`, `for/in` or `for/of` loops for any kind of iteration.

### Timeout handling

Sometimes the execution can take too long because of various reasons, therefore a timeout of 5 seconds is set for
each instruction. If it takes more than that, the script will be forcibly terminated.

### `with` statement

Usage of a `with` statement is forbidden. To avoid it, you can use a temporary variable. So, you can replace this:
```javascript
with (person) {
    console.log("Hello " + firstName + " "+ lastName);
}
```
with this:
```javascript
var p = person;
console.log("Hello " + p.firstName + " "+ p.lastName);
```
### Labeled statement

Labeled statements are rarely found, because usually function calls are used. They are also not permitted when writing 
the extensions. As mentioned above, `for`, `for/in` or `for/of` loops can be used instead.

### Functions limitations

Java methods can be called from the scripts, therefore we limit some harmful usage by applying sandboxing. Access to
`java.lang.System.exit()` and `getClass()` are disabled. In general, access to Java classes is not possible. However,
the standard objects (`Object`, `String`, `Number`, `Date`, etc.) can be used. In addition, we enable the use of
the following classes: `GSON`, [`SimpleHttpClient`](https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/extension/SimpleHttpClient.java),
 `HashMap`, [`ExtensionUtils`]( https://github.com/alfio-event/alf.io/blob/main/src/main/java/alfio/extension/ExtensionUtils.java) 
 and [`Logger`](https://logging.apache.org/log4j/2.x/log4j-api/apidocs/org/apache/logging/log4j/Logger.html).


### Function calls level limitation

Nesting more than one function call is not allowed. Below is an example of code that should ***not*** be used. 
```javascript
function executeScript(scriptEvent) {
    var a = 1;
    var b = 2;
    // first function call
    var result = addAndIncrement(a, b);
    return result;
}

function increment(x) {
    return x++;
}

function addAndIncrement(a, b) {
    // second function call from a previous function call -> exception
    return increment(a + b);
}
```
Instead, here is an example of legit function calls:
```javascript
function executeScript(scriptEvent) {
    var a = 1;
    var b = 2;
    // first function call
    var result = addAndIncrement(a, b);
    return result;
}

function increment(x) {
    return x++;
}

function addAndIncrement(a, b) {
    // no other function call -> OK
    return (a + b + 1);
}
```

### Example of a working script

After showing you what cannot be done, here is some sample code to give you an idea of how a typical script should look like: 
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
            'INVOICE_GENERATION' //, //fired on invoice generation. Returns the invoice model.
            //'TAX_ID_NUMBER_VALIDATION' //fired in case a TAX ID (VAT/GST) Number has to be formally validated
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
    console.log('hello from script with event:', scriptEvent);
    console.log('extension parameters are: ', extensionParameters);
    
    // you can log warning and errors too
    // console.warn('this is a warning related to ', executionKey);
    // console.error('uh oh, something happened');
    
    //this sample calls the https://csrng.net/ website and generates a random invoice number
    var randomNumber = simpleHttpClient.get('https://csrng.net/csrng/csrng.php?min=0&max=100').getJsonBody()[0].random;
    console.log('the invoice number will be:', randomNumber);
    return {
        invoiceNumber: randomNumber
    };
}
```
