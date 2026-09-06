import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/** Merge Tailwind CSS classes with conflict resolution. */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}

/** Format a date string for display. */
export function formatDate(date?: string | null): string {
  if (!date) return '--';
  return new Date(date).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

/** Format a datetime string for display. */
export function formatDateTime(date?: string | null): string {
  if (!date) return '--';
  return new Date(date).toLocaleString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

/** Format a relative time (e.g., "5m ago"). */
export function timeAgo(date?: string | null): string {
  if (!date) return 'Never';
  const now = Date.now();
  const then = new Date(date).getTime();
  const diff = Math.floor((now - then) / 1000);

  if (diff < 60) return 'Just now';
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  if (diff < 2592000) return `${Math.floor(diff / 86400)}d ago`;
  return formatDate(date);
}

/** Get status color class for a machine status. */
export function statusColor(status: string): string {
  switch (status) {
    case 'ONLINE':
    case 'FENCING_ON':
      return 'text-status-online';
    case 'FAULT':
      return 'text-status-fault';
    case 'OFFLINE':
    case 'FENCING_OFF':
      return 'text-status-offline';
    default:
      return 'text-gray-500';
  }
}

/** Get status badge background class. */
export function statusBadgeClass(status: string): string {
  switch (status) {
    case 'ONLINE':
    case 'FENCING_ON':
      return 'bg-green-100 text-green-800';
    case 'FAULT':
      return 'bg-red-100 text-red-800';
    case 'OFFLINE':
    case 'FENCING_OFF':
      return 'bg-gray-100 text-gray-800';
    default:
      return 'bg-gray-100 text-gray-600';
  }
}
