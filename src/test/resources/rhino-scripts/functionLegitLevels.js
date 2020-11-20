/**
 * Executes the extension.
 * @param scriptEvent
 * @returns Object
 */
function executeScript(scriptEvent) {
    var a = 1;
    var b = 2;
    var result = addAndIncrement(a, b);
    return result;
}

function increment(x) {
    return x++;
}

function addAndIncrement(a, b) {
    return (a + b + 1);
}