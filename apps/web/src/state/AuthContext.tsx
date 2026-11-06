import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import type { AuthUser, OAuthProviderStatus } from './types';
import { ApiError, apiRequest } from '../lib/api';

interface AuthContextValue {
  user: AuthUser | null;
  providers: OAuthProviderStatus[];
  loading: boolean;
  apiReachable: boolean | null;
  refresh: () => Promise<void>;
  login: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  deleteAccount: (password?: string) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [providers, setProviders] = useState<OAuthProviderStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [apiReachable, setApiReachable] = useState<boolean | null>(null);

  const refresh = useCallback(async () => {
    try {
      const [me, providerList] = await Promise.all([
        apiRequest<{ user: AuthUser }>('/api/auth/me').catch((error: unknown) => {
          if (error instanceof ApiError && error.status === 401) return null;
          throw error;
        }),
        apiRequest<{ providers: OAuthProviderStatus[] }>('/api/auth/providers').catch(() => null),
      ]);
      setUser(me?.user ?? null);
      if (providerList) setProviders(providerList.providers);
      setApiReachable(true);
    } catch {
      setApiReachable(false);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const login = useCallback(async (email: string, password: string) => {
    const result = await apiRequest<{ user: AuthUser }>('/api/auth/login', {
      method: 'POST',
      body: { email, password },
    });
    setUser(result.user);
    setApiReachable(true);
  }, []);

  const register = useCallback(async (name: string, email: string, password: string) => {
    const result = await apiRequest<{ user: AuthUser }>('/api/auth/register', {
      method: 'POST',
      body: { name, email, password },
    });
    setUser(result.user);
    setApiReachable(true);
  }, []);

  const logout = useCallback(async () => {
    await apiRequest('/api/auth/logout', { method: 'POST' });
    setUser(null);
  }, []);

  const deleteAccount = useCallback(async (password?: string) => {
    await apiRequest('/api/account', { method: 'DELETE', body: password ? { password } : {} });
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ user, providers, loading, apiReachable, refresh, login, register, logout, deleteAccount }),
    [user, providers, loading, apiReachable, refresh, login, register, logout, deleteAccount],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
