import { useEffect, useId, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Button } from './Button';

export interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: ReactNode;
  closeOnBackdrop?: boolean;
  placement?: 'center' | 'bottom';
}

/** 네이티브 dialog의 모달 포커스 제한·배경 비활성화를 사용한다. */
export function Modal({
  open,
  onClose,
  title,
  description,
  children,
  closeOnBackdrop = true,
  placement = 'center',
}: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();
  useEffect(() => {
    const dialog = ref.current;
    if (!open || !dialog) return;
    const previousFocus = document.activeElement;
    dialog.showModal();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      dialog.close();
      document.body.style.overflow = previousOverflow;
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus();
    };
  }, [open]);
  if (!open) return null;
  return createPortal(
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      onKeyDown={(event) => {
        if (event.key !== 'Tab') return;
        const elements = Array.from(
          event.currentTarget.querySelectorAll<HTMLElement>(
            'button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href], [tabindex="0"]',
          ),
        ).filter((element) => element.getClientRects().length > 0);
        const first = elements[0];
        const last = elements[elements.length - 1];
        if (!first) {
          event.preventDefault();
          event.currentTarget.focus();
          return;
        }
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      }}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onClick={(event) => {
        if (!closeOnBackdrop || event.target !== event.currentTarget) return;
        const rect = event.currentTarget.getBoundingClientRect();
        if (
          event.clientX < rect.left ||
          event.clientX > rect.right ||
          event.clientY < rect.top ||
          event.clientY > rect.bottom
        )
          onClose();
      }}
      className={`max-h-[85dvh] w-[calc(100%-2rem)] max-w-md overflow-y-auto border-0 bg-white p-page text-ink shadow-xl backdrop:bg-slate-950/50 ${placement === 'bottom' ? 'mb-0 rounded-t-card pb-[max(1.25rem,env(safe-area-inset-bottom))]' : 'rounded-card'}`}
    >
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 id={titleId} className="text-xl font-bold">
          {title}
        </h2>
        <Button variant="ghost" onClick={onClose} aria-label="닫기">
          닫기
        </Button>
      </div>
      {description && (
        <p id={descriptionId} className="mb-4 text-sm text-slate-600">
          {description}
        </p>
      )}
      {children}
    </dialog>,
    document.body,
  );
}
