import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/auth/auth_provider.dart';
import 'package:yantrago/models/user.dart';

/// Profile provider — exposes current user data.
final profileProvider = Provider<User?>((ref) {
  return ref.watch(currentUserProvider);
});
