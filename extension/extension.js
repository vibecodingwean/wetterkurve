import Cairo from 'cairo';
import Clutter from 'gi://Clutter';
import Gio from 'gi://Gio';
import GLib from 'gi://GLib';
import GObject from 'gi://GObject';
import Soup from 'gi://Soup?version=3.0';
import St from 'gi://St';

import {Extension} from 'resource:///org/gnome/shell/extensions/extension.js';
import * as Main from 'resource:///org/gnome/shell/ui/main.js';
import * as PanelMenu from 'resource:///org/gnome/shell/ui/panelMenu.js';
import * as PopupMenu from 'resource:///org/gnome/shell/ui/popupMenu.js';

import {
    buildForecastUrl,
    chartDaySegments,
    chartForecast,
    round,
    validateForecast,
    weatherInfo,
} from './weather.js';

const LOCATION = {
    name: 'München',
    latitude: 48.1374,
    longitude: 11.5755,
    timezone: 'Europe/Berlin',
};
const UPDATE_SECONDS = 20 * 60;
const STALE_SECONDS = 10 * 60;
const NOW_MARKER_UPDATE_SECONDS = 60;
const CHART_HOURS = 3 * 24;
const TEMPERATURE_COLORS = [
    [-15, [0.36, 0.55, 1.00]],
    [-5, [0.20, 0.78, 1.00]],
    [5, [0.20, 0.88, 0.76]],
    [15, [0.55, 0.86, 0.25]],
    [22, [1.00, 0.84, 0.16]],
    [28, [1.00, 0.48, 0.12]],
    [35, [1.00, 0.20, 0.16]],
];
const DAY_STRIP_COLORS = [
    [0.16, 0.34, 0.56, 0.82],
    [0.10, 0.22, 0.39, 0.88],
];

function label(text, styleClass) {
    return new St.Label({
        text,
        style_class: styleClass,
        y_align: Clutter.ActorAlign.CENTER,
    });
}

const ForecastChart = GObject.registerClass(
class ForecastChart extends St.DrawingArea {
    _init() {
        super._init({
            style_class: 'mw-chart',
            x_expand: true,
        });
        this._forecast = [];
        this.connect('repaint', area => this._repaint(area));
    }

    setForecast(forecast) {
        this._forecast = forecast;
        this.queue_repaint();
    }

    _repaint(area) {
        const cr = area.get_context();
        const [width, height] = area.get_surface_size();
        const data = this._forecast;
        if (data.length < 2 || width < 100 || height < 100)
            return;

        const plot = {left: 36, top: 40, right: width - 16, bottom: height - 42};
        const plotWidth = plot.right - plot.left;
        const plotHeight = plot.bottom - plot.top;
        const temperatures = data.flatMap(point =>
            [point.temperature, point.apparent]);
        let minTemp = Math.floor(Math.min(...temperatures) / 5) * 5;
        let maxTemp = Math.ceil(Math.max(...temperatures) / 5) * 5;
        if (minTemp === maxTemp) {
            minTemp -= 5;
            maxTemp += 5;
        }

        const x = index =>
            plot.left + index * plotWidth / (data.length - 1);
        const y = value =>
            plot.bottom - (value - minTemp) * plotHeight / (maxTemp - minTemp);

        cr.setSourceRGBA(0.04, 0.055, 0.09, 0.32);
        cr.rectangle(plot.left, plot.top, plotWidth, plotHeight);
        cr.fill();

        this._drawDayStrip(cr, data, plot);

        for (let i = 0; i < data.length - 1; i++) {
            const hour = new Date(data[i].time).getHours();
            if (hour >= 21 || hour < 6) {
                cr.setSourceRGBA(0.13, 0.29, 0.62, 0.42);
                cr.rectangle(x(i), plot.top, x(i + 1) - x(i) + 1, plotHeight);
                cr.fill();
            }
        }

        for (let i = 1; i < data.length - 1; i++) {
            const previousHour = new Date(data[i - 1].time).getHours();
            const hour = new Date(data[i].time).getHours();
            const wasNight = previousHour >= 21 || previousHour < 6;
            const isNight = hour >= 21 || hour < 6;
            if (wasNight === isNight)
                continue;

            cr.setSourceRGBA(0.62, 0.80, 1, 0.82);
            cr.setLineWidth(1.5);
            cr.moveTo(x(i), plot.top);
            cr.lineTo(x(i), plot.bottom);
            cr.stroke();
        }

        cr.selectFontFace('Sans', Cairo.FontSlant.NORMAL, Cairo.FontWeight.NORMAL);
        cr.setFontSize(10);
        cr.setLineWidth(1);
        for (let value = minTemp; value <= maxTemp; value += 5) {
            const gridY = y(value);
            cr.setSourceRGBA(1, 1, 1, 0.19);
            cr.moveTo(plot.left, gridY);
            cr.lineTo(plot.right, gridY);
            cr.stroke();
            cr.setSourceRGBA(1, 1, 1, 1);
            cr.moveTo(2, gridY + 3);
            cr.showText(`${value}°`);
        }

        const maxRain = Math.max(1, ...data.map(point => point.precipitation));
        const barWidth = Math.max(2, plotWidth / data.length - 1);
        data.forEach((point, i) => {
            const probabilityHeight =
                plotHeight * 0.36 * point.precipitationProbability / 100;
            if (probabilityHeight > 0) {
                cr.setSourceRGBA(0.30, 0.67, 1, 0.27);
                cr.rectangle(x(i) - barWidth / 2, plot.bottom - probabilityHeight,
                    barWidth, probabilityHeight);
                cr.fill();
            }
            if (point.precipitation > 0) {
                const rainHeight =
                    plotHeight * 0.36 * point.precipitation / maxRain;
                cr.setSourceRGBA(0.25, 0.74, 1, 0.94);
                cr.rectangle(x(i) - barWidth / 2, plot.bottom - rainHeight,
                    barWidth, rainHeight);
                cr.fill();
            }
        });

        const temperatureGradient = new Cairo.LinearGradient(
            0, y(TEMPERATURE_COLORS[0][0]),
            0, y(TEMPERATURE_COLORS.at(-1)[0]));
        const temperatureRange =
            TEMPERATURE_COLORS.at(-1)[0] - TEMPERATURE_COLORS[0][0];
        for (const [temperature, color] of TEMPERATURE_COLORS) {
            temperatureGradient.addColorStopRGBA(
                (temperature - TEMPERATURE_COLORS[0][0]) / temperatureRange,
                ...color, 1);
        }

        this._smoothLine(cr, data.map(point => point.apparent), x, y,
            [0.72, 0.88, 1, 0.92], 2, true, [0.02, 0.04, 0.08, 0.68]);
        this._smoothLine(cr, data.map(point => point.temperature), x, y,
            temperatureGradient, 3.25, false, [0.02, 0.04, 0.08, 0.82]);

        data.forEach((point, i) => {
            const date = new Date(point.time);
            const hour = date.getHours();
            if (hour % 6 !== 0)
                return;

            cr.setSourceRGBA(1, 1, 1, 1);
            cr.setFontSize(10);
            const text = `${String(hour).padStart(2, '0')} h`;
            const extents = cr.textExtents(text);
            cr.moveTo(x(i) - extents.width / 2, height - 19);
            cr.showText(text);
        });

        const firstTime = new Date(data[0].time).getTime();
        const lastTime = new Date(data.at(-1).time).getTime();
        const now = Date.now();
        if (now >= firstTime && now <= lastTime) {
            const nowX = plot.left +
                (now - firstTime) / (lastTime - firstTime) * plotWidth;

            cr.setLineCap(Cairo.LineCap.ROUND);
            cr.setSourceRGBA(0.35, 1, 0.12, 0.24);
            cr.setLineWidth(9);
            cr.moveTo(nowX, plot.top);
            cr.lineTo(nowX, plot.bottom);
            cr.stroke();

            cr.setSourceRGBA(0.45, 1, 0.12, 1);
            cr.setLineWidth(3);
            cr.moveTo(nowX, plot.top);
            cr.lineTo(nowX, plot.bottom);
            cr.stroke();
        }
    }

    _drawDayStrip(cr, data, plot) {
        const stripTop = 6;
        const stripBottom = 30;
        const stripHeight = stripBottom - stripTop;
        const plotWidth = plot.right - plot.left;
        const days = chartDaySegments(data);

        cr.selectFontFace('Sans', Cairo.FontSlant.NORMAL, Cairo.FontWeight.BOLD);
        cr.setFontSize(11);

        days.forEach((day, index) => {
            const left = plot.left + day.start / data.length * plotWidth;
            const right = plot.left + day.end / data.length * plotWidth;
            const [red, green, blue, alpha] =
                DAY_STRIP_COLORS[index % DAY_STRIP_COLORS.length];

            cr.setSourceRGBA(red, green, blue, alpha);
            cr.rectangle(left, stripTop, right - left, stripHeight);
            cr.fillPreserve();
            cr.setSourceRGBA(0.64, 0.81, 1, 0.32);
            cr.setLineWidth(1);
            cr.stroke();

            cr.setSourceRGBA(1, 1, 1, 0.96);
            const extents = cr.textExtents(day.label);
            const textX = left + (right - left - extents.width) / 2;
            const textY = stripTop + (stripHeight - extents.height) / 2 -
                extents.yBearing;
            cr.moveTo(textX, textY);
            cr.showText(day.label);

            if (index > 0) {
                cr.setSourceRGBA(0.64, 0.81, 1, 0.30);
                cr.setLineWidth(1);
                cr.moveTo(left, stripBottom);
                cr.lineTo(left, plot.bottom);
                cr.stroke();
            }
        });
    }

    _smoothLine(cr, values, x, y, source, width, dashed, outline = null) {
        if (outline) {
            cr.setSourceRGBA(...outline);
            this._strokeSmoothLine(cr, values, x, y, width + 2.5, dashed);
        }

        if (Array.isArray(source))
            cr.setSourceRGBA(...source);
        else
            cr.setSource(source);
        this._strokeSmoothLine(cr, values, x, y, width, dashed);
    }

    _strokeSmoothLine(cr, values, x, y, width, dashed) {
        cr.setLineWidth(width);
        cr.setLineCap(Cairo.LineCap.ROUND);
        cr.setLineJoin(Cairo.LineJoin.ROUND);
        cr.setDash(dashed ? [5, 5] : [], 0);
        cr.moveTo(x(0), y(values[0]));

        for (let i = 0; i < values.length - 1; i++) {
            const currentX = x(i);
            const nextX = x(i + 1);
            const controlOffset = (nextX - currentX) * 0.45;
            cr.curveTo(
                currentX + controlOffset, y(values[i]),
                nextX - controlOffset, y(values[i + 1]),
                nextX, y(values[i + 1]));
        }
        cr.stroke();
        cr.setDash([], 0);
    }
});

export default class MuenchenWetterExtension extends Extension {
    enable() {
        this._payload = null;
        this._lastUpdated = 0;
        this._requestInFlight = false;
        this._timeoutId = null;
        this._nowMarkerTimeoutId = null;
        this._cancellable = new Gio.Cancellable();
        this._session = new Soup.Session({
            user_agent: `${this.metadata.name}/1`,
            timeout: 20,
        });

        this._buildUi();
        Main.panel.addToStatusArea(this.uuid, this._indicator);
        this._refresh();
        this._timeoutId = GLib.timeout_add_seconds(
            GLib.PRIORITY_DEFAULT,
            UPDATE_SECONDS,
            () => {
                this._refresh();
                return GLib.SOURCE_CONTINUE;
            });
        this._nowMarkerTimeoutId = GLib.timeout_add_seconds(
            GLib.PRIORITY_DEFAULT,
            NOW_MARKER_UPDATE_SECONDS,
            () => {
                this._chart?.queue_repaint();
                return GLib.SOURCE_CONTINUE;
            });
    }

    disable() {
        if (this._timeoutId) {
            GLib.source_remove(this._timeoutId);
            this._timeoutId = null;
        }
        if (this._nowMarkerTimeoutId) {
            GLib.source_remove(this._nowMarkerTimeoutId);
            this._nowMarkerTimeoutId = null;
        }
        this._cancellable?.cancel();
        this._indicator?.destroy();
        this._indicator = null;
        this._session = null;
        this._cancellable = null;
        this._chart = null;
        this._payload = null;
    }

    _buildUi() {
        this._indicator = new PanelMenu.Button(0, this.metadata.name, false);
        const panelBox = new St.BoxLayout({style_class: 'mw-panel'});
        this._panelIcon = new St.Icon({
            gicon: this._weatherIcon('unknown'),
            icon_size: 17,
            style_class: 'mw-panel-icon',
        });
        this._panelText = label('München …', 'mw-panel-text');
        panelBox.add_child(this._panelIcon);
        panelBox.add_child(this._panelText);
        this._indicator.add_child(panelBox);

        const item = new PopupMenu.PopupBaseMenuItem({
            reactive: false,
            can_focus: false,
        });
        const content = new St.BoxLayout({
            vertical: true,
            style_class: 'mw-content',
            x_expand: true,
        });
        item.add_child(content);

        const header = new St.BoxLayout({
            style_class: 'mw-header',
            x_expand: true,
        });
        const currentBox = new St.BoxLayout({vertical: true, x_expand: true});
        this._title = label(LOCATION.name, 'mw-title');
        this._condition = label('Wetter wird geladen …', 'mw-condition');
        currentBox.add_child(this._title);
        currentBox.add_child(this._condition);
        this._bigIcon = new St.Icon({
            gicon: this._weatherIcon('unknown'),
            icon_size: 42,
            style_class: 'mw-big-icon',
        });
        this._temperature = label('–°', 'mw-temperature');
        this._refreshButton = new St.Button({
            label: '↻',
            style_class: 'mw-refresh-button',
            can_focus: true,
            accessible_name: 'Wetter aktualisieren',
        });
        this._refreshButton.connect('clicked', () => this._refresh(true));
        header.add_child(currentBox);
        header.add_child(this._bigIcon);
        header.add_child(this._temperature);
        header.add_child(this._refreshButton);
        content.add_child(header);

        const stats = new St.BoxLayout({
            style_class: 'mw-stats',
            x_expand: true,
        });
        this._feels = this._stat('Gefühlt', '–°');
        this._rain = this._stat('Regen', '– %');
        this._wind = this._stat('Wind', '– km/h');
        this._humidity = this._stat('Feuchte', '– %');
        for (const stat of [this._feels, this._rain, this._wind, this._humidity])
            stats.add_child(stat.box);
        content.add_child(stats);

        const chartHeading = new St.BoxLayout({
            style_class: 'mw-chart-heading',
            x_expand: true,
        });
        chartHeading.add_child(label('3 Tage ab heute', 'mw-section-title'));
        const legend = label('● Temperatur   ┄ gefühlt   ▮ Regen', 'mw-legend');
        chartHeading.add_child(legend);
        content.add_child(chartHeading);

        this._chart = new ForecastChart();
        content.add_child(this._chart);
        this._status = label('Open-Meteo · lädt …', 'mw-status');
        content.add_child(this._status);

        this._indicator.menu.addMenuItem(item);
        this._indicator.menu.connect('open-state-changed', (_menu, isOpen) => {
            if (isOpen && GLib.get_monotonic_time() / 1e6 - this._lastUpdated >
                STALE_SECONDS)
                this._refresh();
        });
    }

    _stat(name, value) {
        const box = new St.BoxLayout({
            vertical: true,
            style_class: 'mw-stat',
            x_expand: true,
        });
        const valueLabel = label(value, 'mw-stat-value');
        box.add_child(valueLabel);
        box.add_child(label(name, 'mw-stat-name'));
        return {box, value: valueLabel};
    }

    _weatherIcon(name) {
        const path = GLib.build_filenamev([
            this.path,
            'icons',
            `${name}.svg`,
        ]);
        return Gio.icon_new_for_string(path);
    }

    _setWeatherIcon(name) {
        const icon = this._weatherIcon(name);
        this._panelIcon.gicon = icon;
        this._bigIcon.gicon = icon;
    }

    _refresh(force = false) {
        if (this._requestInFlight)
            return;
        if (!force && this._lastUpdated &&
            GLib.get_monotonic_time() / 1e6 - this._lastUpdated < 60)
            return;

        this._requestInFlight = true;
        this._refreshButton?.add_style_pseudo_class('active');
        this._status.text = 'Open-Meteo · aktualisiert …';
        const url = buildForecastUrl(
            LOCATION.latitude,
            LOCATION.longitude,
            LOCATION.timezone);
        const message = Soup.Message.new('GET', url);

        this._session.send_and_read_async(
            message,
            GLib.PRIORITY_DEFAULT,
            this._cancellable,
            (session, result) => {
                this._requestInFlight = false;
                this._refreshButton?.remove_style_pseudo_class('active');
                if (!this._indicator)
                    return;

                try {
                    const bytes = session.send_and_read_finish(result);
                    if (message.status_code !== Soup.Status.OK)
                        throw new Error(`HTTP ${message.status_code}`);
                    const payload = validateForecast(
                        JSON.parse(new TextDecoder().decode(bytes.get_data())));
                    this._payload = payload;
                    this._lastUpdated = GLib.get_monotonic_time() / 1e6;
                    this._render(payload);
                } catch (error) {
                    console.error(`[${this.uuid}] ${error.message}`);
                    this._renderError();
                }
            });
    }

    _render(payload) {
        const current = payload.current;
        const forecast = chartForecast(payload, CHART_HOURS);
        const [, description, iconName] = weatherInfo(current.weather_code);
        const now = Date.now();
        const upcomingRain = forecast
            .filter(point => new Date(point.time).getTime() >= now)
            .slice(0, 12)
            .map(point => point.precipitationProbability);
        const rainChance = upcomingRain.length ? Math.max(...upcomingRain) : 0;

        this._setWeatherIcon(iconName);
        this._panelText.text = `${round(current.temperature_2m)}° München`;
        this._temperature.text = `${round(current.temperature_2m)}°`;
        this._condition.text = description;
        this._feels.value.text = `${round(current.apparent_temperature)}°`;
        this._rain.value.text = `${round(rainChance)} %`;
        this._wind.value.text = `${round(current.wind_speed_10m)} km/h`;
        this._humidity.value.text = `${round(current.relative_humidity_2m)} %`;
        this._chart.setForecast(forecast);

        const updated = new Date().toLocaleTimeString('de-DE', {
            hour: '2-digit',
            minute: '2-digit',
        });
        this._status.text = `Open-Meteo · aktualisiert ${updated} Uhr`;
    }

    _renderError() {
        this._status.text = this._payload
            ? 'Keine Verbindung · letzte Prognose bleibt sichtbar'
            : 'Wetterdaten derzeit nicht erreichbar';
        if (!this._payload) {
            this._setWeatherIcon('unknown');
            this._panelText.text = 'Wetter';
            this._condition.text = 'Keine Verbindung';
        }
    }
}
