import { HTMLAttributes, forwardRef } from 'react';
import { cn } from '@/lib/utils';

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  title?: string;
  description?: React.ReactNode;
}

export const Card = forwardRef<HTMLDivElement, CardProps>(
  ({ className, title, description, children, ...props }, ref) => {
    return (
      <div
        ref={ref}
        className={cn(
          'rounded-lg border border-gray-200 bg-white p-6 shadow-sm',
          className
        )}
        {...props}
      >
        {title && (
          <div className="mb-4 flex items-center justify-between">
            <h3 className="text-lg font-semibold text-gray-900">{title}</h3>
            {description && (
              <div className="text-sm text-gray-500">{description}</div>
            )}
          </div>
        )}
        {children}
      </div>
    );
  }
);
Card.displayName = 'Card';

interface StatCardProps {
  label: string;
  value: string | number;
  icon?: React.ReactNode;
  color?: string;
}

export function StatCard({ label, value, icon, color = 'text-primary' }: StatCardProps) {
  return (
    <Card className="flex items-center gap-4">
      {icon && <div className={cn('text-3xl', color)}>{icon}</div>}
      <div>
        <p className="text-sm text-gray-500">{label}</p>
        <p className="text-2xl font-bold text-gray-900">{value}</p>
      </div>
    </Card>
  );
}
