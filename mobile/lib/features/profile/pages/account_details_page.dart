import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/widgets/app_metadata_row.dart';
import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/profile/providers/profile_provider.dart';

/// Account Details page — shows only the signed-in user's account
/// information (role, organization, phone, status).
class AccountDetailsPage extends ConsumerWidget {
  const AccountDetailsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(profileProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Account Details')),
      body: AppPageBody(
        scrollable: true,
        safeArea: false,
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
        child: AppSurfaceCard(
          child: Column(
            children: <Widget>[
              AppMetadataRow(label: 'Name', value: user?.fullName ?? 'N/A'),
              AppMetadataRow(label: 'Email', value: user?.email ?? 'N/A'),
              AppMetadataRow(label: 'Role', value: user?.role ?? 'N/A'),
              AppMetadataRow(
                label: 'Organization',
                value: user?.organizationName ??
                    user?.organizationId ??
                    'N/A',
              ),
              if (user?.phoneNumber != null)
                AppMetadataRow(label: 'Phone', value: user!.phoneNumber),
              AppMetadataRow(
                label: 'Status',
                value: (user?.active ?? false) ? 'Active' : 'Inactive',
              ),
            ],
          ),
        ),
      ),
    );
  }
}
