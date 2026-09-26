import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../extension/extension.js', import.meta.url), 'utf8')
    .replace(/^import[\s\S]*?;\n/gm, '')
    .replace('export default class WetterkurveExtension', 'class WetterkurveExtension') +
    '\nglobalThis.WetterkurveExtension = WetterkurveExtension;';

let nextSignalId = 1;
class Actor {
    constructor(properties = {}) {
        Object.assign(this, properties);
        this.children = [];
        this.signals = new Map();
        this.disconnected = [];
        this.clutter_text = new ActorText();
    }

    add_child(child) { this.children.push(child); }
    addMenuItem(child) { this.add_child(child); }
    get_children() { return [...this.children]; }
    connect(name, callback) {
        const id = nextSignalId++;
        this.signals.set(id, {name, callback});
        return id;
    }
    disconnect(id) {
        assert.ok(this.signals.delete(id), 'signal ID must be connected');
        this.disconnected.push(id);
    }
    destroy() {
        this.destroyed = true;
        for (const child of this.children)
            child.destroy();
        this.clutter_text.destroyed = true;
    }
    setOrnament() {}
    add_style_class_name() {}
    remove_style_class_name() {}
    add_style_pseudo_class() {}
    remove_style_pseudo_class() {}
    set_height() {}
    set_style() {}
    queue_repaint() {}
    setForecast() {}
    setLocale() {}
    setLayers() {}
    open() {}
}

class ActorText {
    constructor() { this.signals = new Map(); }
    connect(name, callback) { this.signals.set(nextSignalId++, {name, callback}); }
}

class Entry extends Actor {
    get_text() { return this.text ?? ''; }
    set_text(text) { this.text = text; }
    grab_key_focus() {}
}

class Indicator extends Actor {
    constructor() {
        super();
        this.menu = new Actor();
    }
    destroy() {
        super.destroy();
        this.menu.destroy();
    }
}

class SubMenu extends Actor {
    constructor() {
        super();
        this.menu = new Actor();
    }
}

const sessions = [];
class Session {
    constructor() { this.requests = []; this.aborted = false; sessions.push(this); }
    send_and_read_async(message, _priority, cancellable, callback) {
        this.requests.push({message, cancellable, callback});
    }
    send_and_read_finish(result) { return result; }
    abort() { this.aborted = true; }
}

class Cancellable {
    cancel() { this.cancelled = true; }
}

const settings = {
    get_string(key) { return key === 'language' ? 'en' : ''; },
    get_int() { return 0; },
    get_boolean() { return true; },
    set_string() {},
};
const sandbox = {
    Cairo: {},
    Clutter: {ActorAlign: {CENTER: 0}},
    Gio: {Cancellable, icon_new_for_string: path => path},
    GLib: {
        PRIORITY_DEFAULT: 0, SOURCE_CONTINUE: true, SOURCE_REMOVE: false,
        timeout_add_seconds: () => nextSignalId++,
        timeout_add: () => nextSignalId++,
        source_remove: () => {},
        get_monotonic_time: () => 0,
        build_filenamev: parts => parts.join('/'),
    },
    GObject: {registerClass: klass => klass},
    Soup: {Session, Message: {new: () => ({status_code: 200})}, Status: {OK: 200}},
    St: {
        DrawingArea: Actor, Label: Actor, BoxLayout: Actor, Icon: Actor,
        Button: Actor, Entry,
    },
    Extension: class {
        constructor() { this.metadata = {name: 'Wetterkurve'}; this.uuid = 'wetterkurve@test'; this.path = '/tmp'; }
        getSettings() { return settings; }
    },
    Main: {panel: {addToStatusArea() {}}},
    PanelMenu: {Button: Indicator},
    PopupMenu: {
        PopupBaseMenuItem: Actor, PopupSubMenuMenuItem: SubMenu,
        PopupMenuItem: Actor, Ornament: {DOT: 1, NONE: 0},
    },
    languageForLocale: () => 'en', text: (_language, key) => key,
    parseLocations: (_stored, defaults) => defaults,
    buildForecastUrl: () => 'forecast', buildGeocodingUrl: () => 'search',
    chartDaySegments() {}, chartForecast() {}, locationFromGeocodingResult() {},
    round() {}, validateForecast: value => value, weatherInfo() {},
    TextDecoder,
    console,
};
vm.runInNewContext(source, sandbox, {filename: 'extension.js'});
const extension = new sandbox.WetterkurveExtension();

extension.enable();
const oldSession = extension._session;
const oldMenu = extension._indicator.menu;
assert.equal(oldSession.requests.length, 1);
extension._searchBox.visible = true;
extension._searchEntry.set_text('Berlin');
extension._searchLocations();
assert.equal(oldSession.requests.length, 2);
assert.equal([...oldMenu.signals.values()].filter(signal =>
    signal.name === 'open-state-changed').length, 1);

extension._selectLanguage('de');
assert.equal(oldMenu.disconnected.length, 1,
    'language rebuild disconnects the old menu signal');
assert.equal(extension._session, oldSession, 'language rebuild keeps the active request');

extension.disable();
assert.equal(oldSession.aborted, true, 'disable aborts the Soup session');
assert.equal(extension._indicator, null);
assert.equal(extension._panelIcon, null, 'disable releases child actor references');
assert.equal(extension._searchEntry, null, 'disable releases entry references');
assert.equal(extension._refreshButton, null, 'disable releases button references');

extension.enable();
const freshSession = extension._session;
let oldRequestRendered = false;
extension._render = () => { oldRequestRendered = true; };
extension._searchBox.visible = true;
extension._searchEntry.set_text('Berlin');
extension._searchLocations();
let oldSearchRendered = false;
extension._renderLocationResults = () => { oldSearchRendered = true; };
oldSession.requests[0].callback(oldSession, {get_data: () =>
    new TextEncoder().encode('{}')});
assert.equal(oldRequestRendered, false,
    'a callback from a previous enable must not update the new UI');
oldSession.requests[1].callback(oldSession, {get_data: () =>
    new TextEncoder().encode('{"results":[]}')});
assert.equal(oldSearchRendered, false,
    'an old search callback must not update the new UI');
assert.notEqual(freshSession, oldSession);
extension.disable();

console.log('lifecycle.test.js: OK');
