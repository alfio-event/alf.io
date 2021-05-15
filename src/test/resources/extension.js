/**
 * The script metadata object describes whether or not your extension should be invoked asynchronously, and which events it supports
 * @returns {{ async: boolean, events: string[] }}
 */
function getScriptMetadata() {
    return {
        id: 'myExtensionIdentifier', // optional: id and version will be used later as a mechanism for checking if the script has a newer version
        displayName: 'My Extension', //mandatory: the name displayed in the configuration page
        version: 0, // optional
        async: placeHolder,
        events: [
            EVENTS
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
    extensionLogger.logInfo(scriptEvent);//logs into the extension_log table
    var map = {
        test: 'rhino js string',
        name: scriptEvent
    };
    // test JSON stringify/parse
    var string = JSON.stringify(map);
    log.warn(string);
    var parsed = JSON.parse(string, function() {});
    if (scriptEvent === 'EVENT_CREATED') {
        console.log('created event. Here some debug info', parsed.test, parsed.name, 'custom string', map);
    }
    if (scriptEvent === 'INVOICE_GENERATION') {
        return {
            invoiceNumber: 'blabla'
        };
    }
    return null;
}
