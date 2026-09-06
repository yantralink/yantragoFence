'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import { Card } from '@/components/ui/card';

interface Customer {
  id: string;
  organizationId: string;
  userId?: string;
  name: string;
  email?: string;
  phone: string;
  address?: string;
  isActive: boolean;
  assignedMachineId?: string;
  assignedMachineName?: string;
  assignedMachineCode?: string;
}

interface Machine {
  id: string;
  machineId: string;
  name: string;
  status: string;
  customerId?: string | null;
}

export default function CustomersPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [address, setAddress] = useState('');
  const [machineSearch, setMachineSearch] = useState('');
  const [selectedMachineId, setSelectedMachineId] = useState('');
  const [error, setError] = useState<string | null>(null);

  const { data: customers, isLoading } = useQuery<Customer[]>({
    queryKey: ['customers'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ content: Customer[] }>('/customers');
      return data.content;
    },
  });

  const { data: machines } = useQuery<Machine[]>({
    queryKey: ['machines'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ content: Machine[] }>('/machines');
      return data.content;
    },
  });

  const createMutation = useMutation({
    mutationFn: async (params: any) => {
      const { data } = await apiClient.post<Customer>('/customers', params);
      return data;
    },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['customers'] }); queryClient.invalidateQueries({ queryKey: ['machines'] }); resetForm(); },
    onError: (err: any) => setError(err?.response?.data?.message || 'Failed to create customer'),
  });

  const updateMutation = useMutation({
    mutationFn: async (params: any) => {
      const { data } = await apiClient.put<Customer>(`/customers/${params.id}`, params);
      return data;
    },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['customers'] }); queryClient.invalidateQueries({ queryKey: ['machines'] }); resetForm(); },
    onError: (err: any) => setError(err?.response?.data?.message || 'Failed to update customer'),
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: string) => { await apiClient.delete(`/customers/${id}`); },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['customers'] }); queryClient.invalidateQueries({ queryKey: ['machines'] }); },
  });

  const activateMutation = useMutation({
    mutationFn: async (id: string) => { await apiClient.put(`/customers/${id}/activate`); },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['customers'] }),
  });

  const deactivateMutation = useMutation({
    mutationFn: async (id: string) => { await apiClient.put(`/customers/${id}/deactivate`); },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['customers'] }),
  });

  const resetPasswordMutation = useMutation({
    mutationFn: async (id: string) => { await apiClient.post(`/customers/${id}/reset-password`); },
    onSuccess: () => alert('Password reset to "yantrago"'),
  });

  const resetForm = () => {
    setShowForm(false); setEditingId(null);
    setName(''); setPhone(''); setEmail(''); setAddress('');
    setMachineSearch(''); setSelectedMachineId(''); setError(null);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const params: any = { name, phone, email, address };
    if (selectedMachineId) params.assignedMachineId = selectedMachineId;
    if (editingId) {
      updateMutation.mutate({ id: editingId, ...params });
    } else {
      createMutation.mutate(params);
    }
  };

  const handleEdit = (c: Customer) => {
    setEditingId(c.id);
    setName(c.name); setPhone(c.phone); setEmail(c.email || ''); setAddress(c.address || '');
    setSelectedMachineId(c.assignedMachineId || '');
    setMachineSearch(c.assignedMachineCode || '');
    setShowForm(true); setError(null);
  };

  const handleDelete = (c: Customer) => {
    if (confirm(`Delete customer "${c.name}"? Their machine will be unassigned and login disabled.`)) {
      deleteMutation.mutate(c.id);
    }
  };

  // Filter machines for the search dropdown (IN_STOCK or already assigned to this customer)
  const filteredMachines = machines?.filter(m => {
    const isAvailable = !m.customerId || (editingId && m.customerId === editingId);
    if (!isAvailable) return false;
    if (!machineSearch) return true;
    return m.machineId.toLowerCase().includes(machineSearch.toLowerCase()) ||
           m.name.toLowerCase().includes(machineSearch.toLowerCase());
  }) || [];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Customers</h1>
        <button
          onClick={() => { resetForm(); setShowForm(true); }}
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark"
        >
          + New Customer
        </button>
      </div>

      {showForm && (
        <Card title={editingId ? 'Edit Customer' : 'Create Customer'}>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700">Name *</label>
                <input type="text" value={name} onChange={(e) => setName(e.target.value)} required
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none" placeholder="John Farmer" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Phone Number *</label>
                <input type="text" value={phone} onChange={(e) => setPhone(e.target.value)} required
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none" placeholder="+91 98765 43210" />
                <p className="mt-1 text-xs text-gray-500">Used as login ID for the mobile app.</p>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Email</label>
                <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none" placeholder="john@example.com" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Address</label>
                <input type="text" value={address} onChange={(e) => setAddress(e.target.value)}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none" placeholder="Farm Road, Village, State" />
              </div>
            </div>

            {/* Machine search and assignment */}
            <div>
              <label className="block text-sm font-medium text-gray-700">Assigned Machine</label>
              <input
                type="text"
                value={machineSearch}
                onChange={(e) => { setMachineSearch(e.target.value); setSelectedMachineId(''); }}
                placeholder="Search by Machine ID (e.g. YG001)..."
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
              />
              {machineSearch && filteredMachines.length > 0 && !selectedMachineId && (
                <div className="mt-1 max-h-40 overflow-auto rounded-md border border-gray-200 bg-white shadow-sm">
                  {filteredMachines.slice(0, 10).map((m) => (
                    <button
                      key={m.id}
                      type="button"
                      onClick={() => { setSelectedMachineId(m.id); setMachineSearch(`${m.machineId} — ${m.name}`); }}
                      className="block w-full px-3 py-2 text-left text-sm hover:bg-gray-100"
                    >
                      <span className="font-mono">{m.machineId}</span> — {m.name} ({m.status})
                    </button>
                  ))}
                </div>
              )}
              {selectedMachineId && (
                <div className="mt-1 flex items-center gap-2">
                  <span className="text-sm text-green-600">✓ Machine selected</span>
                  <button type="button" onClick={() => { setSelectedMachineId(''); setMachineSearch(''); }}
                    className="text-xs text-red-600 hover:text-red-800">Remove</button>
                </div>
              )}
              <p className="mt-1 text-xs text-gray-500">Optional. Only IN_STOCK machines are shown.</p>
            </div>

            {!editingId && (
              <div className="rounded-md bg-blue-50 p-3 text-sm text-blue-700">
                A mobile app account will be auto-created with password <strong>yantrago</strong>.
                The customer can log in with their phone number and this password.
              </div>
            )}
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

      <Card title="All Customers">
        {isLoading ? (
          <p className="text-sm text-gray-500">Loading...</p>
        ) : customers && customers.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead>
                <tr>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Name</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Phone</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Machine</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {customers.map((c) => (
                  <tr key={c.id}>
                    <td className="px-4 py-2 text-sm text-gray-900">{c.name}</td>
                    <td className="px-4 py-2 text-sm text-gray-500">{c.phone}</td>
                    <td className="px-4 py-2 text-sm text-gray-500">
                      {c.assignedMachineCode ? `${c.assignedMachineCode} — ${c.assignedMachineName}` : '—'}
                    </td>
                    <td className="px-4 py-2 text-sm">
                      <span className={c.isActive ? 'text-green-600' : 'text-red-600'}>
                        {c.isActive ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="px-4 py-2 text-sm">
                      <div className="flex gap-2">
                        <button onClick={() => handleEdit(c)} className="text-blue-600 hover:text-blue-800">Edit</button>
                        {c.isActive ? (
                          <button onClick={() => deactivateMutation.mutate(c.id)} className="text-orange-600 hover:text-orange-800">Deactivate</button>
                        ) : (
                          <button onClick={() => activateMutation.mutate(c.id)} className="text-green-600 hover:text-green-800">Activate</button>
                        )}
                        <button onClick={() => resetPasswordMutation.mutate(c.id)} className="text-purple-600 hover:text-purple-800">Reset Pwd</button>
                        <button onClick={() => handleDelete(c)} className="text-red-600 hover:text-red-800">Delete</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-sm text-gray-500">No customers yet. Create one to get started.</p>
        )}
      </Card>
    </div>
  );
}
