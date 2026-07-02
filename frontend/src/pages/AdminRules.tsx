import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { listScreeningRules, updateScreeningRule } from '../api/admin';
import { Badge, Card, Spinner } from '../components/ui';
import { severityClass } from '../lib/format';
import { apiError } from '../api/client';
import type { ScreeningRule, Severity } from '../types';

/**
 * Admin rule console — demonstrates the SOW 2.2.3 / Appendix 3 §5.1.6 requirement
 * that City staff can toggle and edit business rules without vendor code changes.
 */
export default function AdminRules() {
  const qc = useQueryClient();
  const [error, setError] = useState('');
  const rulesQ = useQuery({ queryKey: ['screeningRules'], queryFn: listScreeningRules });

  const toggle = useMutation({
    mutationFn: (rule: ScreeningRule) => updateScreeningRule(rule.id, {
      code: rule.code, name: rule.name, category: rule.category, severity: rule.severity,
      conditionJson: rule.conditionJson, message: rule.message, recommendation: rule.recommendation,
      codeReference: rule.codeReference, codeUrl: rule.codeUrl, confidence: rule.confidence,
      appliesToPermitTypes: rule.appliesToPermitTypes, priority: rule.priority, active: !rule.active,
    }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['screeningRules'] }),
    onError: (e) => setError(apiError(e)),
  });

  return (
    <div>
      <h1 className="mb-1 text-2xl font-bold text-slate-900">Configurable Rule Engine</h1>
      <p className="mb-4 text-sm text-slate-600">
        Screening rules are the primary detection mechanism. City staff can enable/disable and tune them here —
        no vendor code changes required.
      </p>
      {error && <p role="alert" className="mb-3 text-red-700">{error}</p>}
      {rulesQ.isLoading ? <Spinner /> : (
        <div className="space-y-2">
          {rulesQ.data?.map((rule) => (
            <Card key={rule.id} className="flex flex-wrap items-center justify-between gap-3">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-mono text-xs text-slate-500">{rule.code}</span>
                  <Badge className={severityClass(rule.severity as Severity)}>{rule.severity}</Badge>
                  <Badge className="bg-slate-100 text-slate-700">{rule.category}</Badge>
                  <span className="text-xs text-slate-500">priority {rule.priority} · conf {rule.confidence}%</span>
                </div>
                <p className="mt-1 font-medium">{rule.name}</p>
                <p className="text-xs text-slate-500">Applies to: {rule.appliesToPermitTypes || '*'} · {rule.codeReference ?? '—'}</p>
              </div>
              <label className="flex cursor-pointer items-center gap-2 text-sm">
                <input type="checkbox" checked={rule.active} onChange={() => toggle.mutate(rule)}
                  aria-label={`${rule.active ? 'Disable' : 'Enable'} rule ${rule.code}`} className="h-4 w-4" />
                {rule.active ? 'Active' : 'Disabled'}
              </label>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
