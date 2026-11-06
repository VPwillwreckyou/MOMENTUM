import type { ReactNode } from 'react';
import type { Priority } from '../../state/types';

export function Badge({
  children,
  tone = 'neutral',
  className = '',
}: {
  children: ReactNode;
  tone?: 'neutral' | 'accent' | 'success' | 'warning' | 'danger' | 'outline';
  className?: string;
}) {
  return <span className={`badge badge--${tone}${className ? ` ${className}` : ''}`}>{children}</span>;
}

export const PRIORITY_LABELS: Record<Priority, string> = {
  low: 'Low',
  medium: 'Medium',
  high: 'High',
  critical: 'Critical',
};

export function PriorityBadge({ priority }: { priority: Priority }) {
  return (
    <span className={`priority priority--${priority}`}>
      <span className="priority__dot" aria-hidden="true" />
      {PRIORITY_LABELS[priority]}
    </span>
  );
}
