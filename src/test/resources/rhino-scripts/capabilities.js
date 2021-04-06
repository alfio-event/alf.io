function getScriptMetadata() {
    return {
        id: 'myExtensionIdentifier',
        displayName: 'My Extension',
        version: 0,
        async: false,
        events: [
            'EVENT_METADATA_UPDATE',
            'ONLINE_CHECK_IN_REDIRECT'
        ],
        capabilities: [
            'CREATE_VIRTUAL_ROOM',
            'CREATE_GUEST_LINK'
        ]
    };
}

function executeScript(scriptEvent) {
    return null;
}

function executeCapability(capability) {
    var result;
    if(capability === 'CREATE_VIRTUAL_ROOM') {
        result = 'https://alf.io';
    } else {
        result = 'https://github.com/alfio-event/alf.io';
    }
    return {
        result: result
    };
}