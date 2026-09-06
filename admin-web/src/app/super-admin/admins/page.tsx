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
  const [editingId, setEditingId] = useState<string | null>(null);
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
    mutationFn: async (params: any) => {
      const { data } = await apiClient.post<User>('/users', params);
      return data;
    },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['users'] }); resetForm(); },
    onError: (err: any) => setError(err?.response?.data?.message || 'Failed to create user'),
  });

  const updateMutation = useMutation({
    mutationFn: async (params: any) => {
      const { data } = await apiClient.put<User>(`/users/${params.id}`, params);
      return data;
    },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['users'] }); resetForm(); },
    onError: (err: any) => setError(err?.response?.data?.message || 'Failed to update user'),
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: string) => { await apiClient.delete(`/users/${id}`); },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
    onError: (err: any) => setError(err?.response?.data?.message || 'Failed to delete user'),
  });

  const activateMutation = useMutation({
    mutationFn: async (id: string) => { await apiClient.put(`/users/${id}/activate`); },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });

  const deactivateMutation = useMutation({
    mutationFn: async (id: string) => { await apiClient.put(`/users/${id}/deactivate`); },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });

  const resetForm = () => {
    setShowForm(false); setEditingId(null);
    setEmail(''); setPassword(''); setFullName(''); setPhone('');
    setOrgId(''); setRoleName('org_admin'); setError(null);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (editingId) {
      const params: any = { fullName, phone };
      if (password) params.password = password;
      updateMutation.mutate({ id: editingId, ...params });
    } else {
      if (!orgId) { setError('Please select an organization'); return; }
      createMutation.mutate({ email, password, fullName, phone, organizationId: orgId, roleName });
    }
  };

  const handleEdit = (user: User) => {
    setEditingId(user.id);
    setEmail(user.email); setFullName(user.fullName); setPhone(user.phone || '');
    setPassword(''); setOrgId(user.organizationId || ''); setError(null);
    setShowForm(true);
  };

  const handleDelete = (user: User) => {
    if (confirm(`Delete user "${user.email}"? This cannot be undone.`)) {
      deleteMutation.mutate(user.id);
    }
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
          onClick={() => { resetForm(); setShowForm(true); }}
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark"
        >
          + New Admin User
        </button>
      </div>

      {showForm && (
        <Card title={editingId ? 'Edit User' : 'Create Admin User'}>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700">Email</label>
                <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                  required={!editingId} disabled={!!editingId}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none disabled:bg-gray-100" placeholder="admin@example.com" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Full Name</label>
                <input type="text" value={fullName} onChange={(e) => setFullName(e.target.value)}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none" placeholder="John Doe" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Password {editingId && '(leave blank to keep current)'}
                </label>
                <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                  required={!editingId} minLength={8}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none" placeholder="Min 8 characters" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">Phone</label>
                <input type="text" value={phone} onChange={(e) => setPhone(e.target.value)}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none" placeholder="+91 98765 43210" />
              </div>
              {!editingId && (
                <>
                  <div>
                    <label className="block text-sm font-medium text-gray-700">Organization</label>
                    <select value={orgId} onChange={(e) => setOrgId(e.target.value)} required
                      className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none">
                      <option value="">Select organization...</option>
                      {orgs?.map((org) => (<option key={org.id} value={org.id}>{org.name}</option>))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700">Role</label>
                    <select value={roleName} onChange={(e) => setRoleName(e.target.value)}
                      className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none">
                      {roles?.filter((r) => r.name !== 'super_admin' && r.name !== 'customer').map((role) => (
                        <option key={role.id} value={role.name}>{role.name} — {role.description}</option>
                      ))}
                    </select>
                  </div>
                </>
              )}
            </div>
            {error && <p className="text-sm text-red-600">{error}</p>}
            <div className="flex gap-2">
              <button type="submit" disabled={createMutation.isPending || updateMutation.isPending}
                className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark disabled:opacity-50">
                {editingId ? 'Update' : 'Create User'}
              </button>
              <button type="button" onClick={resetForm}
                className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50">
                Cancel
              </button>
            </div>
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
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
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
                    <td className="px-4 py-2 text-sm">
                      <div className="flex gap-2">
                        <button onClick={() => handleEdit(user)} className="text-blue-600 hover:text-blue-800">Edit</button>
                        {user.isActive ? (
                          <button onClick={() => deactivateMutation.mutate(user.id)} className="text-orange-600 hover:text-orange-800">Deactivate</button>
                        ) : (
                          <button onClick={() => activateMutation.mutate(user.id)} className="text-green-600 hover:text-green-800">Activate</button>
                        )}
                        <button onClick={() => handleDelete(user)} className="text-red-600 hover:text-red-800">Delete</button>
                      </div>
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
