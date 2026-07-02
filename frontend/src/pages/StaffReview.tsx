import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { staffRun, reviewFinding, reviewClearance } from '../api/staff';
import { Badge, Card, Spinner } from '../components/ui';
import { FindingList } from '../components/FindingList';
import { ClearanceList } from '../components/ClearanceList';
import { readinessClass, readinessLabel } from '../lib/format';
import type { ReadinessStatus } from '../types';

export default function StaffReview() {
  const { runId = '' } = useParams();
  const qc = useQueryClient();
  const runQ = useQuery({ queryKey: ['staffRun', runId], queryFn: () => staffRun(runId) });

  const reviewF = useMutation({
    mutationFn: ({ fid, disposition }: { fid: string; disposition: string }) => reviewFinding(fid, disposition),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['staffRun', runId] }),
  });
  const reviewC = useMutation({
    mutationFn: ({ cid, disposition }: { cid: string; disposition: string }) => reviewClearance(cid, disposition),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['staffRun', runId] }),
  });

  if (runQ.isLoading) return <Spinner />;
  if (!runQ.data) return <p role="alert" className="text-red-700">Run not found.</p>;
  const { run, findings, clearances } = runQ.data;

  return (
    <div>
      <Link to="/staff" className="text-sm text-brand-600 underline">&larr; Back to Staff Review</Link>
      <div className="mb-4 mt-2 flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-bold text-slate-900">Review · {run.universalProjectId}</h1>
        <Badge className={readinessClass(run.readinessStatus as ReadinessStatus)}>
          {readinessLabel(run.readinessStatus as ReadinessStatus)} · {run.readinessScore}/100
        </Badge>
      </div>

      <Card className="mb-4">
        <p className="text-sm text-slate-700">{run.summary}</p>
        <p className="mt-1 text-xs text-slate-500">
          Human-in-the-loop: accept, modify, or reject each item below. Your decisions are recorded in the audit log.
        </p>
      </Card>

      <Card className="mb-4">
        <h2 className="mb-3 text-lg font-semibold">Findings</h2>
        <FindingList findings={findings} mode="staff"
          onReview={(fid, disposition) => reviewF.mutate({ fid, disposition })} />
      </Card>

      <Card>
        <h2 className="mb-3 text-lg font-semibold">Clearances</h2>
        <ClearanceList clearances={clearances} mode="staff"
          onReview={(cid, disposition) => reviewC.mutate({ cid, disposition })} />
      </Card>
    </div>
  );
}
