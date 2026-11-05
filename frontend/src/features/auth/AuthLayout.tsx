import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { LegalNotice } from '@/shared/ui/LegalNotice';
import { Badge } from '@/shared/ui';
import { isMockMode } from '@/shared/api/config';
export function AuthLayout({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <div className="mx-auto flex min-h-dvh max-w-app flex-col bg-white">
      <main className="flex-1 space-y-7 p-page pt-12">
        <Link to="/login" className="link px-0 text-xl">
          가치가
        </Link>
        {isMockMode && <Badge variant="dev">DEV · 체험 데이터</Badge>}
        <header>
          <h1 className="text-3xl font-bold leading-tight">{title}</h1>
          <p className="mt-3 text-sm leading-relaxed text-slate-600">{description}</p>
        </header>
        {children}
      </main>
      <LegalNotice />
    </div>
  );
}
