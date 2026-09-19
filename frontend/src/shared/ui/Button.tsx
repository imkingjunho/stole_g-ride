import type { ButtonHTMLAttributes, ReactNode } from 'react';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode;
}

/**
 * 공통 버튼 — Phase 0 뼈대.
 *
 * 디자인 토큰과 variant(primary/secondary/ghost), 크기, 로딩 상태는 오승원이 T1-1 에서 채운다.
 * 지금은 **터치 대상 최소 44px**(PRD §10)만 지켜 둔다.
 *
 * 다른 사람은 `shared/ui` 의 컴포넌트만 쓴다. 각자 버튼을 새로 만들지 않는다 (CLAUDE.md §5).
 */
export function Button({ children, className = '', ...rest }: ButtonProps) {
  return (
    <button
      className={`min-h-touch min-w-touch rounded-lg bg-slate-900 px-4 py-2 text-white disabled:opacity-40 ${className}`}
      {...rest}
    >
      {children}
    </button>
  );
}
