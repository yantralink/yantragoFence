'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import { Card } from '@/components/ui/card';

interface Machine {
  id: string;
  organizationId: string;
  imei: string;
  name: string;
  model?: string;
  serialNumber?: string;
  protocolType: string;
  status: string;
  customerId?: string;
  simNumber?: string;
  firmwareVersion?: string;
  lastSeenAt?: string;
  createdAt: string;
}

export default function MachinesPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [imei, setImei] = useState('');
  const [name, setName] = useState('');
  const [model, setModel] = useState('');
  const [serialNumber, setSerialNumber] = useState('');
  const [protocolType, setProtocolType] = useState('YANTRAGO_FENCING');
  const [simNumber, setSimNumber] = useState('');
  const [error, setError] = useState<string | null>(null);

  const { data: machines, isLoading } = useQuery<Machine[]>({
    queryKey: ['machines'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ content: Machine[] }>('/machines');
      return data.content;
    },
  });

  const createMutation = useMutation({
    mutationFn: async (params: any) => {
      const { data } = await apiClient.post<Machine>('/machines', params);
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['machines'] });
      setShowForm(false);
      setImei('');
      setName('');
      setModel('');
      setSerialNumber('');
      setProtocolType('YANTRAGO_FENCING');
      setSimNumber('');
      setError(null);
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Failed to create machine');
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    createMutation.mutate({ imei, name, model, serialNumber, protocolType, simNumber });
  };

  const statusColor = (status: string) => {
    switch (status) {
      case 'ONLINE':
      case 'FENCING_ON':
        return 'text-green-600';
      case 'FAULT':
        return 'text-red-600';
      default:
        return 'text-gray-500';
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Machines</h1>
        <button
          onClick={() => setShowForm(!showForm)}
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark"
        >
          {showForm ? 'Cancel' : '+ New Machine'}
        </button>
      </div>

      {showForm && (
        <Card title="Register Machine">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700">IMEI *</label>
                <input type="text" value={imei} onChange={(e) => setImei(e.target.value)} required className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none" placeholder="123456789012345" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Name *</label>
                <input type="text" value={name} onChange={(e) => setName(e.target.value)} required className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none" placeholder="Fence Unit #1" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Protocol Type</label>
                <select value={protocolType} onChange={(e) => setProtocolType(e.target.value)} className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none">
                  <option value="YANTRAGO_FENCING">YantraGO Fencing</option>
                  <option value="CONCOX_V5">Concox V5</option>
                  <option value="JT808">JT808</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">SIM Number</label>
                <input type="text" value={simNumber} onChange={(e) => setSimNumber(e.target.value)} className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none" placeholder="+91 98765 43210" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Model</label>
                <input type="text" value={model} onChange={(e) => setModel(e.target.value)} className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none" placeholder="T98" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Serial Number</label>
                <input type="text" value={serialNumber} onChange={(e) => setSerialNumber(e.target.value)} className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none" placeholder="SN001" />
              </div>
            </div>
            {error && <p className="text-sm text-red-600">{error}</p>}
            <button type="submit" disabled={createMutation.isPending} className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark disabled:opacity-50">
              {createMutation.isPending ? 'Creating...' : 'Register Machine'}
            </button>
          </form>
        </Card>
      )}

      <Card title="All Machines">
        {isLoading ? (
          <p className="text-sm text-gray-500">Loading...</p>
        ) : machines && machines.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead>
                <tr>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Name</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">IMEI</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Protocol</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Last Seen</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {machines.map((m) => (
                  <tr key={m.id}>
                    <td className="px-4 py-2 text-sm text-gray-900">{m.name}</td>
                    <td className="px-4 py-2 text-sm text-gray-500">{m.imei}</td>
                    <td className="px-4 py-2 text-sm text-gray-500">{m.protocolType}</td>
                    <td className={`px-4 py-2 text-sm ${statusColor(m.status)}`}>{m.status}</td>
                    <td className="px-4 py-2 text-sm text-gray-500">{m.lastSeenAt || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-sm text-gray-500">No machines yet. Register one to get started.</p>
        )}
      </Card>
    </div>
  );
}
