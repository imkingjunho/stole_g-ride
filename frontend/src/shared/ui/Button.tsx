import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Spinner } from './Spinner';
interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode;
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  loading?: boolean;
  fullWidth?: boolean;
}
/** 폼 제출 버튼은 type="submit"을 명시한다. */
export function Button({
  children,
  variant = 'primary',
  loading = false,
  fullWidth = false,
  disabled,
  type = 'button',
  className = '',
  ...props
}: ButtonProps) {
  const variants = {
    primary: 'bg-brand text-white hover:bg-brand-hover',
    secondary: 'border border-slate-500 bg-white text-ink hover:bg-brand-soft',
    ghost: 'text-brand hover:bg-brand-soft',
    danger: 'bg-danger text-white hover:bg-red-800',
  };
  return (
    <button
      {...props}
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`inline-flex min-h-touch min-w-touch items-center justify-center gap-2 rounded-control px-4 py-3 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50 ${variants[variant]} ${fullWidth ? 'w-full' : ''} ${className}`}
    >
      {loading && <Spinner label="처리 중" />}
      {children}
    </button>
  );
}
