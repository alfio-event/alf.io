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
    if(capability === 'CREATE_VIRTUAL_ROOM') {
        return 'https://alf.io';
    } else {
        var param = ExtensionUtils.base64UrlSafe(request.firstName + ';' +request.lastName + ';' + request.email);
        return 'https://alf.io?user=' + param;
    }
}