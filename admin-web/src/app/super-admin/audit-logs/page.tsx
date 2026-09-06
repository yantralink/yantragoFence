import { Card } from '@/components/ui/card';

export default function AuditLogsPage() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Audit Logs</h1>
      <Card title="Audit Trail">
        <p className="text-sm text-gray-500">
          All sensitive operations are logged for compliance. Filter by user, action, entity type,
          and date range.
        </p>
      </Card>
    </div>
  );
}
