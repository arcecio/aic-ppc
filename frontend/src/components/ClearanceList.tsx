import { ExternalLink, Check, X } from 'lucide-react';
import type { Clearance } from '../types';
import { Badge, Button } from './ui';
import { confidenceClass } from '../lib/format';

interface Props {
  clearances: Clearance[];
  mode: 'applicant' | 'staff';
  onReview?: (id: string, disposition: string) => void;
}

export function ClearanceList({ clearances, mode, onReview }: Props) {
  if (clearances.length === 0) {
    return <p className="text-sm text-slate-500">No departmental clearances identified.</p>;
  }
  return (
    <ul className="space-y-3">
      {clearances.map((c) => (
        <li key={c.id} className="rounded-lg border border-slate-200 bg-white p-4">
          <div className="flex flex-wrap items-center gap-2">
            <Badge className="bg-brand-50 text-brand-700">{c.department}</Badge>
            <Badge className={confidenceClass(c.confidenceLevel)}>
              Confidence: {c.confidenceLevel} ({c.confidence}%)
            </Badge>
            {c.staffDisposition !== 'PENDING' && (
              <Badge className="bg-slate-100 text-slate-700">Staff: {c.staffDisposition}</Badge>
            )}
          </div>
          <h4 className="mt-2 font-semibold text-slate-900">{c.clearanceName}</h4>
          <p className="mt-1 text-sm text-slate-700">{c.reason}</p>
          {c.submittalRequirements.length > 0 && (
            <div className="mt-2">
              <p className="text-sm font-medium text-slate-700">Submittal requirements:</p>
              <ul className="ml-5 list-disc text-sm text-slate-600">
                {c.submittalRequirements.map((r, i) => <li key={i}>{r}</li>)}
              </ul>
            </div>
          )}
          {c.infoUrl && (
            <a href={c.infoUrl} target="_blank" rel="noreferrer" className="mt-1 inline-flex items-center gap-1 text-sm text-brand-600 underline">
              More information <ExternalLink className="h-3 w-3" aria-hidden="true" />
            </a>
          )}
          {mode === 'staff' && onReview && (
            <div className="mt-3 flex gap-2">
              <Button variant="secondary" className="text-xs" onClick={() => onReview(c.id, 'ACCEPTED')}>
                <Check className="h-3.5 w-3.5" aria-hidden="true" /> Accept
              </Button>
              <Button variant="danger" className="text-xs" onClick={() => onReview(c.id, 'REJECTED')}>
                <X className="h-3.5 w-3.5" aria-hidden="true" /> Reject
              </Button>
            </div>
          )}
        </li>
      ))}
    </ul>
  );
}
