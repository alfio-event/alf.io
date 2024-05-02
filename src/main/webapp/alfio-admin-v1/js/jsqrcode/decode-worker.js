importScripts('jsqrcode.min.js')

//web worker portion :D

onmessage = function(message) {
    try {
        decodeImageData(message.data, function(result) {
            postMessage(result);
        });
    } catch(e) {
        postMessage('error decoding QR Code')
    }
}