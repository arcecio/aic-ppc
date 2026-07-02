import { api } from './client';
import type { Analytics, ProjectSummary, Run, RunDetail } from '../types';

export async function getAnalytics(): Promise<Analytics> {
  return (await api.get('/staff/analytics')).data;
}
export async function staffProjects(): Promise<ProjectSummary[]> {
  return (await api.get('/staff/projects')).data;
}
export async function staffRuns(status?: string): Promise<Run[]> {
  return (await api.get('/staff/runs', { params: { status } })).data;
}
export async function staffRun(runId: string): Promise<RunDetail> {
  return (await api.get(`/staff/runs/${runId}`)).data;
}
export async function reviewFinding(findingId: string, disposition: string, comment?: string) {
  return (await api.post(`/staff/findings/${findingId}/review`, { disposition, comment })).data;
}
export async function reviewClearance(clearanceId: string, disposition: string, comment?: string) {
  return (await api.post(`/staff/clearances/${clearanceId}/review`, { disposition, comment })).data;
}
export async function listFeedback(status?: string) {
  return (await api.get('/staff/feedback', { params: { status } })).data;
}
