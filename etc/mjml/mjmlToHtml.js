const {globSync} = require("glob");
const process = require('process');
const fs = require('fs');
const path = require("path");
const mjml2html = require('mjml')

const srcMjml = path.join(process.argv[2], '/src/main/resources/alfio/mjml/**/*.mjml');
const dstMjml = path.join(process.argv[3], '/alfio/templates/');
fs.mkdirSync(dstMjml, { recursive: true });
const mustacheExtension = ".ms"

const files = globSync(srcMjml, { nodir: true });
files.forEach(function (file) {
    fs.readFile(file, { encoding: 'utf8' }, function (err, data) {

        const mjmlOutput = mjml2html(data);

        const extension = path.extname(file);
        const filename = path.basename(file, extension);

        fs.writeFileSync(path.join(dstMjml, filename + mustacheExtension), mjmlOutput.html);
    });
});