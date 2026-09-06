'use client';

import { useQuery } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import { Card, StatCard } from '@/components/ui/card';

interface Machine {
  id: string;
  machineId: string;
  name: string;
  status: string;
  isOnline: boolean;
  customerId?: string | null;
}

interface Customer {
  id: string;
  name: string;
  isActive: boolean;
}

export default function AdminDashboardPage() {
  const { data: machines, isLoading: machinesLoading } = useQuery<Machine[]>({
    queryKey: ['machines'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ content: Machine[] }>('/machines');
      return data.content;
    },
  });

  const { data: customers } = useQuery<Customer[]>({
    queryKey: ['customers'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ content: Customer[] }>('/customers');
      return data.content;
    },
  });

  const totalMachines = machines?.length ?? 0;
  const activeMachines = machines?.filter((m) => m.status === 'ACTIVE').length ?? 0;
  const inStock = machines?.filter((m) => m.status === 'IN_STOCK').length ?? 0;
  const fault = machines?.filter((m) => m.status === 'FAULT').length ?? 0;
  const offline = machines?.filter((m) => m.status === 'OFFLINE' || (m.status === 'ACTIVE' && !m.isOnline)).length ?? 0;
  const assigned = machines?.filter((m) => m.customerId).length ?? 0;
  const totalCustomers = customers?.length ?? 0;
  const activeCustomers = customers?.filter((c) => c.isActive).length ?? 0;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>

      {machinesLoading ? (
        <div className="text-gray-500">Loading...</div>
      ) : (
        <>
          <div>
            <h2 className="mb-3 text-lg font-semibold text-gray-700">Machines</h2>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <StatCard label="Total Machines" value={totalMachines} icon="⚡" color="text-blue-600" />
              <StatCard label="Active" value={activeMachines} icon="🟢" color="text-green-600" />
              <StatCard label="In Stock" value={inStock} icon="📦" color="text-indigo-600" />
              <StatCard label="Fault" value={fault} icon="🔴" color="text-red-600" />
            </div>
          </div>

          <div>
            <h2 className="mb-3 text-lg font-semibold text-gray-700">Customers</h2>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <StatCard label="Total Customers" value={totalCustomers} icon="👥" color="text-blue-600" />
              <StatCard label="Active Customers" value={activeCustomers} icon="✅" color="text-green-600" />
              <StatCard label="Assigned Machines" value={assigned} icon="🔗" color="text-purple-600" />
              <StatCard label="Unassigned" value={totalMachines - assigned} icon="�" color="text-gray-600" />
            </div>
          </div>
        </>
      )}

      <Card title="Recent Activity">
        <p className="text-sm text-gray-500">Machine commands and alerts will appear here.</p>
      </Card>
    </div>
  );
}
