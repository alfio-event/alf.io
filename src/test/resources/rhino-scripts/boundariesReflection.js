/**
 * Executes the extension.
 * @param scriptEvent
 * @returns Object
 */
function executeScript(scriptEvent) {
    var hash = new HashMap();
    // the following line is expected to throw an exception, because using getClass method is forbidden
    var instance = hash.getClass().forName("alfio.util.LocaleUtil").newInstance();
    extensionLogger.logInfo("test");
}