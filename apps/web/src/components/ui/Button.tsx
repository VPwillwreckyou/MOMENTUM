import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { LoaderCircle } from 'lucide-react';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'quiet';
type Size = 'sm' | 'md' | 'lg';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  icon?: ReactNode;
  trailingIcon?: ReactNode;
  loading?: boolean;
  fullWidth?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = 'secondary',
    size = 'md',
    icon,
    trailingIcon,
    loading = false,
    fullWidth = false,
    className = '',
    children,
    disabled,
    type = 'button',
    ...props
  },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={`btn btn--${variant} btn--${size}${fullWidth ? ' btn--full' : ''}${className ? ` ${className}` : ''}`}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading ? <LoaderCircle className="btn__spinner" size={16} aria-hidden="true" /> : icon}
      {children ? <span>{children}</span> : null}
      {trailingIcon}
    </button>
  );
});

interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  label: string;
  variant?: 'plain' | 'outlined' | 'surface';
  size?: 'sm' | 'md';
}

export function IconButton({
  label,
  variant = 'plain',
  size = 'md',
  className = '',
  children,
  type = 'button',
  ...props
}: IconButtonProps) {
  return (
    <button
      type={type}
      className={`icon-btn icon-btn--${variant} icon-btn--${size}${className ? ` ${className}` : ''}`}
      aria-label={label}
      title={label}
      {...props}
    >
      {children}
    </button>
  );
}
