/**
 * Executes the extension.
 * @param scriptEvent
 * @returns Object
 */
function executeScript(scriptEvent) {
    var itemsPassed = 0;
    var i, j;

    top:
        for (i = 0; i < items.length; i++) {
            for (j = 0; j < tests.length; j++) {
                if (!tests[j].pass(items[i])) {
                    continue top;
                }
            }

            itemsPassed++;
        }
}