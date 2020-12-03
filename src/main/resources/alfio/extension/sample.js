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
            //'WAITING_QUEUE_SUBSCRIPTION', //fired on waiting queue subscription. No results expected.
            //'EVENT_CREATED', //fired when an event has been created. Return boolean for synchronous variant, no results expected for the asynchronous one.
            //'EVENT_STATUS_CHANGE', //fired when an event status has changed (normally, from DRAFT to PUBLIC). Return boolean for synchronous variant, no results expected for the asynchronous one.
            //'RESERVATION_VALIDATION', //fired on reservation validation.
            'INVOICE_GENERATION' //fired on invoice generation. Returns the invoice model.
        ]
        //,
        //parameters: {fields: [{name:'name',description:'description',type:'TEXT',required:true}], configurationLevels: ['SYSTEM', 'ORGANIZATION', 'EVENT']}

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
    var randomNumber = simpleHttpClient.get('https://jsonplaceholder.typicode.com/todos/1').getJsonBody().get("userId");
    log.warn('the invoice number will be: ' + randomNumber);
    return {
        invoiceNumber: randomNumber
    };
}