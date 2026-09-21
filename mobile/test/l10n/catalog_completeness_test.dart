import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Catalog completeness tests for the ARB localization files.
///
/// Pure JSON validation (no Flutter bindings): verifies that app_hi.arb and
/// app_mr.arb have exact structural parity with app_en.arb — same keys in the
/// same order, verbatim `@key` metadata, matching placeholder tokens, ICU
/// plural branches (every plural must have an `other` branch), no duplicate
/// keys, and a correct `@@locale`.
void main() {
  final root = _findPackageRoot();
  final l10nDir = '${root.path}/lib/l10n';

  final enRaw = File('$l10nDir/app_en.arb').readAsStringSync();
  final en = json.decode(enRaw) as Map<String, dynamic>;
  final enKeys = en.keys.where((k) => !k.startsWith('@')).toList();

  for (final locale in const ['hi', 'mr']) {
    group('app_$locale.arb', () {
      late final String raw;
      late final Map<String, dynamic> arb;

      setUpAll(() {
        final file = File('$l10nDir/app_$locale.arb');
        expect(
          file.existsSync(),
          isTrue,
          reason: 'Missing catalog file: ${file.path}',
        );
        raw = file.readAsStringSync();
        arb = json.decode(raw) as Map<String, dynamic>;
      });

      test('declares the correct @@locale', () {
        expect(
          arb['@@locale'],
          locale,
          reason: '$locale: "@@locale" must be "$locale"',
        );
      });

      test('has no missing or extra message keys vs English', () {
        final keys = _messageKeys(arb);
        final missing = enKeys.where((k) => !keys.contains(k)).toList();
        final extra = keys.where((k) => !enKeys.contains(k)).toList();
        expect(missing, isEmpty, reason: '$locale: missing message keys');
        expect(extra, isEmpty, reason: '$locale: extra message keys');
      });

      test('keeps the same key order as English', () {
        expect(_messageKeys(arb), orderedEquals(enKeys));
      });

      test('has no duplicate top-level keys in the raw text', () {
        final duplicates = _duplicateTopLevelKeys(raw);
        expect(
          duplicates,
          isEmpty,
          reason:
              '$locale: duplicate top-level keys (json.decode drops '
              'duplicates silently, so these must be fixed in the file): '
              '${duplicates.join(', ')}',
        );
      });

      test('copies @key metadata verbatim from English', () {
        final problems = <String>[];
        for (final key in enKeys) {
          final meta = '@$key';
          if (!arb.containsKey(meta)) {
            problems.add('$locale.$meta: metadata block missing');
          } else if (arb[meta].toString() != en[meta].toString()) {
            problems.add('$locale.$meta: metadata differs from English');
          }
        }
        expect(problems, isEmpty, reason: problems.join('\n'));
      });

      test('messages match English placeholders and plural structure', () {
        final problems = <String>[];
        for (final key in enKeys) {
          final value = arb[key];
          if (value is! String) {
            problems.add('$locale.$key: value must be a String');
            continue;
          }
          if (value == key) {
            problems.add('$locale.$key: value equals its own key name '
                '(untranslated placeholder entry?)');
          }

          // Placeholder tokens must match the English message exactly.
          final enTokens = _placeholderTokens(en[key] as String).toSet();
          final tokens = _placeholderTokens(value).toSet();
          if (!_sameSet(tokens, enTokens)) {
            problems.add('$locale.$key: placeholder tokens '
                '${tokens.toList()..sort()} != English ${enTokens.toList()..sort()}');
          }

          // ...and the English message must match its own declared
          // placeholders (guards against a broken template).
          final meta = en['@$key'] as Map<String, dynamic>?;
          final declared = ((meta?['placeholders'] as Map<String, dynamic>?) ?? {})
              .keys
              .toSet();
          if (!_sameSet(enTokens, declared)) {
            problems.add('en.$key: declared placeholders '
                '${declared.toList()..sort()} != message tokens ${enTokens.toList()..sort()}');
          }

          // ICU plural structure must mirror English, always with `other`.
          final enIsPlural = _pluralPattern.hasMatch(en[key] as String);
          if (enIsPlural) {
            if (!_pluralPattern.hasMatch(value)) {
              problems.add('$locale.$key: English uses ICU plural, translation does not');
              continue;
            }
            if (!_pluralOtherPattern.hasMatch(value)) {
              problems.add('$locale.$key: ICU plural has no `other` branch');
            }
            final enSelectors = _pluralSelectors(en[key] as String).toSet();
            final selectors = _pluralSelectors(value).toSet();
            if (!_sameSet(selectors, enSelectors)) {
              problems.add('$locale.$key: plural selectors '
                  '${selectors.toList()..sort()} != English ${enSelectors.toList()..sort()}');
            }
          } else if (_pluralPattern.hasMatch(value)) {
            problems.add('$locale.$key: English has no ICU plural, translation does');
          }
        }
        expect(problems, isEmpty, reason: problems.join('\n'));
      });
    });
  }
}

final _pluralPattern = RegExp(r',\s*plural\s*,');
final _pluralOtherPattern = RegExp(r'other\s*\{');
final _pluralSelectorPattern = RegExp(r'(?:=\d+|one|other)\s*\{');
final _placeholderPattern = RegExp(r'\{([A-Za-z_][A-Za-z0-9_]*)(\s*,[^}]*)?\}');

/// Message keys are everything that is not `@key` metadata or `@@locale`.
List<String> _messageKeys(Map<String, dynamic> arb) =>
    arb.keys.where((k) => !k.startsWith('@')).toList();

/// Placeholder names used by an ICU message, including the plural argument
/// itself (`{n, plural, ...}` contributes `n`).
Set<String> _placeholderTokens(String message) =>
    _placeholderPattern.allMatches(message).map((m) => m.group(1)!).toSet();

/// ICU plural branch selectors (`=0`, `=1`, `one`, `other`) of a message.
Set<String> _pluralSelectors(String message) =>
    _pluralSelectorPattern.allMatches(message).map((m) => m.group(1)!).toSet();

bool _sameSet(Set<String> a, Set<String> b) =>
    a.length == b.length && a.containsAll(b);

/// Scans raw JSON text and returns every object key at the top nesting level,
/// including duplicates. `json.decode` silently drops duplicate keys, so this
/// depth-aware raw scan is the only reliable way to detect them.
List<String> _duplicateTopLevelKeys(String raw) {
  final all = <String>[];
  final keyBuffer = StringBuffer();
  var depth = 0;
  var inString = false;
  var escaped = false;
  for (var i = 0; i < raw.length; i++) {
    final ch = raw[i];
    if (inString) {
      if (escaped) {
        escaped = false;
      } else if (ch == r'\') {
        escaped = true;
      } else if (ch == '"') {
        inString = false;
        // A string immediately followed by ':' is an object key. Only record
        // it at depth 1 (direct children of the outermost object).
        if (depth == 1) {
          var j = i + 1;
          while (j < raw.length && raw[j] == ' ') {
            j++;
          }
          if (j < raw.length && raw[j] == ':') all.add(keyBuffer.toString());
        }
        keyBuffer.clear();
      } else {
        keyBuffer.write(ch);
      }
      continue;
    }
    if (ch == '"') {
      inString = true;
      keyBuffer.clear();
      continue;
    }
    if (ch == '{' || ch == '[') {
      depth++;
    } else if (ch == '}' || ch == ']') {
      depth--;
    }
  }
  final seen = <String>{};
  final duplicates = <String>{};
  for (final key in all) {
    if (!seen.add(key)) duplicates.add(key);
  }
  return duplicates.toList();
}

/// Walks up from the current directory until it finds the mobile package root
/// (the directory containing both pubspec.yaml and lib/l10n), so the test
/// works regardless of the directory `flutter test` is invoked from.
Directory _findPackageRoot() {
  var dir = Directory.current;
  while (true) {
    final isRoot = File('${dir.path}/pubspec.yaml').existsSync() &&
        Directory('${dir.path}/lib/l10n').existsSync();
    if (isRoot) return dir;
    final parent = dir.parent;
    if (parent.path == dir.path) {
      fail('Could not locate the mobile package root (a directory with '
          'pubspec.yaml and lib/l10n) starting from ${Directory.current.path}');
    }
    dir = parent;
  }
}
