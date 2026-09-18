import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/profile/providers/profile_provider.dart';

/// Supported app locales. Keep in sync with the notification templates'
/// supported languages.
const _supportedLocales = <String, String>{
  'en': 'English',
  'hi': 'हिंदी (Hindi)',
  'mr': 'मराठी (Marathi)',
};

/// Language Settings page — pick a language, then Save to apply it.
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
    final user = ref.watch(profileProvider);
    final savedLocale = user?.preferredLocale ?? 'en';
    if (!_initialized) {
      _selectedLocale = savedLocale;
      _initialized = true;
    }

    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;

    return Scaffold(
      appBar: AppBar(title: const Text('Language Settings')),
      body: AppPageBody(
        scrollable: true,
        safeArea: false,
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            AppSurfaceCard(
              child: Column(
                children: _supportedLocales.entries
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
                'Notifications will be delivered in your selected language.',
                style: text.bodySmall?.copyWith(color: colors.onSurfaceVariant),
              ),
            ),
            const SizedBox(height: 24),
            Row(
              children: <Widget>[
                Expanded(
                  child: OutlinedButton(
                    onPressed: _saving
                        ? null
                        : () => Navigator.of(context).pop(),
                    child: const Text('Cancel'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton(
                    onPressed: _saving ||
                            _selectedLocale == savedLocale
                        ? null
                        : () => _save(),
                    child: _saving
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child:
                                CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Text('Save'),
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
    await ref
        .read(authStateProvider.notifier)
        .updatePreferredLocale(_selectedLocale);
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
