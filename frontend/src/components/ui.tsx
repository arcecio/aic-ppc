import type { ButtonHTMLAttributes, ReactNode } from 'react';
import clsx from 'clsx';

export function Button({
  variant = 'primary', className, ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'danger' | 'ghost' }) {
  const base = 'inline-flex items-center gap-2 rounded-md px-4 py-2 text-sm font-medium transition disabled:opacity-50 disabled:cursor-not-allowed';
  const variants = {
    primary: 'bg-brand-500 text-white hover:bg-brand-600',
    secondary: 'bg-white text-brand-600 border border-brand-500 hover:bg-brand-50',
    danger: 'bg-red-600 text-white hover:bg-red-700',
    ghost: 'text-slate-700 hover:bg-slate-100',
  };
  return <button className={clsx(base, variants[variant], className)} {...props} />;
}

export function Card({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={clsx('rounded-lg border border-slate-200 bg-white p-5 shadow-sm', className)}>{children}</div>;
}

export function Badge({ children, className }: { children: ReactNode; className?: string }) {
  return <span className={clsx('inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold', className)}>{children}</span>;
}

export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 text-slate-500" role="status" aria-live="polite">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-brand-500" />
      {label}
    </div>
  );
}

export function Field({ label, htmlFor, children, hint, required }: {
  label: string; htmlFor: string; children: ReactNode; hint?: string; required?: boolean;
}) {
  return (
    <div className="mb-4">
      <label htmlFor={htmlFor} className="mb-1 block text-sm font-medium text-slate-700">
        {label}{required && <span className="text-red-600" aria-hidden="true"> *</span>}
      </label>
      {children}
      {hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
    </div>
  );
}

export const inputClass =
  'w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:ring-brand-500';

export function ErrorText({ children }: { children: ReactNode }) {
  return children ? <p role="alert" className="mb-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{children}</p> : null;
}
