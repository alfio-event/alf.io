/**
 * The script metadata object describes whether your extension should be invoked asynchronously, and which events it supports
 * @returns {{ async: boolean, events: string[] }}
 */
function getScriptMetadata() {
    return {
        id: 'customTaxPolicyApplicationExample', // optional: id and version will be used later as a mechanism for checking if the script has a newer version
        displayName: 'Custom Tax Policy Application Example', //mandatory: the name displayed in the configuration page
        version: 0, // optional
        async: false,
        events: [
            'CUSTOM_TAX_POLICY_APPLICATION'
        ]
    };
}
/**
 * Executes the extension.
 * @param scriptEvent
 * @returns Object
 */
function executeScript(scriptEvent) {
    console.log('entering customTaxPolicyApplicationExample for reservation', reservationId);
    // in this simple example we need to remove taxes if the attendee email ends with "@example.org"
    var keys = Object.keys(reservationForm.tickets);
    var containsModifiedElements = false;
    var out = [];
    for (i = 0; i < keys.length; i++) {
        var uuid = keys[i];
        var attendee = reservationForm.tickets[uuid];
        var ticketInfo = ticketInfoByUuid[uuid];
        var category = categoriesById[ticketInfo.ticketCategoryId];
        var originalTaxPolicy = ticketInfo.taxPolicy;
        if (!category.free && attendee.email.endsWith('@example.org')) {
            console.log('found attendee with matching email!');
            out.push({
                uuid: uuid,
                taxPolicy: originalTaxPolicy == 'INCLUDED' ? 'CUSTOM_INCLUDED_EXEMPT' : 'CUSTOM_NOT_INCLUDED_EXEMPT'
            });
            containsModifiedElements = true;
        } else {
            out.push({
                uuid: uuid,
                taxPolicy: originalTaxPolicy
            });
        }
    }

    if (!containsModifiedElements) {
        // since nothing was modified, we return "null" to continue with the original validation process
        return null;
    }

    return {
        ticketPolicies: out
    };
}
