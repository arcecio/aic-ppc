import axios from 'axios';

/**
 * Axios instance for the API. Base URL is same-origin `/api` (Vite proxy in dev,
 * nginx in Docker). The JWT is injected from localStorage on every request; a
 * 401 clears it and bounces to login.
 */
export const api = axios.create({ baseURL: '/api' });

const TOKEN_KEY = 'aip_token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}
export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}
export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401 && getToken()) {
      clearToken();
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login';
      }
    }
    return Promise.reject(err);
  },
);

/** Extracts a human-readable message from an axios error. */
export function apiError(err: unknown): string {
  if (axios.isAxiosError(err)) {
    return err.response?.data?.message || err.message || 'Request failed';
  }
  return 'Unexpected error';
}
