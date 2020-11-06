const glob = require("glob")
const fs = require('fs');
const path = require("path");
const mjml2html = require('mjml')


const srcMjml = path.join(__dirname, '/../resources/alfio/mjml/**/*.mjml');
const dstMjml = path.join(__dirname, '/../../../build/generated/resources/alfio/templates/');
fs.mkdirSync(dstMjml, { recursive: true });
const mustacheExtension = ".ms"

glob(srcMjml, {nodir: true}, function (err, files) {

	files.forEach(function (file) { 
        fs.readFile(file, { encoding: 'utf8' }, function (err, data) {

        	const mjmlOutput = mjml2html(data, {beautify: true});
        	
        	const extension = path.extname(file);
        	const filename = path.basename(file, extension);

        	fs.writeFileSync(path.join(dstMjml, filename + mustacheExtension), mjmlOutput.html);
        });
	});
});