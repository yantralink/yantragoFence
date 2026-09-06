import { Card } from '@/components/ui/card';

export default function AdminAlertsPage() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Alerts</h1>
      <Card title="Device Alerts">
        <p className="text-sm text-gray-500">
          View and acknowledge device alerts. Filter by severity, device, and date range.
        </p>
      </Card>
    </div>
  );
}
