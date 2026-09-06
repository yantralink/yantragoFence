'use client';

import { useMachines } from '@/hooks/use-machines';
import { Card, StatCard } from '@/components/ui/card';

export default function AdminDashboardPage() {
  const { data: machines, isLoading } = useMachines();

  const total = machines?.length ?? 0;
  const online = machines?.filter((m) => m.status === 'ONLINE' || m.status === 'FENCING_ON').length ?? 0;
  const fault = machines?.filter((m) => m.status === 'FAULT').length ?? 0;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
      {isLoading ? (
        <div className="text-gray-500">Loading...</div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <StatCard label="Total Machines" value={total} icon="⚡" color="text-blue-600" />
          <StatCard label="Online" value={online} icon="🟢" color="text-green-600" />
          <StatCard label="Fault" value={fault} icon="🔴" color="text-red-600" />
        </div>
      )}
      <Card title="Recent Commands">
        <p className="text-sm text-gray-500">Command history will appear here.</p>
      </Card>
    </div>
  );
}
