import type { ReactNode } from 'react';

interface PlaceholderProps {
  /** 화면 이름 */
  title: string;
  /** 이 화면을 만들 사람 */
  owner: string;
  /** 지시서의 작업 번호. 예: T1-6 */
  task: string;
  children?: ReactNode;
}

/**
 * Phase 0 자리표시 화면.
 *
 * 라우트가 전부 연결돼 있는지 눈으로 확인하고, 각 화면의 담당자와 작업 번호를 보여 준다.
 * 담당자가 실제 화면을 만들면 이 컴포넌트 사용을 지운다.
 */
export function Placeholder({ title, owner, task, children }: PlaceholderProps) {
  return (
    <section className="p-4">
      <p className="mb-2 inline-block rounded bg-amber-100 px-2 py-1 text-xs font-semibold text-amber-900">
        DEV · 아직 만들지 않은 화면
      </p>
      <h1 className="text-xl font-bold">{title}</h1>
      <p className="mt-1 text-sm text-slate-600">
        담당 {owner} · {task}
      </p>
      {children ? <div className="mt-4 text-sm text-slate-700">{children}</div> : null}
    </section>
  );
}
