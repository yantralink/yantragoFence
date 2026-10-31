import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_radii.dart';
import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/theme/app_spacing.dart';
import 'package:yantrago/core/theme/machine_card_theme.dart';
import 'package:yantrago/core/utils/date_utils.dart';
import 'package:yantrago/core/widgets/app_status_badge.dart';
import 'package:yantrago/features/machines/widgets/machine_card_location.dart';
import 'package:yantrago/features/machines/widgets/machine_status_labels.dart';
import 'package:yantrago/l10n/l10n.dart';
import 'package:yantrago/models/machine.dart';

/// Machine card — list item for a machine.
///
/// The blush surface is decorative; badges and text convey actual state.
/// Tapping anywhere, including the location row, opens machine details.
class MachineCard extends StatelessWidget {
  final Machine machine;
  final VoidCallback? onTap;

  const MachineCard({super.key, required this.machine, this.onTap});

  @override
  Widget build(BuildContext context) {
    final style = MachineCardTheme.of(context);
    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: AppRadii.cardRadius,
        boxShadow: [
          BoxShadow(
            color: style.shadow,
            blurRadius: AppSpacing.md,
            offset: const Offset(0, AppSpacing.xs),
          ),
        ],
      ),
      child: Material(
        color: style.background,
        shape: RoundedRectangleBorder(
          borderRadius: AppRadii.cardRadius,
          side: BorderSide(color: style.border),
        ),
        clipBehavior: Clip.antiAlias,
        child: InkWell(onTap: onTap, child: _content(context, style)),
      ),
    );
  }

  Widget _content(BuildContext context, MachineCardTheme style) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.all(AppSpacing.cardPadding),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _MachineIdentity(machine: machine),
              const SizedBox(height: AppSpacing.md),
              Divider(height: 1, color: style.border),
              const SizedBox(height: AppSpacing.md),
              _MachineState(machine: machine),
              const SizedBox(height: AppSpacing.sm),
              MachineCardLocation(machine: machine),
            ],
          ),
        ),
        ColoredBox(
          color: style.accent,
          child: const SizedBox(height: AppSpacing.xs),
        ),
      ],
    );
  }
}

class _MachineIdentity extends StatelessWidget {
  final Machine machine;
  const _MachineIdentity({required this.machine});

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      // Move the badge below the identity before names or translated labels
      // become squeezed at narrow widths / large accessibility text sizes.
      final stackBadge =
          constraints.maxWidth < 300 ||
          MediaQuery.textScalerOf(context).scale(14) > 18;
      return Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const _IconBox(),
          const SizedBox(width: AppSpacing.md),
          Expanded(child: _nameAndId(context, stackBadge)),
          if (!stackBadge) ...[
            const SizedBox(width: AppSpacing.sm),
            _badge(context),
          ],
        ],
      );
    },
  );

  Widget _nameAndId(BuildContext context, bool stackBadge) {
    final text = Theme.of(context).textTheme;
    final id = machine.machineId.isNotEmpty
        ? machine.machineId
        : (machine.imei ?? '');
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(machine.name, style: text.titleMedium),
        if (id.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.xs),
          Text(id, style: text.bodySmall), // always-en: machine ID / IMEI
        ],
        if (stackBadge) ...[
          const SizedBox(height: AppSpacing.sm),
          _badge(context),
        ],
      ],
    );
  }

  Widget _badge(BuildContext context) => AppStatusBadge(
    // Connectivity stays independent of both fence state and decoration.
    label: machine.isOnlineStatus
        ? context.l10n.statusOnline
        : context.l10n.statusOffline,
    tone: machine.isOnlineStatus ? StatusTone.info : StatusTone.neutral,
    dot: true,
  );
}

class _MachineState extends StatelessWidget {
  final Machine machine;
  const _MachineState({required this.machine});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(
          machine.isFault ? Icons.warning_amber_rounded : Icons.fence,
          color: machine.isFault
              ? theme.colorScheme.error
              : theme.colorScheme.onSurfaceVariant,
        ),
        const SizedBox(width: AppSpacing.sm),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                machineStateLabel(context.l10n, machine),
                style: theme.textTheme.bodyLarge,
              ),
              const SizedBox(height: AppSpacing.xs),
              _lastSeen(context),
            ],
          ),
        ),
        Icon(Icons.chevron_right, color: theme.colorScheme.onSurfaceVariant),
      ],
    );
  }

  Widget _lastSeen(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(
          Icons.access_time,
          size: AppSpacing.lg,
          color: theme.colorScheme.onSurfaceVariant,
        ),
        const SizedBox(width: AppSpacing.xs),
        Expanded(
          child: Text(
            context.l10n.lastSeenAt(
              relativeTime(context.l10n, machine.lastSeenAt),
            ),
            style: theme.textTheme.bodySmall,
          ),
        ),
      ],
    );
  }
}

class _IconBox extends StatelessWidget {
  const _IconBox();
  static const double _size = 40;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final semantic = theme.extension<AppSemanticColors>();
    return Container(
      width: _size,
      height: _size,
      decoration: BoxDecoration(
        color: semantic?.primarySoft ?? theme.colorScheme.primaryContainer,
        borderRadius: AppRadii.buttonRadius,
      ),
      child: Icon(Icons.fence, color: theme.colorScheme.primary),
    );
  }
}
