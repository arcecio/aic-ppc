import { useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Download, FileUp, Play, Trash2, Send } from 'lucide-react';
import {
  getProject, latestRun, uploadDocument, deleteDocument, screenProject,
  submitToEplanla, reportUrl, flagFinding,
} from '../api/projects';
import { Badge, Button, Card, ErrorText, Spinner } from '../components/ui';
import { FindingList } from '../components/FindingList';
import { ClearanceList } from '../components/ClearanceList';
import { bytes, readinessClass, readinessLabel, shortDate } from '../lib/format';
import { apiError } from '../api/client';
import type { ReadinessStatus } from '../types';

export default function ProjectDetail() {
  const { id = '' } = useParams();
  const qc = useQueryClient();
  const fileRef = useRef<HTMLInputElement>(null);
  const [docCategory, setDocCategory] = useState('');
  const [error, setError] = useState('');

  const projectQ = useQuery({ queryKey: ['project', id], queryFn: () => getProject(id) });
  const runQ = useQuery({
    queryKey: ['latestRun', id],
    queryFn: () => latestRun(id),
    refetchInterval: (q) => {
      const status = q.state.data?.run?.status;
      return status === 'PENDING' || status === 'PROCESSING' ? 2000 : false;
    },
  });

  const upload = useMutation({
    mutationFn: (file: File) => uploadDocument(id, file, docCategory || undefined),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['project', id] }); if (fileRef.current) fileRef.current.value = ''; },
    onError: (e) => setError(apiError(e)),
  });
  const removeDoc = useMutation({
    mutationFn: (docId: string) => deleteDocument(id, docId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['project', id] }),
  });
  const screen = useMutation({
    mutationFn: () => screenProject(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['latestRun', id] }); },
    onError: (e) => setError(apiError(e)),
  });
  const submit = useMutation({
    mutationFn: () => submitToEplanla(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['project', id] }),
  });
  const flag = useMutation({
    mutationFn: ({ fid, comment }: { fid: string; comment: string }) => flagFinding(fid, comment),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['latestRun', id] }),
  });

  if (projectQ.isLoading) return <Spinner />;
  if (projectQ.error || !projectQ.data) return <p role="alert" className="text-red-700">Project not found.</p>;
  const project = projectQ.data;
  const detail = runQ.data;
  const run = detail?.run;
  const running = run?.status === 'PENDING' || run?.status === 'PROCESSING';

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-start justify-between gap-2">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">{project.title}</h1>
          <p className="text-sm text-slate-500">
            <span className="font-mono">{project.universalProjectId}</span> · {project.permitTypeCode}
            {project.address ? ` · ${project.address}` : ''}
          </p>
        </div>
        <Badge className={readinessClass(project.currentReadinessStatus as ReadinessStatus)}>
          {readinessLabel(project.currentReadinessStatus as ReadinessStatus)}
          {project.currentReadinessScore != null ? ` · ${project.currentReadinessScore}/100` : ''}
        </Badge>
      </div>

      <ErrorText>{error}</ErrorText>

      <div className="grid gap-4 lg:grid-cols-3">
        {/* Left column: docs + actions */}
        <div className="space-y-4">
          {project.parcel && (
            <Card>
              <h2 className="mb-1 text-sm font-semibold uppercase text-slate-500">Parcel</h2>
              <p className="text-sm">APN {project.parcel.apn}</p>
              <p className="text-sm">Zone {project.parcel.zone ?? '—'}</p>
              {project.parcel.overlays.length > 0 && <p className="text-xs text-slate-500">Overlays: {project.parcel.overlays.join(', ')}</p>}
              {project.parcel.hazardZones.length > 0 && <p className="text-xs text-slate-500">Hazards: {project.parcel.hazardZones.join(', ')}</p>}
            </Card>
          )}

          <Card>
            <h2 className="mb-2 text-sm font-semibold uppercase text-slate-500">Documents</h2>
            <label htmlFor="docCategory" className="mb-1 block text-xs font-medium text-slate-600">Document category</label>
            <input id="docCategory" list="docKeys" value={docCategory} onChange={(e) => setDocCategory(e.target.value)}
              placeholder="e.g. architectural_plans" className="mb-2 w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm" />
            <input ref={fileRef} type="file" aria-label="Choose a file to upload"
              onChange={(e) => { const f = e.target.files?.[0]; if (f) upload.mutate(f); }}
              className="mb-2 block w-full text-sm" />
            <Button variant="secondary" onClick={() => fileRef.current?.click()} disabled={upload.isPending} className="w-full justify-center">
              <FileUp className="h-4 w-4" aria-hidden="true" /> {upload.isPending ? 'Uploading…' : 'Upload document'}
            </Button>
            <ul className="mt-3 space-y-2">
              {project.documents.map((d) => (
                <li key={d.id} className="flex items-center justify-between gap-2 rounded border border-slate-100 p-2 text-sm">
                  <div>
                    <p className="font-medium">{d.originalName}</p>
                    <p className="text-xs text-slate-500">
                      {d.fileType} · {bytes(d.sizeBytes)} · {d.docCategory ?? 'uncategorized'} ·{' '}
                      <span className={d.scanStatus === 'PASSED' ? 'text-green-700' : 'text-red-700'}>{d.scanStatus}</span>
                    </p>
                  </div>
                  <button aria-label={`Delete ${d.originalName}`} onClick={() => removeDoc.mutate(d.id)}
                    className="text-slate-400 hover:text-red-600"><Trash2 className="h-4 w-4" /></button>
                </li>
              ))}
              {project.documents.length === 0 && <li className="text-sm text-slate-500">No documents uploaded yet.</li>}
            </ul>
          </Card>

          <Card>
            <h2 className="mb-2 text-sm font-semibold uppercase text-slate-500">Actions</h2>
            <div className="space-y-2">
              <Button onClick={() => screen.mutate()} disabled={running || screen.isPending} className="w-full justify-center">
                <Play className="h-4 w-4" aria-hidden="true" /> {running ? 'Screening…' : 'Run Pre-Plan Check'}
              </Button>
              {run?.status === 'COMPLETED' && (
                <a href={reportUrl(id)} className="block">
                  <Button variant="secondary" className="w-full justify-center">
                    <Download className="h-4 w-4" aria-hidden="true" /> Download PDF report
                  </Button>
                </a>
              )}
              <Button variant="secondary" onClick={() => submit.mutate()} disabled={submit.isPending} className="w-full justify-center">
                <Send className="h-4 w-4" aria-hidden="true" /> Mark submitted to ePlanLA
              </Button>
              {project.submittedToEplanlaAt && (
                <p className="text-xs text-green-700">Submitted to ePlanLA {shortDate(project.submittedToEplanlaAt)}</p>
              )}
            </div>
          </Card>
        </div>

        {/* Right column: results */}
        <div className="space-y-4 lg:col-span-2">
          {!run && <Card><p className="text-slate-600">Run a pre-plan check to see findings and likely clearances.</p></Card>}
          {running && <Card><Spinner label="Screening in progress — this refreshes automatically…" /></Card>}
          {run?.status === 'FAILED' && <Card><p role="alert" className="text-red-700">Screening failed: {run.errorMessage}</p></Card>}

          {run?.status === 'COMPLETED' && detail && (
            <>
              <Card>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <h2 className="text-lg font-semibold">Submission readiness</h2>
                  <Badge className={readinessClass(run.readinessStatus as ReadinessStatus)}>
                    {readinessLabel(run.readinessStatus as ReadinessStatus)} · {run.readinessScore}/100
                  </Badge>
                </div>
                <p className="mt-2 text-sm text-slate-700">{run.summary}</p>
                <p className="mt-2 text-xs text-slate-500">
                  {run.blockingCount} blocking · {run.warningCount} warning · {run.infoCount} informational ·
                  {' '}{run.clearanceCount} clearances · processed in {run.processingMs} ms
                  {run.aiProviderUsed ? ` · AI: ${run.aiProviderUsed}` : ''}
                </p>
              </Card>

              <Card>
                <h2 className="mb-3 text-lg font-semibold">Findings</h2>
                <FindingList findings={detail.findings} mode="applicant"
                  onFlag={(fid, comment) => flag.mutate({ fid, comment })} />
              </Card>

              <Card>
                <h2 className="mb-3 text-lg font-semibold">Likely required clearances</h2>
                <ClearanceList clearances={detail.clearances} mode="applicant" />
              </Card>
            </>
          )}
        </div>
      </div>

      <datalist id="docKeys">
        {['architectural_plans','structural_plans','structural_calcs','title24_energy','green_code',
          'soils_report','accessibility_plans','mep_plans','electrical_plans','site_survey','sign_plans']
          .map((k) => <option key={k} value={k} />)}
      </datalist>
    </div>
  );
}
