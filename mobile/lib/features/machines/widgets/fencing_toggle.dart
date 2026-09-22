import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/theme/app_spacing.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/commands/providers/command_provider.dart';
import 'package:yantrago/features/commands/providers/command_socket_provider.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Fencing toggle — replaces the old Turn ON / Turn OFF buttons with a
/// single switch that reflects the machine's current fencing state and
/// sends the appropriate command when toggled.
///
/// Per AGENTS.md rule 5: never assume a command succeeded until ACK is
/// received. The toggle does not flip to the new state until the command
/// reaches DONE and the machine detail is refreshed from the REST API.
///
/// State derivation:
///   pending  → "Turning On…" / "Turning Off…" (disabled, amber)
///   failed   → revert to machine state + error subtext (enabled)
///   done     → invalidate machine detail → settles to confirmed state
///   idle     → "Fencing On" (green) / "Fencing Off" (grey)
class FencingToggle extends ConsumerStatefulWidget {
  final String machineId;
  final String imei;
  final bool isFencingOn;

  const FencingToggle({
    super.key,
    required this.machineId,
    required this.imei,
    required this.isFencingOn,
  });

  @override
  ConsumerState<FencingToggle> createState() => _FencingToggleState();
}

class _FencingToggleState extends ConsumerState<FencingToggle> {
  bool _sending = false;
  bool _lastPending = false;
  Timer? _timeoutTimer;

  /// Safety timeout — if no ACK arrives within 30s, stop the spinner so
  /// the user can try again. The command may still complete later via
  /// the WebSocket; this only clears the busy UI state.
  static const Duration _commandTimeout = Duration(seconds: 30);

  @override
  void initState() {
    super.initState();
    // Listen to command state transitions outside the build phase so we
    // can safely show snackbars and invalidate providers.
    ref.listenManual(commandProvider, (prev, next) {
      _handleCommandTransition(next);
    });
  }

  @override
  void dispose() {
    _timeoutTimer?.cancel();
    super.dispose();
  }

  void _startTimeout() {
    _timeoutTimer?.cancel();
    _timeoutTimer = Timer(_commandTimeout, () {
      if (mounted && _sending) {
        setState(() => _sending = false);
        final ColorScheme colors = Theme.of(context).colorScheme;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.commandFailedRetry),
            backgroundColor: colors.error,
          ),
        );
      }
    });
  }

  void _cancelTimeout() {
    _timeoutTimer?.cancel();
    _timeoutTimer = null;
  }

  Future<void> _sendCommand(String commandType) async {
    setState(() => _sending = true);
    _startTimeout();
    try {
      await ref.read(commandProvider.notifier).sendCommand(
            machineId: widget.machineId,
            imei: widget.imei,
            commandType: commandType,
          );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(context.l10n.commandSentAwaitingAck)),
        );
      }
    } catch (_) {
      _cancelTimeout();
      if (mounted) {
        setState(() => _sending = false);
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

  /// Detects command lifecycle transitions to show success/failure snackbars
  /// and refresh the machine detail when a command completes.
  void _handleCommandTransition(CommandState cmdState) {
    final last = cmdState.lastCommand;
    if (last == null || last.machineId != widget.machineId) return;

    final wasPending = _lastPending;
    final isPending = cmdState.pending;
    _lastPending = isPending;

    // Transition: pending → terminal (DONE / FAILED / TIMEOUT)
    if (wasPending && !isPending && mounted) {
      _cancelTimeout();
      if (last.isDone) {
        // Success — refresh machine detail so the toggle settles to the
        // confirmed state from the REST snapshot.
        ref.invalidate(machineDetailProvider(widget.machineId));
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              last.commandType == 'FENCING_ON'
                  ? context.l10n.fencingTurnedOn
                  : context.l10n.fencingTurnedOff,
            ),
          ),
        );
      } else if (last.isFailed || last.isTimeout) {
        // Failure — show error, toggle reverts to the actual machine state.
        final ColorScheme colors = Theme.of(context).colorScheme;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(context.l10n.commandFailedRetry),
            backgroundColor: colors.error,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    // Watch the command socket so it connects and delivers real-time ACK
    // updates. Without this, the WebSocket never connects and the spinner
    // would spin forever.
    ref.watch(commandSocketProvider(widget.machineId));
    final cmdState = ref.watch(commandProvider);

    final last = cmdState.lastCommand;
    final cmdForThisMachine =
        (last != null && last.machineId == widget.machineId) ? last : null;

    // Determine the toggle display state.
    final bool isBusy = _sending || (cmdState.pending && cmdForThisMachine != null);
    final bool? turningOn;
    if (isBusy && cmdForThisMachine != null) {
      turningOn = cmdForThisMachine.commandType == 'FENCING_ON';
    } else {
      turningOn = null;
    }
    final bool commandFailed = cmdForThisMachine != null &&
        (cmdForThisMachine.isFailed || cmdForThisMachine.isTimeout);

    // Effective fencing state: confirmed machine state.
    final bool fencingOn = widget.isFencingOn;

    // Switch value: shows the target state while busy, otherwise the actual.
    final bool switchValue =
        isBusy ? (turningOn == true) : fencingOn;

    // Label and colors.
    final String label;
    final Color labelColor;
    final Color trackColor;
    final Color thumbColor;

    if (isBusy) {
      label = turningOn == true ? l10n.turningOn : l10n.turningOff;
      labelColor = Theme.of(context).colorScheme.onSurfaceVariant;
      trackColor = _semanticColor(context, StatusTone.warning);
      thumbColor = Colors.white;
    } else if (fencingOn) {
      label = l10n.fencingOn;
      labelColor = _semanticColor(context, StatusTone.success);
      trackColor = _semanticColor(context, StatusTone.success);
      thumbColor = Colors.white;
    } else {
      label = l10n.fencingOff;
      labelColor = Theme.of(context).colorScheme.onSurfaceVariant;
      trackColor = Theme.of(context).colorScheme.outline.withValues(alpha: 0.4);
      thumbColor = Theme.of(context).colorScheme.onSurfaceVariant;
    }

    return AppSurfaceCard(
      padding: const EdgeInsets.all(AppSpacing.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          // Section header row
          Row(
            children: <Widget>[
              Icon(
                fencingOn ? Icons.power_settings_new : Icons.power_off,
                size: 20,
                color: fencingOn
                    ? _semanticColor(context, StatusTone.success)
                    : Theme.of(context).colorScheme.onSurfaceVariant,
              ),
              const SizedBox(width: AppSpacing.sm),
              Flexible(
                child: Text(
                  l10n.fencingControl,
                  style: Theme.of(context).textTheme.titleSmall?.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.md),
          // Toggle row
          Row(
            children: <Widget>[
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Row(
                      children: <Widget>[
                        if (isBusy) ...<Widget>[
                          SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: _semanticColor(context, StatusTone.warning),
                            ),
                          ),
                          const SizedBox(width: AppSpacing.sm),
                        ],
                        Flexible(
                          child: Text(
                            label,
                            style: Theme.of(context)
                                .textTheme
                                .bodyLarge
                                ?.copyWith(fontWeight: FontWeight.w600, color: labelColor),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                    if (commandFailed) ...<Widget>[
                      const SizedBox(height: AppSpacing.xs),
                      Text(
                        l10n.commandFailedRetry,
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                              color: _semanticColor(context, StatusTone.danger),
                            ),
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              // The switch — disabled while busy or sending.
              Switch(
                value: switchValue,
                onChanged: (isBusy) ? null : (newValue) {
                  // newValue == true means user wants fencing ON.
                  _sendCommand(newValue ? 'FENCING_ON' : 'FENCING_OFF');
                },
                activeTrackColor: trackColor,
                inactiveThumbColor: thumbColor,
                inactiveTrackColor: trackColor,
              ),
            ],
          ),
        ],
      ),
    );
  }

  Color _semanticColor(BuildContext context, StatusTone tone) {
    final AppSemanticColors? semantic =
        Theme.of(context).extension<AppSemanticColors>();
    return semantic?.tone(tone) ?? Theme.of(context).colorScheme.primary;
  }
}
