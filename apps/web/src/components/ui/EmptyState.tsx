import type { ReactNode } from 'react';

export function EmptyState({
  icon,
  eyebrow,
  title,
  description,
  actions,
  compact = false,
}: {
  icon: ReactNode;
  eyebrow?: string;
  title: string;
  description: ReactNode;
  actions?: ReactNode;
  compact?: boolean;
}) {
  return (
    <div className={`empty-state${compact ? ' empty-state--compact' : ''}`}>
      <div className="empty-state__icon" aria-hidden="true">
        {icon}
      </div>
      {eyebrow ? <span className="eyebrow">{eyebrow}</span> : null}
      <h2 className="empty-state__title">{title}</h2>
      <p className="empty-state__description">{description}</p>
      {actions ? <div className="empty-state__actions">{actions}</div> : null}
    </div>
  );
}
