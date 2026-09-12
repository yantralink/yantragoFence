'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import { Card } from '@/components/ui/card';

interface Command {
  id: string;
  organizationId: string;
  machineId: string;
  deviceId?: string;
  issuedBy?: string;
  commandType: string;
  status: string;
  attemptCount: number;
  maxAttempts: number;
  lastError?: string;
  createdAt: string;
  updatedAt: string;
  completedAt?: string;
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

const STATUS_COLORS: Record<string, string> = {
  DONE: 'bg-green-100 text-green-700',
  FAILED: 'bg-red-100 text-red-700',
  ACK: 'bg-blue-100 text-blue-700',
  SENT: 'bg-amber-100 text-amber-700',
  QUEUED: 'bg-gray-100 text-gray-700',
  PENDING: 'bg-gray-100 text-gray-700',
};

const COMMAND_TYPE_LABELS: Record<string, string> = {
  ON: 'Turn On',
  OFF: 'Turn Off',
  FENCING_ON: 'Turn On',
  FENCING_OFF: 'Turn Off',
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

function formatRelative(dateStr: string): string {
  const d = new Date(dateStr);
  const diff = Date.now() - d.getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins} min ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} h ago`;
  return formatDate(dateStr);
}

export default function AdminCommandsPage() {
  const [statusFilter, setStatusFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
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
  if (machineFilter) queryParams.set('machineId', machineFilter);
  queryParams.set('page', String(page));
  queryParams.set('size', String(pageSize));
  queryParams.set('sort', 'createdAt,desc');

  const { data: commandsPage, isLoading } = useQuery<PageResponse<Command>>({
    queryKey: ['commands', machineFilter, page],
    queryFn: async () => {
      const { data } = await apiClient.get<PageResponse<Command>>(
        `/commands?${queryParams.toString()}`
      );
      return data;
    },
  });

  const commands = commandsPage?.content ?? [];
  const totalElements = commandsPage?.totalElements ?? 0;
  const totalPages = commandsPage?.totalPages ?? 0;

  // Client-side filters for status and type (backend doesn't support these params)
  const filteredCommands = commands.filter((c) => {
    if (statusFilter && c.status !== statusFilter) return false;
    if (typeFilter && c.commandType !== typeFilter) return false;
    return true;
  });

  const machineName = (machineId: string) =>
    machines?.find((m) => m.id === machineId)?.name ?? machineId.slice(0, 8);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Command History</h1>
        <span className="text-sm text-gray-500">
          {totalElements} command{totalElements !== 1 ? 's' : ''} total
        </span>
      </div>

      {/* Filters */}
      <Card>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <label className="block text-sm font-medium text-gray-700">
              Command Type
            </label>
            <select
              value={typeFilter}
              onChange={(e) => {
                setTypeFilter(e.target.value);
                setPage(0);
              }}
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
            >
              <option value="">All types</option>
              <option value="ON">Turn On</option>
              <option value="OFF">Turn Off</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">
              Status
            </label>
            <select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value);
                setPage(0);
              }}
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
            >
              <option value="">All statuses</option>
              <option value="PENDING">Pending</option>
              <option value="QUEUED">Queued</option>
              <option value="SENT">Sent</option>
              <option value="ACK">Acknowledged</option>
              <option value="DONE">Completed</option>
              <option value="FAILED">Failed</option>
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
        </div>
      </Card>

      {/* Commands table */}
      <Card title="Machine Commands">
        {isLoading ? (
          <p className="text-sm text-gray-500">Loading commands...</p>
        ) : filteredCommands.length > 0 ? (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-gray-200">
                <thead>
                  <tr>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                      Type
                    </th>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                      Status
                    </th>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                      Machine
                    </th>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                      Attempts
                    </th>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                      Error
                    </th>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                      Issued
                    </th>
                    <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                      Completed
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200">
                  {filteredCommands.map((c) => (
                    <tr key={c.id} className={c.status === 'FAILED' ? 'bg-red-50' : ''}>
                      <td className="px-4 py-3 text-sm text-gray-900 font-medium">
                        {COMMAND_TYPE_LABELS[c.commandType] ?? c.commandType}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${
                            STATUS_COLORS[c.status] ?? 'bg-gray-100 text-gray-700'
                          }`}
                        >
                          {c.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-600">
                        {machineName(c.machineId)}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-600">
                        {c.attemptCount} / {c.maxAttempts}
                      </td>
                      <td className="px-4 py-3 text-sm text-red-600 max-w-xs truncate">
                        {c.lastError ?? '—'}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-500">
                        {formatRelative(c.createdAt)}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-500">
                        {c.completedAt ? formatRelative(c.completedAt) : '—'}
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
            No commands found. Commands will appear here when relay ON/OFF
            operations are issued from the mobile app.
          </p>
        )}
      </Card>
    </div>
  );
}
