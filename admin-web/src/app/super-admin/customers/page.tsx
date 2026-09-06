import { Card } from '@/components/ui/card';

export default function CustomersPage() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Customers</h1>
      <Card title="Customer Management">
        <p className="text-sm text-gray-500">
          Manage customers and their associated machines. View customer details, contact info,
          and machine assignments.
        </p>
      </Card>
    </div>
  );
}
