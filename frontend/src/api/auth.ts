import { api } from './client';
import type { AuthResponse, User } from '../types';

export async function login(email: string, password: string): Promise<AuthResponse> {
  return (await api.post('/auth/login', { email, password })).data;
}
export async function register(input: {
  email: string; password: string; name: string; organization?: string;
}): Promise<AuthResponse> {
  return (await api.post('/auth/register', input)).data;
}
export async function me(): Promise<User> {
  return (await api.get('/auth/me')).data;
}
