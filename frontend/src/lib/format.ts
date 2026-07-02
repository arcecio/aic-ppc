import type { ReadinessStatus, Severity } from '../types';

export function readinessLabel(status?: ReadinessStatus | null): string {
  switch (status) {
    case 'READY_FOR_SUBMISSION': return 'Ready for Submission';
    case 'REQUIRES_ATTENTION': return 'Requires Attention';
    case 'INCOMPLETE': return 'Incomplete';
    default: return 'Not Assessed';
  }
}

/** Tailwind classes for a readiness badge (also readable text, not color-only — WCAG). */
export function readinessClass(status?: ReadinessStatus | null): string {
  switch (status) {
    case 'READY_FOR_SUBMISSION': return 'bg-green-100 text-green-800 border border-green-300';
    case 'REQUIRES_ATTENTION': return 'bg-amber-100 text-amber-900 border border-amber-300';
    case 'INCOMPLETE': return 'bg-red-100 text-red-800 border border-red-300';
    default: return 'bg-slate-100 text-slate-700 border border-slate-300';
  }
}

export function severityClass(sev: Severity): string {
  switch (sev) {
    case 'BLOCKING': return 'bg-red-100 text-red-800 border border-red-300';
    case 'WARNING': return 'bg-amber-100 text-amber-900 border border-amber-300';
    default: return 'bg-sky-100 text-sky-800 border border-sky-300';
  }
}

export function confidenceClass(level: string): string {
  switch (level) {
    case 'HIGH': return 'bg-green-50 text-green-700';
    case 'MEDIUM': return 'bg-amber-50 text-amber-800';
    default: return 'bg-slate-100 text-slate-600';
  }
}

export function bytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

export function shortDate(iso?: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString();
}
