import type { HTMLAttributes } from 'react';

export function Card({ className = '', ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      {...props}
      className={`rounded-card border border-slate-200 bg-white p-page shadow-card ${className}`}
    />
  );
}
