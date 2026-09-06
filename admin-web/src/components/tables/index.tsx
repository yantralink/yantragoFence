'use client';

import { cn } from '@/lib/utils';
import { Table, Td, Badge } from '@/components/ui/table';
import type { Machine } from '@/types';
import { statusBadgeClass, formatDateTime } from '@/lib/utils';

interface MachineTableProps {
  machines: Machine[];
  onRowClick?: (machine: Machine) => void;
}

export function MachineTable({ machines, onRowClick }: MachineTableProps) {
  return (
    <Table columns={['Name', 'IMEI', 'Protocol', 'Status', 'Last Seen']}>
      {machines.map((m) => (
        <tr
          key={m.id}
          onClick={() => onRowClick?.(m)}
          className={cn('cursor-pointer hover:bg-gray-50')}
        >
          <Td className="font-medium">{m.name}</Td>
          <Td>{m.imei}</Td>
          <Td>{m.protocolType}</Td>
          <Td>
            <span className={cn('inline-flex rounded-full px-2 py-1 text-xs font-medium', statusBadgeClass(m.status))}>
              {m.status}
            </span>
          </Td>
          <Td>{formatDateTime(m.lastSeenAt)}</Td>
        </tr>
      ))}
    </Table>
  );
}

interface CommandTableProps {
  commands: Array<{
    id: string;
    commandType: string;
    status: string;
    createdAt: string;
  }>;
}

export function CommandTable({ commands }: CommandTableProps) {
  return (
    <Table columns={['Command', 'Status', 'Created At']}>
      {commands.map((c) => (
        <tr key={c.id}>
          <Td className="font-medium">{c.commandType}</Td>
          <Td><Badge status={c.status}>{c.status}</Badge></Td>
          <Td>{formatDateTime(c.createdAt)}</Td>
        </tr>
      ))}
    </Table>
  );
}
