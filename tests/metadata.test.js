const fs = require('fs');
const path = require('path');

const metadataPath = path.join(__dirname, '..', 'extension', 'metadata.json');
const metadata = JSON.parse(fs.readFileSync(metadataPath, 'utf8'));

if (metadata.uuid !== 'wetterkurve@wean.de')
    throw new Error('metadata must use the stable Wetterkurve UUID');

if (metadata.name !== 'Wetterkurve')
    throw new Error('metadata must use the Wetterkurve public name');

if (metadata.url !== 'https://github.com/vibecodingwean/wetterkurve')
    throw new Error('metadata must link to the public source repository');

if (metadata['settings-schema'] !== 'org.gnome.shell.extensions.wetterkurve')
    throw new Error('metadata must reference the bundled GSettings schema');

if ('version' in metadata)
    throw new Error('store-managed metadata version must not be committed');

console.log('metadata.test.js: OK');
