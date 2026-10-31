import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/theme/app_spacing.dart';
import 'package:yantrago/features/commands/providers/command_provider.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/l10n/l10n.dart';
import 'package:yantrago/models/machine.dart';

/// Machine action pills row below the map — Machine status pill
/// (display-only), Fence status pill (tappable toggle), and the Set
/// Schedule action.
///
/// Per AGENTS.md rules 5–6: pill labels reflect the last confirmed
/// device state. Tapping the Fence pill issues a FENCING_* command but
/// the label only flips when the device confirms via telemetry — the
/// snackbar reports dispatch, never success.
class MachineActionPills extends ConsumerStatefulWidget {
  /// Machine whose fencing state drives the Fence pill and commands.
  /// Null hides the Fence pill.
  final Machine? machine;

  /// Ignition/power state (already live-overridden upstream) — null
  /// hides the Machine pill.
  final bool? machineOn;
  final String machineOnLabel;
  final String machineOffLabel;
  final String fenceOnLabel;
  final String fenceOffLabel;
  final String scheduleLabel;
  final VoidCallback onScheduleTap;

  const MachineActionPills({
    super.key,
    required this.machine,
    required this.machineOn,
    required this.machineOnLabel,
    required this.machineOffLabel,
    required this.fenceOnLabel,
    required this.fenceOffLabel,
    required this.scheduleLabel,
    required this.onScheduleTap,
  });

  @override
  ConsumerState<MachineActionPills> createState() =>
      _MachineActionPillsState();
}

class _MachineActionPillsState extends ConsumerState<MachineActionPills> {
  bool _sending = false;

  Future<void> _toggleFence() async {
    final m = widget.machine;
    if (m == null || _sending) return;
    setState(() => _sending = true);
    final commandType = m.isFencingOn ? 'FENCING_OFF' : 'FENCING_ON';
    try {
      await ref.read(commandProvider.notifier).sendCommand(
            machineId: m.id,
            imei: m.imei ?? '',
            commandType: commandType,
          );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.commandSentAwaitingAck),
          ),
        );
      }
    } catch (_) {
      if (mounted) {
        final ColorScheme colors = Theme.of(context).colorScheme;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.commandSendFailed),
            backgroundColor: colors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final AppSemanticColors? semantic =
        Theme.of(context).extension<AppSemanticColors>();

    // When this machine's command reaches ACK or a terminal state, the
    // device has applied (or definitively rejected) it — refresh the
    // machine so the pill shows the newly confirmed fencingState.
    ref.listen(commandProvider, (prev, next) {
      final cmd = next.lastCommand;
      final machineId = widget.machine?.id;
      if (cmd == null || machineId == null || cmd.machineId != machineId) {
        return;
      }
      if (cmd.status == prev?.lastCommand?.status) return;
      if (cmd.isAcked || cmd.isTerminal) {
        ref.invalidate(machineDetailProvider(machineId));
      }
    });
    final machine = widget.machine;
    final machineOn = widget.machineOn;
    final bool? fenceOn = machine == null
        ? null
        : machine.isFencingOn
            ? true
            : machine.isFencingOff
                ? false
                : null;

    final pills = <Widget>[
      if (machineOn != null)
        Expanded(
          child: _StatusPill(
            icon: Icons.power_settings_new,
            label:
                machineOn ? widget.machineOnLabel : widget.machineOffLabel,
            foreground: semantic
                ?.tone(machineOn ? StatusTone.success : StatusTone.neutral),
            background: semantic?.toneSoft(
                machineOn ? StatusTone.success : StatusTone.neutral),
          ),
        ),
      if (fenceOn != null)
        Expanded(
          child: _StatusPill(
            icon: Icons.fence,
            label: fenceOn ? widget.fenceOnLabel : widget.fenceOffLabel,
            foreground: semantic
                ?.tone(fenceOn ? StatusTone.success : StatusTone.danger),
            background: semantic?.toneSoft(
                fenceOn ? StatusTone.success : StatusTone.danger),
            onTap: _sending ? null : _toggleFence,
          ),
        ),
      Expanded(
        child: _ActionPill(
          icon: Icons.calendar_month_outlined,
          label: widget.scheduleLabel,
          foreground: Theme.of(context).colorScheme.onSurface,
          background: semantic?.toneSoft(StatusTone.neutral),
          onTap: widget.onScheduleTap,
        ),
      ),
    ];

    return Row(
      children: <Widget>[
        for (var i = 0; i < pills.length; i++) ...<Widget>[
          if (i > 0) const SizedBox(width: AppSpacing.sm),
          pills[i],
        ],
      ],
    );
  }
}

/// Status pill — shows confirmed device state. Tappable when [onTap]
/// is provided (Fence toggle), static otherwise (Machine indicator).
class _StatusPill extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color? foreground;
  final Color? background;
  final VoidCallback? onTap;

  const _StatusPill({
    required this.icon,
    required this.label,
    required this.foreground,
    required this.background,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final Color fg = foreground ?? colors.primary;
    final content = Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm + 2),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: <Widget>[
          Icon(icon, size: 16, color: fg),
          const SizedBox(width: AppSpacing.xs),
          Flexible(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context)
                  .textTheme
                  .labelMedium
                  ?.copyWith(color: fg, fontWeight: FontWeight.w700),
            ),
          ),
        ],
      ),
    );

    return Material(
      color: background ?? colors.primaryContainer,
      borderRadius: BorderRadius.circular(AppSpacing.xl),
      clipBehavior: Clip.antiAlias,
      child: onTap == null
          ? content
          : InkWell(onTap: onTap, child: content),
    );
  }
}

/// Tappable action pill (Set Schedule).
class _ActionPill extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color? foreground;
  final Color? background;
  final VoidCallback onTap;

  const _ActionPill({
    required this.icon,
    required this.label,
    required this.foreground,
    required this.background,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final Color fg = foreground ?? colors.primary;
    return Material(
      color: background ?? colors.primaryContainer,
      borderRadius: BorderRadius.circular(AppSpacing.xl),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm + 2),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: <Widget>[
              Icon(icon, size: 16, color: fg),
              const SizedBox(width: AppSpacing.xs),
              Flexible(
                child: Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context)
                      .textTheme
                      .labelMedium
                      ?.copyWith(color: fg, fontWeight: FontWeight.w700),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
