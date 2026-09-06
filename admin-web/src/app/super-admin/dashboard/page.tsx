'use client';

import { useQuery } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import { Card, StatCard } from '@/components/ui/card';

interface Organization {
  id: string;
  name: string;
  slug: string;
  isActive: boolean;
}

interface User {
  id: string;
  email: string;
  fullName: string;
  isActive: boolean;
}

export default function SuperAdminDashboardPage() {
  const { data: orgs } = useQuery<Organization[]>({
    queryKey: ['organizations'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ content: Organization[] }>('/organizations');
      return data.content;
    },
  });

  const { data: users } = useQuery<User[]>({
    queryKey: ['users'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ content: User[] }>('/users');
      return data.content;
    },
  });

  const totalOrgs = orgs?.length ?? 0;
  const activeOrgs = orgs?.filter((o) => o.isActive).length ?? 0;
  const totalUsers = users?.length ?? 0;
  const activeUsers = users?.filter((u) => u.isActive).length ?? 0;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Super Admin Dashboard</h1>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Organizations" value={totalOrgs} icon="🏢" color="text-blue-600" />
        <StatCard label="Active Orgs" value={activeOrgs} icon="✅" color="text-green-600" />
        <StatCard label="Total Users" value={totalUsers} icon="�" color="text-purple-600" />
        <StatCard label="Active Users" value={activeUsers} icon="🟢" color="text-green-600" />
      </div>
      <Card title="Quick Actions">
        <div className="space-y-2 text-sm text-gray-600">
          <p>1. Create an Organization (wholesaler/company)</p>
          <p>2. Create an Admin User and assign them to the organization</p>
          <p>3. The admin user can then log in and create customers, machines, and send commands</p>
        </div>
      </Card>
    </div>
  );
}
