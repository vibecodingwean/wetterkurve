import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const source = fs.readFileSync(new URL('../extension/extension.js', import.meta.url), 'utf8');
const chartSource = source.slice(source.indexOf('const ForecastChart ='),
    source.indexOf('export default class WetterkurveExtension'));
const context = {
    GObject: {registerClass: chart => chart},
    St: {DrawingArea: class {}},
    Cairo: {
        FontSlant: {NORMAL: 0}, FontWeight: {NORMAL: 0},
        LineCap: {ROUND: 0}, LineJoin: {ROUND: 0},
        LinearGradient: class { addColorStopRGBA() {} },
    },
    DAY_STRIP_BOTTOM: 30,
    PLOT_TOP_GAP: 10,
    CLOUD_STRIP_HEIGHT: 24,
    TEMPERATURE_COLORS: [[-15, [0, 0, 1]], [35, [1, 0, 0]]],
    WIND_COLOR: [0, 1, 0, 1],
};
const ForecastChart = vm.runInNewContext(
    chartSource + '\nForecastChart;', context);
const chart = new ForecastChart();
const times = [
    '2026-07-31T20:00', '2026-07-31T21:00',
    '2026-08-01T05:00', '2026-08-01T06:00', '2026-08-01T07:00',
];
const data = times.map(time => ({
    time, temperature: 20, apparent: 19, precipitation: 0,
    precipitationProbability: 0, wind: 5, cloudCover: 0,
}));

const cloudColors = [];
chart._drawCloudStrip({
    setSourceRGB(...color) { cloudColors.push(color); },
    setSourceRGBA() {}, setLineWidth() {}, rectangle() {}, fill() {},
    moveTo() {}, lineTo() {}, stroke() {},
}, data, index => index, 40, 64);
assert.deepEqual(cloudColors.map(color => color[0]),
    [29 / 255, 26 / 255, 26 / 255, 29 / 255],
    'cloud strip must use dark colors during 21:00 through 05:59');

let color = [];
let pendingRectangle = null;
let pendingLine = null;
const dayRectangles = [];
const transitions = [];
const cr = {
    setSourceRGBA(...rgba) { color = rgba; },
    rectangle(...bounds) { pendingRectangle = bounds; },
    fill() {
        if (color.join(',') === '0.13,0.29,0.62,0.42')
            dayRectangles.push(pendingRectangle);
    },
    moveTo(x, y) { pendingLine = [x, y]; },
    lineTo(x, y) { pendingLine = [...pendingLine, x, y]; },
    stroke() {
        if (color.join(',') === '0.62,0.8,1,0.82')
            transitions.push(pendingLine);
    },
    textExtents: () => ({width: 10}),
};
for (const method of ['setSourceRGB', 'selectFontFace', 'setFontSize',
    'setLineWidth', 'showText', 'setLineCap'])
    cr[method] = () => {};
chart._forecast = data;
chart._showClouds = false;
chart._showWind = false;
chart._drawDayStrip = () => {};
const curves = [];
chart._smoothLine = (_cr, values, _x, y) => curves.push({values, y20: y(20)});
chart._repaint({get_context: () => cr, get_surface_size: () => [300, 300]});
assert.equal(dayRectangles.length, 2,
    'plot must shade both daylight intervals');
assert.deepEqual(dayRectangles.map(bounds => bounds[0]), [36, 213],
    'daylight shading must start at 20:00 and 06:00');
assert.equal(curves.length, 1, 'default chart must omit the feels-like curve');
const noFeelsY20 = curves[0].y20;
assert.equal(transitions.length, 2,
    'plot must mark day-to-night and night-to-day transitions');
assert.deepEqual(transitions.map(line => line[0]), [95, 213],
    'transition markers must align with 21:00 and 06:00');
curves.length = 0;
chart._showFeelsLike = true;
chart._forecast = data.map(point => ({...point, apparent: 100}));
chart._repaint({get_context: () => cr, get_surface_size: () => [300, 300]});
assert.equal(curves.length, 2, 'enabled chart must draw the feels-like curve');
assert(curves[1].y20 > noFeelsY20,
    'enabled feels-like values must expand the temperature range');
curves.length = 0;
chart._showFeelsLike = false;
chart._repaint({get_context: () => cr, get_surface_size: () => [300, 300]});
assert.equal(curves[0].y20, noFeelsY20,
    'hidden feels-like values must not affect the temperature range');
console.log('night.test.js: OK');
