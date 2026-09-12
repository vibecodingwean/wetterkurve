import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import {text} from '../extension/language.js';

// Exercise the extension's language action with Shell actors stubbed out.
const source = fs.readFileSync(new URL('../extension/extension.js', import.meta.url), 'utf8');
const extensionClass = source.slice(source.indexOf('export default class '))
    .replace('export default class ', 'class ');
let added = 0;
let removed = 0;
const context = vm.createContext({
    Extension: class {}, text,
    Main: {panel: {addToStatusArea() { added++; }}},
    GLib: {source_remove() { removed++; }},
});
const ExtensionClass = vm.runInContext(extensionClass + '\nWetterkurveExtension;', context);
const extension = new ExtensionClass();
const saved = new Map();
let cancelled = 0;
let destroyed = 0;
let opened = 0;
extension._language = 'en';
extension._locale = 'en-US';
extension._settings = {set_string(key, value) { saved.set(key, value); }};
extension._searchRequestId = 4;
extension._searchTimeoutId = 1;
extension._searchCancellable = {cancel() { cancelled++; }};
extension._payload = {current: {temperature_2m: 20}};
const payload = extension._payload;
extension._indicator = {destroy() { destroyed++; }};
extension._buildUi = function () {
    this.label = this._t('searchLocation');
    this._indicator = {destroy() { destroyed++; }, menu: {open() { opened++; }}};
};
extension._render = function (data) { assert.equal(data, payload); this.renderedLanguage = this._language; };
extension._selectLanguage('de');
assert.equal(saved.get('language'), 'de');
assert.equal(extension.label, 'Ort suchen …');
assert.equal(extension._locale, 'de-DE');
assert.equal(extension.renderedLanguage, 'de');
assert.equal(extension._searchRequestId, 5);
assert.equal(extension._searchTimeoutId, null);
assert.equal(cancelled, 1);
assert.equal(removed, 1);
assert.equal(added, 1);
assert.equal(opened, 1);
assert.equal(destroyed, 1);
extension._selectLanguage('de');
assert.equal(destroyed, 1, 'selecting the active language must preserve the UI');
extension._selectLanguage('en');
assert.equal(saved.get('language'), 'en');
assert.equal(extension.label, 'Search for a location …');
assert.equal(extension._locale, 'en-US');
assert.equal(extension.renderedLanguage, 'en');
console.log('language.test.js: OK');
