import { useState } from 'react';
import { ExternalLink, Flag, Check, X, Pencil } from 'lucide-react';
import type { Finding } from '../types';
import { Badge, Button } from './ui';
import { severityClass, confidenceClass } from '../lib/format';

interface Props {
  findings: Finding[];
  mode: 'applicant' | 'staff';
  onFlag?: (id: string, comment: string) => void;
  onReview?: (id: string, disposition: string) => void;
}

export function FindingList({ findings, mode, onFlag, onReview }: Props) {
  if (findings.length === 0) {
    return <p className="text-sm text-slate-500">No findings identified.</p>;
  }
  return (
    <ul className="space-y-3">
      {findings.map((f) => (
        <FindingRow key={f.id} f={f} mode={mode} onFlag={onFlag} onReview={onReview} />
      ))}
    </ul>
  );
}

function FindingRow({ f, mode, onFlag, onReview }: {
  f: Finding; mode: Props['mode']; onFlag?: Props['onFlag']; onReview?: Props['onReview'];
}) {
  const [flagging, setFlagging] = useState(false);
  const [comment, setComment] = useState('');

  return (
    <li className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap items-center gap-2">
        <Badge className={severityClass(f.severity)}>{f.severity}</Badge>
        <Badge className="bg-slate-100 text-slate-700">{f.category}</Badge>
        <Badge className={confidenceClass(f.confidenceLevel)}>
          Confidence: {f.confidenceLevel} ({f.confidence}%)
        </Badge>
        <Badge className="bg-slate-100 text-slate-600">{f.source}</Badge>
        {f.staffDisposition !== 'PENDING' && (
          <Badge className="bg-brand-50 text-brand-700">Staff: {f.staffDisposition}</Badge>
        )}
        {f.applicantFlagged && <Badge className="bg-red-50 text-red-700">Flagged by applicant</Badge>}
      </div>
      <h4 className="mt-2 font-semibold text-slate-900">{f.title}</h4>
      <p className="mt-1 text-sm text-slate-700">{f.description}</p>
      {f.recommendation && (
        <p className="mt-1 text-sm text-slate-600"><span className="font-medium">Recommendation:</span> {f.recommendation}</p>
      )}
      {f.codeReference && (
        <p className="mt-1 text-sm">
          <span className="font-medium">Code:</span>{' '}
          {f.codeUrl ? (
            <a href={f.codeUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-brand-600 underline">
              {f.codeReference} <ExternalLink className="h-3 w-3" aria-hidden="true" />
            </a>
          ) : f.codeReference}
        </p>
      )}
      {f.triggeringCondition && (
        <p className="mt-1 text-xs text-slate-500">Triggered by: {f.triggeringCondition}</p>
      )}

      {mode === 'applicant' && onFlag && (
        <div className="mt-3">
          {!flagging ? (
            <Button variant="ghost" onClick={() => setFlagging(true)} className="text-xs">
              <Flag className="h-3.5 w-3.5" aria-hidden="true" /> Flag as inaccurate
            </Button>
          ) : (
            <div className="flex flex-col gap-2 sm:flex-row">
              <label className="sr-only" htmlFor={`flag-${f.id}`}>Why is this inaccurate?</label>
              <input
                id={`flag-${f.id}`}
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                placeholder="Tell staff why this may be inaccurate"
                className="flex-1 rounded-md border border-slate-300 px-3 py-1.5 text-sm"
              />
              <Button variant="secondary" onClick={() => { onFlag(f.id, comment); setFlagging(false); }}>Send</Button>
            </div>
          )}
        </div>
      )}

      {mode === 'staff' && onReview && (
        <div className="mt-3 flex flex-wrap gap-2">
          <Button variant="secondary" className="text-xs" onClick={() => onReview(f.id, 'ACCEPTED')}>
            <Check className="h-3.5 w-3.5" aria-hidden="true" /> Accept
          </Button>
          <Button variant="secondary" className="text-xs" onClick={() => onReview(f.id, 'MODIFIED')}>
            <Pencil className="h-3.5 w-3.5" aria-hidden="true" /> Modify
          </Button>
          <Button variant="danger" className="text-xs" onClick={() => onReview(f.id, 'REJECTED')}>
            <X className="h-3.5 w-3.5" aria-hidden="true" /> Reject
          </Button>
        </div>
      )}
    </li>
  );
}
