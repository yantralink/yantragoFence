/*
 * YantraGO map bootstrap — SEC-15.
 *
 * Loaded only from bundled assets (see map.html). The Flutter layer calls
 * window.yantraMap.init(config) via the WebView's evaluateJavascript bridge
 * after the page finishes loading. `config` is a JSON object produced by
 * Dart's jsonEncode, so all labels/coordinates arrive as data, never as
 * string-interpolated code.
 *
 * config = {
 *   latitude: number,              // -90..90
 *   longitude: number,             // -180..180
 *   zoom?: number,                 // 1..19, default 16
 *   markerPopupLabel?: string,
 *   geofence?: {
 *     latitude: number,
 *     longitude: number,
 *     radiusMeters: number,
 *     popupLabel?: string
 *   }
 * }
 */
(function () {
  'use strict';

  var TILE_URL = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png';
  var ATTRIBUTION =
    '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

  var map = null;
  var marker = null;
  var geofenceCircle = null;

  function isValidLat(v) {
    return typeof v === 'number' && isFinite(v) && Math.abs(v) <= 90;
  }

  function isValidLng(v) {
    return typeof v === 'number' && isFinite(v) && Math.abs(v) <= 180;
  }

  function init(config) {
    if (!config || !isValidLat(config.latitude) || !isValidLng(config.longitude)) {
      return;
    }

    var zoom = (typeof config.zoom === 'number' && isFinite(config.zoom))
      ? Math.min(19, Math.max(1, Math.round(config.zoom)))
      : 16;

    var center = [config.latitude, config.longitude];

    if (map === null) {
      map = L.map('map').setView(center, zoom);
      L.tileLayer(TILE_URL, {
        maxZoom: 19,
        attribution: ATTRIBUTION
      }).addTo(map);
    } else {
      map.setView(center, zoom);
    }

    // Replace overlays on re-init — repeated location updates must not
    // accumulate stale markers/circles.
    if (marker !== null) {
      map.removeLayer(marker);
      marker = null;
    }
    if (geofenceCircle !== null) {
      map.removeLayer(geofenceCircle);
      geofenceCircle = null;
    }

    // Popup content is set via textContent — labels are never interpreted
    // as HTML, so a markup-bearing label cannot inject elements.
    var markerLabel = document.createElement('div');
    markerLabel.textContent = String(config.markerPopupLabel || '');
    marker = L.marker(center).addTo(map).bindPopup(markerLabel);

    var g = config.geofence;
    if (g && isValidLat(g.latitude) && isValidLng(g.longitude) &&
        typeof g.radiusMeters === 'number' && isFinite(g.radiusMeters) &&
        g.radiusMeters > 0) {
      var geofenceLabel = document.createElement('div');
      geofenceLabel.textContent =
        String(g.popupLabel || '') + ' (' + Math.round(g.radiusMeters) + ' m)';
      geofenceCircle = L.circle([g.latitude, g.longitude], {
        radius: g.radiusMeters,
        color: '#1976D2',
        fillColor: '#1976D2',
        fillOpacity: 0.15,
        weight: 2
      }).addTo(map).bindPopup(geofenceLabel);
    }
  }

  window.yantraMap = { init: init };
})();
