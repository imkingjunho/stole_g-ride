import type { HTMLAttributes } from 'react';

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: 'neutral' | 'estimated' | 'dev' | 'success';
}
export function Badge({ variant = 'neutral', children, className = '', ...props }: BadgeProps) {
  const styles = {
    neutral: 'bg-slate-100 text-ink',
    estimated: 'bg-amber-100 text-amber-900',
    dev: 'bg-brand-soft text-brand',
    success: 'bg-emerald-100 text-emerald-900',
  };
  return (
    <span
      {...props}
      className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ${styles[variant]} ${className}`}
    >
      {children ?? (variant === 'estimated' ? '추정치' : variant === 'dev' ? 'DEV' : null)}
    </span>
  );
}
