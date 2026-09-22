import 'package:flutter/material.dart';

/// Dummy mockup of the Machine Details screen with the new toggle switch.
///
/// This is a standalone, self-contained widget — no providers, no network,
/// no Riverpod. Run it directly to preview the UI:
///
///   flutter run lib/features/machines/widgets/fencing_toggle_mockup.dart
///
/// Tap the toggle to cycle through the states:
///   OFF → Turning On… → ON → Turning Off… → OFF
///   Tap "Simulate Fail" to see the error state.
void main() => runApp(const _MockupApp());

class _MockupApp extends StatelessWidget {
  const _MockupApp();

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Fencing Toggle Mockup',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF2E7D32),
          brightness: Brightness.light,
        ),
        useMaterial3: true,
      ),
      home: const _MockupScaffold(),
    );
  }
}

class _MockupScaffold extends StatefulWidget {
  const _MockupScaffold();

  @override
  State<_MockupScaffold> createState() => _MockupScaffoldState();
}

class _MockupScaffoldState extends State<_MockupScaffold> {
  // Cycle: off → turningOn → on → turningOff → off
  _ToggleState _state = _ToggleState.off;
  bool _showError = false;

  void _cycle() {
    setState(() {
      _showError = false;
      switch (_state) {
        case _ToggleState.off:
          _state = _ToggleState.turningOn;
          // Simulate command completing after 2 seconds
          Future.delayed(const Duration(seconds: 2), () {
            if (mounted) setState(() => _state = _ToggleState.on);
          });
        case _ToggleState.turningOn:
          _state = _ToggleState.on;
        case _ToggleState.on:
          _state = _ToggleState.turningOff;
          Future.delayed(const Duration(seconds: 2), () {
            if (mounted) setState(() => _state = _ToggleState.off);
          });
        case _ToggleState.turningOff:
          _state = _ToggleState.off;
      }
    });
  }

  void _simulateFail() {
    setState(() {
      _showError = true;
      _state = _ToggleState.off; // revert
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('North Field Fence'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => setState(() {
              _state = _ToggleState.off;
              _showError = false;
            }),
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // ===== Machine Info Card =====
            const _MockInfoCard(
              name: 'North Field Fence',
              statusLabel: 'Online',
              statusColor: Colors.green,
              runningLabel: 'Machine Running',
              runningColor: Colors.green,
            ),
            const SizedBox(height: 8),

            // ===== Telemetry Section =====
            const _MockSectionHeader(title: 'Telemetry', icon: Icons.monitor_heart),
            const SizedBox(height: 8),
            _MockTelemetryGrid(),
            const SizedBox(height: 16),

            // ===== NEW: Fencing Toggle (replaces Turn ON / Turn OFF buttons) =====
            _MockFencingToggle(
              state: _state,
              showError: _showError,
              onToggle: _cycle,
              onSimulateFail: _simulateFail,
            ),
            const SizedBox(height: 8),

            // ===== Location Card =====
            _MockLocationCard(),
            const SizedBox(height: 12),

            // ===== Footnote =====
            Center(
              child: Text(
                'Machine state changes only after device confirmation.',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ============================================================
// Fencing Toggle — the new widget (mockup version)
// ============================================================

enum _ToggleState { off, turningOn, on, turningOff }

class _MockFencingToggle extends StatelessWidget {
  final _ToggleState state;
  final bool showError;
  final VoidCallback onToggle;
  final VoidCallback onSimulateFail;

  const _MockFencingToggle({
    required this.state,
    required this.showError,
    required this.onToggle,
    required this.onSimulateFail,
  });

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final isPending = state == _ToggleState.turningOn || state == _ToggleState.turningOff;
    final isOn = state == _ToggleState.on;

    // Colors
    final Color trackColor;
    final Color thumbColor;
    final String label;
    final String? subLabel;

    if (isPending) {
      trackColor = Colors.amber.shade300;
      thumbColor = Colors.amber.shade700;
      label = state == _ToggleState.turningOn ? 'Turning On…' : 'Turning Off…';
      subLabel = null;
    } else if (isOn) {
      trackColor = Colors.green;
      thumbColor = Colors.white;
      label = 'Fencing On';
      subLabel = null;
    } else if (showError) {
      trackColor = Colors.grey.shade300;
      thumbColor = Colors.grey.shade600;
      label = 'Fencing Off';
      subLabel = 'Command failed — try again';
    } else {
      trackColor = Colors.grey.shade300;
      thumbColor = Colors.grey.shade600;
      label = 'Fencing Off';
      subLabel = null;
    }

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: colors.surfaceContainerHighest.withValues(alpha: 0.5),
        borderRadius: BorderRadius.circular(12),
        border: showError
            ? Border.all(color: colors.error.withValues(alpha: 0.5), width: 1.5)
            : null,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Label row
          Row(
            children: [
              Icon(
                isOn ? Icons.power_settings_new : Icons.power_off,
                size: 20,
                color: isOn ? Colors.green : colors.onSurfaceVariant,
              ),
              const SizedBox(width: 8),
              Text(
                'Fencing Control',
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Toggle row
          Row(
            children: [
              // State label
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        if (isPending) ...[
                          SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.amber.shade700,
                            ),
                          ),
                          const SizedBox(width: 8),
                        ],
                        Text(
                          label,
                          style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                                fontWeight: FontWeight.w600,
                                color: isPending
                                    ? Colors.amber.shade800
                                    : isOn
                                        ? Colors.green.shade700
                                        : colors.onSurfaceVariant,
                              ),
                        ),
                      ],
                    ),
                    if (subLabel != null) ...[
                      const SizedBox(height: 4),
                      Text(
                        subLabel,
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                              color: colors.error,
                            ),
                      ),
                    ],
                  ],
                ),
              ),

              // The actual toggle switch
              Transform.scale(
                scale: 1.2,
                child: Switch(
                  value: isOn || state == _ToggleState.turningOn,
                  onChanged: isPending ? null : (_) => onToggle(),
                  activeTrackColor: trackColor,
                  inactiveThumbColor: thumbColor,
                  inactiveTrackColor: trackColor,
                ),
              ),
            ],
          ),

          if (showError) ...[
            const SizedBox(height: 8),
            Text(
              'Last command: FENCING_ON → FAILED (device timeout)',
              style: Theme.of(context).textTheme.labelSmall?.copyWith(
                    color: colors.onSurfaceVariant,
                  ),
            ),
          ],

          const SizedBox(height: 8),
          // Debug helper — simulate fail (not in production)
          TextButton.icon(
            onPressed: onSimulateFail,
            icon: const Icon(Icons.error_outline, size: 16),
            label: const Text('Simulate Command Fail (debug)'),
            style: TextButton.styleFrom(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              minimumSize: const Size(0, 32),
              tapTargetSize: MaterialTapTargetSize.shrinkWrap,
            ),
          ),
        ],
      ),
    );
  }
}

// ============================================================
// Mock widgets to render the rest of the screen
// ============================================================

class _MockInfoCard extends StatelessWidget {
  final String name;
  final String statusLabel;
  final Color statusColor;
  final String runningLabel;
  final Color runningColor;

  const _MockInfoCard({
    required this.name,
    required this.statusLabel,
    required this.statusColor,
    required this.runningLabel,
    required this.runningColor,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Text(
                    name,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                ),
                const SizedBox(width: 8),
                _MockBadge(label: statusLabel, color: statusColor),
                const SizedBox(width: 6),
                _MockBadge(label: runningLabel, color: runningColor),
              ],
            ),
            const SizedBox(height: 8),
            const Row(
              children: [
                Expanded(
                  child: _MockField(label: 'Machine ID', value: 'YG-001'),
                ),
                SizedBox(width: 12),
                Expanded(
                  child: _MockField(label: 'IMEI', value: '000000000000001'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _MockBadge extends StatelessWidget {
  final String label;
  final Color color;
  const _MockBadge({required this.label, required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 6,
            height: 6,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle),
          ),
          const SizedBox(width: 4),
          Text(
            label,
            style: TextStyle(
              color: color,
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class _MockField extends StatelessWidget {
  final String label;
  final String value;
  const _MockField({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: Theme.of(context).textTheme.labelSmall?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
        ),
        const SizedBox(height: 2),
        Text(value, style: Theme.of(context).textTheme.bodyMedium),
      ],
    );
  }
}

class _MockSectionHeader extends StatelessWidget {
  final String title;
  final IconData icon;
  const _MockSectionHeader({required this.title, required this.icon});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 18, color: Theme.of(context).colorScheme.primary),
        const SizedBox(width: 8),
        Text(
          title,
          style: Theme.of(context).textTheme.titleSmall?.copyWith(
                fontWeight: FontWeight.w600,
              ),
        ),
      ],
    );
  }
}

class _MockTelemetryGrid extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    const tiles = <_TelemetryTile>[
      _TelemetryTile('Ignition', 'On', Icons.key, Colors.green),
      _TelemetryTile('Ext. Battery', '12.5 V', Icons.bolt, Colors.blue),
      _TelemetryTile('Battery', '88%', Icons.battery_full, Colors.green),
      _TelemetryTile('GSM', 'Good', Icons.signal_cellular_4_bar, Colors.green),
      _TelemetryTile('Voltage', '24.2 V', Icons.electrical_services, Colors.blue),
      _TelemetryTile('Faults', '0', Icons.check_circle, Colors.green),
    ];

    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 3,
        childAspectRatio: 1.1,
        crossAxisSpacing: 8,
        mainAxisSpacing: 8,
      ),
      itemCount: tiles.length,
      itemBuilder: (context, i) => tiles[i],
    );
  }
}

class _TelemetryTile extends StatelessWidget {
  final String label;
  final String value;
  final IconData icon;
  final Color color;
  const _TelemetryTile(this.label, this.value, this.icon, this.color);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest.withValues(alpha: 0.3),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 20, color: color),
          const SizedBox(height: 4),
          Text(
            label,
            style: Theme.of(context).textTheme.labelSmall?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
          ),
          Text(
            value,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  fontWeight: FontWeight.w600,
                ),
          ),
        ],
      ),
    );
  }
}

class _MockLocationCard extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.location_on, size: 18,
                    color: Theme.of(context).colorScheme.primary),
                const SizedBox(width: 8),
                Text(
                  'Location',
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            const Text('18.520430, 73.856740'),
            const SizedBox(height: 4),
            Text(
              'Pune, Maharashtra',
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
            ),
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton.icon(
                onPressed: () {},
                icon: const Icon(Icons.map_outlined, size: 18),
                label: const Text('View Map'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
