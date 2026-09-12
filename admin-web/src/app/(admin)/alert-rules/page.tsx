'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import { Card } from '@/components/ui/card';

interface AlertRule {
  id: string;
  organizationId: string;
  machineId: string | null;
  name: string;
  alertType: string;
  conditionConfig: string;
  severity: string;
  isActive: boolean;
  sustainMinutes: number;
  recoveryMinutes: number;
  escalationMinutes: number | null;
  escalationSeverity: string | null;
  createdAt: string;
  updatedAt: string;
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

const ALERT_TYPES = [
  { value: 'LOW_BATTERY', label: 'Low Battery', metric: 'battery', unit: '%' },
  { value: 'VOLTAGE_DROP', label: 'Voltage Drop', metric: 'voltage', unit: 'V' },
  { value: 'GSM_SIGNAL_LOW', label: 'Low GSM Signal', metric: 'gsm_signal', unit: '' },
];

const SEVERITIES = ['CRITICAL', 'WARNING', 'INFO'];

const OPERATORS = [
  { value: '<', label: 'Less than (<)' },
  { value: '>', label: 'Greater than (>)' },
  { value: '<=', label: 'Less or equal (<=)' },
  { value: '>=', label: 'Greater or equal (>=)' },
];

interface FormData {
  name: string;
  alertType: string;
  metric: string;
  operator: string;
  threshold: string;
  windowMinutes: string;
  severity: string;
  machineId: string;
  sustainMinutes: string;
  recoveryMinutes: string;
  isActive: boolean;
}

const emptyForm: FormData = {
  name: '',
  alertType: 'VOLTAGE_DROP',
  metric: 'voltage',
  operator: '<',
  threshold: '',
  windowMinutes: '5',
  severity: 'WARNING',
  machineId: '',
  sustainMinutes: '0',
  recoveryMinutes: '5',
  isActive: true,
};

export default function AdminAlertRulesPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<FormData>(emptyForm);
  const [error, setError] = useState<string | null>(null);

  const { data: rulesPage, isLoading } = useQuery<PageResponse<AlertRule>>({
    queryKey: ['alert-rules'],
    queryFn: async () => {
      const { data } = await apiClient.get<PageResponse<AlertRule>>(
        '/alert-rules?size=100'
      );
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

  const rules = rulesPage?.content ?? [];

  const buildConditionConfig = (f: FormData): string => {
    return JSON.stringify({
      metric: f.metric,
      operator: f.operator,
      threshold: parseFloat(f.threshold),
      windowMinutes: parseInt(f.windowMinutes) || 5,
    });
  };

  const parseConditionConfig = (config: string): Partial<FormData> => {
    try {
      const parsed = JSON.parse(config);
      return {
        metric: parsed.metric || 'voltage',
        operator: parsed.operator || '<',
        threshold: String(parsed.threshold ?? ''),
        windowMinutes: String(parsed.windowMinutes ?? '5'),
      };
    } catch {
      return {};
    }
  };

  const createMutation = useMutation({
    mutationFn: async (f: FormData) => {
      const body = {
        name: f.name,
        alertType: f.alertType,
        conditionConfig: buildConditionConfig(f),
        severity: f.severity,
        machineId: f.machineId || null,
        sustainMinutes: parseInt(f.sustainMinutes) || 0,
        recoveryMinutes: parseInt(f.recoveryMinutes) || 5,
        isActive: f.isActive,
      };
      const { data } = await apiClient.post('/alert-rules', body);
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['alert-rules'] });
      setShowForm(false);
      setForm(emptyForm);
      setError(null);
    },
    onError: (err: any) =>
      setError(err?.response?.data?.message || 'Failed to create rule'),
  });

  const updateMutation = useMutation({
    mutationFn: async (params: { id: string; f: FormData }) => {
      const body = {
        name: params.f.name,
        alertType: params.f.alertType,
        conditionConfig: buildConditionConfig(params.f),
        severity: params.f.severity,
        machineId: params.f.machineId || null,
        sustainMinutes: parseInt(params.f.sustainMinutes) || 0,
        recoveryMinutes: parseInt(params.f.recoveryMinutes) || 5,
        isActive: params.f.isActive,
      };
      const { data } = await apiClient.put(`/alert-rules/${params.id}`, body);
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['alert-rules'] });
      setShowForm(false);
      setEditingId(null);
      setForm(emptyForm);
      setError(null);
    },
    onError: (err: any) =>
      setError(err?.response?.data?.message || 'Failed to update rule'),
  });

  const toggleMutation = useMutation({
    mutationFn: async (params: { id: string; activate: boolean }) => {
      const endpoint = params.activate
        ? `/alert-rules/${params.id}/activate`
        : `/alert-rules/${params.id}/deactivate`;
      const { data } = await apiClient.post(endpoint);
      return data;
    },
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['alert-rules'] }),
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.delete(`/alert-rules/${id}`);
    },
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['alert-rules'] }),
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.name || !form.threshold) {
      setError('Name and threshold are required');
      return;
    }
    if (editingId) {
      updateMutation.mutate({ id: editingId, f: form });
    } else {
      createMutation.mutate(form);
    }
  };

  const handleEdit = (rule: AlertRule) => {
    const parsed = parseConditionConfig(rule.conditionConfig);
    setForm({
      name: rule.name,
      alertType: rule.alertType,
      metric: parsed.metric || 'voltage',
      operator: parsed.operator || '<',
      threshold: parsed.threshold || '',
      windowMinutes: parsed.windowMinutes || '5',
      severity: rule.severity,
      machineId: rule.machineId || '',
      sustainMinutes: String(rule.sustainMinutes ?? 0),
      recoveryMinutes: String(rule.recoveryMinutes ?? 5),
      isActive: rule.isActive,
    });
    setEditingId(rule.id);
    setShowForm(true);
  };

  const handleAlertTypeChange = (value: string) => {
    const at = ALERT_TYPES.find((t) => t.value === value);
    setForm({
      ...form,
      alertType: value,
      metric: at?.metric ?? form.metric,
    });
  };

  const machineName = (machineId: string | null) => {
    if (!machineId) return 'All machines (org-wide)';
    return (
      machines?.find((m) => m.id === machineId)?.name ?? machineId.slice(0, 8)
    );
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Alert Rules</h1>
        {!showForm && (
          <button
            onClick={() => {
              setForm(emptyForm);
              setEditingId(null);
              setShowForm(true);
            }}
            className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-dark"
          >
            + Create Rule
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
        <Card title={editingId ? 'Edit Alert Rule' : 'Create Alert Rule'}>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Rule Name *
                </label>
                <input
                  type="text"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  placeholder="e.g. Low Voltage Alert"
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Alert Type *
                </label>
                <select
                  value={form.alertType}
                  onChange={(e) => handleAlertTypeChange(e.target.value)}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                >
                  {ALERT_TYPES.map((t) => (
                    <option key={t.value} value={t.value}>
                      {t.label}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Operator *
                </label>
                <select
                  value={form.operator}
                  onChange={(e) =>
                    setForm({ ...form, operator: e.target.value })
                  }
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                >
                  {OPERATORS.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Threshold *
                </label>
                <input
                  type="number"
                  step="0.01"
                  value={form.threshold}
                  onChange={(e) =>
                    setForm({ ...form, threshold: e.target.value })
                  }
                  placeholder="e.g. 13.0"
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Window (minutes)
                </label>
                <input
                  type="number"
                  value={form.windowMinutes}
                  onChange={(e) =>
                    setForm({ ...form, windowMinutes: e.target.value })
                  }
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                />
                <p className="mt-1 text-xs text-gray-500">
                  Look back window for telemetry data
                </p>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Severity *
                </label>
                <select
                  value={form.severity}
                  onChange={(e) =>
                    setForm({ ...form, severity: e.target.value })
                  }
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                >
                  {SEVERITIES.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Machine (optional)
                </label>
                <select
                  value={form.machineId}
                  onChange={(e) =>
                    setForm({ ...form, machineId: e.target.value })
                  }
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                >
                  <option value="">All machines (org-wide)</option>
                  {machines?.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.name} ({m.machineId})
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Sustain (minutes)
                </label>
                <input
                  type="number"
                  value={form.sustainMinutes}
                  onChange={(e) =>
                    setForm({ ...form, sustainMinutes: e.target.value })
                  }
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                />
                <p className="mt-1 text-xs text-gray-500">
                  Condition must persist this long before alerting
                </p>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">
                  Recovery (minutes)
                </label>
                <input
                  type="number"
                  value={form.recoveryMinutes}
                  onChange={(e) =>
                    setForm({ ...form, recoveryMinutes: e.target.value })
                  }
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                />
                <p className="mt-1 text-xs text-gray-500">
                  Condition must clear this long before resolving
                </p>
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
                {editingId ? 'Update Rule' : 'Create Rule'}
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

      {/* Rules list */}
      <Card title="Configured Alert Rules">
        {isLoading ? (
          <p className="text-sm text-gray-500">Loading...</p>
        ) : rules.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead>
                <tr>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                    Name
                  </th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                    Type
                  </th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                    Condition
                  </th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                    Severity
                  </th>
                  <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                    Machine
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
                {rules.map((r) => {
                  const parsed = parseConditionConfig(r.conditionConfig);
                  return (
                    <tr key={r.id}>
                      <td className="px-4 py-3 text-sm font-medium text-gray-900">
                        {r.name}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-600">
                        {r.alertType}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-600">
                        {parsed.metric} {parsed.operator} {parsed.threshold}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${
                            r.severity === 'CRITICAL'
                              ? 'bg-red-100 text-red-700'
                              : r.severity === 'WARNING'
                                ? 'bg-amber-100 text-amber-700'
                                : 'bg-blue-100 text-blue-700'
                          }`}
                        >
                          {r.severity}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-600">
                        {machineName(r.machineId)}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`text-xs ${
                            r.isActive ? 'text-green-600' : 'text-gray-400'
                          }`}
                        >
                          {r.isActive ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm">
                        <div className="flex gap-3">
                          <button
                            onClick={() => handleEdit(r)}
                            className="text-primary hover:text-primary-dark"
                          >
                            Edit
                          </button>
                          <button
                            onClick={() =>
                              toggleMutation.mutate({
                                id: r.id,
                                activate: !r.isActive,
                              })
                            }
                            className="text-gray-600 hover:text-gray-800"
                          >
                            {r.isActive ? 'Deactivate' : 'Activate'}
                          </button>
                          <button
                            onClick={() => {
                              if (
                                confirm(
                                  `Delete rule "${r.name}"? This cannot be undone.`
                                )
                              )
                                deleteMutation.mutate(r.id);
                            }}
                            className="text-red-600 hover:text-red-800"
                          >
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-sm text-gray-500">
            No alert rules configured. Create a rule to start monitoring
            telemetry thresholds.
          </p>
        )}
      </Card>
    </div>
  );
}
