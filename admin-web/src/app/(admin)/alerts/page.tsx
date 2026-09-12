'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import { Card } from '@/components/ui/card';

interface Alert {
  id: string;
  organizationId: string;
  alertRuleId?: string;
  machineId: string;
  deviceId?: string;
  alertType: string;
  severity: string;
  message: string;
  isAcknowledged: boolean;
  acknowledgedBy?: string;
  acknowledgedAt?: string;
  triggeredAt: string;
  createdAt: string;
}

interface Machine {
  id: string;
  machineId: string;
  name: string;
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
};

function formatDate(dateStr: string): string {
  const d = new Date(dateStr);
  return d.toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function AdminAlertsPage() {
  const queryClient = useQueryClient();
  const [severityFilter, setSeverityFilter] = useState('');
  const [ackFilter, setAckFilter] = useState('');
  const [machineFilter, setMachineFilter] = useState('');
  const [page, setPage] = useState(0);
  const pageSize = 20;

  const { data: machines } = useQuery<Machine[]>({
    queryKey: ['machines'],
    queryFn: async () => {
      const { data } = await apiClient.get<PageResponse<Machine>>('/machines');
      return data.content;
    },
  });

  const queryParams = new URLSearchParams();
  if (severityFilter) queryParams.set('severity', severityFilter);
  if (machineFilter) queryParams.set('machineId', machineFilter);
  if (ackFilter === 'unack') queryParams.set('unacknowledged', 'true');
  queryParams.set('page', String(page));
  queryParams.set('size', String(pageSize));
  queryParams.set('sort', 'createdAt,desc');

  const { data: alertsPage, isLoading } = useQuery<PageResponse<Alert>>({
    queryKey: ['alerts', severityFilter, ackFilter, machineFilter, page],
    queryFn: async () => {
      const { data } = await apiClient.get<PageResponse<Alert>>(
        `/alerts?${queryParams.toString()}`
      );
      return data;
    },
  });

  const ackMutation = useMutation({
    mutationFn: async (alertId: string) => {
      const { data } = await apiClient.post<Alert>(
        `/alerts/${alertId}/acknowledge`
      );
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['alerts'] });
    },
  });

  const alerts = alertsPage?.content ?? [];
  const totalElements = alertsPage?.totalElements ?? 0;
  const totalPages = alertsPage?.totalPages ?? 0;

  const machineName = (machineId: string) =>
    machines?.find((m) => m.id === machineId)?.name ?? machineId.slice(0, 8);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Alerts</h1>
        <span className="text-sm text-gray-500">
          {totalElements} alert{totalElements !== 1 ? 's' : ''} total
        </span>
      </div>

      {/* Filters */}
      <Card>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <label className="block text-sm font-medium text-gray-700">
              Severity
            </label>
            <select
              value={severityFilter}
              onChange={(e) => {
                setSeverityFilter(e.target.value);
                setPage(0);
              }}
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
            >
              <option value="">All severities</option>
              <option value="CRITICAL">Critical</option>
              <option value="WARNING">Warning</option>
              <option value="INFO">Info</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">
              Machine
            </label>
            <select
              value={machineFilter}
              onChange={(e) => {
                setMachineFilter(e.target.value);
                setPage(0);
              }}
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
            >
              <option value="">All machines</option>
              {machines?.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name} ({m.machineId})
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">
              Status
            </label>
            <select
              value={ackFilter}
              onChange={(e) => {
                setAckFilter(e.target.value);
                setPage(0);
              }}
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
            >
              <option value="">All alerts</option>
              <option value="unack">Unacknowledged only</option>
              <option value="ack">Acknowledged only</option>
            </select>
          </div>
        </div>
      </Card>

      {/* Alerts table */}
      <Card title="Device Alerts">
        {isLoading ? (
          <p className="text-sm text-gray-500">Loading alerts...</p>
        ) : alerts.length > 0 ? (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200">
                <thead>
                  <tr>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                      Type
                    </th>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                      Severity
                    </th>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                      Machine
                    </th>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                      Message
                    </th>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                      Triggered
                    </th>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                      Status
                    </th>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                      Action
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {alerts.map((a) => (
                    <tr key={a.id} className={a.isAcknowledged ? 'opacity-60' : ''}>
                      <td className="px-4 py-3 text-sm text-gray-900">
                        {ALERT_TYPE_LABELS[a.alertType] ?? a.alertType}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${
                            SEVERITY_COLORS[a.severity] ?? 'bg-gray-100 text-gray-700'
                          }`}
                        >
                          {a.severity}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-600">
                        {machineName(a.machineId)}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-600 max-w-xs truncate">
                        {a.message}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-500">
                        {formatDate(a.triggeredAt)}
                      </td>
                      <td className="px-4 py-3">
                        {a.isAcknowledged ? (
                          <span className="text-xs text-green-600">
                            Acknowledged
                          </span>
                        ) : (
                          <span className="text-xs text-amber-600">
                            Unacknowledged
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-sm">
                        {!a.isAcknowledged && (
                          <button
                            onClick={() => ackMutation.mutate(a.id)}
                            disabled={ackMutation.isPending}
                            className="text-primary hover:text-primary-dark disabled:opacity-50"
                          >
                            Acknowledge
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="mt-4 flex items-center justify-between">
                <span className="text-sm text-gray-500">
                  Page {page + 1} of {totalPages}
                </span>
                <div className="flex gap-2">
                  <button
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={page === 0}
                    className="rounded-md border border-gray-300 px-3 py-1 text-sm disabled:opacity-50"
                  >
                    Previous
                  </button>
                  <button
                    onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                    disabled={page >= totalPages - 1}
                    className="rounded-md border border-gray-300 px-3 py-1 text-sm disabled:opacity-50"
                  >
                    Next
                  </button>
                </div>
              </div>
            )}
          </>
        ) : (
          <p className="text-sm text-gray-500">
            No alerts found. Create alert rules on the Alert Rules page to start
            receiving alerts.
          </p>
        )}
      </Card>
    </div>
  );
}
