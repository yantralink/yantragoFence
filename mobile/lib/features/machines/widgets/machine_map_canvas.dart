import 'dart:async';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/theme/app_spacing.dart';
import 'package:yantrago/features/machines/widgets/map_overlay_widgets.dart';
import 'package:yantrago/features/machines/widgets/map_speed_gauge.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Map canvas for the machine location screen — the WebView map plus
/// its overlay controls: status chip (top-left), alerts button (left
/// rail), zoom pill (bottom-right), and the speed gauge (bottom-left,
/// only when the location reports a speed).
///
/// The widget is sized by its parent (the page wraps it in Expanded)
/// so the info panel below never covers the OSM attribution.
class MachineMapCanvas extends StatelessWidget {
  final WebViewController controller;
  final bool isMoving;
  final double? speedKmh;

  /// Whether the satellite base layer is active — reflects the last
  /// confirmed toggle, never an optimistic guess.
  final bool isSatelliteLayer;
  final VoidCallback onToggleLayer;

  const MachineMapCanvas({
    super.key,
    required this.controller,
    required this.isMoving,
    required this.speedKmh,
    required this.isSatelliteLayer,
    required this.onToggleLayer,
  });

  /// Left control rail starts below the status chip.
  static const double _controlRailTop = 64;

  /// Keeps overlay controls clear of the OSM attribution strip.
  static const double _overlayBottom = 40;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final semantic = Theme.of(context).extension<AppSemanticColors>();

    return Stack(
      children: <Widget>[
        Positioned.fill(
          child: WebViewWidget(controller: controller),
        ),

        // Status chip — Moving/Stopped derived from speed.
        Positioned(
          top: AppSpacing.md,
          left: AppSpacing.lg,
          child: MapStatusChip(
            label: isMoving ? l10n.statusMoving : l10n.statusStopped,
            dotColor: isMoving
                ? (semantic?.tone(StatusTone.success) ??
                    const Color(0xFF22C55E))
                : (semantic?.tone(StatusTone.neutral) ?? Colors.grey),
          ),
        ),

        // Left control rail — alerts, OSM ↔ satellite layer toggle.
        Positioned(
          top: _controlRailTop,
          left: AppSpacing.lg,
          child: Column(
            children: <Widget>[
              MapControlButton(
                tooltip: l10n.navAlerts,
                onPressed: () => context.push('/app/alerts'),
                child: const Icon(Icons.notifications_outlined),
              ),
              const SizedBox(height: AppSpacing.sm),
              MapControlButton(
                tooltip: l10n.mapLayers,
                onPressed: onToggleLayer,
                child: Icon(isSatelliteLayer
                    ? Icons.layers
                    : Icons.layers_outlined),
              ),
            ],
          ),
        ),

        // Zoom pill — bottom-right.
        Positioned(
          right: AppSpacing.lg,
          bottom: _overlayBottom,
          child: MapZoomPill(
            onZoomIn: () => unawaited(controller
                .runJavaScript('window.yantraMap.zoomIn();')),
            onZoomOut: () => unawaited(controller
                .runJavaScript('window.yantraMap.zoomOut();')),
          ),
        ),

        // Speed gauge — bottom-left, only when speed is reported.
        if (speedKmh != null)
          Positioned(
            left: AppSpacing.lg,
            bottom: _overlayBottom,
            child: MapSpeedGauge(
              valueLabel: speedKmh!.round().toString(),
              unitLabel: 'km/h',
            ),
          ),
      ],
    );
  }
}
