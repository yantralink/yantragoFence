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
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [error, setError] = useState<string | null>(null);

  const { data: orgs, isLoading } = useQuery<Organization[]>({
    queryKey: ['organizations'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ content: Organization[] }>('/organizations');
      return data.content;
    },
  });

  const createMutation = useMutation({
    mutationFn: async (params: { name: string; slug: string }) => {
      const { data } = await apiClient.post<Organization>('/organizations', params);
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations'] });
      setShowForm(false);
      setName('');
      setSlug('');
      setError(null);
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Failed to create organization');
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    createMutation.mutate({ name, slug });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Organizations</h1>
        <button
          onClick={() => setShowForm(!showForm)}
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark"
        >
          {showForm ? 'Cancel' : '+ New Organization'}
        </button>
      </div>

      {showForm && (
        <Card title="Create Organization">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700">Name</label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                placeholder="Acme Corp"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700">Slug</label>
              <input
                type="text"
                value={slug}
                onChange={(e) => setSlug(e.target.value.toLowerCase().replace(/\s+/g, '-'))}
                required
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                placeholder="acme-corp"
              />
            </div>
            {error && <p className="text-sm text-red-600">{error}</p>}
            <button
              type="submit"
              disabled={createMutation.isPending}
              className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark disabled:opacity-50"
            >
              {createMutation.isPending ? 'Creating...' : 'Create'}
            </button>
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
