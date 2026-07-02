import { api } from './client';
import type { Parcel, PermitType } from '../types';

export async function getPermitTypes(): Promise<PermitType[]> {
  return (await api.get('/reference/permit-types')).data;
}
export async function getPermitType(code: string): Promise<PermitType> {
  return (await api.get(`/reference/permit-types/${code}`)).data;
}
export async function searchParcels(q: string): Promise<Parcel[]> {
  return (await api.get('/reference/parcels', { params: { q } })).data;
}
