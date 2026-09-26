import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/l10n/l10n.dart';
import 'package:yantrago/models/telemetry_series.dart';

/// External Battery Health card — voltage line chart over the selected
/// range with a shaded healthy band (11.8–14.0 V, confirmed by user)
/// and a status chip from the latest reading.
class BatteryHealthCard extends StatelessWidget {
  final List<TelemetryPoint> voltage;

  /// True when the machine was added <48h ago — the empty state then
  /// explains that data appears once the device starts reporting.
  final bool isNewDevice;

  const BatteryHealthCard({
    super.key,
    required this.voltage,
    this.isNewDevice = false,
  });

  static const double healthyMin = 11.8;
  static const double healthyMax = 14.0;

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final colors = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;

    return Card(
      clipBehavior: Clip.antiAlias,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.batteryHealthTitle, style: text.titleMedium),
            const SizedBox(height: 12),
            if (voltage.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 24),
                child: AppStatePanel.empty(
                  title: l10n.analyticsNoData,
                  message: isNewDevice
                      ? l10n.analyticsNewDeviceMessage
                      : l10n.analyticsNoDataMessage,
                  icon: Icons.battery_unknown_outlined,
                ),
              )
            else ...[
              SizedBox(height: 180, child: _chart(colors, text)),
              const SizedBox(height: 12),
              _statusRow(context),
            ],
          ],
        ),
      ),
    );
  }

  Widget _chart(ColorScheme colors, TextTheme text) {
    final spots = [
      for (final p in voltage)
        FlSpot(p.at.millisecondsSinceEpoch.toDouble(), p.value),
    ];

    double minY = voltage.first.value;
    double maxY = voltage.first.value;
    for (final p in voltage) {
      if (p.value < minY) minY = p.value;
      if (p.value > maxY) maxY = p.value;
    }
    // Pad the axis so the line never touches edges and the healthy
    // band is always visible.
    minY = (minY - 0.5 < healthyMin) ? minY - 0.5 : healthyMin - 0.5;
    maxY = (maxY + 0.5 > healthyMax) ? maxY + 0.5 : healthyMax + 0.5;

    final labelCount = spots.length == 1 ? 2 : 4;

    return LineChart(
      LineChartData(
        minY: minY,
        maxY: maxY,
        clipData: const FlClipData.all(),
        gridData: FlGridData(
          drawVerticalLine: false,
          getDrawingHorizontalLine: (v) =>
              FlLine(color: colors.outlineVariant, strokeWidth: 0.5),
        ),
        borderData: FlBorderData(show: false),
        rangeAnnotations: RangeAnnotations(
          horizontalRangeAnnotations: [
            HorizontalRangeAnnotation(
              y1: healthyMin,
              y2: healthyMax,
              color: Colors.green.withValues(alpha: 0.08),
            ),
          ],
        ),
        titlesData: FlTitlesData(
          topTitles:
              const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          rightTitles:
              const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          leftTitles: AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              reservedSize: 34,
              getTitlesWidget: (value, meta) => Text(
                value.toStringAsFixed(1),
                style: text.labelSmall,
              ),
            ),
          ),
          bottomTitles: AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              reservedSize: 22,
              interval: (spots.last.x - spots.first.x) / (labelCount - 1),
              getTitlesWidget: (value, meta) {
                if (value < meta.min || value > meta.max) {
                  return const SizedBox.shrink();
                }
                final t =
                    DateTime.fromMillisecondsSinceEpoch(value.toInt());
                return Text(DateFormat('HH:mm').format(t),
                    style: text.labelSmall);
              },
            ),
          ),
        ),
        lineTouchData: LineTouchData(
          touchTooltipData: LineTouchTooltipData(
            getTooltipItems: (touched) => [
              for (final s in touched)
                LineTooltipItem(
                  '${DateFormat('HH:mm').format(DateTime.fromMillisecondsSinceEpoch(s.x.toInt()))}  ${s.y.toStringAsFixed(2)} V',
                  const TextStyle(fontSize: 12),
                ),
            ],
          ),
        ),
        lineBarsData: [
          LineChartBarData(
            spots: spots,
            isCurved: true,
            preventCurveOverShooting: true,
            color: colors.primary,
            barWidth: 2,
            dotData: const FlDotData(show: false),
            belowBarData: BarAreaData(
              show: true,
              color: colors.primary.withValues(alpha: 0.08),
            ),
          ),
        ],
      ),
    );
  }

  Widget _statusRow(BuildContext context) {
    final l10n = context.l10n;
    final latest = voltage.last.value;
    final String status;
    final Color tone;
    if (latest < healthyMin) {
      status = l10n.batteryStatusLow;
      tone = Colors.red;
    } else if (latest > healthyMax) {
      status = l10n.batteryStatusHigh;
      tone = Colors.orange;
    } else {
      status = l10n.batteryStatusHealthy;
      tone = Colors.green;
    }

    return Row(
      children: [
        Expanded(
          child: Text(
            l10n.batteryNowVolts(latest.toStringAsFixed(1)),
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
          decoration: BoxDecoration(
            color: tone.withValues(alpha: 0.12),
            borderRadius: BorderRadius.circular(999),
          ),
          child: Text(
            status,
            style: TextStyle(
                color: tone, fontWeight: FontWeight.w600, fontSize: 12),
          ),
        ),
      ],
    );
  }
}
