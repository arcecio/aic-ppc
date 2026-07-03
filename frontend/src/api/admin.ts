import { api } from './client';
import type { ScreeningRule } from '../types';

export async function listScreeningRules(): Promise<ScreeningRule[]> {
  return (await api.get('/admin/screening-rules')).data;
}
export async function updateScreeningRule(id: string, input: Record<string, unknown>): Promise<ScreeningRule> {
  return (await api.put(`/admin/screening-rules/${id}`, input)).data;
}
export async function createScreeningRule(input: Record<string, unknown>): Promise<ScreeningRule> {
  return (await api.post('/admin/screening-rules', input)).data;
}
export async function listClearanceRules() {
  return (await api.get('/admin/clearance-rules')).data;
}
export async function listApiClients() {
  return (await api.get('/admin/api-clients')).data;
}
export async function createApiClient(name: string, webhookUrl?: string) {
  return (await api.post('/admin/api-clients', { name, webhookUrl })).data;
}
export async function listUsers() {
  return (await api.get('/admin/users')).data;
}
export async function setUserRole(id: string, role: string) {
  return (await api.patch(`/admin/users/${id}/role`, { role })).data;
}
export interface AuditFilters {
  page?: number;
  size?: number;
  actorType?: string;
  actor?: string;
  action?: string;
  entityType?: string;
  entityId?: string;
  from?: string;
  to?: string;
}
export async function auditLog(filters: AuditFilters = {}) {
  return (await api.get('/admin/audit', { params: filters })).data;
}
