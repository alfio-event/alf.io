/**
 * Executes the extension.
 * @param scriptEvent
 * @returns Object
 */
function executeScript(scriptEvent) {
    var hash = new HashMap();
    // the following line is expected to throw an exception, because using System.exit is forbidden
    java.lang.System.exit(0);
}