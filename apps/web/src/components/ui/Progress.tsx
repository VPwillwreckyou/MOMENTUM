import type { CSSProperties, ReactNode } from 'react';

export function ProgressRing({
  value,
  size = 68,
  stroke = 5,
  children,
  label,
}: {
  value: number;
  size?: number;
  stroke?: number;
  children?: ReactNode;
  label?: string;
}) {
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const clamped = Math.max(0, Math.min(100, value));
  const offset = circumference - (clamped / 100) * circumference;
  return (
    <div
      className="progress-ring"
      style={{ width: size, height: size, '--ring-value': `${clamped * 3.6}deg` } as CSSProperties}
      role="img"
      aria-label={label ?? `${clamped}% complete`}
    >
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true">
        <circle
          className="progress-ring__track"
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          strokeWidth={stroke}
        />
        <circle
          className="progress-ring__value"
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          strokeWidth={stroke}
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          strokeLinecap="round"
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </svg>
      <div className="progress-ring__label">{children ?? <strong>{clamped}%</strong>}</div>
    </div>
  );
}

export function ProgressBar({
  value,
  tone = 'accent',
  size = 'md',
  animated = true,
}: {
  value: number;
  tone?: 'accent' | 'success' | 'warning' | 'danger';
  size?: 'sm' | 'md';
  animated?: boolean;
}) {
  const clamped = Math.max(0, Math.min(100, value));
  return (
    <div
      className={`progress-bar progress-bar--${size}`}
      role="progressbar"
      aria-valuenow={clamped}
      aria-valuemin={0}
      aria-valuemax={100}
    >
      <span
        className={`progress-bar__fill progress-bar__fill--${tone}${animated ? ' progress-bar__fill--animated' : ''}`}
        style={{ width: `${clamped}%` }}
      />
    </div>
  );
}
