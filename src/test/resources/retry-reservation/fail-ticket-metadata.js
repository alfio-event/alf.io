/**
 * The script metadata object describes whether or not your extension should be invoked asynchronously, and which events it supports
 * @returns {{ async: boolean, events: string[] }}
 */
function getScriptMetadata() {
    return {
        id: 'myExtensionIdentifier',
        displayName: 'My Extension',
        version: 0, // optional
        async: false,
        events: [
            'TICKET_ASSIGNED_GENERATE_METADATA'
        ]
    };
}
/**
 * Executes the extension.
 * @param scriptEvent
 * @returns Object
 */
function executeScript(scriptEvent) {
    throw 'error during process';
}