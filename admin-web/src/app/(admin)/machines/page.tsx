'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import { Card } from '@/components/ui/card';

interface Machine {
  id: string;
  machineId: string;
  organizationId: string | null;
  customerId: string | null;
  name: string;
  status: string;
  isOnline: boolean;
  imei?: string;
  simNumber?: string;
  protocolType?: string;
  customerName?: string;
}

interface Customer {
  id: string;
  name: string;
  phone: string;
}

export default function MachinesPage() {
  const queryClient = useQueryClient();
  const [assignMachineId, setAssignMachineId] = useState('');
  const [assignCustomerId, setAssignCustomerId] = useState('');
  const [error, setError] = useState<string | null>(null);

  const { data: machines, isLoading } = useQuery<Machine[]>({
    queryKey: ['machines'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ content: Machine[] }>('/machines');
      return data.content;
    },
  });

  const { data: customers } = useQuery<Customer[]>({
    queryKey: ['customers'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ content: Customer[] }>('/customers');
      return data.content;
    },
  });

  const assignMutation = useMutation({
    mutationFn: async (params: { machineId: string; customerId: string }) => {
      const { data } = await apiClient.post<Machine>(`/machines/${params.machineId}/assign-customer`, { customerId: params.customerId });
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['machines'] });
      setAssignMachineId(''); setAssignCustomerId('');
      setError(null);
    },
    onError: (err: any) => setError(err?.response?.data?.message || 'Failed to assign machine'),
  });

  const unassignMutation = useMutation({
    mutationFn: async (machineId: string) => {
      const { data } = await apiClient.post<Machine>(`/machines/${machineId}/unassign-customer`);
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['machines'] }),
    onError: (err: any) => setError(err?.response?.data?.message || 'Failed to unassign machine'),
  });

  const statusColor = (status: string) => {
    switch (status) {
      case 'ACTIVE': return 'text-green-600';
      case 'IN_STOCK': return 'text-blue-600';
      case 'FAULT': return 'text-red-600';
      case 'OFFLINE': return 'text-gray-500';
      default: return 'text-gray-500';
    }
  };

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Machines</h1>

      {error && (
        <div className="rounded-md bg-red-50 p-3 text-sm text-red-600">{error}</div>
      )}

      {/* Assign machine to customer */}
      <Card title="Assign Machine to Customer">
        <div className="flex gap-2 items-end">
          <div className="flex-1">
            <label className="block text-sm font-medium text-gray-700">Machine</label>
            <select value={assignMachineId} onChange={(e) => setAssignMachineId(e.target.value)}
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none">
              <option value="">Select machine...</option>
              {machines?.filter(m => !m.customerId).map((m) => (
                <option key={m.id} value={m.id}>{m.machineId} — {m.name}</option>
              ))}
            </select>
          </div>
          <div className="flex-1">
            <label className="block text-sm font-medium text-gray-700">Customer</label>
            <select value={assignCustomerId} onChange={(e) => setAssignCustomerId(e.target.value)}
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none">
              <option value="">Select customer...</option>
              {customers?.map((c) => (
                <option key={c.id} value={c.id}>{c.name} ({c.phone})</option>
              ))}
            </select>
          </div>
          <button
            onClick={() => assignMachineId && assignCustomerId && assignMutation.mutate({ machineId: assignMachineId, customerId: assignCustomerId })}
            disabled={!assignMachineId || !assignCustomerId || assignMutation.isPending}
            className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark disabled:opacity-50"
          >
            Assign
          </button>
        </div>
        <p className="mt-2 text-xs text-gray-500">Only unassigned machines (IN_STOCK) are shown.</p>
      </Card>

      <Card title="All Machines">
        {isLoading ? (
          <p className="text-sm text-gray-500">Loading...</p>
        ) : machines && machines.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead>
                <tr>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Machine ID</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Name</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">IMEI</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Customer</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {machines.map((m) => (
                  <tr key={m.id}>
                    <td className="px-4 py-2 text-sm font-mono text-gray-900">{m.machineId}</td>
                    <td className="px-4 py-2 text-sm text-gray-900">{m.name}</td>
                    <td className="px-4 py-2 text-sm text-gray-500">{m.imei || '—'}</td>
                    <td className="px-4 py-2 text-sm text-gray-500">{m.customerName || '—'}</td>
                    <td className={`px-4 py-2 text-sm ${statusColor(m.status)}`}>{m.status}</td>
                    <td className="px-4 py-2 text-sm">
                      {m.customerId ? (
                        <button
                          onClick={() => { if (confirm('Unassign this machine from the customer?')) unassignMutation.mutate(m.id); }}
                          className="text-orange-600 hover:text-orange-800"
                        >
                          Unassign
                        </button>
                      ) : (
                        <span className="text-gray-400">—</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-sm text-gray-500">No machines in your organization yet. Contact the super admin to assign machines.</p>
        )}
      </Card>
    </div>
  );
}
