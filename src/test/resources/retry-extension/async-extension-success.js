/**
 * The script metadata object describes whether or not your extension should be invoked asynchronously, and which events it supports
 * @returns {{ async: boolean, events: string[] }}
 */
function getScriptMetadata() {
    return {
        id: 'myExtensionIdentifier', // optional: id and version will be used later as a mechanism for checking if the script has a newer version
        displayName: 'My Extension', //mandatory: the name displayed in the configuration page
        version: 0, // optional
        async: true,
        events: [
            'EVENT_CREATED'
        ]
        //,
        //parameters: {fields: [{name:'name',description:'description',type:'TEXT',required:true}], configurationLevels: ['SYSTEM', 'ORGANIZATION', 'EVENT']}

    };
}
/**
 * Executes the extension.
 * @param scriptEvent
 * @returns Object
 */
function executeScript(scriptEvent) {
    if (!eventId) {
        throw 'Event ID is not defined';
    }
    console.log('eventId is defined', eventId);
    console.log('event format:', event.format, 'begin', event.begin, 'end', event.end, 'displayName', event.displayName);
    console.log('event timezone:', event.timeZone);
    console.log('organization ID', organizationId);
    console.log('purchaseContext title en:', purchaseContext.title['en']);
    console.log('parsed event dates are: begin', ExtensionUtils.formatDateTime(event.begin, "yyyy-MM-dd'T'HH:mm:ss", false), 'end', ExtensionUtils.formatDateTime(event.end, "yyyy-MM-dd'T'HH:mm:ss", false))
}
