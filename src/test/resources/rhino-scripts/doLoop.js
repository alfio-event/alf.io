/**
 * Executes the extension.
 * @param scriptEvent
 * @returns Object
 */
function executeScript(scriptEvent) {
    var text = "";
    var i = 0;
    // the following line is expected to throw an exception, because using do loops is forbidden
    do {
        text += "The number is " + i;
        i++;
    }
    while (i < 5);
}