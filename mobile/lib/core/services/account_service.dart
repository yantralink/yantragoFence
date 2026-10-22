import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/config/app_config.dart';
import 'package:yantrago/core/network/api_client.dart';

final accountServiceProvider =
    Provider<AccountService>((ref) => AccountService(ref.watch(apiClientProvider)));

/// PLAY-06: account self-service operations.
///
/// Accounts are admin-provisioned (no in-app signup), so actual erasure is
/// performed by an organisation administrator. The app exposes a
/// deletion-request path that the backend records in audit_logs and
/// notifies to the configured privacy mailbox.
class AccountService {
  AccountService(this._dio);

  final Dio _dio;

  /// Submits an account-deletion request for the signed-in user.
  ///
  /// Identity comes from the JWT carried by the auth interceptor — the
  /// client never sends a user/org id. Returns true when the backend
  /// accepted the request (202).
  Future<bool> requestAccountDeletion() async {
    try {
      final response =
          await _dio.post(AppConfig.accountDeletionRequestEndpoint);
      return response.statusCode == 202;
    } on DioException {
      return false;
    }
  }
}
