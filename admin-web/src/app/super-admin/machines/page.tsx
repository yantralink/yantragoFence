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
  serialNumber?: string;
  model?: string;
  status: string;
  isOnline: boolean;
  imei?: string;
  simNumber?: string;
  protocolType?: string;
  firmwareVersion?: string;
  organizationName?: string;
  customerName?: string;
}

interface Organization {
  id: string;
  name: string;
  slug: string;
}

export default function SuperAdminMachinesPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [imei, setImei] = useState('');
  const [simNumber, setSimNumber] = useState('');
  const [protocolType, setProtocolType] = useState('YANTRAGO_FENCING');
  const [model, setModel] = useState('');
  const [serialNumber, setSerialNumber] = useState('');
  const [firmwareVersion, setFirmwareVersion] = useState('');
  const [assignOrgId, setAssignOrgId] = useState('');
  const [assignMachineId, setAssignMachineId] = useState('');
  const [error, setError] = useState<string | null>(null);

  const { data: machines, isLoading } = useQuery<Machine[]>({
    queryKey: ['machines'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ content: Machine[] }>('/machines');
      return data.content;
    },
  });

  const { data: orgs } = useQuery<Organization[]>({
    queryKey: ['organizations'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ content: Organization[] }>('/organizations');
      return data.content;
    },
  });

  const createMutation = useMutation({
    mutationFn: async (params: any) => {
      const { data } = await apiClient.post<Machine>('/machines', params);
      return data;
    },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['machines'] }); resetForm(); },
    onError: (err: any) => setError(err?.response?.data?.message || 'Failed to create machine'),
  });

  const updateMutation = useMutation({
    mutationFn: async (params: any) => {
      const { data } = await apiClient.put<Machine>(`/machines/${params.id}`, params);
      return data;
    },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['machines'] }); resetForm(); },
    onError: (err: any) => setError(err?.response?.data?.message || 'Failed to update machine'),
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: string) => { await apiClient.delete(`/machines/${id}`); },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['machines'] }),
    onError: (err: any) => setError(err?.response?.data?.message || 'Failed to delete machine'),
  });

  const assignOrgMutation = useMutation({
    mutationFn: async (params: { machineId: string; orgId: string }) => {
      const { data } = await apiClient.post<Machine>(`/machines/${params.machineId}/assign-org`, { organizationId: params.orgId });
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['machines'] }),
    onError: (err: any) => setError(err?.response?.data?.message || 'Failed to assign organization'),
  });

  const resetForm = () => {
    setShowForm(false);
    setEditingId(null);
    setName(''); setImei(''); setSimNumber(''); setProtocolType('YANTRAGO_FENCING');
    setModel(''); setSerialNumber(''); setFirmwareVersion('');
    setError(null);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const params = { name, imei, simNumber, protocolType, model, serialNumber, firmwareVersion };
    if (editingId) {
      updateMutation.mutate({ id: editingId, ...params });
    } else {
      createMutation.mutate(params);
    }
  };

  const handleEdit = (m: Machine) => {
    setEditingId(m.id);
    setName(m.name); setImei(m.imei || ''); setSimNumber(m.simNumber || '');
    setProtocolType(m.protocolType || 'YANTRAGO_FENCING'); setModel(m.model || '');
    setSerialNumber(m.serialNumber || ''); setFirmwareVersion(m.firmwareVersion || '');
    setShowForm(true); setError(null);
  };

  const handleDelete = (m: Machine) => {
    if (confirm(`Delete machine "${m.machineId}" (${m.name})? This cannot be undone.`)) {
      deleteMutation.mutate(m.id);
    }
  };

  const handleAssignOrg = (machineId: string) => {
    if (!assignOrgId) { setError('Select an organization first'); return; }
    assignOrgMutation.mutate({ machineId, orgId: assignOrgId });
    setAssignMachineId('');
  };

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
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Machines (Inventory)</h1>
        <button
          onClick={() => { resetForm(); setShowForm(true); }}
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark"
        >
          + New Machine
        </button>
      </div>

      {showForm && (
        <Card title={editingId ? 'Edit Machine' : 'Create Machine'}>
          <form onSubmit={handleSubmit} className="space-y-4">
            {editingId && (
              <div>
                <label className="block text-sm font-medium text-gray-700">Machine ID (locked)</label>
                <input type="text" value={machines?.find(m => m.id === editingId)?.machineId || ''} disabled
                  className="mt-1 block w-full rounded-md border border-gray-200 bg-gray-100 px-3 py-2 text-sm text-gray-500" />
              </div>
            )}
            {!editingId && (
              <p className="text-xs text-gray-500 bg-blue-50 p-2 rounded">
                Machine ID will be auto-generated (e.g. YG000001) and cannot be changed after creation.
              </p>
            )}
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700">Name *</label>
                <input type="text" value={name} onChange={(e) => setName(e.target.value)} required
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                  placeholder="Fence Unit #1" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">IMEI *</label>
                <input type="text" value={imei} onChange={(e) => setImei(e.target.value)} required
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                  placeholder="123456789012345" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Protocol Type *</label>
                <select value={protocolType} onChange={(e) => setProtocolType(e.target.value)}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none">
                  <option value="YANTRAGO_FENCING">YantraGO Fencing</option>
                  <option value="CONCOX_V5">Concox V5</option>
                  <option value="JT808">JT808</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">SIM Number</label>
                <input type="text" value={simNumber} onChange={(e) => setSimNumber(e.target.value)}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                  placeholder="+91 98765 43210" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Model</label>
                <input type="text" value={model} onChange={(e) => setModel(e.target.value)}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                  placeholder="T98" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Serial Number</label>
                <input type="text" value={serialNumber} onChange={(e) => setSerialNumber(e.target.value)}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                  placeholder="SN001" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Firmware Version</label>
                <input type="text" value={firmwareVersion} onChange={(e) => setFirmwareVersion(e.target.value)}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                  placeholder="v1.0.0" />
              </div>
            </div>
            {error && <p className="text-sm text-red-600">{error}</p>}
            <div className="flex gap-2">
              <button type="submit" disabled={createMutation.isPending || updateMutation.isPending}
                className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark disabled:opacity-50">
                {editingId ? 'Update' : 'Create'}
              </button>
              <button type="button" onClick={resetForm}
                className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50">
                Cancel
              </button>
            </div>
          </form>
        </Card>
      )}

      {/* Assign to Organization section */}
      <Card title="Assign Machine to Organization">
        <div className="flex gap-2 items-end">
          <div className="flex-1">
            <label className="block text-sm font-medium text-gray-700">Select Organization</label>
            <select value={assignOrgId} onChange={(e) => setAssignOrgId(e.target.value)}
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none">
              <option value="">Select organization...</option>
              {orgs?.map((org) => (
                <option key={org.id} value={org.id}>{org.name}</option>
              ))}
            </select>
          </div>
          <div className="flex-1">
            <label className="block text-sm font-medium text-gray-700">Select Machine</label>
            <select value={assignMachineId} onChange={(e) => setAssignMachineId(e.target.value)}
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none">
              <option value="">Select machine...</option>
              {machines?.filter(m => !m.organizationId).map((m) => (
                <option key={m.id} value={m.id}>{m.machineId} — {m.name}</option>
              ))}
            </select>
          </div>
          <button
            onClick={() => assignMachineId && handleAssignOrg(assignMachineId)}
            disabled={!assignMachineId || !assignOrgId || assignOrgMutation.isPending}
            className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark disabled:opacity-50"
          >
            Assign
          </button>
        </div>
        <p className="mt-2 text-xs text-gray-500">Only unassigned machines (no organization) are shown in the dropdown.</p>
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
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Organization</th>
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
                    <td className="px-4 py-2 text-sm text-gray-500">{m.organizationName || (m.organizationId ? 'Assigned' : 'Unassigned')}</td>
                    <td className="px-4 py-2 text-sm text-gray-500">{m.customerName || '—'}</td>
                    <td className={`px-4 py-2 text-sm ${statusColor(m.status)}`}>{m.status}</td>
                    <td className="px-4 py-2 text-sm">
                      <div className="flex gap-2">
                        <button onClick={() => handleEdit(m)} className="text-blue-600 hover:text-blue-800">Edit</button>
                        <button onClick={() => handleDelete(m)} className="text-red-600 hover:text-red-800">Delete</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-sm text-gray-500">No machines yet. Create one to get started.</p>
        )}
      </Card>
    </div>
  );
}
