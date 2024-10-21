function getScriptMetadata() {
    return {
        id: 'metadata',
        displayName: 'Add custom ticket metadata',
        version: 0,
        async: false,
        events: [
            'TICKET_ASSIGNED_GENERATE_METADATA'
        ]
    };
}
function executeScript() {
    console.log('ticket pdf', attendeeResources.ticketPdf, attendeeResources.ticketQrCode);
    return {
        attributes: {
            pdfLink: attendeeResources.ticketPdf,
            qrCodeLink: attendeeResources.ticketQrCode,
            metadataAttribute: 'fixedValue'
        }
    }
}