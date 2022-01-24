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
        ],
        capabilityDetails: [
            { key: 'CREATE_VIRTUAL_ROOM', label: 'Create a new virtual room (mode 1)', description: 'This is the description', selector: 'room1' },
            { key: 'CREATE_VIRTUAL_ROOM', label: 'Create a new virtual room (mode 2)', description: 'This is the description', selector: 'room2' },
        ]
    };
}

function executeScript(scriptEvent) {
    return null;
}

function executeCapability(capability) {
    if(capability === 'CREATE_VIRTUAL_ROOM') {
        return 'https://alf.io/' + request.selector;
    } else {
        var param = ExtensionUtils.base64UrlSafe(request.firstName + ';' +request.lastName + ';' + request.email);
        return 'https://alf.io?user=' + param;
    }
}