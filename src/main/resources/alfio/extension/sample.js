/**
 * The script metadata object describes whether or not your extension should be invoked asynchronously, and which events it supports
 * @returns {{ async: boolean, events: string[] }}
 */
function getScriptMetadata() {
    return {
        id: 'myExtensionIdentifier',
        displayName: 'My Extension', //mandatory: the name displayed in the configuration page
        version: 0,
        async: false,
        events: [
            // the events handled by the extension. In the admin editor, press Ctrl+Space or type a quote to list the supported values
            'INVOICE_GENERATION'
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
    var randomNumber = simpleHttpClient.get('https://jsonplaceholder.typicode.com/todos/1').getJsonBody().userId;
    log.warn('the invoice number will be: ' + randomNumber);
    return {
        invoiceNumber: randomNumber
    };
}