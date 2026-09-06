'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import { Card } from '@/components/ui/card';

interface User {
  id: string;
  organizationId: string | null;
  email: string;
  fullName: string;
  phone?: string;
  isActive: boolean;
  isLocked: boolean;
}

interface Organization {
  id: string;
  name: string;
  slug: string;
}

interface Role {
  id: string;
  name: string;
  description: string;
}

export default function AdminsPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [orgId, setOrgId] = useState('');
  const [roleName, setRoleName] = useState('org_admin');
  const [error, setError] = useState<string | null>(null);

  const { data: users, isLoading } = useQuery<User[]>({
    queryKey: ['users'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ content: User[] }>('/users');
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

  const { data: roles } = useQuery<Role[]>({
    queryKey: ['roles'],
    queryFn: async () => {
      const { data } = await apiClient.get<Role[]>('/roles');
      return data;
    },
  });

  const createMutation = useMutation({
    mutationFn: async (params: {
      email: string;
      password: string;
      fullName: string;
      phone: string;
      organizationId: string;
      roleName: string;
    }) => {
      const { data } = await apiClient.post<User>('/users', params);
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      setShowForm(false);
      setEmail('');
      setPassword('');
      setFullName('');
      setPhone('');
      setOrgId('');
      setRoleName('org_admin');
      setError(null);
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Failed to create user');
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!orgId) {
      setError('Please select an organization');
      return;
    }
    createMutation.mutate({ email, password, fullName, phone, organizationId: orgId, roleName });
  };

  const orgName = (id: string | null) => {
    if (!id || !orgs) return '—';
    const org = orgs.find((o) => o.id === id);
    return org ? org.name : '—';
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Admin Users</h1>
        <button
          onClick={() => setShowForm(!showForm)}
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark"
        >
          {showForm ? 'Cancel' : '+ New Admin User'}
        </button>
      </div>

      {showForm && (
        <Card title="Create Admin User">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700">Email</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                  placeholder="admin@example.com"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Full Name</label>
                <input
                  type="text"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                  placeholder="John Doe"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Password</label>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  minLength={8}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                  placeholder="Min 8 characters"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Phone</label>
                <input
                  type="text"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                  placeholder="+91 98765 43210"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Organization</label>
                <select
                  value={orgId}
                  onChange={(e) => setOrgId(e.target.value)}
                  required
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                >
                  <option value="">Select organization...</option>
                  {orgs?.map((org) => (
                    <option key={org.id} value={org.id}>
                      {org.name} ({org.slug})
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Role</label>
                <select
                  value={roleName}
                  onChange={(e) => setRoleName(e.target.value)}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                >
                  {roles?.filter((r) => r.name !== 'super_admin').map((role) => (
                    <option key={role.id} value={role.name}>
                      {role.name} — {role.description}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            {error && <p className="text-sm text-red-600">{error}</p>}
            <button
              type="submit"
              disabled={createMutation.isPending}
              className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark disabled:opacity-50"
            >
              {createMutation.isPending ? 'Creating...' : 'Create User'}
            </button>
          </form>
        </Card>
      )}

      <Card title="All Users">
        {isLoading ? (
          <p className="text-sm text-gray-500">Loading...</p>
        ) : users && users.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead>
                <tr>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Name</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Email</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Organization</th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {users.map((user) => (
                  <tr key={user.id}>
                    <td className="px-4 py-2 text-sm text-gray-900">{user.fullName || '—'}</td>
                    <td className="px-4 py-2 text-sm text-gray-500">{user.email}</td>
                    <td className="px-4 py-2 text-sm text-gray-500">{orgName(user.organizationId)}</td>
                    <td className="px-4 py-2 text-sm">
                      <span className={user.isActive ? 'text-green-600' : 'text-red-600'}>
                        {user.isActive ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-sm text-gray-500">No users yet. Create one to get started.</p>
        )}
      </Card>
    </div>
  );
}
