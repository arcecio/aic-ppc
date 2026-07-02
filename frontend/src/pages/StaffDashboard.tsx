import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getAnalytics, staffRuns } from '../api/staff';
import { Badge, Card, Spinner } from '../components/ui';
import { readinessClass, readinessLabel, shortDate } from '../lib/format';
import type { ReadinessStatus } from '../types';

function Kpi({ label, value, sub }: { label: string; value: string | number; sub?: string }) {
  return (
    <Card>
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-bold text-brand-700">{value}</p>
      {sub && <p className="text-xs text-slate-500">{sub}</p>}
    </Card>
  );
}

export default function StaffDashboard() {
  const analyticsQ = useQuery({ queryKey: ['analytics'], queryFn: getAnalytics });
  const runsQ = useQuery({ queryKey: ['staffRuns'], queryFn: () => staffRuns() });

  return (
    <div>
      <h1 className="mb-4 text-2xl font-bold text-slate-900">Staff Review &amp; Analytics</h1>

      {analyticsQ.isLoading ? <Spinner /> : analyticsQ.data && (
        <>
          <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Kpi label="Projects" value={analyticsQ.data.totalProjects} sub={`${analyticsQ.data.projectsUsingAipPpc} used AIP PPC`} />
            <Kpi label="Screenings completed" value={analyticsQ.data.completedRuns} sub={`${analyticsQ.data.failedRuns} failed`} />
            <Kpi label="Avg processing" value={`${Math.round(analyticsQ.data.avgProcessingMs)} ms`} sub={`${analyticsQ.data.pctWithinTarget}% within target`} />
            <Kpi label="Avg readiness" value={`${Math.round(analyticsQ.data.avgReadinessScore)}/100`} />
            <Kpi label="Findings surfaced" value={analyticsQ.data.totalFindings} />
            <Kpi label="Clearances identified" value={analyticsQ.data.totalClearances} />
            <Kpi label="Submitted to ePlanLA" value={analyticsQ.data.submittedToEplanla} />
            <Kpi label="Open feedback" value={analyticsQ.data.openFeedback} />
          </div>
          <div className="mb-6 grid gap-4 sm:grid-cols-3">
            <Kpi label="Ready for Submission" value={analyticsQ.data.readyForSubmission} />
            <Kpi label="Requires Attention" value={analyticsQ.data.requiresAttention} />
            <Kpi label="Incomplete" value={analyticsQ.data.incomplete} />
          </div>
        </>
      )}

      <h2 className="mb-2 text-lg font-semibold">Recent screening runs</h2>
      {runsQ.isLoading ? <Spinner /> : (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Screening runs for staff review</caption>
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th scope="col" className="px-4 py-3">Project ID</th>
                <th scope="col" className="px-4 py-3">Status</th>
                <th scope="col" className="px-4 py-3">Readiness</th>
                <th scope="col" className="px-4 py-3">Findings</th>
                <th scope="col" className="px-4 py-3">Completed</th>
                <th scope="col" className="px-4 py-3"><span className="sr-only">Review</span></th>
              </tr>
            </thead>
            <tbody>
              {runsQ.data?.map((r) => (
                <tr key={r.id} className="border-b border-slate-100 hover:bg-slate-50">
                  <td className="px-4 py-3 font-mono text-xs">{r.universalProjectId}</td>
                  <td className="px-4 py-3">{r.status}</td>
                  <td className="px-4 py-3">
                    <Badge className={readinessClass(r.readinessStatus as ReadinessStatus)}>
                      {readinessLabel(r.readinessStatus as ReadinessStatus)}
                    </Badge>
                  </td>
                  <td className="px-4 py-3">{r.findingCount} ({r.blockingCount} blocking)</td>
                  <td className="px-4 py-3 text-slate-500">{shortDate(r.completedAt)}</td>
                  <td className="px-4 py-3">
                    <Link to={`/staff/runs/${r.id}`} className="text-brand-600 underline">Review</Link>
                  </td>
                </tr>
              ))}
              {runsQ.data?.length === 0 && <tr><td colSpan={6} className="px-4 py-6 text-center text-slate-500">No runs yet.</td></tr>}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
