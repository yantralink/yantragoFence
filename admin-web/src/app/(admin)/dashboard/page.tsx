'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import apiClient from '@/lib/api-client';
import { Card, StatCard } from '@/components/ui/card';

interface Machine {
  id: string;
  machineId: string;
  name: string;
  status: string;
  isOnline: boolean;
  customerId?: string | null;
}

interface Customer {
  id: string;
  name: string;
  isActive: boolean;
}

interface Alert {
  id: string;
  machineId: string;
  alertType: string;
  severity: string;
  message: string;
  isAcknowledged: boolean;
  triggeredAt: string;
  createdAt: string;
}

interface Command {
  id: string;
  machineId: string;
  commandType: string;
  status: string;
  attemptCount: number;
  lastError?: string;
  createdAt: string;
  completedAt?: string;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

const SEVERITY_COLORS: Record<string, string> = {
  CRITICAL: 'bg-red-100 text-red-700',
  WARNING: 'bg-amber-100 text-amber-700',
  INFO: 'bg-blue-100 text-blue-700',
};

const STATUS_COLORS: Record<string, string> = {
  DONE: 'bg-green-100 text-green-700',
  FAILED: 'bg-red-100 text-red-700',
  ACK: 'bg-blue-100 text-blue-700',
  SENT: 'bg-amber-100 text-amber-700',
  QUEUED: 'bg-gray-100 text-gray-700',
  PENDING: 'bg-gray-100 text-gray-700',
};

const ALERT_TYPE_LABELS: Record<string, string> = {
  LOW_BATTERY: 'Low Battery',
  VOLTAGE_DROP: 'Voltage Drop',
  GSM_SIGNAL_LOW: 'Low GSM Signal',
  DEVICE_OFFLINE: 'Device Offline',
  SIM_EXPIRY: 'SIM Expiry',
  EXTERNAL_POWER_LOW: 'External Power Low',
  EXTERNAL_POWER_CUT: 'External Power Cut',
  LOW_POWER_SHUTDOWN: 'Low Power Shutdown',
  INTERNAL_BATTERY_LOW: 'Internal Battery Low',
  COMMAND_ACK: 'Command Acknowledged',
  MACHINE_ON: 'Machine Turned On',
  MACHINE_OFF: 'Machine Turned Off',
  COMMAND_FAILED: 'Command Failed',
};

const COMMAND_TYPE_LABELS: Record<string, string> = {
  ON: 'Turn On',
  OFF: 'Turn Off',
  FENCING_ON: 'Turn On',
  FENCING_OFF: 'Turn Off',
};

function formatRelative(dateStr: string): string {
  const d = new Date(dateStr);
  const diff = Date.now() - d.getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins} min ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.floor(hours / 24);
  return `${days} d ago`;
}

export default function AdminDashboardPage() {
  const { data: machines, isLoading: machinesLoading } = useQuery<Machine[]>({
    queryKey: ['machines'],
    queryFn: async () => {
      const { data } = await apiClient.get<PageResponse<Machine>>('/machines');
      return data.content;
    },
  });

  const { data: customers } = useQuery<Customer[]>({
    queryKey: ['customers'],
    queryFn: async () => {
      const { data } = await apiClient.get<PageResponse<Customer>>('/customers');
      return data.content;
    },
  });

  const { data: recentAlerts } = useQuery<PageResponse<Alert>>({
    queryKey: ['dashboard-alerts'],
    queryFn: async () => {
      const { data } = await apiClient.get<PageResponse<Alert>>(
        '/alerts?size=5&sort=createdAt,desc'
      );
      return data;
    },
  });

  const { data: recentCommands } = useQuery<PageResponse<Command>>({
    queryKey: ['dashboard-commands'],
    queryFn: async () => {
      const { data } = await apiClient.get<PageResponse<Command>>(
        '/commands?size=5&sort=createdAt,desc'
      );
      return data;
    },
  });

  const { data: unackAlerts } = useQuery<PageResponse<Alert>>({
    queryKey: ['dashboard-unack-alerts'],
    queryFn: async () => {
      const { data } = await apiClient.get<PageResponse<Alert>>(
        '/alerts?unacknowledged=true&size=1'
      );
      return data;
    },
  });

  const totalMachines = machines?.length ?? 0;
  const activeMachines = machines?.filter((m) => m.status === 'ACTIVE').length ?? 0;
  const inStock = machines?.filter((m) => m.status === 'IN_STOCK').length ?? 0;
  const fault = machines?.filter((m) => m.status === 'FAULT').length ?? 0;
  const offline = machines?.filter((m) => m.status === 'OFFLINE' || (m.status === 'ACTIVE' && !m.isOnline)).length ?? 0;
  const assigned = machines?.filter((m) => m.customerId).length ?? 0;
  const totalCustomers = customers?.length ?? 0;
  const activeCustomers = customers?.filter((c) => c.isActive).length ?? 0;
  const unackCount = unackAlerts?.totalElements ?? 0;

  const machineName = (machineId: string) =>
    machines?.find((m) => m.id === machineId)?.name ?? machineId.slice(0, 8);

  const alerts = recentAlerts?.content ?? [];
  const commands = recentCommands?.content ?? [];

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>

      {machinesLoading ? (
        <div className="text-gray-500">Loading...</div>
      ) : (
        <>
          <div>
            <h2 className="mb-3 text-lg font-semibold text-gray-700">Machines</h2>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <StatCard label="Total Machines" value={totalMachines} icon="⚡" color="text-blue-600" />
              <StatCard label="Active" value={activeMachines} icon="🟢" color="text-green-600" />
              <StatCard label="In Stock" value={inStock} icon="📦" color="text-indigo-600" />
              <StatCard label="Fault" value={fault} icon="🔴" color="text-red-600" />
            </div>
          </div>

          <div>
            <h2 className="mb-3 text-lg font-semibold text-gray-700">Customers</h2>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <StatCard label="Total Customers" value={totalCustomers} icon="👥" color="text-blue-600" />
              <StatCard label="Active Customers" value={activeCustomers} icon="✅" color="text-green-600" />
              <StatCard label="Assigned Machines" value={assigned} icon="🔗" color="text-purple-600" />
              <StatCard label="Unassigned" value={totalMachines - assigned} icon="🔓" color="text-gray-600" />
            </div>
          </div>

          <div>
            <h2 className="mb-3 text-lg font-semibold text-gray-700">Alerts</h2>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <StatCard label="Unacknowledged" value={unackCount} icon="🔔" color="text-amber-600" />
              <StatCard label="Offline Machines" value={offline} icon="📡" color="text-red-600" />
            </div>
          </div>
        </>
      )}

      {/* Recent Activity */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Recent Alerts */}
        <Card
          title="Recent Alerts"
          description={
            <Link href="/alerts" className="text-sm text-primary hover:text-primary-dark">
              View all →
            </Link>
          }
        >
          {alerts.length > 0 ? (
            <div className="space-y-3">
              {alerts.map((a) => (
                <div key={a.id} className="flex items-start gap-3 border-b border-gray-100 pb-3 last:border-0 last:pb-0">
                  <span
                    className={`mt-0.5 inline-flex shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${
                      SEVERITY_COLORS[a.severity] ?? 'bg-gray-100 text-gray-700'
                    }`}
                  >
                    {a.severity}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-gray-900">
                      {ALERT_TYPE_LABELS[a.alertType] ?? a.alertType}
                    </p>
                    <p className="text-xs text-gray-500 truncate">
                      {machineName(a.machineId)} — {a.message}
                    </p>
                  </div>
                  <span className="shrink-0 text-xs text-gray-400">
                    {formatRelative(a.triggeredAt)}
                  </span>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-gray-500">No recent alerts.</p>
          )}
        </Card>

        {/* Recent Commands */}
        <Card
          title="Recent Commands"
          description={
            <Link href="/commands" className="text-sm text-primary hover:text-primary-dark">
              View all →
            </Link>
          }
        >
          {commands.length > 0 ? (
            <div className="space-y-3">
              {commands.map((c) => (
                <div key={c.id} className="flex items-start gap-3 border-b border-gray-100 pb-3 last:border-0 last:pb-0">
                  <span
                    className={`mt-0.5 inline-flex shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${
                      STATUS_COLORS[c.status] ?? 'bg-gray-100 text-gray-700'
                    }`}
                  >
                    {c.status}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-gray-900">
                      {COMMAND_TYPE_LABELS[c.commandType] ?? c.commandType}
                    </p>
                    <p className="text-xs text-gray-500 truncate">
                      {machineName(c.machineId)}
                      {c.lastError ? ` — ${c.lastError}` : ''}
                    </p>
                  </div>
                  <span className="shrink-0 text-xs text-gray-400">
                    {formatRelative(c.createdAt)}
                  </span>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-gray-500">No recent commands.</p>
          )}
        </Card>
      </div>
    </div>
  );
}
