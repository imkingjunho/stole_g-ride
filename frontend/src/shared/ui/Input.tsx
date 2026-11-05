import { forwardRef, useId, type InputHTMLAttributes } from 'react';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  hint?: string;
  error?: string;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, hint, error, id, className = '', 'aria-describedby': describedBy, ...props },
  ref,
) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const description =
    [describedBy, hint ? `${inputId}-hint` : '', error ? `${inputId}-error` : '']
      .filter(Boolean)
      .join(' ') || undefined;
  return (
    <div className="space-y-2">
      <label htmlFor={inputId} className="block text-sm font-semibold text-ink">
        {label}
        {props.required && (
          <span aria-hidden="true" className="text-danger">
            {' '}
            *
          </span>
        )}
      </label>
      <input
        {...props}
        ref={ref}
        id={inputId}
        aria-invalid={error ? true : props['aria-invalid']}
        aria-describedby={description}
        className={`min-h-touch w-full rounded-control border bg-white px-4 py-3 text-base text-ink placeholder:text-slate-600 disabled:bg-slate-100 ${error ? 'border-danger' : 'border-slate-500'} ${className}`}
      />
      {hint && (
        <p id={`${inputId}-hint`} className="text-sm text-slate-600">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${inputId}-error`} role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
});
