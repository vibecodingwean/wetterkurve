const fs = require('fs');
const path = require('path');
const vm = require('vm');
const {deepStrictEqual} = require('assert/strict');

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
assert(source.includes('setLayers(showClouds, showWind, showFeelsLike)'),
    'GNOME chart must accept independent cloud, wind, and feels-like layers');
assert(source.includes('if (this._showClouds)'),
    'cloud strip must be toggleable');
assert(source.includes('if (this._showWind)'),
    'wind curve must be toggleable');
assert(source.includes('if (this._showFeelsLike)'),
    'feels-like curve must be toggleable');
assert(source.includes('yWind'), 'wind curve must use a dedicated vertical scale');
assert(source.includes('#248BFF'), 'wind curve must use the blue Android wind color');
assert(source.includes('cr.lineTo(x(data.length - 1), cloudTop)'),
    'cloud strip must keep the top delimiter toward the weekday strip');
assert(source.includes('CLOUD_STRIP_EXTRA'),
    'cloud strip extra height must be a named constant');
assert(source.includes('_applyChartHeight'),
    'showing clouds must grow the chart widget instead of shrinking the plot');
assert(source.includes('CHART_HEIGHT + (this._showClouds ? CLOUD_STRIP_EXTRA : 0)'),
    'chart height must add the cloud strip extra only while clouds are visible');
assert(source.includes('CLOUD_STRIP_EXTRA = CLOUD_STRIP_HEIGHT'),
    'cloud extra height is only the strip, the weekday gap already exists');
assert(source.includes('PLOT_TOP_GAP'),
    'weekday-to-plot gap must be a named constant');
assert(source.includes('top: this._showClouds ? cloudBottom : stripBottom + PLOT_TOP_GAP'),
    'clouds sit in the weekday gap and flush against the temperature plot');
assert(source.includes('hideTopGrid'),
    'top temperature grid line must be hideable when clouds are shown');
assert(!source.includes('cloudBottom, border'),
    'cloud strip must not stroke a bottom line toward the temperature plot');
assert(source.includes("_toggleClouds()"), 'popup must toggle clouds');
assert(source.includes("_toggleWind()"), 'popup must toggle wind');
assert(source.includes("add_style_class_name('mw-layer-on')"),
    'layer badges must use a style class, not the pressed :active pseudo-class');
assert(source.includes("remove_style_class_name('mw-layer-on')"),
    'layer badges must clear mw-layer-on when off');
assert(source.includes("set_boolean('show-clouds'"),
    'cloud visibility must persist in GSettings');
assert(source.includes("set_boolean('show-wind'"),
    'wind visibility must persist in GSettings');
assert(source.includes("set_boolean('show-feels-like'"),
    'feels-like visibility must persist in GSettings');
assert(schema.includes('name="show-clouds"'), 'schema must define show-clouds');
assert(schema.includes('name="show-wind"'), 'schema must define show-wind');
assert(schema.includes('name="show-feels-like" type="b">\n      <default>false</default>'),
    'fresh GNOME installs must hide feels-like');
assert(schema.includes('name="show-wind" type="b">\n      <default>false</default>'),
    'fresh GNOME installs must hide wind');
assert(schema.includes('name="show-clouds" type="b">\n      <default>true</default>'),
    'fresh GNOME installs must show clouds');
assert(schema.includes('"name":"Berlin"'), 'fresh GNOME installs must use Berlin');
assert(language.includes("clouds: 'Wolke'"), 'German cloud toggle label is missing');
assert(language.includes("clouds: 'Cloud'"), 'English cloud toggle label is missing');
for (const key of ['Temperatur', 'gefühlt', 'Regen %', 'Regen mm', 'Wolke', 'Wind'])
    assert(language.includes(key), `German full-view legend must include ${key}`);
for (const key of ['Temperature', 'feels like', 'rain %', 'rain mm', 'cloud', 'wind'])
    assert(language.includes(key), `English full-view legend must include ${key}`);

const extensionClass = source.slice(source.indexOf('export default class '))
    .replace('export default class ', 'class ');
const WetterkurveExtension = vm.runInNewContext(
    extensionClass + '\nWetterkurveExtension;', {Extension: class {}});
const extension = new WetterkurveExtension();
const saved = [];
const layers = [];
extension._settings = {set_boolean(key, value) { saved.push([key, value]); }};
extension._chart = {setLayers(...value) { layers.push(value); }};
extension._updateLayerButtons = () => {};
extension._showClouds = true;
extension._showWind = false;
extension._showFeelsLike = false;
extension._toggleFeelsLike();
extension._toggleFeelsLike();
deepStrictEqual(saved, [
    ['show-feels-like', true], ['show-feels-like', false],
], 'feels-like toggle must persist both on and off');
deepStrictEqual(layers.map(value => [...value]), [
    [true, false, true], [true, false, false],
], 'feels-like toggle must immediately update chart layers');

console.log('chart.test.js: OK');
