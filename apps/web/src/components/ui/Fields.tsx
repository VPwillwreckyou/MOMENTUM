import { forwardRef, useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { Check } from 'lucide-react';

interface CommonFieldProps {
  label?: ReactNode;
  hint?: ReactNode;
  error?: string | null;
  optional?: boolean;
  className?: string;
}

function FieldShell({
  id,
  label,
  hint,
  error,
  optional,
  className = '',
  children,
}: CommonFieldProps & { id: string; children: ReactNode }) {
  return (
    <div className={`field${error ? ' field--error' : ''}${className ? ` ${className}` : ''}`}>
      {label ? (
        <div className="field__label-row">
          <label className="field__label" htmlFor={id}>
            {label}
          </label>
          {optional ? <span className="field__optional">Optional</span> : null}
        </div>
      ) : null}
      {children}
      {error ? (
        <p className="field__error" role="alert">
          {error}
        </p>
      ) : hint ? (
        <p className="field__hint">{hint}</p>
      ) : null}
    </div>
  );
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & CommonFieldProps>(
  function Input({ label, hint, error, optional, className, id, ...props }, ref) {
    const generated = useId();
    const inputId = id ?? generated;
    return (
      <FieldShell id={inputId} label={label} hint={hint} error={error} optional={optional} className={className}>
        <div className="field__control">
          <input ref={ref} id={inputId} className="input" aria-invalid={error ? true : undefined} {...props} />
        </div>
      </FieldShell>
    );
  },
);

export const Textarea = forwardRef<
  HTMLTextAreaElement,
  TextareaHTMLAttributes<HTMLTextAreaElement> & CommonFieldProps
>(function Textarea({ label, hint, error, optional, className, id, ...props }, ref) {
  const generated = useId();
  const inputId = id ?? generated;
  return (
    <FieldShell id={inputId} label={label} hint={hint} error={error} optional={optional} className={className}>
      <textarea
        ref={ref}
        id={inputId}
        className="textarea"
        aria-invalid={error ? true : undefined}
        {...props}
      />
    </FieldShell>
  );
});

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement> & CommonFieldProps>(
  function Select({ label, hint, error, optional, className, id, children, ...props }, ref) {
    const generated = useId();
    const selectId = id ?? generated;
    return (
      <FieldShell id={selectId} label={label} hint={hint} error={error} optional={optional} className={className}>
        <div className="select-wrap">
          <select ref={ref} id={selectId} className="select" aria-invalid={error ? true : undefined} {...props}>
            {children}
          </select>
        </div>
      </FieldShell>
    );
  },
);

export function Toggle({
  checked,
  onChange,
  label,
  description,
  disabled = false,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: ReactNode;
  description?: ReactNode;
  disabled?: boolean;
}) {
  return (
    <label className={`toggle-row${disabled ? ' toggle-row--disabled' : ''}`}>
      <span className="toggle-row__copy">
        <span className="toggle-row__label">{label}</span>
        {description ? <span className="toggle-row__description">{description}</span> : null}
      </span>
      <span className="toggle">
        <input
          type="checkbox"
          checked={checked}
          onChange={(event) => onChange(event.target.checked)}
          disabled={disabled}
          className="sr-only"
        />
        <span className="toggle__track" aria-hidden="true">
          <span className="toggle__thumb" />
        </span>
      </span>
    </label>
  );
}

export function Checkbox({
  checked,
  onChange,
  label,
  description,
  disabled = false,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: ReactNode;
  description?: ReactNode;
  disabled?: boolean;
}) {
  return (
    <label className={`checkbox-row${disabled ? ' checkbox-row--disabled' : ''}`}>
      <span className={`checkbox${checked ? ' checkbox--checked' : ''}`}>
        <input
          type="checkbox"
          checked={checked}
          onChange={(event) => onChange(event.target.checked)}
          disabled={disabled}
          className="sr-only"
        />
        {checked ? <Check size={13} strokeWidth={3} aria-hidden="true" /> : null}
      </span>
      <span>
        <span className="checkbox-row__label">{label}</span>
        {description ? <span className="checkbox-row__description">{description}</span> : null}
      </span>
    </label>
  );
}

export function SegmentedControl<T extends string>({
  value,
  options,
  onChange,
  ariaLabel,
}: {
  value: T;
  options: { value: T; label: ReactNode }[];
  onChange: (value: T) => void;
  ariaLabel: string;
}) {
  return (
    <div className="segmented" role="group" aria-label={ariaLabel}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          className={`segmented__item${value === option.value ? ' segmented__item--active' : ''}`}
          onClick={() => onChange(option.value)}
          aria-pressed={value === option.value}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
