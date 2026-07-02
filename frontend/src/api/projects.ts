import { api } from './client';
import type { DocumentDto, Project, ProjectSummary, Run, RunDetail } from '../types';

export async function listProjects(): Promise<ProjectSummary[]> {
  return (await api.get('/projects')).data;
}
export async function getProject(id: string): Promise<Project> {
  return (await api.get(`/projects/${id}`)).data;
}
export async function createProject(input: Record<string, unknown>): Promise<Project> {
  return (await api.post('/projects', input)).data;
}
export async function updateProject(id: string, input: Record<string, unknown>): Promise<Project> {
  return (await api.patch(`/projects/${id}`, input)).data;
}
export async function uploadDocument(
  id: string, file: File, docCategory?: string,
): Promise<DocumentDto> {
  const form = new FormData();
  form.append('file', file);
  if (docCategory) form.append('docCategory', docCategory);
  return (await api.post(`/projects/${id}/documents`, form)).data;
}
export async function deleteDocument(id: string, docId: string): Promise<void> {
  await api.delete(`/projects/${id}/documents/${docId}`);
}
export async function screenProject(id: string): Promise<Run> {
  return (await api.post(`/projects/${id}/screen`)).data;
}
export async function listRuns(id: string): Promise<Run[]> {
  return (await api.get(`/projects/${id}/runs`)).data;
}
export async function latestRun(id: string): Promise<RunDetail | null> {
  const res = await api.get(`/projects/${id}/runs/latest`, { validateStatus: (s) => s === 200 || s === 204 });
  return res.status === 204 ? null : res.data;
}
export async function submitToEplanla(id: string): Promise<Project> {
  return (await api.post(`/projects/${id}/submit-eplanla`)).data;
}
export function reportUrl(id: string): string {
  return `/api/projects/${id}/report`;
}
export async function getRun(runId: string): Promise<RunDetail> {
  return (await api.get(`/runs/${runId}`)).data;
}
export async function flagFinding(findingId: string, comment: string): Promise<void> {
  await api.post(`/runs/findings/${findingId}/flag`, { comment });
}
