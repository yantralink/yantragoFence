import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:yantrago/core/services/map_consent_service.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';
import 'package:yantrago/features/machines/providers/machine_address_provider.dart';
import 'package:yantrago/features/machines/providers/theft_protection_provider.dart';
import 'package:yantrago/l10n/l10n.dart';
import 'package:yantrago/models/machine_location.dart';

/// Machine location map page — shows a single machine's GPS location on an
/// in-app map using the bundled Leaflet.js assets (SEC-15: no remote
/// executable code is ever loaded) with a default marker.
/// Opened from the LocationCard on the machine detail page.
///
/// Displays:
/// - Interactive map with marker at the machine's coordinates
/// - AppBar with the machine name and a back arrow
/// - Address text below the map (reverse-geocoded via the backend proxy)
///
/// SEC-15 privacy gate: map tiles (OpenStreetMap) and address lookups
/// transfer data to third parties, so the map only renders after the user
/// grants affirmative consent. Without consent the page shows raw
/// coordinates only — no third-party request is made.
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

  WebViewController? _mapController;
  bool _mapPageLoaded = false;
  Map<String, Object?>? _pendingMapConfig;

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
        error: (_, __) => const _MapConsentGate(),
        data: (decision) => switch (decision) {
          MapConsent.granted => _buildMapBody(context),
          MapConsent.declined =>
            _MapDeclinedView(machineId: widget.machineId),
          MapConsent.undecided => const _MapConsentGate(),
        },
      ),
    );
  }

  Widget _buildMapBody(BuildContext context) {
    final location = ref.watch(machineLocationProvider(widget.machineId));
    final address = ref.watch(machineAddressProvider(widget.machineId));
    final protection =
        ref.watch(theftProtectionNotifierProvider(widget.machineId));
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
        if (loc == null || !_isValidCoordinate(loc.latitude, loc.longitude)) {
          return AppStatePanel.empty(
            title: l10n.noLocationData,
            message: l10n.noLocationMessage,
            icon: Icons.location_off,
          );
        }

        final protectionStatus = protection.valueOrNull;
        _updateMap(
          loc: loc,
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

        return Column(
          children: <Widget>[
            // Map — 75% of available height
            Expanded(
              flex: 3,
              child: WebViewWidget(controller: controller),
            ),
            // Bottom panel — 25% of available height
            Expanded(
              flex: 1,
              child: Column(
                children: <Widget>[
                  _AddressBar(
                    latitude: loc.latitude,
                    longitude: loc.longitude,
                    address: address,
                  ),
                  _TheftProtectionPanel(machineId: widget.machineId),
                ],
              ),
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

/// Consent gate shown before any third-party map/geocoding transfer.
///
/// SEC-15 / Play user-data policy: machine coordinates are farmer-linked
/// precise location, so rendering the map requires prior affirmative
/// consent. The disclosure names the recipients (OpenStreetMap tiles;
/// the backend geocoding proxy) and states what is shared.
class _MapConsentGate extends ConsumerWidget {
  const _MapConsentGate();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;
    final l10n = context.l10n;

    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Icon(Icons.privacy_tip_outlined,
                color: colors.primary, size: 48),
            const SizedBox(height: 16),
            Text(
              l10n.mapConsentTitle,
              style: text.titleMedium
                  ?.copyWith(fontWeight: FontWeight.w600),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            Text(
              l10n.mapConsentMessage,
              style: text.bodyMedium
                  ?.copyWith(color: colors.onSurfaceVariant),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: () =>
                    ref.read(mapConsentProvider.notifier).grant(),
                child: Text(l10n.mapConsentAllow),
              ),
            ),
            const SizedBox(height: 8),
            SizedBox(
              width: double.infinity,
              child: TextButton(
                onPressed: () =>
                    ref.read(mapConsentProvider.notifier).decline(),
                child: Text(l10n.mapConsentNotNow),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Shown when the user declined map services — raw coordinates only, no
/// third-party requests. Offers a path to grant consent later.
class _MapDeclinedView extends ConsumerWidget {
  final String machineId;

  const _MapDeclinedView({required this.machineId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;
    final l10n = context.l10n;
    final location = ref.watch(machineLocationProvider(machineId));

    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Icon(Icons.map_outlined,
                color: colors.onSurfaceVariant, size: 48),
            const SizedBox(height: 16),
            Text(
              l10n.mapDisabledTitle,
              style: text.titleMedium
                  ?.copyWith(fontWeight: FontWeight.w600),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            Text(
              l10n.mapDisabledMessage,
              style: text.bodyMedium
                  ?.copyWith(color: colors.onSurfaceVariant),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),
            location.maybeWhen(
              data: (loc) => loc == null
                  ? const SizedBox.shrink()
                  : Text(
                      '${loc.latitude.toStringAsFixed(6)}, '
                      '${loc.longitude.toStringAsFixed(6)}',
                      style: text.bodyMedium
                          ?.copyWith(fontWeight: FontWeight.w500),
                    ),
              orElse: () => const SizedBox.shrink(),
            ),
            const SizedBox(height: 24),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton(
                onPressed: () =>
                    ref.read(mapConsentProvider.notifier).grant(),
                child: Text(l10n.mapEnableServices),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Address bar below the map — shows reverse-geocoded address or coordinates.
class _AddressBar extends StatelessWidget {
  final double latitude;
  final double longitude;
  final AsyncValue<dynamic> address;

  const _AddressBar({
    required this.latitude,
    required this.longitude,
    required this.address,
  });

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: colors.surface,
        boxShadow: <BoxShadow>[
          BoxShadow(
            color: colors.shadow.withValues(alpha: 0.08),
            blurRadius: 8,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: address.when(
        loading: () => Row(
          children: <Widget>[
            const SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
            const SizedBox(width: 12),
            Text(context.l10n.resolvingAddress,
                style: text.bodyMedium?.copyWith(color: colors.onSurfaceVariant)),
          ],
        ),
        error: (_, __) => _AddressContent(
          lines: <String>[
            '${latitude.toStringAsFixed(6)}, ${longitude.toStringAsFixed(6)}'
          ],
          latitude: latitude,
          longitude: longitude,
        ),
        data: (addr) {
          final lines = addr != null
              ? addr.toShortLines()
              : <String>[
                  '${latitude.toStringAsFixed(6)}, ${longitude.toStringAsFixed(6)}'
                ];
          return _AddressContent(
            lines: lines,
            latitude: latitude,
            longitude: longitude,
          );
        },
      ),
    );
  }
}

class _AddressContent extends StatelessWidget {
  final List<String> lines;
  final double latitude;
  final double longitude;

  const _AddressContent({
    required this.lines,
    required this.latitude,
    required this.longitude,
  });

  Future<void> _openInGoogleMaps() async {
    final uri = Uri.parse(
      'https://www.google.com/maps?q=$latitude,$longitude',
    );
    await launchUrl(uri, mode: LaunchMode.externalApplication);
  }

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Icon(Icons.location_on, color: colors.primary, size: 20),
            const SizedBox(width: 8),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: lines
                    .map((line) => Text(
                          line,
                          style: text.bodyMedium?.copyWith(
                            fontWeight: FontWeight.w500,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ))
                    .toList(),
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Align(
          alignment: Alignment.centerRight,
          child: GestureDetector(
            onTap: _openInGoogleMaps,
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                Icon(Icons.map_outlined, color: colors.primary, size: 16),
                const SizedBox(width: 4),
                Text(
                  context.l10n.openInGoogleMaps,
                  style: text.bodySmall?.copyWith(
                    color: colors.primary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(width: 2),
                Icon(Icons.arrow_forward, color: colors.primary, size: 14),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

/// Theft protection bottom panel — shows a toggle to enable/disable protection.
class _TheftProtectionPanel extends ConsumerWidget {
  final String machineId;

  const _TheftProtectionPanel({required this.machineId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final statusAsync =
        ref.watch(theftProtectionNotifierProvider(machineId));

    return statusAsync.when(
      loading: () => const Padding(
        padding: EdgeInsets.all(16),
        child: SizedBox(
          height: 24,
          child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
        ),
      ),
      error: (_, __) => Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: <Widget>[
            Icon(Icons.error_outline,
                color: Theme.of(context).colorScheme.error, size: 20),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                context.l10n.theftProtectionLoadFailed,
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ),
            TextButton(
              onPressed: () => ref
                  .read(theftProtectionNotifierProvider(machineId).notifier)
                  .load(),
              child: Text(context.l10n.retry),
            ),
          ],
        ),
      ),
      data: (status) {
        final ColorScheme colors = Theme.of(context).colorScheme;
        final TextTheme text = Theme.of(context).textTheme;
        final bool enabled = status.protectionEnabled;

        return Container(
          width: double.infinity,
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: colors.surface,
            boxShadow: <BoxShadow>[
              BoxShadow(
                color: colors.shadow.withValues(alpha: 0.08),
                blurRadius: 8,
                offset: const Offset(0, -2),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              // Header row with toggle
              Row(
                children: <Widget>[
                  Icon(
                    enabled ? Icons.shield : Icons.shield_outlined,
                    color: enabled ? colors.primary : colors.onSurfaceVariant,
                    size: 22,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      context.l10n.theftProtection,
                      style: text.titleSmall?.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  Switch(
                    value: enabled,
                    onChanged: statusAsync.isLoading ? null : (value) async {
                      final notifier = ref.read(
                          theftProtectionNotifierProvider(machineId).notifier);
                      if (value) {
                        await notifier.enable();
                      } else {
                        await notifier.disable();
                      }
                    },
                  ),
                ],
              ),
            ],
          ),
        );
      },
    );
  }
}
