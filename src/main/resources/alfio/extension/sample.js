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