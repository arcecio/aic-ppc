import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { clearToken, setToken } from '../api/client';
import * as authApi from '../api/auth';
import type { AuthResponse, User } from '../types';

interface AuthContextValue {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (input: { email: string; password: string; name: string; organization?: string }) => Promise<void>;
  logout: () => void;
  isStaff: boolean;
  isAdmin: boolean;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        setUser(await authApi.me());
      } catch {
        setUser(null);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  function apply(res: AuthResponse) {
    setToken(res.token);
    setUser(res.user);
  }

  const value: AuthContextValue = {
    user,
    loading,
    login: async (email, password) => apply(await authApi.login(email, password)),
    register: async (input) => apply(await authApi.register(input)),
    logout: () => {
      clearToken();
      setUser(null);
    },
    isStaff: user?.role === 'STAFF' || user?.role === 'ADMIN',
    isAdmin: user?.role === 'ADMIN',
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
