import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/locale/locale_controller.dart';
import 'package:yantrago/core/locale/supported_locales.dart';

/// Compact language picker for the login screen (pre-login language choice).
///
/// Shows native language names (English / हिन्दी / मराठी — always in their
/// own script, never translated via ARB). Tapping a choice switches the UI
/// language immediately and persists the device-level preference, so the
/// selection survives logout and account switches (Phase 3 policy).
class LanguagePickerRow extends ConsumerWidget {
  const LanguagePickerRow({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final currentCode = ref.watch(localeControllerProvider).locale.languageCode;
    final colors = Theme.of(context).colorScheme;

    return Wrap(
      alignment: WrapAlignment.center,
      spacing: 8,
      children: kLanguageChoices.entries
          .map((entry) => ChoiceChip(
                label: Text(entry.value),
                selected: currentCode == entry.key,
                selectedColor: colors.primaryContainer,
                showCheckmark: false,
                visualDensity: VisualDensity.compact,
                onSelected: (_) {
                  ref
                      .read(localeControllerProvider.notifier)
                      .setLocale(Locale(entry.key));
                },
              ))
          .toList(),
    );
  }
}
