'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import { Card } from '@/components/ui/card';

interface Geofence {
  id: string;
  organizationId: string;
  machineId: string;
  name: string;
  latitude: number;
  longitude: number;
  radiusMeters: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
  customerName?: string | null;
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

interface FormData {
  machineId: string;
  name: string;
  latitude: string;
  longitude: string;
  radiusMeters: string;
  isActive: boolean;
}

const emptyForm: FormData = {
  machineId: '',
  name: '',
  latitude: '',
  longitude: '',
  radiusMeters: '100',
  isActive: true,
};

export default function AdminGeofencesPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<FormData>(emptyForm);
  const [error, setError] = useState<string | null>(null);

  const { data: geofences, isLoading } = useQuery<Geofence[]>({
    queryKey: ['geofences'],
    queryFn: async () => {
      const { data } = await apiClient.get<Geofence[]>('/geofences');
      return data;
    },
  });

  const { data: machines } = useQuery<Machine[]>({
    queryKey: ['machines'],
    queryFn: async () => {
      const { data } = await apiClient.get<PageResponse<Machine>>('/machines');
      return data.content;
    },
  });

  const createMutation = useMutation({
    mutationFn: async (f: FormData) => {
      const body = {
        machineId: f.machineId,
        name: f.name,
        latitude: parseFloat(f.latitude),
        longitude: parseFloat(f.longitude),
        radiusMeters: parseInt(f.radiusMeters) || 100,
        isActive: f.isActive,
      };
      const { data } = await apiClient.post('/geofences', body);
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['geofences'] });
      setShowForm(false);
      setForm(emptyForm);
      setError(null);
    },
    onError: (err: any) =>
      setError(err?.response?.data?.message || 'Failed to create geofence'),
  });

  const updateMutation = useMutation({
    mutationFn: async (params: { id: string; f: FormData }) => {
      const body = {
        machineId: params.f.machineId,
        name: params.f.name,
        latitude: parseFloat(params.f.latitude),
        longitude: parseFloat(params.f.longitude),
        radiusMeters: parseInt(params.f.radiusMeters) || 100,
        isActive: params.f.isActive,
      };
      const { data } = await apiClient.put(`/geofences/${params.id}`, body);
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['geofences'] });
      setShowForm(false);
      setEditingId(null);
      setForm(emptyForm);
      setError(null);
    },
    onError: (err: any) =>
      setError(err?.response?.data?.message || 'Failed to update geofence'),
  });

  const toggleMutation = useMutation({
    mutationFn: async (params: { id: string; activate: boolean }) => {
      const endpoint = params.activate
        ? `/geofences/${params.id}/activate`
        : `/geofences/${params.id}/deactivate`;
      const { data } = await apiClient.post(endpoint);
      return data;
    },
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['geofences'] }),
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.delete(`/geofences/${id}`);
    },
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['geofences'] }),
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.machineId || !form.name || !form.latitude || !form.longitude) {
      setError('Machine, name, latitude, and longitude are required');
      return;
    }
    const lat = parseFloat(form.latitude);
    const lng = parseFloat(form.longitude);
    if (lat < -90 || lat > 90) {
      setError('Latitude must be between -90 and 90');
      return;
    }
    if (lng < -180 || lng > 180) {
      setError('Longitude must be between -180 and 180');
      return;
    }
    if (editingId) {
      updateMutation.mutate({ id: editingId, f: form });
    } else {
      createMutation.mutate(form);
    }
  };

  const handleEdit = (g: Geofence) => {
    setForm({
      machineId: g.machineId,
      name: g.name,
      latitude: String(g.latitude),
      longitude: String(g.longitude),
      radiusMeters: String(g.radiusMeters),
      isActive: g.isActive,
    });
    setEditingId(g.id);
    setShowForm(true);
  };

  const machineName = (machineId: string) => {
    return (
      machines?.find((m) => m.id === machineId)?.name ?? machineId.slice(0, 8)
    );
  };

  const handleAutoFillLocation = async () => {
    if (!form.machineId) {
      setError('Select a machine first');
      return;
    }
    try {
      const { data } = await apiClient.get(
        `/geofences/machine/${form.machineId}`
      );
      if (data) {
        setForm({
          ...form,
          latitude: String(data.latitude),
          longitude: String(data.longitude),
        });
        setError(null);
      } else {
        // Try to get current machine location
        const { data: loc } = await apiClient.get(
          `/locations/${form.machineId}`
        );
        if (loc) {
          setForm({
            ...form,
            latitude: String(loc.latitude),
            longitude: String(loc.longitude),
          });
          setError(null);
        } else {
          setError('No location data available for this machine');
        }
      }
    } catch {
      setError('No location data available for this machine');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Geo-Fences</h1>
          <p className="mt-1 text-sm text-gray-500">
            Define location boundaries for theft detection. Machines outside
            their geo-fence trigger a critical alert.
          </p>
        </div>
        {!showForm && (
          <button
            onClick={() => {
              setForm(emptyForm);
              setEditingId(null);
              setShowForm(true);
            }}
            className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark"
          >
            + Create Geofence
          </button>
        )}
      </div>

      {error && (
        <div className="rounded-md bg-red-50 p-3 text-sm text-red-600">
          {error}
        </div>
      )}

      {/* Create/Edit form */}
      {showForm && (
        <Card title={editingId ? 'Edit Geo-Fence' : 'Create Geo-Fence'}>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Machine *
                </label>
                <select
                  value={form.machineId}
                  onChange={(e) =>
                    setForm({ ...form, machineId: e.target.value })
                  }
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                  required
                >
                  <option value="">Select a machine</option>
                  {machines?.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.name} ({m.machineId})
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Geofence Name *
                </label>
                <input
                  type="text"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  placeholder="e.g. Workshop Boundary"
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Latitude *
                </label>
                <input
                  type="number"
                  step="0.000001"
                  value={form.latitude}
                  onChange={(e) =>
                    setForm({ ...form, latitude: e.target.value })
                  }
                  placeholder="e.g. 18.520430"
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Longitude *
                </label>
                <input
                  type="number"
                  step="0.000001"
                  value={form.longitude}
                  onChange={(e) =>
                    setForm({ ...form, longitude: e.target.value })
                  }
                  placeholder="e.g. 73.856743"
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Radius (meters) *
                </label>
                <input
                  type="number"
                  min="10"
                  max="10000"
                  value={form.radiusMeters}
                  onChange={(e) =>
                    setForm({ ...form, radiusMeters: e.target.value })
                  }
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                  required
                />
                <p className="mt-1 text-xs text-gray-500">
                  10–10000 meters. Machine outside this radius triggers a
                  breach alert.
                </p>
              </div>
              <div className="flex items-end gap-2">
                <button
                  type="button"
                  onClick={handleAutoFillLocation}
                  className="rounded-md border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                >
                  Use Machine Location
                </button>
              </div>
              <div className="flex items-end">
                <label className="flex items-center gap-2 text-sm font-medium text-gray-700">
                  <input
                    type="checkbox"
                    checked={form.isActive}
                    onChange={(e) =>
                      setForm({ ...form, isActive: e.target.checked })
                    }
                    className="h-4 w-4 rounded border-gray-300"
                  />
                  Active
                </label>
              </div>
            </div>

            <div className="flex gap-2">
              <button
                type="submit"
                disabled={createMutation.isPending || updateMutation.isPending}
                className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark disabled:opacity-50"
              >
                {editingId ? 'Update Geofence' : 'Create Geofence'}
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowForm(false);
                  setEditingId(null);
                  setForm(emptyForm);
                  setError(null);
                }}
                className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
              >
                Cancel
              </button>
            </div>
          </form>
        </Card>
      )}

      {/* Geofences list */}
      <Card title="Configured Geo-Fences">
        {isLoading ? (
          <p className="text-sm text-gray-500">Loading...</p>
        ) : geofences && geofences.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead>
                <tr>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                    Name
                  </th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                    Customer
                  </th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                    Machine
                  </th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                    Center
                  </th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                    Radius
                  </th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                    Status
                  </th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {geofences.map((g) => (
                  <tr key={g.id}>
                    <td className="px-4 py-3 text-sm font-medium text-gray-900">
                      {g.name}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {g.customerName ?? (
                        <span className="text-gray-400">Unassigned</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {machineName(g.machineId)}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {g.latitude.toFixed(6)}, {g.longitude.toFixed(6)}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {g.radiusMeters}m
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`text-xs ${
                          g.isActive ? 'text-green-600' : 'text-gray-400'
                        }`}
                      >
                        {g.isActive ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-sm">
                      <div className="flex gap-3">
                        <button
                          onClick={() => handleEdit(g)}
                          className="text-primary hover:text-primary-dark"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() =>
                            toggleMutation.mutate({
                              id: g.id,
                              activate: !g.isActive,
                            })
                          }
                          className="text-gray-600 hover:text-gray-800"
                        >
                          {g.isActive ? 'Deactivate' : 'Activate'}
                        </button>
                        <button
                          onClick={() => {
                            if (
                              confirm(
                                `Delete geofence "${g.name}"? This will stop theft detection for this machine.`
                              )
                            ) {
                              deleteMutation.mutate(g.id);
                            }
                          }}
                          className="text-red-600 hover:text-red-700"
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="py-8 text-center">
            <p className="text-sm text-gray-500">
              No geo-fences configured. Create one to enable theft detection.
            </p>
          </div>
        )}
      </Card>
    </div>
  );
}
