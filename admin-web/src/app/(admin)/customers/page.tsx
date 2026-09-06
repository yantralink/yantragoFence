import { Card } from '@/components/ui/card';

export default function AdminCustomersPage() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Customers</h1>
      <Card title="Customer Management">
        <p className="text-sm text-gray-500">
          Manage your customers and their machine assignments.
        </p>
      </Card>
    </div>
  );
}
