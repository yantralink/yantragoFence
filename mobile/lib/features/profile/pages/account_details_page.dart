import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/widgets/app_metadata_row.dart';
import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/profile/providers/profile_provider.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Account Details page — shows only the signed-in user's account
/// information (role, organization, phone, status).
class AccountDetailsPage extends ConsumerWidget {
  const AccountDetailsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(profileProvider);
    final l10n = context.l10n;

    return Scaffold(
      appBar: AppBar(title: Text(l10n.accountDetails)),
      body: AppPageBody(
        scrollable: true,
        safeArea: false,
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
        child: AppSurfaceCard(
          child: Column(
            children: <Widget>[
              AppMetadataRow(
                  label: l10n.fieldName,
                  value: user?.fullName ?? 'N/A'), // always-en: placeholder
              AppMetadataRow(
                  label: l10n.fieldEmail,
                  value: user?.email ?? 'N/A'), // always-en: placeholder
              AppMetadataRow(
                  label: l10n.fieldRole,
                  value:
                      user?.role ?? 'N/A'), // always-en: raw role / placeholder
              AppMetadataRow(
                label: l10n.fieldOrganization,
                value: user?.organizationName ??
                    user?.organizationId ??
                    'N/A', // always-en: org name/id / placeholder
              ),
              if (user?.phoneNumber != null)
                AppMetadataRow(
                    label: l10n.fieldPhone,
                    value: user!.phoneNumber), // always-en: phone number
              AppMetadataRow(
                label: l10n.detailStatus,
                value: (user?.active ?? false)
                    ? l10n.statusActive
                    : l10n.statusInactive,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
