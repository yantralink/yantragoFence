import { Card } from '@/components/ui/card';

export default function AdminsPage() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Admins</h1>
      <Card title="Admin User Management">
        <p className="text-sm text-gray-500">
          Manage admin users across organizations. Create, edit, and revoke admin access.
        </p>
      </Card>
    </div>
  );
}
