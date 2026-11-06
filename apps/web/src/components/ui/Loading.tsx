import { LogoMark } from './Logo';

export function AppLoading({ message = 'Opening your command center' }: { message?: string }) {
  return (
    <div className="app-loading" role="status" aria-live="polite">
      <div className="app-loading__mark">
        <LogoMark size={46} />
        <span className="app-loading__scan" />
      </div>
      <div>
        <p className="app-loading__brand">MOMENTUM</p>
        <p className="app-loading__message">{message}…</p>
      </div>
    </div>
  );
}

export function InlineLoader({ label = 'Loading' }: { label?: string }) {
  return (
    <span className="inline-loader" role="status">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </span>
  );
}

export function Skeleton({ className = '' }: { className?: string }) {
  return <span className={`skeleton${className ? ` ${className}` : ''}`} aria-hidden="true" />;
}
