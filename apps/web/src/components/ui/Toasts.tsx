import { AlertTriangle, CheckCircle2, Info, X } from 'lucide-react';
import { useMomentum } from '../../state/MomentumContext';

export function Toasts() {
  const { toasts, dismissToast } = useMomentum();
  return (
    <div className="toast-viewport" role="region" aria-label="Notifications" aria-live="polite">
      {toasts.map((toast) => (
        <div key={toast.id} className={`toast toast--${toast.tone}`} role="status">
          <span className="toast__icon" aria-hidden="true">
            {toast.tone === 'success' ? <CheckCircle2 size={18} /> : null}
            {toast.tone === 'error' ? <AlertTriangle size={18} /> : null}
            {toast.tone === 'info' ? <Info size={18} /> : null}
          </span>
          <span className="toast__message">{toast.message}</span>
          {toast.actionLabel && toast.onAction ? (
            <button
              type="button"
              className="toast__action"
              onClick={() => {
                toast.onAction?.();
                dismissToast(toast.id);
              }}
            >
              {toast.actionLabel}
            </button>
          ) : null}
          <button type="button" className="toast__close" onClick={() => dismissToast(toast.id)} aria-label="Dismiss notification">
            <X size={14} />
          </button>
        </div>
      ))}
    </div>
  );
}
