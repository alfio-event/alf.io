importScripts('jsqrcode.min.js')

//web worker portion :D

onmessage = function(message) {
	decodeImageData(message.data, function(result) {
		postMessage(result);
	});
}