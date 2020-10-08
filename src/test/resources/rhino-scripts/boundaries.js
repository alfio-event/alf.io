// importPackage(Packages.java.io);
/**
 * Executes the extension.
 * @param scriptEvent
 * @returns Object
 */
function executeScript(scriptEvent) {
    var hash = new HashMap();
    var instance = hash.getClass().forName("alfio.util.LocaleUtil").newInstance();
    // java.lang.System.exit(0);
    var arr = new Array([1, 2, 3, 4]);
    for (var i = 0; i < arr.length; i++) {
        extensionLogger.logInfo(scriptEvent);
        if (arr[i] >= 3) {
            // extensionLogger.logInfo(scriptEvent);
            hash.put("England", "London");
        }
    }
    // java.lang.System.exit(0);
}