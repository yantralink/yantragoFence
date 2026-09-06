import { Card } from '@/components/ui/card';

export default function OrganizationsPage() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Organizations</h1>
      <Card title="Organization Management">
        <p className="text-sm text-gray-500">
          Super admins can manage all organizations. This page will list all organizations
          with create, edit, and deactivate actions.
        </p>
      </Card>
    </div>
  );
}
