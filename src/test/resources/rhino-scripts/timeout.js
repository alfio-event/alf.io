/**
 * Executes the extension.
 * @param scriptEvent
 * @returns Object
 */
function executeScript(scriptEvent) {
    var i = 0;
    // the following loop should cause an exception after a timeout of 5 seconds
    try {
        while (true) {
            i++;
        }
    } catch (e) {

    }
}