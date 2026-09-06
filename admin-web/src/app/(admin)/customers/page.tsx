'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import { Card } from '@/components/ui/card';

interface Customer {
  id: string;
  organizationId: string;
  name: string;
  email?: string;
  phoneNumber?: string;
  address?: string;
  active: boolean;
  createdAt: string;
}

export default function CustomersPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [address, setAddress] = useState('');
  const [error, setError] = useState<string | null>(null);

  const { data: customers, isLoading } = useQuery<Customer[]>({
    queryKey: ['customers'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ content: Customer[] }>('/customers');
      return data.content;
    },
  });

  const createMutation = useMutation({
    mutationFn: async (params: any) => {
      const { data } = await apiClient.post<Customer>('/customers', params);
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      setShowForm(false);
      setName('');
      setEmail('');
      setPhoneNumber('');
      setAddress('');
      setError(null);
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Failed to create customer');
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    createMutation.mutate({ name, email, phoneNumber, address });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Customers</h1>
        <button
          onClick={() => setShowForm(!showForm)}
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark"
        >
          {showForm ? 'Cancel' : '+ New Customer'}
        </button>
      </div>

      {showForm && (
        <Card title="Create Customer">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700">Name *</label>
                <input type="text" value={name} onChange={(e) => setName(e.target.value)} required className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none" placeholder="John Farmer" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Email</label>
                <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none" placeholder="john@example.com" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Phone</label>
                <input type="text" value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none" placeholder="+91 98765 43210" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Address</label>
                <input type="text" value={address} onChange={(e) => setAddress(e.target.value)} className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none" placeholder="Farm Road, Village, State" />
              </div>
            </div>
            {error && <p className="text-sm text-red-600">{error}</p>}
            <button type="submit" disabled={createMutation.isPending} className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark disabled:opacity-50">
              {createMutation.isPending ? 'Creating...' : 'Create Customer'}
            </button>
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
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Email</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Phone</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {customers.map((c) => (
                  <tr key={c.id}>
                    <td className="px-4 py-2 text-sm text-gray-900">{c.name}</td>
                    <td className="px-4 py-2 text-sm text-gray-500">{c.email || '—'}</td>
                    <td className="px-4 py-2 text-sm text-gray-500">{c.phoneNumber || '—'}</td>
                    <td className="px-4 py-2 text-sm">
                      <span className={c.active ? 'text-green-600' : 'text-red-600'}>
                        {c.active ? 'Active' : 'Inactive'}
                      </span>
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
