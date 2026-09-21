import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:yantrago/core/auth/auth_state.dart';
import 'package:yantrago/core/network/network_error_messages.dart';
import 'package:yantrago/core/utils/validation_error_messages.dart';
import 'package:yantrago/core/utils/validators.dart';
import 'package:yantrago/core/widgets/app_action_button.dart';
import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/language_picker_row.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Login page — full implementation with form validation and auth flow.
///
/// Accepts either email or phone number as the login identifier.
/// Customers log in with their phone number; admins log in with email.
///
/// Per AGENTS.md rule 9: sensitive operations require authorization.
/// Per AGENTS.md rule 12: production features require validation, error handling, logging.
class LoginPage extends ConsumerStatefulWidget {
  const LoginPage({super.key});

  @override
  ConsumerState<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends ConsumerState<LoginPage> {
  final _formKey = GlobalKey<FormState>();
  final _identifierController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _obscurePassword = true;
  bool _isLoading = false;

  @override
  void dispose() {
    _identifierController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _handleLogin() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isLoading = true);

    await ref.read(authStateProvider.notifier).login(
          email: _identifierController.text.trim(),
          password: _passwordController.text,
        );

    if (!mounted) return;
    setState(() => _isLoading = false);

    final l10n = context.l10n;
    final state = ref.read(authStateProvider);
    if (state is AuthError) {
      _showErrorSnackBar(networkErrorMessage(l10n, state.code));
    } else if (state is Authenticated) {
      context.go('/app/machines');
    }
  }

  void _showErrorSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    // Listen to auth state changes for error handling
    ref.listen<AuthState>(authStateProvider, (previous, next) {
      if (next is AuthError && mounted) {
        _showErrorSnackBar(networkErrorMessage(l10n, next.code));
      }
    });

    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;
    final l10n = context.l10n;

    // Map a validation code to its localized message inside the widget
    // layer — validators themselves stay context-free.
    String? validate(String? value, ValidationErrorCode? code) =>
        code == null ? null : validationErrorMessage(l10n, code);

    return Scaffold(
      body: AppPageBody(
        safeArea: true,
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Form(
              key: _formKey,
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  // Logo
                  Image.asset(
                    'assets/images/logo.png',
                    width: 120,
                    height: 120,
                    fit: BoxFit.contain,
                  ),
                  const SizedBox(height: 16),
                  Text(
                    'YantraGO', // always-en: brand name
                    textAlign: TextAlign.center,
                    style: text.headlineMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: colors.primary,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    l10n.splashTagline,
                    textAlign: TextAlign.center,
                    style: text.bodyMedium?.copyWith(
                      color: colors.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 48),

                  // Identifier field (email or phone)
                  TextFormField(
                    controller: _identifierController,
                    keyboardType: TextInputType.text,
                    textInputAction: TextInputAction.next,
                    autocorrect: false,
                    decoration: InputDecoration(
                      labelText: l10n.loginIdentifierLabel,
                      prefixIcon: const Icon(Icons.person_outlined),
                      hintText: l10n.loginIdentifierHint,
                    ),
                    validator: (v) =>
                        validate(v, Validators.required(v)),
                  ),
                  const SizedBox(height: 16),

                  // Password field
                  TextFormField(
                    controller: _passwordController,
                    obscureText: _obscurePassword,
                    textInputAction: TextInputAction.done,
                    onFieldSubmitted: (_) => _handleLogin(),
                    decoration: InputDecoration(
                      labelText: l10n.loginPasswordLabel,
                      prefixIcon: const Icon(Icons.lock_outlined),
                      suffixIcon: IconButton(
                        icon: Icon(
                          _obscurePassword
                              ? Icons.visibility_outlined
                              : Icons.visibility_off_outlined,
                        ),
                        onPressed: () {
                          setState(() {
                            _obscurePassword = !_obscurePassword;
                          });
                        },
                      ),
                    ),
                    validator: (v) =>
                        validate(v, Validators.password(v)),
                  ),
                  const SizedBox(height: 24),

                  // Login button
                  AppActionButton(
                    label: l10n.loginButton,
                    style: AppActionButtonStyle.primary,
                    busy: _isLoading,
                    onPressed: _isLoading ? null : _handleLogin,
                  ),
                  const SizedBox(height: 16),

                  // Info text
                  Text(
                    l10n.loginHint,
                    textAlign: TextAlign.center,
                    style: text.bodySmall?.copyWith(
                      color: colors.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 16),

                  // Pre-login language picker — native names, immediate
                  // switch, device-persisted (survives logout).
                  const LanguagePickerRow(),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
