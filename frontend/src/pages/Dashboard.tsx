import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { listProjects } from '../api/projects';
import { Badge, Button, Card, Spinner } from '../components/ui';
import { readinessClass, readinessLabel, shortDate } from '../lib/format';
import type { ReadinessStatus } from '../types';

export default function Dashboard() {
  const { data, isLoading, error } = useQuery({ queryKey: ['projects'], queryFn: listProjects });

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-900">My Projects</h1>
        <Link to="/projects/new"><Button><Plus className="h-4 w-4" aria-hidden="true" /> New project</Button></Link>
      </div>

      {isLoading && <Spinner />}
      {error && <p role="alert" className="text-red-700">Could not load projects.</p>}

      {data && data.length === 0 && (
        <Card>
          <p className="text-slate-600">You have no projects yet. Start a pre-plan check to validate your submittal before you file.</p>
          <Link to="/projects/new" className="mt-3 inline-block"><Button>Create your first project</Button></Link>
        </Card>
      )}

      {data && data.length > 0 && (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Your pre-plan check projects</caption>
            <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th scope="col" className="px-4 py-3">Project</th>
                <th scope="col" className="px-4 py-3">Universal Project ID</th>
                <th scope="col" className="px-4 py-3">Readiness</th>
                <th scope="col" className="px-4 py-3">Updated</th>
              </tr>
            </thead>
            <tbody>
              {data.map((p) => (
                <tr key={p.id} className="border-b border-slate-100 hover:bg-slate-50">
                  <td className="px-4 py-3">
                    <Link to={`/projects/${p.id}`} className="font-medium text-brand-600 underline">{p.title}</Link>
                    <div className="text-xs text-slate-500">{p.permitTypeCode}{p.address ? ` · ${p.address}` : ''}</div>
                  </td>
                  <td className="px-4 py-3 font-mono text-xs">{p.universalProjectId}</td>
                  <td className="px-4 py-3">
                    <Badge className={readinessClass(p.currentReadinessStatus as ReadinessStatus)}>
                      {readinessLabel(p.currentReadinessStatus as ReadinessStatus)}
                      {p.currentReadinessScore != null ? ` · ${p.currentReadinessScore}` : ''}
                    </Badge>
                  </td>
                  <td className="px-4 py-3 text-slate-500">{shortDate(p.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
