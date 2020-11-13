/**
 * Executes the extension.
 * @param scriptEvent
 * @returns Object
 */
function executeScript(scriptEvent) {
    var a, x, y;
    var r = 10;

    // the following line is expected to throw an exception, because using with statements is forbidden
    with (Math) {
        a = PI * r * r;
        x = r * cos(PI);
        y = r * sin(PI / 2);
    }
}