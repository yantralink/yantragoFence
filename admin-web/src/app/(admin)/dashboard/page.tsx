'use client';

import { useQuery } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import { Card, StatCard } from '@/components/ui/card';

interface Machine {
  id: string;
  name: string;
  status: string;
}

export default function AdminDashboardPage() {
  const { data: machines, isLoading } = useQuery<Machine[]>({
    queryKey: ['machines'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ content: Machine[] }>('/machines');
      return data.content;
    },
  });

  const total = machines?.length ?? 0;
  const online = machines?.filter((m) => m.status === 'ONLINE' || m.status === 'FENCING_ON').length ?? 0;
  const fault = machines?.filter((m) => m.status === 'FAULT').length ?? 0;
  const offline = machines?.filter((m) => m.status === 'OFFLINE' || m.status === 'FENCING_OFF').length ?? 0;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
      {isLoading ? (
        <div className="text-gray-500">Loading...</div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard label="Total Machines" value={total} icon="⚡" color="text-blue-600" />
          <StatCard label="Online" value={online} icon="🟢" color="text-green-600" />
          <StatCard label="Offline" value={offline} icon="⚪" color="text-gray-600" />
          <StatCard label="Fault" value={fault} icon="🔴" color="text-red-600" />
        </div>
      )}
      <Card title="Recent Activity">
        <p className="text-sm text-gray-500">Machine commands and alerts will appear here.</p>
      </Card>
    </div>
  );
}
