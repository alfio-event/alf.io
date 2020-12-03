/**
 * Executes the extension.
 * @param scriptEvent
 * @returns Object
 */
function executeScript(scriptEvent) {
    log.warn('hello from script with event: ' + scriptEvent);
    extensionLogger.logInfo(scriptEvent);//logs into the extension_log table
    if(scriptEvent === 'INVOICE_GENERATION') {
        return {
            invoiceNumber: 'blabla'
        };
    }
    return null;
}
//simulate execution from within alf.io
// GSON.fromJson(JSON.stringify(executeScript(extensionEvent)), returnClass);
