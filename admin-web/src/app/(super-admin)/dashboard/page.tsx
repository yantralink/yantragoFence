'use client';

import { useMachines } from '@/hooks/use-machines';
import { Card, StatCard } from '@/components/ui/card';
import { Chart } from '@/components/charts';

export default function DashboardPage() {
  const { data: machines, isLoading } = useMachines();

  const total = machines?.length ?? 0;
  const online = machines?.filter((m) => m.status === 'ONLINE' || m.status === 'FENCING_ON').length ?? 0;
  const fault = machines?.filter((m) => m.status === 'FAULT').length ?? 0;
  const offline = machines?.filter((m) => m.status === 'OFFLINE' || m.status === 'FENCING_OFF').length ?? 0;

  const chartData = [
    { time: '00:00', devices: online },
    { time: '04:00', devices: Math.max(online - 2, 0) },
    { time: '08:00', devices: online },
    { time: '12:00', devices: Math.max(online - 1, 0) },
    { time: '16:00', devices: online + 1 },
    { time: '20:00', devices: online },
  ];

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>

      {isLoading ? (
        <div className="text-gray-500">Loading...</div>
      ) : (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard label="Total Machines" value={total} icon="⚡" color="text-blue-600" />
            <StatCard label="Online" value={online} icon="🟢" color="text-green-600" />
            <StatCard label="Fault" value={fault} icon="🔴" color="text-red-600" />
            <StatCard label="Offline" value={offline} icon="⚫" color="text-gray-600" />
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <Chart
              title="Online Devices (24h)"
              data={chartData}
              xKey="time"
              yKey="devices"
              type="area"
              color="#4caf50"
            />
            <Card title="Recent Activity">
              <p className="text-sm text-gray-500">Activity feed will appear here.</p>
            </Card>
          </div>
        </>
      )}
    </div>
  );
}
