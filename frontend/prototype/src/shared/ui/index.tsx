import { createContext, useCallback, useContext, useEffect, useId, useRef, useState } from 'react';
import type { ButtonHTMLAttributes, HTMLAttributes, InputHTMLAttributes, ReactNode } from 'react';
import { Icon } from './Icon';
export { Icon } from './Icon';

export function Button({
  variant = 'primary',
  className = '',
  type = 'button',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'ghost' }) {
  return <button type={type} className={`button button-${variant} ${className}`} {...props} />;
}

export function Input({
  label,
  error,
  hint,
  id,
  className = '',
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string; hint?: string }) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  return (
    <div className={`field ${className}`}>
      <label htmlFor={fieldId}>{label}</label>
      <input
        id={fieldId}
        aria-invalid={error ? true : undefined}
        aria-describedby={error || hint ? `${fieldId}-help` : undefined}
        {...props}
      />
      {(error || hint) && (
        <p
          id={`${fieldId}-help`}
          className={error ? 'field-error' : 'field-hint'}
          role={error ? 'alert' : undefined}
        >
          {error || hint}
        </p>
      )}
    </div>
  );
}

export function Card({ className = '', ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={`card ${className}`} {...props} />;
}

export function Badge({
  tone = 'green',
  children,
}: {
  tone?: 'green' | 'neutral' | 'amber' | 'blue';
  children: ReactNode;
}) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}

export function Spinner({ label = '화면을 불러오는 중이에요' }: { label?: string }) {
  return (
    <span className="spinner-wrap" role="status">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </span>
  );
}

export function Modal({
  open,
  onClose,
  title,
  children,
  variant = 'modal',
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  variant?: 'modal' | 'sheet';
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);
  return (
    <dialog
      ref={ref}
      className={`dialog dialog-${variant}`}
      aria-labelledby={titleId}
      onCancel={onClose}
      onClose={onClose}
      onKeyDown={(event) => {
        if (event.key !== 'Tab') return;
        const dialog = event.currentTarget;
        const focusable = Array.from(
          dialog.querySelectorAll<HTMLElement>(
            'a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]',
          ),
        ).filter((element) => element.tabIndex >= 0 && element.getClientRects().length > 0);
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (!first || !last) {
          event.preventDefault();
          dialog.focus();
          return;
        }
        if (
          event.shiftKey &&
          (document.activeElement === first || document.activeElement === dialog)
        ) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div className="dialog-body">
        <div className="dialog-header">
          <h2 id={titleId}>{title}</h2>
          <button className="icon-button" aria-label="닫기" onClick={onClose}>
            <Icon name="close" />
          </button>
        </div>
        {children}
      </div>
    </dialog>
  );
}

export function BottomSheet(props: Omit<Parameters<typeof Modal>[0], 'variant'>) {
  return <Modal {...props} variant="sheet" />;
}

const ToastContext = createContext<(message: string) => void>(() => undefined);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState('');
  const timer = useRef<ReturnType<typeof setTimeout>>();
  const notify = useCallback((text: string) => {
    clearTimeout(timer.current);
    setMessage(text);
    timer.current = setTimeout(() => setMessage(''), 4500);
  }, []);
  useEffect(() => () => clearTimeout(timer.current), []);
  return (
    <ToastContext.Provider value={notify}>
      {children}
      <div className={`toast ${message ? 'toast-visible' : ''}`} role="status" aria-live="polite">
        {message && (
          <>
            <Icon name="check" />
            <span>{message}</span>
            <button className="icon-button" aria-label="알림 닫기" onClick={() => setMessage('')}>
              <Icon name="close" />
            </button>
          </>
        )}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}

export function LegalNotice() {
  return (
    <p className="legal-notice">
      본 서비스는 동승자 매칭 및 요금 분담 계산만 제공하며, 택시 호출·운송·결제를 중개하지 않습니다.
    </p>
  );
}

export function Avatar({
  label,
  tone = 'mint',
  small = false,
}: {
  label: string;
  tone?: 'mint' | 'peach' | 'lavender';
  small?: boolean;
}) {
  return (
    <span className={`avatar avatar-${tone} ${small ? 'avatar-small' : ''}`} aria-hidden="true">
      {label.slice(0, 1)}
    </span>
  );
}
