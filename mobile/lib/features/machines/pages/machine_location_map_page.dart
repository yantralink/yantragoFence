import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:yantrago/core/services/map_consent_service.dart';
import 'package:yantrago/core/utils/date_utils.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/features/machines/providers/location_socket_provider.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';
import 'package:yantrago/features/machines/providers/machine_address_provider.dart';
import 'package:yantrago/features/machines/providers/machine_weather_provider.dart';
import 'package:yantrago/features/machines/providers/telemetry_socket_provider.dart';
import 'package:yantrago/features/machines/providers/theft_protection_provider.dart';
import 'package:yantrago/features/machines/widgets/machine_map_canvas.dart';
import 'package:yantrago/features/machines/widgets/map_consent_views.dart';
import 'package:yantrago/features/machines/widgets/map_info_panel.dart';
import 'package:yantrago/features/machines/widgets/weather_details_sheet.dart';
import 'package:yantrago/l10n/l10n.dart';
import 'package:yantrago/models/machine_location.dart';

/// Machine location map page — a single machine's GPS location on the
/// bundled Leaflet map (SEC-15: no remote executable code is loaded).
/// Opened from the LocationCard on the machine detail page.
///
/// Layout (per docs/MAP_SCREEN_REDESIGN_PLAN.md): dominant map canvas
/// with status chip, alerts button, zoom pill, and speed-gauge overlays;
/// below it an info panel (environmental strip, status pills + Set
/// Schedule, location card, theft protection card).
///
/// SEC-15 privacy gate: the map renders only after affirmative consent;
/// without it the page shows raw coordinates and no third-party request
/// is made.
class MachineLocationMapPage extends ConsumerStatefulWidget {
  final String machineId;

  const MachineLocationMapPage({super.key, required this.machineId});

  @override
  ConsumerState<MachineLocationMapPage> createState() =>
      _MachineLocationMapPageState();
}

class _MachineLocationMapPageState
    extends ConsumerState<MachineLocationMapPage> {
  static const _mapAssetPath = 'assets/map/map.html';

  /// Speed above this threshold (kph) marks the machine as Moving —
  /// small enough to catch real movement, large enough to ignore GPS
  /// jitter on a stationary machine.
  static const double _movingSpeedThresholdKmh = 1.0;

  // STUB — static placeholder values until the backend weather feature
  // lands (docs/WEATHER_FEATURE_PLAN.md). Swap this source for the real
  // weather provider; EnvironmentalStrip itself does not change.
  WebViewController? _mapController;
  bool _mapPageLoaded = false;
  Map<String, Object?>? _pendingMapConfig;
  bool _isSatelliteLayer = false;

  @override
  Widget build(BuildContext context) {
    final machine = ref.watch(machineDetailProvider(widget.machineId));
    final consent = ref.watch(mapConsentProvider);
    final l10n = context.l10n;

    return Scaffold(
      appBar: AppBar(
        title: machine.maybeWhen(
          data: (m) => Text(m.name),
          orElse: () => Text(l10n.machineLocationTitle),
        ),
      ),
      body: consent.when(
        loading: () =>
            AppStatePanel.loading(message: l10n.loadingLocation),
        // Fail closed: an unreadable consent store means "not asked yet".
        error: (_, __) => const MapConsentGate(),
        data: (decision) => switch (decision) {
          MapConsent.granted => _buildMapBody(context),
          MapConsent.declined =>
            MapDeclinedView(machineId: widget.machineId),
          MapConsent.undecided => const MapConsentGate(),
        },
      ),
    );
  }

  Widget _buildMapBody(BuildContext context) {
    final location = ref.watch(machineLocationProvider(widget.machineId));
    final address = ref.watch(machineAddressProvider(widget.machineId));
    final weatherAsync = ref.watch(machineWeatherProvider(widget.machineId));
    final machine = ref.watch(machineDetailProvider(widget.machineId));
    final protection =
        ref.watch(theftProtectionNotifierProvider(widget.machineId));
    // Sockets are watched at the top level so their connections survive
    // REST loading/error cycles — watching inside a when() branch would
    // dispose and recreate them on every invalidation.
    final liveLoc = ref.watch(locationSocketProvider(widget.machineId));
    final m = machine.valueOrNull;
    final liveIgnition = m == null
        ? null
        : ref.watch(telemetrySocketProvider(m.id))?.ignitionOn;
    final l10n = context.l10n;

    return location.when(
      loading: () =>
          AppStatePanel.loading(message: l10n.loadingLocation),
      error: (_, __) => AppStatePanel.empty(
        title: l10n.noLocationData,
        message: l10n.noLocationMessage,
        icon: Icons.location_off,
      ),
      data: (loc) {
        // Live socket location overrides the REST snapshot — a frame
        // can render the page even when the initial fetch was empty.
        final effectiveLoc = (liveLoc != null &&
                _isValidCoordinate(liveLoc.latitude, liveLoc.longitude))
            ? liveLoc
            : loc;
        if (effectiveLoc == null ||
            !_isValidCoordinate(
                effectiveLoc.latitude, effectiveLoc.longitude)) {
          return AppStatePanel.empty(
            title: l10n.noLocationData,
            message: l10n.noLocationMessage,
            icon: Icons.location_off,
          );
        }

        final protectionStatus = protection.valueOrNull;
        _updateMap(
          loc: effectiveLoc,
          geofenceLatitude: protectionStatus?.geofenceLatitude,
          geofenceLongitude: protectionStatus?.geofenceLongitude,
          geofenceRadiusMeters: protectionStatus?.geofenceRadiusMeters,
          markerPopupLabel: l10n.machineLocationTitle,
          geofencePopupLabel: l10n.geofencePopupPrefix,
        );

        final controller = _mapController;
        if (controller == null) {
          return AppStatePanel.loading(message: l10n.loadingLocation);
        }

        final bool isMoving =
            (effectiveLoc.speed ?? 0) > _movingSpeedThresholdKmh;
        // Live ACC override from the telemetry socket — same override the
        // machine detail page applies to its Machine Running chip.
        final bool? machineOn = m == null
            ? null
            : (liveIgnition ?? m.ignitionOn ?? m.isOnline);

        return Column(
          children: <Widget>[
            // Map — dominant canvas with overlays. The info panel sits
            // below it so the OSM attribution is never covered.
            Expanded(
              child: MachineMapCanvas(
                controller: controller,
                isMoving: isMoving,
                speedKmh: effectiveLoc.speed,
                isSatelliteLayer: _isSatelliteLayer,
                onToggleLayer: () => _toggleBaseLayer(controller),
              ),
            ),

            // Below-map info panel.
            MapInfoPanel(
              machineId: widget.machineId,
              machine: m,
              machineOn: machineOn,
              address: address.valueOrNull?.displayName ??
                  '${effectiveLoc.latitude.toStringAsFixed(6)}, '
                      '${effectiveLoc.longitude.toStringAsFixed(6)}',
              updatedLabel: l10n.updatedAt(relativeTime(
                  l10n, effectiveLoc.recordedAt ?? effectiveLoc.updatedAt)),
              latitude: effectiveLoc.latitude,
              longitude: effectiveLoc.longitude,
              weather: weatherAsync.valueOrNull,
              weatherLoading: weatherAsync.isLoading,
              onWeatherTap: weatherAsync.valueOrNull == null
                  ? null
                  : () => showWeatherDetailsSheet(
                      context, weatherAsync.valueOrNull!),
            ),
          ],
        );
      },
    );
  }

  /// Creates the WebView controller once, loads the bundled map page, and
  /// constrains navigation to local assets only (SEC-15). OSM tiles are
  /// subresource image loads, not navigations, so they are unaffected by
  /// the delegate — the page CSP (assets/map/map.html) is what scopes them.
  void _ensureMapController() {
    if (_mapController != null) return;
    _mapController = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(NavigationDelegate(
        onNavigationRequest: (request) => request.url.startsWith('file://')
            ? NavigationDecision.navigate
            : NavigationDecision.prevent,
        onPageFinished: (_) {
          _mapPageLoaded = true;
          _flushMapConfig();
        },
      ))
      ..loadFlutterAsset(_mapAssetPath);
  }

  /// Stages the map configuration and pushes it into the WebView once the
  /// bundled page has finished loading. Parameters cross the bridge as a
  /// jsonEncode'd object — labels are data, never interpolated code.
  void _updateMap({
    required MachineLocation loc,
    double? geofenceLatitude,
    double? geofenceLongitude,
    int? geofenceRadiusMeters,
    required String markerPopupLabel,
    required String geofencePopupLabel,
  }) {
    _ensureMapController();

    final hasGeofence = _isValidLat(geofenceLatitude) &&
        _isValidLng(geofenceLongitude) &&
        geofenceRadiusMeters != null &&
        geofenceRadiusMeters > 0;

    _pendingMapConfig = <String, Object?>{
      'latitude': loc.latitude,
      'longitude': loc.longitude,
      'zoom': 16,
      'markerPopupLabel': markerPopupLabel,
      if (hasGeofence)
        'geofence': <String, Object?>{
          'latitude': geofenceLatitude,
          'longitude': geofenceLongitude,
          'radiusMeters': geofenceRadiusMeters,
          'popupLabel': geofencePopupLabel,
        },
    };
    _flushMapConfig();
  }

  /// Toggles the base tile layer between OSM and Esri satellite imagery.
  /// The icon reflects the Dart-side state, which only flips after the
  /// JS call is issued — the WebView switch itself is synchronous and
  /// cannot fail silently (setBaseLayer is a no-op on an unknown name).
  void _toggleBaseLayer(WebViewController controller) {
    final next = !_isSatelliteLayer;
    setState(() => _isSatelliteLayer = next);
    unawaited(controller.runJavaScript(
        "window.yantraMap.setBaseLayer('${next ? 'satellite' : 'osm'}');"));
  }

  void _flushMapConfig() {
    final config = _pendingMapConfig;
    final controller = _mapController;
    if (config == null || controller == null || !_mapPageLoaded) return;
    _pendingMapConfig = null;
    unawaited(controller.runJavaScript(
        'window.yantraMap.init(${jsonEncode(config)});'));
  }

  static bool _isValidLat(double? v) =>
      v != null && v.isFinite && v.abs() <= 90;

  static bool _isValidLng(double? v) =>
      v != null && v.isFinite && v.abs() <= 180;

  static bool _isValidCoordinate(double lat, double lng) =>
      _isValidLat(lat) && _isValidLng(lng);
}
