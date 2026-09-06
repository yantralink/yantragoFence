'use client';

import { useMachines } from '@/hooks/use-machines';
import { Card } from '@/components/ui/card';
import { MachineTable } from '@/components/tables';

export default function MachinesPage() {
  const { data: machines, isLoading } = useMachines();

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Machines</h1>
      <Card>
        {isLoading ? (
          <div className="text-gray-500">Loading machines...</div>
        ) : machines && machines.length > 0 ? (
          <MachineTable machines={machines} />
        ) : (
          <p className="text-sm text-gray-500">No machines found.</p>
        )}
      </Card>
    </div>
  );
}
