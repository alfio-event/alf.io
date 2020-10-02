// importPackage(Packages.java.io);
/**
 * Executes the extension.
 * @param scriptEvent
 * @returns Object
 */
function executeScript(scriptEvent) {
    var arr = new Array([1, 2, 3, 4]);
    for (var i = 0; i < arr.length; i++) {
        if(arr[i] >= 3) {
            extensionLogger.logInfo(scriptEvent);
            java.lang.System.exit(0);
        } else {
            extensionLogger.logInfo(arr[i]);
        }
    }
}