import { TableHTMLAttributes, ThHTMLAttributes, TdHTMLAttributes } from 'react';
import { cn } from '@/lib/utils';

interface TableProps extends TableHTMLAttributes<HTMLTableElement> {
  columns: string[];
}

export function Table({ columns, children, className, ...props }: TableProps) {
  return (
    <div className="overflow-x-auto">
      <table className={cn('min-w-full divide-y divide-gray-200', className)} {...props}>
        <thead className="bg-gray-50">
          <tr>
            {columns.map((col, i) => (
              <th
                key={i}
                className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500"
              >
                {col}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-200 bg-white">{children}</tbody>
      </table>
    </div>
  );
}

export function Th({ className, ...props }: ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th
      className={cn('px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500', className)}
      {...props}
    />
  );
}

export function Td({ className, ...props }: TdHTMLAttributes<HTMLTableCellElement>) {
  return (
    <td className={cn('whitespace-nowrap px-6 py-4 text-sm text-gray-900', className)} {...props} />
  );
}

export function Badge({ status, children }: { status: string; children: React.ReactNode }) {
  const colors: Record<string, string> = {
    ONLINE: 'bg-green-100 text-green-800',
    FENCING_ON: 'bg-green-100 text-green-800',
    OFFLINE: 'bg-gray-100 text-gray-800',
    FENCING_OFF: 'bg-gray-100 text-gray-800',
    FAULT: 'bg-red-100 text-red-800',
    PENDING: 'bg-yellow-100 text-yellow-800',
    QUEUED: 'bg-yellow-100 text-yellow-800',
    SENT: 'bg-blue-100 text-blue-800',
    ACK: 'bg-blue-100 text-blue-800',
    DONE: 'bg-green-100 text-green-800',
    FAILED: 'bg-red-100 text-red-800',
  };

  return (
    <span
      className={cn(
        'inline-flex rounded-full px-2 py-1 text-xs font-medium',
        colors[status] || 'bg-gray-100 text-gray-600'
      )}
    >
      {children}
    </span>
  );
}
