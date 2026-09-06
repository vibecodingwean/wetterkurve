const fs = require('fs');
const path = require('path');

function assert(condition, message) {
    if (!condition)
        throw new Error(message);
}

const source = fs.readFileSync(
    path.join(__dirname, '..', 'extension', 'extension.js'), 'utf8');
const schema = fs.readFileSync(
    path.join(__dirname, '..', 'extension', 'schemas',
        'org.gnome.shell.extensions.wetterkurve.gschema.xml'), 'utf8');
const language = fs.readFileSync(
    path.join(__dirname, '..', 'extension', 'language.js'), 'utf8');

assert(source.includes('_drawCloudStrip'), 'GNOME chart must draw a cloud strip');
assert(source.includes('cloudCover'), 'GNOME cloud strip must use hourly cloud cover');
assert(source.includes('setLayers(showClouds, showWind)'),
    'GNOME chart must accept independent cloud and wind layers');
assert(source.includes('if (this._showClouds)'),
    'cloud strip must be toggleable');
assert(source.includes('if (this._showWind)'),
    'wind curve must be toggleable');
assert(source.includes('yWind'), 'wind curve must use a dedicated vertical scale');
assert(source.includes('#00E272'), 'wind curve must use the Android wind color');
assert(source.includes('cr.lineTo(x(data.length - 1), cloudTop)'),
    'cloud strip must keep the top delimiter toward the weekday strip');
assert(!source.includes('cloudBottom, border'),
    'cloud strip must not stroke a bottom line toward the temperature plot');
assert(source.includes("_toggleClouds()"), 'popup must toggle clouds');
assert(source.includes("_toggleWind()"), 'popup must toggle wind');
assert(source.includes("set_boolean('show-clouds'"),
    'cloud visibility must persist in GSettings');
assert(source.includes("set_boolean('show-wind'"),
    'wind visibility must persist in GSettings');
assert(schema.includes('name="show-clouds"'), 'schema must define show-clouds');
assert(schema.includes('name="show-wind"'), 'schema must define show-wind');
assert(language.includes("clouds: 'Wolke'"), 'German cloud toggle label is missing');
assert(language.includes("clouds: 'Cloud'"), 'English cloud toggle label is missing');

console.log('chart.test.js: OK');
