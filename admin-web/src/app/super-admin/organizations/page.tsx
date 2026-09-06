'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import { Card } from '@/components/ui/card';

interface Organization {
  id: string;
  name: string;
  slug: string;
  whiteLabelConfig?: string;
  isActive: boolean;
}

export default function OrganizationsPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);

  const { data: orgs, isLoading } = useQuery<Organization[]>({
    queryKey: ['organizations'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ content: Organization[] }>('/organizations');
      return data.content;
    },
  });

  const createMutation = useMutation({
    mutationFn: async (params: { name: string }) => {
      const { data } = await apiClient.post<Organization>('/organizations', params);
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations'] });
      resetForm();
    },
    onError: (err: any) => setError(err?.response?.data?.message || 'Failed to create organization'),
  });

  const updateMutation = useMutation({
    mutationFn: async (params: { id: string; name: string }) => {
      const { data } = await apiClient.put<Organization>(`/organizations/${params.id}`, { name: params.name });
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations'] });
      resetForm();
    },
    onError: (err: any) => setError(err?.response?.data?.message || 'Failed to update organization'),
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.delete(`/organizations/${id}`);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['organizations'] }),
    onError: (err: any) => setError(err?.response?.data?.message || 'Failed to delete organization'),
  });

  const activateMutation = useMutation({
    mutationFn: async (id: string) => {
      const { data } = await apiClient.put<Organization>(`/organizations/${id}/activate`);
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['organizations'] }),
  });

  const deactivateMutation = useMutation({
    mutationFn: async (id: string) => {
      const { data } = await apiClient.put<Organization>(`/organizations/${id}/deactivate`);
      return data;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['organizations'] }),
  });

  const resetForm = () => {
    setShowForm(false);
    setEditingId(null);
    setName('');
    setError(null);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (editingId) {
      updateMutation.mutate({ id: editingId, name });
    } else {
      createMutation.mutate({ name });
    }
  };

  const handleEdit = (org: Organization) => {
    setEditingId(org.id);
    setName(org.name);
    setShowForm(true);
    setError(null);
  };

  const handleDelete = (org: Organization) => {
    if (confirm(`Delete organization "${org.name}"? This cannot be undone.`)) {
      deleteMutation.mutate(org.id);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Organizations</h1>
        <button
          onClick={() => { resetForm(); setShowForm(true); }}
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark"
        >
          + New Organization
        </button>
      </div>

      {showForm && (
        <Card title={editingId ? 'Edit Organization' : 'Create Organization'}>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700">Name</label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                placeholder="Acme Wholesaler"
              />
              <p className="mt-1 text-xs text-gray-500">Slug will be auto-generated from the name.</p>
            </div>
            {error && <p className="text-sm text-red-600">{error}</p>}
            <div className="flex gap-2">
              <button
                type="submit"
                disabled={createMutation.isPending || updateMutation.isPending}
                className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark disabled:opacity-50"
              >
                {editingId ? 'Update' : 'Create'}
              </button>
              <button
                type="button"
                onClick={resetForm}
                className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
              >
                Cancel
              </button>
            </div>
          </form>
        </Card>
      )}

      <Card title="All Organizations">
        {isLoading ? (
          <p className="text-sm text-gray-500">Loading...</p>
        ) : orgs && orgs.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead>
                <tr>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Name</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Slug</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {orgs.map((org) => (
                  <tr key={org.id}>
                    <td className="px-4 py-2 text-sm text-gray-900">{org.name}</td>
                    <td className="px-4 py-2 text-sm text-gray-500">{org.slug}</td>
                    <td className="px-4 py-2 text-sm">
                      <span className={org.isActive ? 'text-green-600' : 'text-red-600'}>
                        {org.isActive ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="px-4 py-2 text-sm">
                      <div className="flex gap-2">
                        <button onClick={() => handleEdit(org)} className="text-blue-600 hover:text-blue-800">Edit</button>
                        {org.isActive ? (
                          <button onClick={() => deactivateMutation.mutate(org.id)} className="text-orange-600 hover:text-orange-800">Deactivate</button>
                        ) : (
                          <button onClick={() => activateMutation.mutate(org.id)} className="text-green-600 hover:text-green-800">Activate</button>
                        )}
                        <button onClick={() => handleDelete(org)} className="text-red-600 hover:text-red-800">Delete</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-sm text-gray-500">No organizations yet. Create one to get started.</p>
        )}
      </Card>
    </div>
  );
}
