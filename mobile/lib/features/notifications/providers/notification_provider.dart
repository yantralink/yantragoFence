import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/config/app_config.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/models/notification_inbox.dart';
import 'package:yantrago/models/notification_preference.dart';

/// Notification inbox filter state.
class NotificationFilter {
  final bool unreadOnly;
  final String? alertType;
  final int page;
  final int size;

  const NotificationFilter({
    this.unreadOnly = false,
    this.alertType,
    this.page = 0,
    this.size = 20,
  });

  NotificationFilter copyWith({
    bool? unreadOnly,
    String? alertType,
    int? page,
    int? size,
    bool clearAlertType = false,
  }) {
    return NotificationFilter(
      unreadOnly: unreadOnly ?? this.unreadOnly,
      alertType: clearAlertType ? null : (alertType ?? this.alertType),
      page: page ?? this.page,
      size: size ?? this.size,
    );
  }

  Map<String, dynamic> toQueryParams() {
    final params = <String, dynamic>{
      'page': page,
      'size': size,
    };
    if (unreadOnly) params['unreadOnly'] = 'true';
    if (alertType != null) params['alertType'] = alertType;
    return params;
  }
}

/// Filter state notifier — drives the inbox list provider.
class NotificationFilterNotifier extends StateNotifier<NotificationFilter> {
  NotificationFilterNotifier() : super(const NotificationFilter());

  void setUnreadOnly(bool value) =>
      state = state.copyWith(unreadOnly: value, page: 0);

  void setAlertType(String? value) =>
      state = value == null ? state.copyWith(clearAlertType: true, page: 0) : state.copyWith(alertType: value, page: 0);

  void nextPage() => state = state.copyWith(page: state.page + 1);
  void previousPage() =>
      state = state.copyWith(page: state.page > 0 ? state.page - 1 : 0);
  void resetPage() => state = state.copyWith(page: 0);
}

final notificationFilterProvider =
    StateNotifierProvider<NotificationFilterNotifier, NotificationFilter>(
  (ref) => NotificationFilterNotifier(),
);

/// Paginated inbox list provider.
/// Re-fetches when the authenticated user changes or filter changes.
final notificationInboxProvider =
    FutureProvider<NotificationInboxPage>((ref) async {
  // Re-fetch when the authenticated user changes (clears state on account switch)
  final user = ref.watch(currentUserProvider);
  if (user == null) return NotificationInboxPage.empty();

  final filter = ref.watch(notificationFilterProvider);
  final dio = ref.watch(apiClientProvider);

  final response = await dio.get(
    AppConfig.notificationsInboxEndpoint,
    queryParameters: filter.toQueryParams(),
  );

  final data = response.data;
  final List<dynamic> content = data is Map<String, dynamic>
      ? data['content'] as List
      : data as List;

  final items = content
      .map((n) => NotificationInbox.fromJson(n as Map<String, dynamic>))
      .toList();

  // Avoid duplicate list items across pagination and live refresh
  final deduped = _deduplicateById(items);

  final totalElements =
      data is Map<String, dynamic> ? data['totalElements'] as int? : deduped.length;
  final totalPages =
      data is Map<String, dynamic> ? data['totalPages'] as int? : 1;

  return NotificationInboxPage(
    items: deduped,
    page: filter.page,
    size: filter.size,
    totalElements: totalElements ?? deduped.length,
    totalPages: totalPages ?? 1,
  );
});

/// Unread count provider — for badge display.
/// Invalidated by socket events and manual refresh.
final unreadCountProvider = FutureProvider<int>((ref) async {
  final user = ref.watch(currentUserProvider);
  if (user == null) return 0;

  final dio = ref.watch(apiClientProvider);
  final response =
      await dio.get(AppConfig.notificationsUnreadCountEndpoint);
  final data = response.data;
  if (data is Map<String, dynamic>) {
    return data['unreadCount'] as int? ?? 0;
  }
  return 0;
});

/// Notification detail provider — fetches a single inbox item.
final notificationDetailProvider =
    FutureProvider.family<NotificationInbox?, String>((ref, id) async {
  final user = ref.watch(currentUserProvider);
  if (user == null) return null;

  final dio = ref.watch(apiClientProvider);
  final response =
      await dio.get('${AppConfig.notificationsInboxEndpoint}/$id');
  return NotificationInbox.fromJson(response.data as Map<String, dynamic>);
});

/// Mark-read notifier — marks a single notification as read (optimistic).
class MarkReadNotifier extends StateNotifier<AsyncValue<void>> {
  final Ref _ref;
  final String _notificationId;

  MarkReadNotifier(this._ref, this._notificationId)
      : super(const AsyncData(null));

  Future<void> markRead() async {
    state = const AsyncLoading();
    try {
      final dio = _ref.read(apiClientProvider);
      await dio.post(
        '${AppConfig.notificationsInboxEndpoint}/$_notificationId/read',
      );
      // Invalidate unread count and inbox list
      _ref.invalidate(unreadCountProvider);
      _ref.invalidate(notificationInboxProvider);
      state = const AsyncData(null);
    } catch (e, st) {
      state = AsyncError(e, st);
    }
  }
}

final markReadProvider = StateNotifierProvider.family<
    MarkReadNotifier, AsyncValue<void>, String>(
  (ref, id) => MarkReadNotifier(ref, id),
);

/// Mark-all-read notifier.
class MarkAllReadNotifier extends StateNotifier<AsyncValue<int>> {
  final Ref _ref;

  MarkAllReadNotifier(this._ref) : super(const AsyncData(0));

  Future<void> markAllRead() async {
    state = const AsyncLoading();
    try {
      final dio = _ref.read(apiClientProvider);
      final response =
          await dio.post('${AppConfig.notificationsInboxEndpoint}/read-all');
      final count = response.data is Map<String, dynamic>
          ? response.data['markedRead'] as int
          : 0;
      _ref.invalidate(unreadCountProvider);
      _ref.invalidate(notificationInboxProvider);
      state = AsyncData(count);
    } catch (e, st) {
      state = AsyncError(e, st);
    }
  }
}

final markAllReadProvider =
    StateNotifierProvider<MarkAllReadNotifier, AsyncValue<int>>(
  (ref) => MarkAllReadNotifier(ref),
);

/// Acknowledge notifier — explicit user action separate from mark-read.
/// Per notification plan Phase 4: authorized acknowledgement.
class AcknowledgeNotifier extends StateNotifier<AsyncValue<void>> {
  final Ref _ref;
  final String _notificationId;

  AcknowledgeNotifier(this._ref, this._notificationId)
      : super(const AsyncData(null));

  Future<void> acknowledge() async {
    state = const AsyncLoading();
    try {
      final dio = _ref.read(apiClientProvider);
      await dio.post(
        '${AppConfig.notificationsInboxEndpoint}/$_notificationId/acknowledge',
      );
      // Invalidate providers to re-fetch
      _ref.invalidate(unreadCountProvider);
      _ref.invalidate(notificationInboxProvider);
      _ref.invalidate(notificationDetailProvider(_notificationId));
      state = const AsyncData(null);
    } catch (e, st) {
      state = AsyncError(e, st);
    }
  }
}

final acknowledgeProvider = StateNotifierProvider.family<
    AcknowledgeNotifier, AsyncValue<void>, String>(
  (ref, id) => AcknowledgeNotifier(ref, id),
);

/// Notification preferences list provider.
final notificationPreferencesProvider =
    FutureProvider<List<NotificationPreference>>((ref) async {
  final user = ref.watch(currentUserProvider);
  if (user == null) return [];

  final dio = ref.watch(apiClientProvider);
  final response =
      await dio.get(AppConfig.notificationsPreferencesEndpoint);
  final List<dynamic> list = response.data is Map<String, dynamic>
      ? response.data['content'] as List
      : response.data as List;
  return list
      .map((p) => NotificationPreference.fromJson(p as Map<String, dynamic>))
      .toList();
});

/// Set preference notifier.
class SetPreferenceNotifier extends StateNotifier<AsyncValue<void>> {
  final Ref _ref;

  SetPreferenceNotifier(this._ref) : super(const AsyncData(null));

  Future<void> setPreference({
    required String channel,
    String? alertType,
    required bool isEnabled,
    bool? pushEnabled,
  }) async {
    state = const AsyncLoading();
    try {
      final dio = _ref.read(apiClientProvider);
      final data = <String, dynamic>{
        'channel': channel,
        'alertType': alertType,
        'isEnabled': isEnabled,
      };
      // SIG 22: send pushEnabled in the request body so the backend can
      // track the push-specific enabled flag per alert type.
      if (pushEnabled != null) {
        data['pushEnabled'] = pushEnabled;
      }
      await dio.put(
        AppConfig.notificationsPreferencesEndpoint,
        data: data,
      );
      _ref.invalidate(notificationPreferencesProvider);
      state = const AsyncData(null);
    } catch (e, st) {
      state = AsyncError(e, st);
    }
  }
}

final setPreferenceProvider =
    StateNotifierProvider<SetPreferenceNotifier, AsyncValue<void>>(
  (ref) => SetPreferenceNotifier(ref),
);

/// Deduplicate inbox items by ID to avoid duplicate list items across
/// pagination and live refresh.
List<NotificationInbox> _deduplicateById(List<NotificationInbox> items) {
  final seen = <String>{};
  return items.where((item) {
    if (seen.contains(item.id)) return false;
    seen.add(item.id);
    return true;
  }).toList();
}

/// Paginated inbox result.
class NotificationInboxPage {
  final List<NotificationInbox> items;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;

  const NotificationInboxPage({
    required this.items,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
  });

  factory NotificationInboxPage.empty() => const NotificationInboxPage(
        items: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      );

  bool get hasNext => page < totalPages - 1;
  bool get hasPrevious => page > 0;
  bool get isEmpty => items.isEmpty;
}
