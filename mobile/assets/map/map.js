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
  // Esri World Imagery — free satellite basemap (see docs/MAP_SCREEN_REDESIGN_PLAN.md §4.5).
  var SATELLITE_TILE_URL =
    'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}';
  var SATELLITE_ATTRIBUTION =
    'Tiles &copy; Esri &mdash; Source: Esri, Maxar, Earthstar Geographics, and the GIS User Community';

  // Inline-SVG machine marker (excavator) inside a white circular badge —
  // replaces the default Leaflet pin to match the approved map mockup.
  var MACHINE_MARKER_HTML =
    '<div style="width:36px;height:36px;border-radius:50%;' +
    'background:#ffffff;border:2px solid #22C55E;' +
    'box-shadow:0 2px 6px rgba(0,0,0,0.35);display:flex;' +
    'align-items:center;justify-content:center;">' +
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48" width="26" height="26">' +
    '<path d="M28 8 L38 17 L35 21 L25 13 Z" fill="#F5A623"/>' +
    '<path d="M36 16 L43 24 L40 27 L33 20 Z" fill="#D98E1B"/>' +
    '<path d="M41 24 q5 2 4 7 l-7 -2 Z" fill="#B87714"/>' +
    '<rect x="6" y="15" width="10" height="9" rx="1" fill="#F5A623"/>' +
    '<rect x="16" y="19" width="13" height="5" fill="#2B5F8A"/>' +
    '<rect x="4" y="26" width="27" height="8" rx="4" fill="#37474F"/>' +
    '<circle cx="10" cy="30" r="2.4" fill="#90A4AE"/>' +
    '<circle cx="17" cy="30" r="2.4" fill="#90A4AE"/>' +
    '<circle cx="24" cy="30" r="2.4" fill="#90A4AE"/>' +
    '</svg></div>';

  var map = null;
  var marker = null;
  var geofenceCircle = null;
  var currentCenter = null;
  var osmLayer = null;
  var satelliteLayer = null;
  var baseLayerName = 'osm';

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
    currentCenter = center;

    if (map === null) {
      // Leaflet's default zoom control is disabled — the Flutter layer
      // renders its own zoom pill (yantraMap.zoomIn/zoomOut).
      map = L.map('map', { zoomControl: false }).setView(center, zoom);
      osmLayer = L.tileLayer(TILE_URL, {
        maxZoom: 19,
        attribution: ATTRIBUTION
      });
      satelliteLayer = L.tileLayer(SATELLITE_TILE_URL, {
        maxZoom: 19,
        attribution: SATELLITE_ATTRIBUTION
      });
      (baseLayerName === 'satellite' ? satelliteLayer : osmLayer)
        .addTo(map);
    } else {
      // Re-init (location updates) follows the marker but preserves the
      // user's current zoom — live frames must not snap the zoom back.
      map.setView(center, map.getZoom());
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
    marker = L.marker(center, { icon: machineIcon() })
      .addTo(map)
      .bindPopup(markerLabel);

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

  function machineIcon() {
    return L.divIcon({
      className: 'machine-div-icon',
      html: MACHINE_MARKER_HTML,
      iconSize: [36, 36],
      iconAnchor: [18, 18],
      popupAnchor: [0, -18]
    });
  }

  function zoomIn() {
    if (map !== null) map.zoomIn();
  }

  function zoomOut() {
    if (map !== null) map.zoomOut();
  }

  // Re-centers on the last reported machine coordinate, keeping the
  // user's current zoom level.
  function recenter() {
    if (map !== null && currentCenter !== null) {
      map.setView(currentCenter, map.getZoom());
    }
  }

  // Switches the base tile layer between 'osm' and 'satellite'.
  // baseLayerName updates eagerly so an early toggle (before init) is
  // honored when the map is created; the layer swap itself needs map.
  function setBaseLayer(name) {
    if ((name !== 'osm' && name !== 'satellite') ||
        name === baseLayerName) return;
    baseLayerName = name;
    if (map === null) return;
    var incoming = name === 'satellite' ? satelliteLayer : osmLayer;
    var outgoing = name === 'satellite' ? osmLayer : satelliteLayer;
    if (incoming === null || outgoing === null) return;
    map.removeLayer(outgoing);
    incoming.addTo(map);
  }

  window.yantraMap = {
    init: init,
    zoomIn: zoomIn,
    zoomOut: zoomOut,
    recenter: recenter,
    setBaseLayer: setBaseLayer
  };
})();
