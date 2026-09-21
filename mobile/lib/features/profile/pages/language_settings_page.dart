import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/locale/locale_controller.dart';
import 'package:yantrago/core/locale/locale_sync_provider.dart';
import 'package:yantrago/core/locale/supported_locales.dart';
import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Language Settings page — pick a language, then Save to apply it.
///
/// Phase 3: Save switches the UI locale immediately (the labels on this page
/// re-render in the new language) and persists the choice to device storage,
/// which survives logout. The existing best-effort backend preference update
/// (notification language) is kept until the robust account-scoped sync
/// arrives in Phase 5.
///
/// The selection is local until Save is pressed; Cancel/Back discards it.
class LanguageSettingsPage extends ConsumerStatefulWidget {
  const LanguageSettingsPage({super.key});

  @override
  ConsumerState<LanguageSettingsPage> createState() =>
      _LanguageSettingsPageState();
}

class _LanguageSettingsPageState extends ConsumerState<LanguageSettingsPage> {
  late String _selectedLocale;
  bool _initialized = false;
  bool _saving = false;

  @override
  Widget build(BuildContext context) {
    // The current UI locale is the source of truth for the picker.
    final currentCode = ref.watch(localeControllerProvider).locale.languageCode;
    final saveStatus = ref.watch(localeControllerProvider).saveStatus;
    // Phase 5: live server-sync state of the notification language.
    final syncState = ref.watch(localeSyncProvider);
    if (!_initialized) {
      _selectedLocale = currentCode;
      _initialized = true;
    }

    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;

    return Scaffold(
      appBar: AppBar(title: Text(context.l10n.languageSettings)),
      body: AppPageBody(
        scrollable: true,
        safeArea: false,
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            AppSurfaceCard(
              child: Column(
                children: kLanguageChoices.entries
                    .map((entry) => _LanguageTile(
                          label: entry.value,
                          code: entry.key,
                          selected: _selectedLocale == entry.key,
                          onTap: () => setState(
                              () => _selectedLocale = entry.key),
                        ))
                    .toList(),
              ),
            ),
            const SizedBox(height: 8),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: Text(
                context.l10n.languageNote,
                style: text.bodySmall?.copyWith(color: colors.onSurfaceVariant),
              ),
            ),
            // Persist failure is visible and retryable — the UI switch has
            // already happened, but restart persistence must not be assumed.
            if (saveStatus == LocaleSaveStatus.failed) ...[
              const SizedBox(height: 12),
              AppSurfaceCard(
                child: Row(
                  children: <Widget>[
                    Icon(Icons.cloud_off_outlined,
                        color: colors.error, size: 20),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        context.l10n.languageSaveFailed,
                        style: text.bodySmall
                            ?.copyWith(color: colors.onSurfaceVariant),
                      ),
                    ),
                    TextButton(
                      onPressed: () => ref
                          .read(localeControllerProvider.notifier)
                          .retrySave(),
                      child: Text(context.l10n.retry),
                    ),
                  ],
                ),
              ),
            ],
            // Phase 5: server-sync indicator — a pending/failed notification
            // language must never be reported as synced.
            if (syncState.status != LocaleSyncStatus.synced) ...[
              const SizedBox(height: 12),
              AppSurfaceCard(
                child: Row(
                  children: <Widget>[
                    Icon(Icons.sync_problem_outlined,
                        color: colors.tertiary, size: 20),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        context.l10n.languageSyncPending,
                        style: text.bodySmall
                            ?.copyWith(color: colors.onSurfaceVariant),
                      ),
                    ),
                    TextButton(
                      onPressed: () => ref
                          .read(localeSyncProvider.notifier)
                          .retry(),
                      child: Text(context.l10n.retry),
                    ),
                  ],
                ),
              ),
            ],
            const SizedBox(height: 24),
            Row(
              children: <Widget>[
                Expanded(
                  child: OutlinedButton(
                    onPressed: _saving
                        ? null
                        : () => Navigator.of(context).pop(),
                    child: Text(context.l10n.cancel),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton(
                    onPressed: _saving ||
                            _selectedLocale == currentCode
                        ? null
                        : () => _save(),
                    child: _saving
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child:
                                CircularProgressIndicator(strokeWidth: 2),
                          )
                        : Text(context.l10n.save),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _save() async {
    setState(() => _saving = true);

    // Switch the UI locale immediately and persist the device preference.
    final controller = ref.read(localeControllerProvider.notifier);
    await controller.setLocale(Locale(_selectedLocale));
    if (!mounted) return;

    // If persisting failed, stay on this page — the failed banner and Retry
    // are visible here, and the user has not been promised persistence.
    if (ref.read(localeControllerProvider).saveStatus ==
        LocaleSaveStatus.failed) {
      setState(() => _saving = false);
      return;
    }

    // Phase 5: durable account-scoped sync of the notification language.
    // Marks the pending intent and starts the PUT with retries; the banner
    // below reflects the live sync state. The page closes once the device
    // preference is confirmed — server sync continues in the background.
    final user = ref.read(currentUserProvider);
    await ref.read(localeSyncProvider.notifier).localeChanged(
          locale: _selectedLocale,
          userId: user?.id,
          organizationId: user?.organizationId,
        );
    if (mounted) {
      setState(() => _saving = false);
      Navigator.of(context).pop();
    }
  }
}

/// Language selection tile for the language settings page.
class _LanguageTile extends StatelessWidget {
  final String label;
  final String code;
  final bool selected;
  final VoidCallback onTap;

  const _LanguageTile({
    required this.label,
    required this.code,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 4),
        child: Row(
          children: <Widget>[
            Expanded(
              child: Text(label, style: text.bodyLarge),
            ),
            if (selected)
              Icon(Icons.check_circle, color: colors.primary, size: 22)
            else
              Icon(Icons.radio_button_unchecked,
                  color: colors.onSurfaceVariant, size: 22),
          ],
        ),
      ),
    );
  }
}
